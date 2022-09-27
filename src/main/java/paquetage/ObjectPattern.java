package paquetage;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.Var;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This models an object and its properties extracted from a Sparql query.
 * The work "object" is misleading : It does not mean the right side of an RDF triple.
 * Rather, in this context, an object is a subject variable of a triple which is given a rdf:type.
 * Its predicates and properties are all key-values (key is the predicate) parsed from the query.
 * The term "Instance" might be more appropriate.
 */
public class ObjectPattern {
    final static private Logger logger = Logger.getLogger(ObjectPattern.class);

    public String ClassName;
    public String VariableName;

    String CurrentNamespace;
    String ShortClassName;

    public ArrayList<PredicateObjectPair> Members = new ArrayList<>();

    /**
     * This is for debugging purpose. An object can be created with a single call.
     * @param variableName
     * @param className
     */
    ObjectPattern(String variableName, String className) {
        ClassName = className;
        VariableName = variableName;
    }

    ObjectPattern(String variableName) {
        this(variableName, null);
    }

    // TODO: ObjectName must be a Variable or Value. See rdf4j
    public static class PredicateObjectPair{
        public String Predicate;
        public boolean IsVariableObject;
        public String ObjectContent;
        public String ShortPredicate;

        PredicateObjectPair(String predicate, boolean isVariableObject, String objectContent) {
            Predicate = predicate;
            IsVariableObject = isVariableObject;
            ObjectContent = objectContent;
        }
    }

    public void AddPredicateObjectPair(String predicate, boolean isVariableObject, String objectContent)
    {
        PredicateObjectPair predicateObjectPair = new PredicateObjectPair(predicate, isVariableObject, objectContent);
        Members.add(predicateObjectPair);
    }

    public String toString() {
        return "className=" + ClassName + " VariableName=" + VariableName;
    }

    /** If some predicates are prefixed with the class name, it is extracted, checked if it is unique,
     * and used to deduce the class of the instance if it is not given.
     * If the class name of the instance is given, then it must be identical to the deduced one.
     */
    public void PreparePattern() throws Exception
    {
        // The namespace must be the same for all predicates and the class.
        CurrentNamespace = null;
        // This will always be null if the properties are not prefixed with the class name.
        // This is OK of the type is given with a triple with rdf:type as predicate.
        String deducedClassName = null;

        // Now, split the variables of this object, between:
        // - the variables known at this stage from the previous queries, which can be used in the "WHERE" clause,
        // - the variables which are not known yet, and returned by this WQL query.
        // The variable representing the object is selected anyway and contains the WMI relative path.
        for(ObjectPattern.PredicateObjectPair keyValue: Members) {
            WmiOntology.NamespaceTokenPair namespacedPredicate = WmiOntology.SplitToken(keyValue.Predicate);
            String shortPredicate = namespacedPredicate.Token;
            if(CurrentNamespace == null) {
                CurrentNamespace = namespacedPredicate.nameSpace;
            } else {
                if(!CurrentNamespace.equals(namespacedPredicate.nameSpace)) {
                    throw new RuntimeException("Different namespaces:"+CurrentNamespace+"!="+namespacedPredicate.nameSpace);
                }
            }

            // Maybe the predicate is prefixed with the class name, for example "CIM_Process.Handle".
            // If so, the class name is deduced and will be compared.
            String[] splitPredicate = shortPredicate.split("\\.");
            if(splitPredicate.length > 1) {
                if (splitPredicate.length == 2) {
                    String predicateDotPrefix = splitPredicate[0];
                    shortPredicate = splitPredicate[1];
                    if(deducedClassName == null)
                        deducedClassName = predicateDotPrefix;
                    else {
                        if(!deducedClassName.equals(predicateDotPrefix)) {
                            throw new Exception("Different predicates prefixes:" + shortPredicate + "/" + deducedClassName);
                        }
                    }
                } else {
                    throw new Exception("Too many dots in invalid predicate:" + shortPredicate);
                }
            }
            keyValue.ShortPredicate = shortPredicate;
        }

        if(ClassName == null) {
            // Maybe the class is not given.
            if(deducedClassName == null) {
                // This is acceptable because it might work in the further Sparql execution.
                logger.debug("Class name is null and cannot be deduced.");
                ShortClassName = null;
            } else {
                logger.debug("Short class name deduced to " + deducedClassName);
                ShortClassName = deducedClassName;
            }
        }  else {
            // If the class is explicitly given, it must be correct.
            if (!ClassName.contains("#")) {
                throw new Exception("Invalid class name:" + ClassName);
            }
            // Example: className = "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_Process"
            WmiOntology.NamespaceTokenPair pairNamespaceToken = WmiOntology.SplitToken(ClassName);
            ShortClassName = pairNamespaceToken.Token;
            if (deducedClassName != null) {
                // If the class is explicitly given, and also is the prefix of some attributes.
                if (!ShortClassName.equals(deducedClassName)) {
                    throw new Exception("Different short class=" + ShortClassName + " and deduced=" + deducedClassName);
                }
            }
            if (CurrentNamespace != null) {
                if (!CurrentNamespace.equals(pairNamespaceToken.nameSpace)) {
                    throw new RuntimeException("Different namespaces:" + CurrentNamespace + "!=" + pairNamespaceToken.nameSpace);
                }
            } else {
                CurrentNamespace = pairNamespaceToken.nameSpace;
            }
        }

        if(CurrentNamespace != null) {
            // A class name is need to run WQL queries, and also its WMI namespace.
            WmiOntology.CheckValidNamespace(CurrentNamespace);
        } else {
            // Maybe this is not a WMI-style IRI, so there is no WMI namespace.
            logger.debug("The namespace could not be found in:" + ClassName);
        }
    }

    /** This receives a list of StatementPattern taken from a SparqQuery and creates a map of ObjectPattern,
     * whose key is the variable name of the common subject of these statements.
     * @param visitorPatternsRaw
     * @return
     */
    public static Map<String, ObjectPattern> PartitionBySubject(List<StatementPattern> visitorPatternsRaw) {
        HashMap<String, ObjectPattern> patternsMap = new HashMap<>();
        for (StatementPattern myPattern : visitorPatternsRaw) {
            Var subject = myPattern.getSubjectVar();
            String subjectName = subject.getName();
            logger.debug("subjectName=" + subjectName);
            if (subject.isConstant()) {
                logger.warn("Constant subject:" + subjectName);
                continue;
            }

            Var predicate = myPattern.getPredicateVar();
            Var object = myPattern.getObjectVar();
            // TODO: Try comparing the nodes instead of the strings, if this is possible. It could be faster.
            Value predicateValue = predicate.getValue();
            // If the predicate is not usable, continue without creating a pattern.
            if (predicateValue == null) {
                logger.warn("Predicate is null");
                continue;
            }
            String predicateStr = predicateValue.stringValue();

            if (subject.isAnonymous()) {
                logger.warn("Anonymous subject:" + subjectName);
                    /* Anonymous nodes due to fixed-length paths should be processed by creating an anonymous variable.
                    but this is not implemented yet for data later loaded from WMI.
                    This occurs with triples like:
                    "^cimv2:Win32_DependentService.Dependent/cimv2:Win32_DependentService.Antecedent"
                    or, on top of an ArbitraryLengthPath:
                    "?service1 (^cimv2:Win32_DependentService.Dependent/cimv2:Win32_DependentService.Antecedent)+ ?service2"

                    However, with triples whose instance are unrelated to WMI, this is OK. Like:
                    "cimv2:Win32_Process.Handle rdfs:label ?label"
                    */
                if (predicateStr.startsWith(WmiOntology.namespaces_url_prefix)) {
                    throw new RuntimeException("Anonymous WMI subjects are not allowed yet.");
                }
            }

            ObjectPattern refPattern;
            if (!patternsMap.containsKey(subjectName)) {
                refPattern = new ObjectPattern(subjectName);
                patternsMap.put(subjectName, refPattern);
            } else {
                refPattern = patternsMap.get(subjectName);
            }

            // TODO: Make this comparison faster and simpler.
            if (predicateStr.equals(RDF.TYPE.stringValue())) {
                refPattern.ClassName = object.getValue().stringValue();
            }
            else if (predicateStr.equals(RDFS.SEEALSO.stringValue())) {
                logger.debug("""
                        Add SeeAlso scripts: They cannot be created with WMI.
                        To start with, any static RDF file is correct.
                        """);
            }
            else {
                if (object.isConstant()) {
                    if (!object.isAnonymous()) {
                        throw new RuntimeException("isConstant and not isAnonymous");
                    }
                    refPattern.AddPredicateObjectPair(predicateStr, false, object.getValue().stringValue());
                } else {
                    // If it is a variable.
                    if (object.isAnonymous()) {
                        throw new RuntimeException("not isConstant and isAnonymous");
                    }
                    refPattern.AddPredicateObjectPair(predicateStr, true, object.getName());
                }
            }
        }
        logger.debug("Generated patterns: " + Long.toString(patternsMap.size()));
        return patternsMap;
    } // for
}
