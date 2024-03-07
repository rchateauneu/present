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
    // This string is easy to find in log files.
    public static String ALL_PREDICATES = "**ALL**";

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
            return ValueTypePair.ValueType.NODE_TYPE;
        }
    }

    /*
    RDFS labels have usual equivalents in WMI.
    */
    static String replaceRDFSToWMI(String className, String beforeReplacement) {
        if(! beforeReplacement.startsWith("BeforeReplacement.")) {
            return null;
        }
        String shortName = beforeReplacement.split("\\.")[1];

        Set<String> classAssociators = Set.of("CIM_ProcessExecutable", "CIM_DirectoryContainsFile");
        if(classAssociators.contains(className)) {
            // FIXME: This is a temporary hack:
            // FIXME: Some classes - possibly the associators - do not have the members "Name" and "Description"
            if (shortName.equals("LABEL")) {
                return "BaseAddress";
            }
            if (shortName.equals("COMMENT")) {
                return "BaseAddress";
            }
        } else {
            if (shortName.equals("LABEL")) {
                return "Name";
            }
            if (shortName.equals("COMMENT")) {
                return "Description";
            }
        }
        throw new RuntimeException("Cannot map shortName=" + shortName);
    }

    static String beforeReplacementRDFSToWMI(String rawPredicate) {
        logger.debug("rawPredicate=" + rawPredicate);
        if (rawPredicate.equals(RDFS.LABEL.stringValue())) {
            return "BeforeReplacement." + "LABEL";
        }
        if (rawPredicate.equals(RDFS.COMMENT.stringValue())) {
            return "BeforeReplacement." + "COMMENT";
        }
        return null;
    }

    // TODO: ObjectName must be a Variable or Value. See rdf4j
    public static class PredicateObjectPair{
        public String predicateVariableName;
        public String variableName; // Where to store the result.
        public ValueTypePair objectContent;
        public String shortPredicate; // Used to construct the WQL query. The prefix "http:..." is chopped.

        PredicateObjectPair(String varPredicate, String shortPredicate, String variable, ValueTypePair objectContent) {
            if(!(variable == null ^ objectContent == null)) {
                throw new RuntimeException("The variable or the value must be null");
            }

            if(shortPredicate.equals(ALL_PREDICATES)) {
                if (varPredicate == null) {
                    throw new RuntimeException("Should not be null: varPredicate");
                }
            } else {
                // At this stage, this is an IRI.
                if (varPredicate != null) {
                    throw new RuntimeException("Should be null: varPredicate=" + varPredicate);
                }
            }
            logger.debug("varPredicate=" + varPredicate + " variable=" + variable + " shortPredicate=" + shortPredicate);
            predicateVariableName = varPredicate;
            variableName = variable;
            this.objectContent = objectContent;
            this.shortPredicate = shortPredicate;
        }
    } // PredicateObjectPair

    private String rawClassName;
    public String variableName; // Contains the subject if it is a variable, otherwise null.
    public String constantSubject; // Null if the subject is a variable.

    String subjectNamespace = null;
    String subjectClassname = null;

    /* The object might be the instance of a normal WMI class such as "CIM_Process" or "CIM_DataFile".
    This is the normal situation. But it could also be a WMI class or a WMI predicate : this is used
    for queries on the ontology itself.
    */
    Boolean isWMIObject = true;

    public ArrayList<PredicateObjectPair> membersList = new ArrayList<>();

    // Debugging and testing purpose only.
    public String toString() {
        return "subjectClassname=" + subjectClassname + " variableName=" + variableName;
    }

    /* This parses the predicates and extract the namespace, and optionally the class name of the subject. */
    private void determineNamespaceClassnameFromPatterns(List<StatementPattern> visitorPatternsRaw) {
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
                    rawClassName = objectValue.stringValue();
                } else {
                    logger.warn("Variable type. Not implemented yet");
                    rawClassName = null;
                }
                continue;
            }

            WmiOntology.NamespaceTokenPair namespacedPredicate = WmiOntology.splitIRI(predicateStr);
            if(namespacedPredicate == null) {
                logger.debug("myPattern.getObjectVar().toString():" + myPattern.getObjectVar().toString());
                logger.debug("Predicate:" + predicateStr + " cannot be used for WMI. Continue to next pattern.");
                continue;
            }

            String shortPredicate = namespacedPredicate.pairToken;
            logger.debug("shortPredicate=" + shortPredicate);
            if(namespacedPredicate.pairNamespace != null) {
                if (subjectNamespace == null) {
                    subjectNamespace = namespacedPredicate.pairNamespace;
                } else {
                    if (!subjectNamespace.equals(namespacedPredicate.pairNamespace)) {
                        throw new RuntimeException("Different namespaces:" + subjectNamespace
                                + "!=" + namespacedPredicate.pairNamespace
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

        if(rawClassName == null) {
            // Maybe the class is not given.
            if(deducedClassName == null) {
                // This is acceptable because it might work in the further Sparql execution.
                logger.debug("Class name is null and cannot be deduced.");
                subjectClassname = null;
            } else {
                logger.debug("Short class name deduced to " + deducedClassName);
                subjectClassname = deducedClassName;
            }
        }  else {
            // If the class is explicitly given, it must be correct.
            if (!rawClassName.contains("#")) {
                throw new RuntimeException("Invalid raw class name:" + rawClassName);
            }
            // Example: className = "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_Process"
            WmiOntology.NamespaceTokenPair pairNamespaceToken = WmiOntology.splitIRI(rawClassName);
            if(pairNamespaceToken == null) {
                logger.debug("Not a WMI class : RawClassName=" + rawClassName);
                subjectNamespace = null;
                subjectClassname = null;
            } else {
                subjectClassname = pairNamespaceToken.pairToken;
                if (deducedClassName != null) {
                    // If the class is explicitly given, and also is the prefix of some attributes.
                    if (!subjectClassname.equals(deducedClassName)) {
                        throw new RuntimeException("Different short class=" + subjectClassname + " and deduced=" + deducedClassName);
                    }
                }
                if (subjectNamespace != null) {
                    if (!subjectNamespace.equals(pairNamespaceToken.pairNamespace)) {
                        throw new RuntimeException("Different namespaces:" + subjectNamespace + "!=" + pairNamespaceToken.pairNamespace);
                    }
                } else {
                    subjectNamespace = pairNamespaceToken.pairNamespace;
                }
            }
        }

        if(subjectNamespace != null) {
            // A class name is need to run WQL queries, and also its WMI namespace.
            WmiProvider.checkValidNamespace(subjectNamespace);
        } else {
            // Maybe this is not a WMI-style IRI, so there is no WMI namespace.
            logger.debug("The namespace could not be found in:" + subjectClassname);
        }
        if(subjectClassname == null && subjectNamespace == null) {
            logger.warn("ObjectPattern:" + variableName + " CANNOT be used for WMI: Class and namespace are unknown.");
        }
    }

    static private void checkVariableNameSyntax(String variableName) {
        boolean hasNonAlpha = variableName.matches("^.*[^a-zA-Z0-9_].*$");
        if(hasNonAlpha) {
            throw new RuntimeException("Non-alphanumeric variable name:" + variableName);
        }
    }

    public int compareTo(ObjectPattern other) {
        if(this.variableName == null) {
            if(other.variableName == null) {
                return this.constantSubject.compareTo(other.constantSubject);
            }
            else {
                return -1;
            }
        } else {
            if(other.variableName == null) {
                return 1;
            }
            else {
                return this.variableName.compareTo(other.variableName);
            }
        }
    }

    static public void Sort(List<ObjectPattern> patternsArray) {
        // patternsArray.sort(Comparator.comparing(s -> s.VariableName));
        Collections.sort(patternsArray);
        for(ObjectPattern obj:patternsArray) {
            logger.debug("obj.VariableName=" + obj.variableName + " obj.ConstantSubject=" + obj.constantSubject);
        }
    }

    ObjectPattern(String subjectName, boolean isConstant, List<StatementPattern> visitorPatternsRaw) {
        // This will always be null if the properties are not prefixed with the class name.
        // This is OK of the type is given with a triple with rdf:type as predicate.
        // FIXME: It would be possible to deduce the type of the subject,
        // FIXME: if it is used as an object in another pattern.
        determineNamespaceClassnameFromPatterns(visitorPatternsRaw);

        // If the subject is constant, the namespace and the class can be extracted.
        if(isConstant) {
            if(subjectClassname == null || subjectNamespace == null) {
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
            WmiOntology.NamespaceTokenPair splitSubjectIri = WmiOntology.splitIRI(subjectName);
            logger.debug("Extracted splitSubjectIri.nameSpace=" + splitSubjectIri.pairNamespace
                + " splitSubjectIri.Token=" + splitSubjectIri.pairToken);
            if (splitSubjectIri == null) {
                throw new RuntimeException("Constant subject:" + subjectName + " cannot be used for WMI");
            }
            if (subjectNamespace == null) {
                subjectNamespace = splitSubjectIri.pairNamespace;
                logger.debug("From constant subject CurrentNamespace=" + subjectNamespace);
            } else {
                if(!splitSubjectIri.pairNamespace.equals(subjectNamespace)) {
                    throw new RuntimeException("Different namespace than the constant subject one:"
                        + splitSubjectIri.pairNamespace + " != " + subjectNamespace);
                }
            }
            if(subjectClassname == null) {
                subjectClassname = splitSubjectIri.pairToken;
                logger.debug("From constant subject ClassName=" + subjectClassname);
                switch(splitSubjectIri.pairTokenType) {
                    case INSTANCE_IRI:
                        // The subject is the IRI of an instance with a WMI class name.
                        WmiProvider.checkValidClassname(subjectClassname);
                        isWMIObject = true;
                        break;
                    case CLASS_IRI:
                        // The subject is a WMI class.
                        throw new RuntimeException("Class as subject not implemented yet:" + subjectClassname);
                    case PREDICATE_IRI:
                        // The subject is a WMI predicate, such as "WMI_Process.Handle".
                        WmiProvider.checkValidPredicate(subjectClassname);
                        isWMIObject = false;
                        break;
                    default:
                        throw new RuntimeException("Invalid TokenType:" + splitSubjectIri.pairTokenType);
                }
            } else {
                if(!splitSubjectIri.pairToken.equals(subjectClassname)) {
                    throw new RuntimeException("Different class than the constant subject one:"
                            + splitSubjectIri.pairToken + " != " + subjectClassname);
                }
            }

            variableName = null;
            constantSubject = WmiOntology.iriToWbemPath(subjectNamespace, subjectName);
        } else {
            checkVariableNameSyntax(subjectName);
            variableName = subjectName;
            constantSubject = null;
        }

        for (StatementPattern myPattern : visitorPatternsRaw) {
            logger.debug("subjectName=" + subjectName);

            Var predicate = myPattern.getPredicateVar();
            Var object = myPattern.getObjectVar();

            String predicateStr;
            String shortPredicate;

            if(predicate.isConstant()) {
                Value predicateValue = predicate.getValue();
                // If the predicate is not usable, continue without creating a pattern.
                if (predicateValue == null) {
                    logger.warn("Predicate is null");
                    continue;
                }
                /* String */
                predicateStr = predicateValue.stringValue();

                // Possible case of a non-WMI but RDFS column, for example:
                // keyValue.RawPredicate = "http://www.w3.org/2000/01/rdf-schema#label"
                // with a triple like: ?o <http://www.w3.org/2000/01/rdf-schema#label> ?ol .
                // The namespace is not known and returned as null.
                // The, WMI cannot do anything with it, but some generic properties like RDFS.LABEL or RDFS.COMMENT
                // can be fabricated, and the filtering si later done by Sparql.
                logger.debug("To be tested column:" + predicateStr);
                // RDFS.LABEL = "http://www.w3.org/2000/01/rdf-schema#label"

                // ClassName might not be known, for example if the subject is a class or a predicate.
                //String predicateRDFSToWMI = subjectClassname == null ? null : RDFSToWMI(subjectClassname, predicateStr);

                String predicateRDFSToWMI = beforeReplacementRDFSToWMI(predicateStr);
                if (predicateRDFSToWMI != null) {
                    logger.debug("RDFS column:" + predicateStr);
                    shortPredicate = predicateRDFSToWMI;
                    logger.debug("RDFS shortPredicate=" + shortPredicate);
                } else {
                    WmiOntology.NamespaceTokenPair namespacedPredicate = WmiOntology.splitIRI(predicateStr);
                    if (namespacedPredicate == null) {
                        logger.debug("Predicate:" + predicateStr + " unusable for WMI");
                        continue;

                        // ET POURTANT !!
                        //membersList.add(new PredicateObjectPair());
                        //RDFSToWMI();
                        //"Label"," "Description"
                        //public Map<String, List<String>> QueryData.variablesSynonyms;


                        /*
                        Si on ne connait pas la classe mais on sait que Label ou Comment,
                        on pourrait avancer un peu.
                        Il faudrait fabriquer le QueryData ou du moins le terminer,
                        seulement quand on connait le sujet.
                        */
                    }

                    /* String */
                    shortPredicate = namespacedPredicate.pairToken;
                    logger.debug("shortPredicate=" + shortPredicate);
                    if (namespacedPredicate.pairNamespace != null) {
                        if (subjectNamespace == null) {
                            subjectNamespace = namespacedPredicate.pairNamespace;
                        } else {
                            if (!subjectNamespace.equals(namespacedPredicate.pairNamespace)) {
                                throw new RuntimeException("Different namespaces:" + subjectNamespace
                                        + "!=" + namespacedPredicate.pairNamespace
                                        + ". predicateStr=" + predicateStr
                                        + ". shortPredicate=" + shortPredicate);
                            }
                        }
                    }
                    // Maybe the predicate is prefixed with the class name, for example "CIM_Process.Handle".
                    // If so, the class name is deduced and will be compared.
                    String[] splitPredicate = shortPredicate.split("\\.");
                    if (splitPredicate.length > 1) {
                        if (splitPredicate.length == 2) {
                            shortPredicate = splitPredicate[1];
                        } else {
                            throw new RuntimeException("Too many dots in invalid predicate:" + shortPredicate);
                        }
                    }
                }
            } else {
                // If the predicate is a variable.
                predicateStr = null;
                shortPredicate = null;
            }

            if((subjectNamespace == null) && (shortPredicate != null)) {
                logger.debug("Namespace cannot be deduced from the subject and the predicates."
                        +" predicateStr=" + predicateStr
                        +" shortPredicate=" + shortPredicate
                        +" variableName=" + variableName
                        +" object=" + object
                );
                // return;
                String objectVariableName = object.getName();
                PredicateObjectPair predicateObjectPair = new PredicateObjectPair(null, shortPredicate, objectVariableName, null);
                // FIXME: What happens if the same predicate appears several times ?
                membersList.add(predicateObjectPair);
            } else if(predicateStr == null) {
                assert shortPredicate == null;
                logger.debug("Getting classes from " + subjectNamespace);
                if (object.isConstant()) {
                    throw new RuntimeException("Cannot handle variable predicate and constant object for ClassName=" + subjectClassname);
                } else {
                    shortPredicate = ALL_PREDICATES;

                    String predicateVariableName = predicate.getName();
                    logger.debug("predicateVariableName=" + predicateVariableName);
                    if(predicate.getValue() != null) {
                        throw new RuntimeException("Predicate value should be null.");
                    }

                    String objectVariableName = object.getName();
                    PredicateObjectPair predicateObjectPair = new PredicateObjectPair(predicateVariableName, shortPredicate, objectVariableName, null);
                    // FIXME: What happens if the same predicate appears several times ?
                    membersList.add(predicateObjectPair);
                }
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
                    if(subjectNamespace != null) {
                        boolean isIri = WmiOntology.isWbemPath(subjectNamespace, subjectClassname, shortPredicate);
                        if (isIri) {
                            // This is a constant and should be an IRI surrounded by <> brackets in the Sparql query.
                            if (dataType == ValueTypePair.ValueType.NODE_TYPE) {
                                if (!strValue.startsWith("http://")) {
                                    // Double safety check.
                                    throw new RuntimeException("Inconsistent node type:" + strValue);
                                }
                                strValue = WmiOntology.iriToWbemPath(subjectNamespace, strValue);
                            } else {
                                throw new RuntimeException("Value should not be an IRI:" + strValue);
                            }
                        }
                    } else {
                        logger.debug("Constant object not in WMI:"+object.getValue().stringValue());
                    }

                    ValueTypePair vtp = new ValueTypePair(strValue, dataType);
                    PredicateObjectPair predicateObjectPair = new PredicateObjectPair(null, shortPredicate, null, vtp);
                    // FIXME: What happens if the same predicate appears several times ?
                    membersList.add(predicateObjectPair);
                } else {
                    // If it is a variable.
                    if (object.isAnonymous()) {
                        logger.debug("object not isConstant and isAnonymous:" + object.getName());
                    }
                    String variableName = object.getName();
                    PredicateObjectPair predicateObjectPair = new PredicateObjectPair(null, shortPredicate, variableName, null);
                    // FIXME: What happens if the same predicate appears several times ?
                    membersList.add(predicateObjectPair);
                }
            }
        } // for on triples.
    } // ObjectPattern constructor.

    /* This receives a list of BGP and groups them based on the subject. */
    private static Map<Pair<String, Boolean>, List<StatementPattern>> splitBySubject(List<StatementPattern> visitorPatternsRaw) {
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
    public static List<ObjectPattern> partitionBySubject(List<StatementPattern> visitorPatternsRaw) {
        List<ObjectPattern> patternsMap = new ArrayList<>();

        Map<Pair<String, Boolean>, List<StatementPattern>> splitSubject = splitBySubject(visitorPatternsRaw);

        for (Map.Entry<Pair<String, Boolean>, List<StatementPattern>> entry : splitSubject.entrySet()) {
            Pair<String, Boolean> subjectKey = entry.getKey();
            String subjectName = subjectKey.getLeft();
            Boolean isConstant = subjectKey.getRight();
            logger.debug("subjectName=" + subjectName + " isConstant=" + isConstant);
            ObjectPattern refPattern = new ObjectPattern(subjectName, isConstant, entry.getValue());

            if (refPattern.subjectNamespace == null) {
                /*
                Oui, mais on va peut etre decouvrir tardivement le namespace. Exemple:
			        <{processExecutableUri}> ?p ?objiri .
			        ?objiri <http://www.w3.org/2000/01/rdf-schema#label> ?objlab .

			        On essaye de l'ajouter quand meme mais:
			        - Il faut que ca soit evalue dans le bon ordre.
			        - Il faut qu'on rededuise la classe en parsant le sujet objiri qui etait un objet.
			    */
                logger.debug("Not adding key to patternsMap:" + entry.getKey());
                patternsMap.add(refPattern);
            } else if(refPattern.isWMIObject){
                patternsMap.add(refPattern);
            } else {
                logger.debug("Subject is not a WMI instance:" + refPattern.subjectClassname);
            }
        }
        return patternsMap;
    } // PartitionBySubject

}
