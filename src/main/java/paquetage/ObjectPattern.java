package paquetage;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleIRI;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;
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

    private String RawClassName;
    public String VariableName;

    String CurrentNamespace = null;
    String ClassName = null;

    public ArrayList<PredicateObjectPair> Members = new ArrayList<>();

    /**
     * This is for debugging purpose. An object can be created with a single call.
     * @param variableName
     * @param className
     */
    ObjectPattern(String variableName, String className) {
        RawClassName = className;
        VariableName = variableName;
    }

    ObjectPattern(String variableName) {
        this(variableName, null);
    }

    public boolean IsValid() {
        if(ClassName == null) {
            return false;
        }
        return true;
    }

    // TODO: ObjectName must be a Variable or Value. See rdf4j
    public static class PredicateObjectPair{
        private String RawPredicate;
        public String variableName;
        public ValueTypePair ObjectContent;
        public String ShortPredicate;

        PredicateObjectPair(String rawPredicate, String variable, ValueTypePair objectContent) {
            if(!(variable == null ^ objectContent == null)) {
                throw new RuntimeException("The variable or the value must be null");
            }
            RawPredicate = rawPredicate;
            variableName = variable;
            ObjectContent = objectContent;
        }
    }

    public void AddPredicateObjectPairVariable(String predicate, String variable)
    {
        PredicateObjectPair predicateObjectPair = new PredicateObjectPair(predicate, variable, null);
        Members.add(predicateObjectPair);
    }

    public void AddPredicateObjectPairValue(String predicate, ValueTypePair objectContent)
    {
        PredicateObjectPair predicateObjectPair = new PredicateObjectPair(predicate, null, objectContent);
        Members.add(predicateObjectPair);
    }

    public String toString() {
        return "className=" + ClassName + " VariableName=" + VariableName;
    }

    /** If some predicates are prefixed with the class name, it is extracted, checked if it is unique,
     * and used to deduce the class of the instance if it is not given.
     * If the class name of the instance is given, then it must be identical to the deduced one.
     */
    public void PreparePattern() // throws Exception
    {
        // The namespace must be the same for all predicates and the class.
        if(CurrentNamespace != null) {
            throw new RuntimeException("CurrentNamespace should not have been set.");
        }
        if(ClassName != null) {
            throw new RuntimeException("ClassName should not have been set.");
        }

        // This will always be null if the properties are not prefixed with the class name.
        // This is OK of the type is given with a triple with rdf:type as predicate.
        String deducedClassName = null;

        // Now, split the variables of this object, between:
        // - the variables known at this stage from the previous queries, which can be used in the "WHERE" clause,
        // - the variables which are not known yet, and returned by this WQL query.
        // The variable representing the object is selected anyway and contains the WMI relative path.
        for(ObjectPattern.PredicateObjectPair keyValue: Members) {
            WmiOntology.NamespaceTokenPair namespacedPredicate = WmiOntology.SplitToken(keyValue.RawPredicate);
            if(namespacedPredicate == null) {
                logger.debug("Predicate:" + keyValue + "cannot be used for WMI");
                continue;
            }
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
                            throw new RuntimeException("Different predicates prefixes:" + shortPredicate + "/" + deducedClassName);
                        }
                    }
                } else {
                    throw new RuntimeException("Too many dots in invalid predicate:" + shortPredicate);
                }
            }
            keyValue.ShortPredicate = shortPredicate;
        }

        if(RawClassName == null) {
            // Maybe the class is not given.
            if(deducedClassName == null) {
                // This is acceptable because it might work in the further Sparql execution.
                logger.debug("Class name is null and cannot be deduced.");
                ClassName = null;
            } else {
                logger.debug("Short class name deduced to " + deducedClassName);
                ClassName = deducedClassName;
            }
        }  else {
            // If the class is explicitly given, it must be correct.
            if (!RawClassName.contains("#")) {
                throw new RuntimeException("Invalid raw class name:" + RawClassName);
            }
            // Example: className = "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_Process"
            WmiOntology.NamespaceTokenPair pairNamespaceToken = WmiOntology.SplitToken(RawClassName);
            ClassName = pairNamespaceToken.Token;
            if (deducedClassName != null) {
                // If the class is explicitly given, and also is the prefix of some attributes.
                if (!ClassName.equals(deducedClassName)) {
                    throw new RuntimeException("Different short class=" + ClassName + " and deduced=" + deducedClassName);
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
        if(ClassName == null && CurrentNamespace == null) {
            logger.warn("ObjectPattern:" + VariableName + " CANNOT be used for WMI.");

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

                    However, with triples whose predicate is unrelated to WMI, this is OK. Like:
                    "cimv2:Win32_Process.Handle rdfs:label ?label"
                    */
                if (predicateStr.startsWith(WmiOntology.namespaces_url_prefix)) {
                    logger.debug("Anonymous WMI subjects are now allowed.");
                }
            }

            ObjectPattern refPattern;
            if (!patternsMap.containsKey(subjectName)) {
                // If a triple with this subject was already met.
                refPattern = new ObjectPattern(subjectName);
                patternsMap.put(subjectName, refPattern);
            } else {
                refPattern = patternsMap.get(subjectName);
            }

            // TODO: Make this comparison faster than a string comparison.
            if (predicateStr.equals(RDF.TYPE.stringValue())) {
                refPattern.RawClassName = object.getValue().stringValue();
            }
            else if (predicateStr.equals(RDFS.SEEALSO.stringValue())) {
                logger.debug("TODO: Add SeeAlso scripts, any static RDF file is correct.");
            }
            else {
                if (object.isConstant()) {
                    if (!object.isAnonymous()) {
                        throw new RuntimeException("isConstant and not isAnonymous");
                    }
                    Value objectValue = object.getValue();
                    ValueTypePair.ValueType dataType = ValueToType(objectValue);

                    ValueTypePair vtp = new ValueTypePair(objectValue.stringValue(), dataType);
                    refPattern.AddPredicateObjectPairValue(predicateStr, vtp);
                } else {
                    // If it is a variable.
                    if (object.isAnonymous()) {
                        logger.debug("object not isConstant and isAnonymous:" + object.getName());
                        // throw new RuntimeException("object not isConstant and isAnonymous:" + object.getName());
                    }
                    refPattern.AddPredicateObjectPairVariable(predicateStr, object.getName());
                }
            }
        }
        logger.debug("Generated patterns: " + patternsMap.size());
        for(Map.Entry<String, ObjectPattern> entry : patternsMap.entrySet()) {
            entry.getValue().PreparePattern();
        }
        return patternsMap;
    } // PartitionBySubject

    /** This is used to transform a constant value parsed from a Sparql query, into a value compatible with WMI.
     * Specifically, this extracts the data type in XSD format, such as in ' "1"^^xsd:integer '.
     * */
    static private ValueTypePair.ValueType ValueToType(Value objectValue) {
        if (objectValue instanceof SimpleLiteral) {
            SimpleLiteral objectLiteral = (SimpleLiteral) objectValue;

            // NODE_TYPE is intentionaly not in this map.
            Map<String, ValueTypePair.ValueType> mapXmlToSolution = Map.of(
                    "string", ValueTypePair.ValueType.STRING_TYPE,
                    "dateTime", ValueTypePair.ValueType.DATE_TYPE,
                    "long", ValueTypePair.ValueType.INT_TYPE,
                    "integer", ValueTypePair.ValueType.INT_TYPE,
                    "float", ValueTypePair.ValueType.FLOAT_TYPE,
                    "double", ValueTypePair.ValueType.FLOAT_TYPE,
                    "boolean", ValueTypePair.ValueType.BOOL_TYPE);
            IRI datatype = objectLiteral.getDatatype();
            ValueTypePair.ValueType valueType = mapXmlToSolution.get(datatype.getLocalName());
            if(valueType == null) {
                throw new RuntimeException("Cannot map type:" + datatype);
            }
            return valueType;
        } else {
            SimpleIRI objectIRI = (SimpleIRI) objectValue;
            return ValueTypePair.ValueType.NODE_TYPE;
        }
    }
}
