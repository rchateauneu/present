package paquetage;

import org.apache.log4j.Logger;

import java.util.ArrayList;

/**
 * This models an object and its properties extracted from a Sparql query.
 * The work "object" is misleading : It does not mean the right side of an RDF triple.
 * Rather, in this context, an object is a subject variable of a triple which is given a rdf:type.
 * Its predicates and properties are all key-values (key is the predicate) parsed from the query.
 * The term "Instance" might be more appropriate.
 */
public class ObjectPattern {
    final static private Logger logger = Logger.getLogger(ObjectPattern.class);

    public String className;
    public String VariableName;

    String CurrentNamespace;
    String ShortClassName;

    public ArrayList<PredicateObjectPair> Members = new ArrayList<>();

    /**
     * This is for debugging purpose. An object can be created with a single call.
     * @param variableName
     * @param the_class
     */
    ObjectPattern(String variableName, String the_class) {
        className = the_class;
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
        return "className=" + className + " VariableName=" + VariableName;
    }

    /** If some predicates are prefixed with the class name, it is extracted, checked if it is unique,
     * and used to deduce the class of the instance if it is not given.
     * If the class name of the instance is given, the it must be identical to the deduced one.
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

        //ShortClassName = null;
        if(className == null) {
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
            if (!className.contains("#")) {
                throw new Exception("Invalid class name:" + className);
            }
            // Example: className = "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_Process"
            WmiOntology.NamespaceTokenPair pairNamespaceToken = WmiOntology.SplitToken(className);
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
            logger.debug("The namespace could not be found in:" + className);
        }
    }

}
