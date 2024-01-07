package paquetage;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.Var;

import java.util.*;

import static paquetage.WmiSelecter.wmiProvider;

/**
 * This models an object and its properties extracted from a Sparql query.
 * The work "object" is misleading : It does not mean the right side of an RDF triple.
 * Rather, in this context, an object is a subject variable of a triple which is given a rdf:type.
 * Its predicates and properties are all key-values (key is the predicate) parsed from the query.
 * The term "Instance" might be more appropriate.
 *
 * TODO: Consider naming it "Property Graph", because this transforms a BGP into a property graph.
 */
public class ObjectPattern implements Comparable<ObjectPattern> {
    final static private Logger logger = Logger.getLogger(ObjectPattern.class);

    /** This is used to transform a constant value parsed from a Sparql query, into a value compatible with WMI.
     * Specifically, this extracts the data type in XSD format, such as in ' "1"^^xsd:integer '.
     * */
    static private ValueTypePair.ValueType ValueToType(Value objectValue) {
        if (objectValue instanceof SimpleLiteral) {
            SimpleLiteral objectLiteral = (SimpleLiteral) objectValue;

            // NODE_TYPE is intentionally not in this map.
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
            // SimpleIRI objectIRI = (SimpleIRI) objectValue;
            return ValueTypePair.ValueType.NODE_TYPE;
        }
    }

    /*
    RDFS labels have usual equivalents in WMI
     */
    static String RDFSToWMI(String rawPredicate) {
        if(rawPredicate.equals(RDFS.LABEL.stringValue())) {
            return "Name";
        }
        if(rawPredicate.equals(RDFS.COMMENT.stringValue())) {
            return "Description";
        }
        return null;
    }

    // TODO: ObjectName must be a Variable or Value. See rdf4j
    public static class PredicateObjectPair{
        public String RawPredicate; // As taken from the Sparql query.
        public String variableName; // Where to store the result.
        public ValueTypePair ObjectContent;
        public String ShortPredicate; // Used to construct the WQL query. The prefix "http:..." is chopped.

        PredicateObjectPair(String rawPredicate, String shortPredicate, String variable, ValueTypePair objectContent) {
            if(!(variable == null ^ objectContent == null)) {
                throw new RuntimeException("The variable or the value must be null");
            }
            // At this stage, this is an IRI.
            if(!rawPredicate.startsWith("http:")) {
                throw new RuntimeException("Predicate must be an IRI:" + rawPredicate);
            }
            RawPredicate = rawPredicate;
            variableName = variable;
            ObjectContent = objectContent;
            ShortPredicate = shortPredicate;
        }
    } // PredicateObjectPair

    private String RawClassName;
    public String VariableName; // Contains the subject if it is a variable, otherwise null.
    public String ConstantSubject; // Null if the subject is a variable.

    String CurrentNamespace = null;
    String ClassName = null;

    /* The object might be the instance of a normal WMI class such as "CIM_Process" or "CIM_DataFile".
    This is the normal situation. But it could also be a WMI class or a WMI predicate : this is used
    for queries on the ontology itself.
    */
    Boolean IsWMIObject = true;

    public ArrayList<PredicateObjectPair> Members = new ArrayList<>();

    // Debugging and testing purpose only.
    public String toString() {
        return "className=" + ClassName + " VariableName=" + VariableName;
    }

    /* This parses the predicates and extract the namespace, and optionally the class name of the subject. */
    private void DetermineNamespaceClassnameFromPatterns(List<StatementPattern> visitorPatternsRaw) {
        // This will always be null if the properties are not prefixed with the class name.
        // This is OK of the type is given with a triple with rdf:type as predicate.
        String deducedClassName = null;

        for (StatementPattern myPattern : visitorPatternsRaw) {
            Var predicate = myPattern.getPredicateVar();
            // TODO: Try comparing the nodes instead of the strings, if this is possible. It could be faster.
            Value predicateValue = predicate.getValue();
            // If the predicate is not usable, continue without creating a pattern.
            if (predicateValue == null) {
                logger.warn("Predicate is null");
                continue;
            }
            String predicateStr = predicateValue.stringValue();
            if (predicateStr.equals(RDF.TYPE.stringValue())) {
                // If the type is explicitly given with the predicate rdf:Type.
                Var object = myPattern.getObjectVar();
                Value objectValue = object.getValue();
                if(objectValue != null) {
                    RawClassName = objectValue.stringValue();
                } else {
                    logger.warn("Variable type. Not implemenetd yet");
                    RawClassName = null;
                }
                continue;
            }

            WmiOntology.NamespaceTokenPair namespacedPredicate = WmiOntology.SplitIRI(predicateStr);
            if(namespacedPredicate == null) {
                logger.debug("Predicate:" + predicateStr + "cannot be used for WMI");
                continue;
            }

            String shortPredicate = namespacedPredicate.Token;
            logger.debug("shortPredicate=" + shortPredicate);
            if(namespacedPredicate.nameSpace != null) {
                if (CurrentNamespace == null) {
                    CurrentNamespace = namespacedPredicate.nameSpace;
                } else {
                    if (!CurrentNamespace.equals(namespacedPredicate.nameSpace)) {
                        throw new RuntimeException("Different namespaces:" + CurrentNamespace
                                + "!=" + namespacedPredicate.nameSpace
                                + ". predicateStr=" + predicateStr
                                + ". shortPredicate=" + shortPredicate);
                    }
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
        } // for on triples.

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
            WmiOntology.NamespaceTokenPair pairNamespaceToken = WmiOntology.SplitIRI(RawClassName);
            if(pairNamespaceToken == null) {
                logger.debug("Not a WMI class : RawClassName=" + RawClassName);
                CurrentNamespace = null;
                ClassName = null;
            } else {
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
        }

        if(CurrentNamespace != null) {
            // A class name is need to run WQL queries, and also its WMI namespace.
            WmiProvider.CheckValidNamespace(CurrentNamespace);
        } else {
            // Maybe this is not a WMI-style IRI, so there is no WMI namespace.
            logger.debug("The namespace could not be found in:" + ClassName);
        }
        if(ClassName == null && CurrentNamespace == null) {
            logger.warn("ObjectPattern:" + VariableName + " CANNOT be used for WMI.");
        }
    }

    static private void CheckVariableNameSyntax(String variableName) {
        boolean hasNonAlpha = variableName.matches("^.*[^a-zA-Z0-9_].*$");
        if(hasNonAlpha) {
            throw new RuntimeException("Non-alphanumeric variable name:" + variableName);
        }
    }

    public int compareTo(ObjectPattern other) {
        if(this.VariableName == null) {
            if(other.VariableName == null) {
                return this.ConstantSubject.compareTo(other.ConstantSubject);
            }
            else {
                return -1;
            }
        } else {
            if(other.VariableName == null) {
                return 1;
            }
            else {
                return this.VariableName.compareTo(other.VariableName);
            }
        }
    }

    static public void Sort(List<ObjectPattern> patternsArray) {
        // patternsArray.sort(Comparator.comparing(s -> s.VariableName));
        Collections.sort(patternsArray);
        for(ObjectPattern obj:patternsArray) {
            logger.debug("obj.VariableName=" + obj.VariableName + " obj.ConstantSubject=" + obj.ConstantSubject);
        }
    }

    ObjectPattern(String subjectName, boolean isConstant, List<StatementPattern> visitorPatternsRaw) {
        /*
        if(isConstant) {
            // Then it must be an IRI.
            if(!subjectName.startsWith("http:")) {
                throw new RuntimeException("Incorrect syntax for IRI:" + subjectName);
            }
            VariableName = null;
            // FIXME: We need the namespace to parse the IRI, but parsing the IRI gives the namespace...
            ConstantSubject = WmiOntology.IriToWbemPath("ROOT\\CIMV2", subjectName);
        } else {
            CheckVariableNameSyntax(subjectName);
            VariableName = subjectName;
            ConstantSubject = null;
        }
        */

        // This will always be null if the properties are not prefixed with the class name.
        // This is OK of the type is given with a triple with rdf:type as predicate.
        DetermineNamespaceClassnameFromPatterns(visitorPatternsRaw);

        // If the subject is constant, the namespace and the class can be extracted.
        if(isConstant) {
            if(ClassName == null || CurrentNamespace == null) {
                // These could be deduced from a predicate, possibly.
                logger.debug("Class or namespace could not be deduced yet");
            }

            // Then it must be an IRI.
            if(!subjectName.startsWith("http:")) {
                throw new RuntimeException("Incorrect syntax for IRI:" + subjectName);
            }

            logger.debug("subjectName=" + subjectName);
            // FIXME: The namespace is needed to extract the wbem path from the IRI.

            // This IRI can be a RDF/RDFS one, a WBEM class or predicate, or a WBEM instance.
            WmiOntology.NamespaceTokenPair subjectNamespacedPredicate = WmiOntology.SplitIRI(subjectName);
            logger.debug("Extracted subjectNamespacedPredicate.nameSpace=" + subjectNamespacedPredicate.nameSpace
                + " subjectNamespacedPredicate.Token=" + subjectNamespacedPredicate.Token);
            if (subjectNamespacedPredicate == null) {
                throw new RuntimeException("Constant subject:" + subjectName + " cannot be used for WMI");
            }
            if (CurrentNamespace == null) {
                CurrentNamespace = subjectNamespacedPredicate.nameSpace;
                logger.debug("From constant subject CurrentNamespace=" + CurrentNamespace);
            } else {
                if(!subjectNamespacedPredicate.nameSpace.equals(CurrentNamespace)) {
                    throw new RuntimeException("Different namespace than the constant subject one:"
                        + subjectNamespacedPredicate.nameSpace + " != " + CurrentNamespace);
                }
            }
            if(ClassName == null) {
                ClassName = subjectNamespacedPredicate.Token;
                logger.debug("From constant subject ClassName=" + ClassName);
                switch(subjectNamespacedPredicate.TokenType) {
                    case INSTANCE_IRI:
                        // The subject is the IRI of an instance with a WMI class name.
                        WmiProvider.CheckValidClassname(ClassName);
                        IsWMIObject = true;
                        break;
                    case CLASS_IRI:
                        // The subject is a WMI class.
                        throw new RuntimeException("Class as subject not implemented yet:" + ClassName);
                        //WmiProvider.CheckValidClassname(ClassName);
                        //IsWMIObject = false;
                        //break;
                    case PREDICATE_IRI:
                        // The subject is a WMI predicate, such as "WMI_Process.Handle".
                        WmiProvider.CheckValidPredicate(ClassName);
                        IsWMIObject = false;
                        break;
                    default:
                        throw new RuntimeException("Invalid TokenType:" + subjectNamespacedPredicate.TokenType);
                }
            } else {
                if(!subjectNamespacedPredicate.Token.equals(ClassName)) {
                    throw new RuntimeException("Different class than the constant subject one:"
                            + subjectNamespacedPredicate.Token + " != " + ClassName);
                }
            }

            VariableName = null;
            ConstantSubject = WmiOntology.IriToWbemPath(CurrentNamespace, subjectName);
        } else {
            CheckVariableNameSyntax(subjectName);
            VariableName = subjectName;
            ConstantSubject = null;
        }

        for (StatementPattern myPattern : visitorPatternsRaw) {
            logger.debug("subjectName=" + subjectName);

            Var predicate = myPattern.getPredicateVar();
            Var object = myPattern.getObjectVar();

            String predicateStr;
            String shortPredicate;

            if(!predicate.isConstant()) {
                // If the predicate is a variable.
                predicateStr = null;
                shortPredicate = null;
            } else {
                Value predicateValue = predicate.getValue();
                // If the predicate is not usable, continue without creating a pattern.
                if (predicateValue == null) {
                    logger.warn("Predicate is null");
                    continue;
                }
                /* String */
                predicateStr = predicateValue.stringValue();

                WmiOntology.NamespaceTokenPair namespacedPredicate = WmiOntology.SplitIRI(predicateStr);
                if (namespacedPredicate == null) {
                    logger.debug("Predicate:" + predicateStr + "cannot be used for WMI");
                    continue;
                }

                // Possible case of a non-WMI but RDFS column, for example:
                // keyValue.RawPredicate = "http://www.w3.org/2000/01/rdf-schema#label"
                // with a triple like: ?o <http://www.w3.org/2000/01/rdf-schema#label> ?ol .
                // The namespace is not known and returned as null.
                // The, WMI cannot do anything with it, but some generic properties like RDFS.LABEL or RDFS.COMMENT
                // can be fabricated, and the filtering si later done by Sparql.
                logger.debug("To be tested column:" + predicateStr);
                // RDFS.LABEL = "http://www.w3.org/2000/01/rdf-schema#label"

                String predicateRDFSToWMI = RDFSToWMI(predicateStr);
                if (predicateRDFSToWMI != null) {
                    logger.debug("RDFS column:" + predicateStr);
                /*
                Remplacer par "Name" et mettre "@en" a la fin ??????????????????????????????
                Ou bien mettre une pseudo-expression, style: "label = $Name+@en" ?
                Surtout, il faut retrouver ce pattern quand on fabrique la solution.
                 */
                    continue;
                }

                /* String */
                shortPredicate = namespacedPredicate.Token;
                logger.debug("shortPredicate=" + shortPredicate);
                if (namespacedPredicate.nameSpace != null) {
                    if (CurrentNamespace == null) {
                        CurrentNamespace = namespacedPredicate.nameSpace;
                    } else {
                        if (!CurrentNamespace.equals(namespacedPredicate.nameSpace)) {
                            throw new RuntimeException("Different namespaces:" + CurrentNamespace
                                    + "!=" + namespacedPredicate.nameSpace
                                    + ". predicateStr=" + predicateStr
                                    + ". shortPredicate=" + shortPredicate);
                        }
                    }
                }
                // Maybe the predicate is prefixed with the class name, for example "CIM_Process.Handle".
                // If so, the class name is deduced and will be compared.
                String[] splitPredicate = shortPredicate.split("\\.");
                if(splitPredicate.length > 1) {
                    if (splitPredicate.length == 2) {
                        shortPredicate = splitPredicate[1];
                    } else {
                        throw new RuntimeException("Too many dots in invalid predicate:" + shortPredicate);
                    }
                }
            }

            if(CurrentNamespace != null) {
                // If the namespace cannot be deduced from the subject and the predicate, cannot do anything.
                logger.debug("Namespace cannot be deduced from the sujbject and the predicates");
                return;
            }

            if(predicateStr == null) {
                /* The predicate is a variable.
                FIXME: Ajouter un triplet par predicat ? Mais il faudrait une Union.
                */
                assert shortPredicate == null;
                Map<String, WmiProvider.WmiClass> classes = wmiProvider.Classes(CurrentNamespace);
                WmiProvider.WmiClass wmiClass = classes.get(ClassName);
                for (Map.Entry<String, WmiProvider.WmiProperty> entry_property : wmiClass.Properties.entrySet()) {
                    String propertyName = entry_property.getKey();
                    logger.debug("propertyName=" + propertyName);
                }
                throw new RuntimeException("Cannot handle variable predicate for ClassName=" + ClassName);
                /*
                ValueTypePair vtp = new ValueTypePair(strValue, dataType);
                PredicateObjectPair predicateObjectPair = new PredicateObjectPair(predicateStr, shortPredicate, null, vtp);
                // FIXME: What happens if the same predicate appears several times ?
                Members.add(predicateObjectPair);
                */
            }
            // TODO: Make this comparison faster than a string comparison.
            else if (predicateStr.equals(RDF.TYPE.stringValue())) {
                // This has already been treated.
                continue;
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
                    String strValue = objectValue.stringValue();

                    /* Sets the data type expected by WBEM, if this is a constant passed from the Sparql query.
                    This is used for runtime validation, and overall to convert RDF IRIs to WBEM paths. */
                    if(CurrentNamespace != null) {
                        boolean isIri = WmiOntology.isWbemPath(CurrentNamespace, ClassName, shortPredicate);
                        if (isIri) {
                            // This is a constant and should be an IRI surrounded by <> brackets in the Sparql query.
                            if (dataType == ValueTypePair.ValueType.NODE_TYPE) {
                                if (!strValue.startsWith("http://")) {
                                    // Double safety check.
                                    throw new RuntimeException("Inconsistent node type:" + strValue);
                                }
                                strValue = WmiOntology.IriToWbemPath(CurrentNamespace, strValue);
                            } else {
                                throw new RuntimeException("Value should not be an IRI:" + strValue);
                            }
                        }
                    } else {
                        logger.debug("Constant object not in WMI:"+object.getValue().stringValue());
                    }

                    ValueTypePair vtp = new ValueTypePair(strValue, dataType);
                    PredicateObjectPair predicateObjectPair = new PredicateObjectPair(predicateStr, shortPredicate, null, vtp);
                    // FIXME: What happens if the same predicate appears several times ?
                    Members.add(predicateObjectPair);
                } else {
                    // If it is a variable.
                    if (object.isAnonymous()) {
                        logger.debug("object not isConstant and isAnonymous:" + object.getName());
                    }
                    String variableName = object.getName();
                    PredicateObjectPair predicateObjectPair = new PredicateObjectPair(predicateStr, shortPredicate, variableName, null);
                    // FIXME: What happens if the same predicate appears several times ?
                    Members.add(predicateObjectPair);
                }
            }
        } // for on triples.
    }

    private static Map<Pair<String, Boolean>, List<StatementPattern>> SplitBySubject(List<StatementPattern> visitorPatternsRaw) {
        Map<Pair<String, Boolean>, List<StatementPattern> > splitSubject = new HashMap();

        for (StatementPattern myPattern : visitorPatternsRaw) {
            Var subject = myPattern.getSubjectVar();
            String subjectName = subject.getName();
            logger.debug("subjectName=" + subjectName + " getValue()=" + subject.getValue());
            boolean isConstant = subject.isConstant();
            if (isConstant) {
                /* The subject, i.e. the WMI entity, might be an input because its properties are to be selected.
                This happens for example with Wikidata queries investigate a node.
                */
                logger.warn("Constant subject:" + subjectName + "=" + subject.getValue());
                // The constant value is not the name.
                subjectName = subject.getValue().stringValue();
            }
            if (subject.isAnonymous()) {
                logger.warn("Anonymous subject:" + subjectName);
            }

            Pair<String, Boolean> subjectKey = new ImmutablePair<>(subjectName, isConstant);

            if (!splitSubject.containsKey(subjectKey)) {
                // If a triple with this subject was not already met, then add it.
                List<StatementPattern> listSubject = new ArrayList();
                splitSubject.put(subjectKey, listSubject);
            }
            splitSubject.get(subjectKey).add(myPattern);
        }
        return splitSubject;
    }

    /** This receives a list of StatementPattern taken from a SparqQuery and creates a map of ObjectPattern,
     * whose key is the variable name of the common subject of these statements.
     * Only subjects belonging to a WMI class are kept.
     * @param visitorPatternsRaw
     * @return
     */
    public static List<ObjectPattern> PartitionBySubject(List<StatementPattern> visitorPatternsRaw) {
        List<ObjectPattern> patternsMap = new ArrayList<>();

        Map<Pair<String, Boolean>, List<StatementPattern>> splitSubject = SplitBySubject(visitorPatternsRaw);

        for (Map.Entry<Pair<String, Boolean>, List<StatementPattern>> entry : splitSubject.entrySet()) {
            Pair<String, Boolean> subjectKey = entry.getKey();
            String subjectName = subjectKey.getLeft();
            Boolean isConstant = subjectKey.getRight();
            logger.debug("subjectName=" + subjectName + " isConstant=" + isConstant);
            ObjectPattern refPattern = new ObjectPattern(subjectName, isConstant, entry.getValue());

            if (refPattern.CurrentNamespace == null) {
                logger.debug("Removing key:" + entry.getKey());
            } else if(refPattern.IsWMIObject){
                patternsMap.add(refPattern);
            } else {
                logger.debug("Subject is not a WMI instance:" + refPattern.ClassName);
            }
        }
        return patternsMap;
    } // PartitionBySubject

}
