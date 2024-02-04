package paquetage;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.Var;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
Consider: https://spark.apache.org/docs/1.6.1/api/java/index.html?org/apache/spark/sql/DataFrame.html
"A distributed collection of data organized into named columns. "

The header will contain the name of each column, and possibly the type.
Columns can be moved around to construct new Solution objects when doing a Projection.
These columns have the same number of elements.

To ease the transition:
- Adding the first row creates the header, which is checked at subsequent rows additions.
- A row is only a transient type used for insertion.
*/

public class Solution implements Iterable<Solution.Row> {
    final static private Logger logger = Logger.getLogger(Solution.class);

    private static ValueFactory solutionFactory = SimpleValueFactory.getInstance();

    IRI dereferencePredicate(Var predicate, Row row) {
        if(predicate.isConstant()) {
            return Values.iri(predicate.getValue().stringValue());
        }
        String variablePredicate = predicate.getName();
        logger.debug("variablePredicate=" + variablePredicate);
        String predicateShortValue = row.getStringValue(variablePredicate);
        logger.debug("predicateShortValue=" + predicateShortValue);
        // "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#" + predicateShortValue;
        String predicateValue = WmiOntology.NamespaceTermToIRI("ROOT\\CIMV2", predicateShortValue);
        logger.debug("predicateValue=" + predicateValue);
        IRI predicateIRI = Values.iri(predicateValue);
        return predicateIRI;
    }

    /** This takes a BGP as parsed from the original Sparql query, and replace the variables with the ones calculated
     * from WMI.
     * The resulting triples will be inserted in an RDF repository.
     *
     * TODO: When adding a predicate value in a statement, consider RDF.VALUE (rdf:value) to add more information
     * TODO: such as the unit for a numerical type. Units are sometimes available from WMI.
     *
     * FIXME: JoinExpressionNode is the top-level node type of expressions.
     *
     * @param generatedTriples
     * @param myPattern
     * @throws Exception
     */
    void patternToStatements(List<Statement> generatedTriples, StatementPattern myPattern) throws Exception {
        Var subject = myPattern.getSubjectVar();
        String subjectName = subject.getName();
        Var predicate = myPattern.getPredicateVar();
        Var object = myPattern.getObjectVar();
        String objectName = object.getName();
        Value objectValue = object.getValue();
        if(rowsList.isEmpty()) {
            logger.debug("No rows");
        } else {
            logger.debug("Rows.get(0).KeySet()=" + rowsList.get(0).keySet());
        }
        if (subject.isConstant()) {
            String subjectString = subject.getValue().stringValue();
            Resource resourceSubject = Values.iri(subjectString);

            if (object.isConstant()) {
                // One insertion only. Variables are not needed.
                String objectString = objectValue.stringValue();
                Resource resourceObject = Values.iri(objectString);
                // Maybe this value was not used in a "where" clause, so how can we return it ?
                // FIXME: And if the constant object was not found, we should not insert it.
                logger.error("CONST_OBJECT1: If the constant subject is not found, it must not be inserted:" + objectString);

                if(!predicate.isConstant()) {
                    logger.debug("Predicate must be constant with constant object:" + predicate + " subjectName=" + subjectName + ". Leaving.");
                }
                IRI predicateIri = Values.iri(predicate.getValue().stringValue());

                generatedTriples.add(solutionFactory.createStatement(
                        resourceSubject,
                        predicateIri,
                        resourceObject));
            } else {
                // Only the object changes for each row.
                for(Row row : rowsList) {
                    ValueTypePair objectWmiValueType = row.tryValueType(objectName);
                    if(objectWmiValueType == null) {
                        // TODO: If this triple contains a variable calculated by WMI, maybe replicate it ?
                        logger.debug("Variable " + objectName + " not defined. Continuing to next pattern.");
                        continue;
                    }
                    IRI predicateIri = dereferencePredicate(predicate, row);
                    String objectString = objectWmiValueType.Value();
                    Value resourceObject = objectWmiValueType.Type() == ValueTypePair.ValueType.NODE_TYPE
                            ? Values.iri(objectString)
                            : objectWmiValueType.convertValueTypeToLiteral();
                    generatedTriples.add(solutionFactory.createStatement(
                            resourceSubject,
                            predicateIri,
                            resourceObject));
                }
            }
        } else {
            if (object.isConstant()) {
                // Only the subject changes for each row.
                String objectString = objectValue.stringValue();
                // Maybe this value was not used in a "where" clause, so how can we return it ?
                logger.debug("objectString=" + objectString + " isIRI=" + objectValue.isIRI());

                // TODO: Maybe this is already an IRI ? So, should not transform it again !
                Value resourceObject = objectValue.isIRI()
                        ? Values.iri(objectString)
                        : objectValue; // Keep the original type of the constant.

                for(Row row : rowsList) {
                    Resource resourceSubject = row.asIRI(subjectName);
                    IRI predicateIri = dereferencePredicate(predicate, row);

                    generatedTriples.add(solutionFactory.createStatement(
                            resourceSubject,
                            predicateIri,
                            resourceObject));
                }
            } else {
                // The subject and the object change for each row.
                // Special RDFS property, which does not exist in WMI but can be computed on-the-fly.
                logger.debug("predicate=" + predicate + ".");
                if(predicate.isConstant()) {
                    Value predicateValue = predicate.getValue();
                    logger.debug("predicateValue=" + predicateValue + ".");
                    String predicateValueString = predicateValue.stringValue();
                    logger.debug("subjectName=" + subjectName);
                    logger.debug("RDFS.LABEL.stringValue()=" + RDFS.LABEL.stringValue() + ".");
                    boolean isRdfsLabel = predicateValueString.equals(RDFS.LABEL.stringValue());
                    logger.debug("isRdfsLabel=" + isRdfsLabel);
                    for (Row row : rowsList) {
                        //logger.debug("row=" + row);
                        /* The subject might not be a node if the query comes from Wikidata GUI.
                        In this case, a label must be generated anyway, but WMI cannot help for this.
                        Strictly speaking, a resourceSubject should be generated based on the various predicates used
                        to get this value. But it is not possible to have them
                        */
                        Resource resourceSubject;
                        Value resourceObject;
                        if (isRdfsLabel) {
                            ValueTypePair subjectWmiValue = row.getValueType(subjectName);
                            if (subjectWmiValue == null) {
                                throw new RuntimeException("Null value for subjectName=" + subjectName);
                            }

                            logger.debug("subjectName=" + subjectName + " objectName=" + objectName);
                            logger.debug("predicate=" + predicate);

                            if (subjectWmiValue.Type() == ValueTypePair.ValueType.NODE_TYPE) {
                                resourceSubject = row.asIRI(subjectName);
                                logger.debug("resourceSubject=" + resourceSubject);
                                String valueObject = row.getStringValue(objectName);
                                // logger.debug("valueObject=" + valueObject);

                                resourceObject = Values.literal("\"" + valueObject + "\"" + "@en");
                                logger.debug("resourceObject.stringValue()=" + resourceObject.stringValue());
                            } else {
                                Value literalSubject = subjectWmiValue.convertValueTypeToLiteral();
                                /*
                                Ca ne peut pas marcher ici:
                                processUri="http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Process.Handle%3D%222404%22"
                                 ... qui est parse en ceci: \\LAPTOP-R89KG6V1\ROOT\CIMV2:Win32_Process.Handle="2404"
                                propertyUri="http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.CreationDate"
                                    <{processUri}> <{propertyUri}> ?date_value .
                                    ?date_value <http://www.w3.org/2000/01/rdf-schema#label> ?date_label .

                                D'une part ?date_value est un literal, mais doit aussi etre un iri.
                                Utilisons un predicat qui est un IRI.

                                Probleme: Ca n'existe que dans les associators !

                                The label could not be calculated.
                                */
                                resourceSubject = null;
                                resourceObject = null;
                                logger.error("Subject is not an IRI:" + subjectName + "=" + literalSubject.stringValue()
                                + " objectName=" + objectName);
                            }
                        } else {
                            resourceSubject = row.asIRI(subjectName);
                            ValueTypePair objectWmiValue = row.getValueType(objectName);
                            if (objectWmiValue == null) {
                                throw new RuntimeException("Null value for objectName=" + objectName);
                            }

                            if (objectWmiValue.Type() == ValueTypePair.ValueType.NODE_TYPE) {
                                resourceObject = row.asIRI(objectName);
                            } else {
                                resourceObject = objectWmiValue.convertValueTypeToLiteral();
                            }
                        }

                        if(resourceSubject != null) {
                            IRI predicateIri = dereferencePredicate(predicate, row);
                            generatedTriples.add(solutionFactory.createStatement(
                                    resourceSubject,
                                    predicateIri,
                                    resourceObject));
                        }
                    }
                } else {
                    logger.debug("Predicate must be constant with variable subject and variable object:"
                            + predicate + " subjectName=" + subjectName + ". Do nothing..");
                }
            }
        }
    }

    /** TODO: This should be faster. */
    /** TODO: Maybe not necessary, because ultimately, Solutions will just be created in "Join" nodes. */
    void appendSolution(Solution solution) {
        if(solution.rowsList.isEmpty()) {
            return;
        }
        if(rowsList.isEmpty()) {

            assert ! solution.header().contains(null);

            for(Row row: solution) {
                // TODO: Maybe just point to the solution, which normally should not change.
                add(row);
            }
            return;
        }

        Set<String> oldBindings = rowsList.isEmpty() ? null : rowsList.get(0).keySet();
        Set<String> newBindings = solution.rowsList.get(0).keySet();

        Set<String> newColumns = new HashSet<String>(newBindings);
        newColumns.removeAll(oldBindings);
        Set<String> oldColumns = new HashSet<String>(oldBindings);
        oldColumns.removeAll(newBindings);

        for(Solution.Row oldRow : rowsList) {
            oldRow.extendColumnsWithNull(newColumns);
        }
        for(Row row: solution) {
            // TODO: Find a faster way to compare bindings.
            Row newRow = row.shallowCopy();
            newRow.extendColumnsWithNull(oldColumns);
            add(newRow);
        }
    }

    /**
     * This is a row returned by a WMI select query.
     * For simplicity, each row contains all column names.
     * This is equivalent to a RDF4J BindingSet : a set of named value bindings, used to represent a query solution.
     * Values are indexed by name of the binding corresponding to the variables names in the projection of the query.
     *
     * TODO: When returning List<Row>, store the variable names once only, in a header. All rows have the same binding.
     */
    public static class Row {
        private Map<String, ValueTypePair> rowElements;

        /**
         * IRIS must look like this:
         * objectString=http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process isIRI=true
         *
         * But Wbem path are like that:
         * subjectString=\\LAPTOP-R89KG6V1\ROOT\CIMV2:Win32_Process.Handle="31640"
         *
         * So, Wbem path must be URL-encoded and prefixed.
         *
         * Beware that in references, that is, "Antecedent" or "Precedent" for an associators,
         * namespaces are in lowercase : Why does WMI do this ??
         *
         * @param varName
         * @return
         * @throws Exception
         */
        Resource asIRI(String varName) throws Exception {
            ValueTypePair pairValueType = getValueType(varName);
            if(pairValueType.Type() != ValueTypePair.ValueType.NODE_TYPE) {
                throw new Exception("This should be a NODE:" + varName + "=" + pairValueType.toDisplayString());
            }
            String valueString = pairValueType.Value();

            // Consistency check, for debugging.
            if(valueString.startsWith(WmiOntology.namespaces_url_prefix)) {
                throw new Exception("Double transformation in IRI:" + valueString);
            }

            // FIXME: It would me MUCH BETTER to store the namespace with the solution.
            // FIXME: Otherwise this parsing must be repeated over and over.
            // FIXME: But, are we sure it will always be the same namespace ?
            // FIXME: Or, when the value is a node, carry the namespace with it ?
            // FIXME: For the moment, the namespace must be extracted from the node.
            String namespaceExtracted = WmiProvider.extractNamespaceFromRef(valueString);
            // \\LAPTOP-R89KG6V1\ROOT\StandardCimv2:MSFT_NetIPAddress.CreationClassName="",Name="poB:DD;C:@D<n>nD==:@DB=:m/;@55;@55;55;",SystemCreationClassName="",SystemName=""

            Resource resourceValue;
            if(namespaceExtracted == null) {
                throw new Exception("Cannot extract namespace from:" + valueString);
            } else {
                resourceValue = WmiOntology.wbemPathToIri(namespaceExtracted, valueString);
            }

            return resourceValue;
        }
        public ValueTypePair tryValueType(String key) {
            ValueTypePair vtp = rowElements.get(key);
            // This is just a hint to check that wbem paths are correctly typed.
            if(vtp != null && !vtp.IsValid())
            {
                throw new RuntimeException("TryValueType: Key=" + key + " invalid:" + vtp.Value());
            }
            return vtp;
        }

        public ValueTypePair getValueType(String key) {
            if(key == null) {
                throw new RuntimeException("Input key is null");
            }
            ValueTypePair value = tryValueType(key);
            if(value == null) {
                throw new RuntimeException("Unknown variable " + key + ". Vars=" + keySet());
            }
            return value;
        }

        public String getStringValue(String key) {
            return getValueType(key).Value();
        }

        public long getLongValue(String key) {
            return Long.parseLong(getValueType(key).Value());
        }

        public void putString(String key, String str) {
            if(str == null) {
                logger.warn("PutString: Key=" + key + " null value");
            } else if(PresentUtils.hasWmiReferenceSyntax(str)) {
                // This is a hint which might not always work, but helps finding problems.
                throw new RuntimeException("PutString: Key=" + key + " looks like a node:" + str);
            }
            rowElements.put(key, new ValueTypePair(str, ValueTypePair.ValueType.STRING_TYPE));
        }
        public void putNode(String key, String str) {
            if(key == null) {
                throw new RuntimeException("Null key for str=" + str);
            }
            rowElements.put(key, new ValueTypePair(str, ValueTypePair.ValueType.NODE_TYPE));
        }

        public void putValueType(String key, ValueTypePair pairValueType) {
            // This is just a hint to detect that Wbem paths are correctly typed.
            // It also checks for "\\?\Volume{e88d2f2b-332b-4eeb-a420-20ba76effc48}\" which is not a path.
            if(pairValueType != null) {
                if (!pairValueType.IsValid()) {
                    throw new RuntimeException("PutValueType: Key=" + key + " looks like a node:" + pairValueType.toDisplayString());
                }
            }
            rowElements.put(key, pairValueType);
        }

        public long elementsSize() {
            return rowElements.size();
        }

        public Set<String> keySet() {
            return rowElements.keySet();
        }

        public boolean containsKey(String key) {
            return rowElements.containsKey(key);
        }

        public Row() {
            rowElements = new HashMap<>();
        }

        /**
         * This is for testing. An input row is inserted, then triples are created using an existing list of patterns.
         * @param elements
         */
        public Row(Map<String, ValueTypePair> elements) {
            rowElements = elements;
        }

        public String toString() {
            return rowElements.toString();
        }

        /** It needs a special function to serialize the value. */
        public String toValueString() {
            Function<Map.Entry<String, ValueTypePair>, String> converter = (Map.Entry<String, ValueTypePair> entry)
            -> {
                ValueTypePair entryValue = entry.getValue();
                return entry.getKey() + "=" + (entryValue == null ? "null" : entryValue.toDisplayString());
            };

            String result = "{" + rowElements.entrySet()
                    .stream()
                    .map(entry -> converter.apply(entry))
                    .collect(Collectors.joining(", ")) + "}";
            return result;
        }

        void extendColumnsWithNull(Set<String> newBindings) {
            for(String newColumn: newBindings) {
                if(rowElements.containsKey(newColumn)) {
                    throw new RuntimeException(("Row should not contain column:" + newColumn));
                }
                rowElements.put(newColumn, null);
            }
        }

        /** The elements are not copied, only the map is. */
        Row shallowCopy() {
            Row newRow = new Row();
            newRow.rowElements = new HashMap<>(rowElements);
            return newRow;
        }

        /** TODO: This is not very efficient and it would be better to merge all BGPs.
         * This happens when several joins and projections are not merged into a single join.
         * @param otherRow
         * @return
         */
        Row Merge(Row otherRow) {
            Row newRow = shallowCopy();

            for(Map.Entry<String, ValueTypePair> entry : otherRow.rowElements.entrySet()) {
                String newKey = entry.getKey();
                ValueTypePair newValue = entry.getValue();
                ValueTypePair previousValue = newRow.rowElements.get(newKey);
                if(previousValue == null) {
                    // The existing row does not have this key.
                    newRow.rowElements.put(newKey, newValue);
                } else {
                    if( ValueTypePair.identical(previousValue, newValue) ) {
                        logger.debug("Identical values for key:" + newKey);
                    } else {
                        // Duplicate key with different values.
                        logger.warn("Different values for key:" + newKey);
                        return null;
                    }
                }
            }
            return newRow;
        }
    }

    List<Row> rowsList;

    /** TODO: This will change with the implementation of Solution. */
    Set<String> header() {
        if(rowsList.isEmpty()) {
            return new HashSet<>();
        } else {
            return rowsList.get(0).keySet();
        }
    }

    Solution( /*List<String> header */ ) {
        rowsList = new ArrayList<>();
    }

    public Iterator<Row> iterator() {
        return rowsList.iterator();
    }

    void add(Row row) {
        rowsList.add(row);
    }

    long size() {
        return rowsList.size();
    }

    Stream<Row> stream() {
        return rowsList.stream();
    }

    Row get(int index) {
        return rowsList.get(index);
    }

    public String toString() {
        String result = "Elements:" + rowsList.size() + "\n";
        for(Row row : rowsList) {
            result += "\t" + row.toString() + "\n";
        }
        return result;
    }

    public Solution cartesianProduct(Solution otherSolution) {
        Solution resultSolution = new Solution();
        for(Row row : rowsList) {
            for(Row otherRow : otherSolution.rowsList) {
                Row mergedRow = row.Merge(otherRow);
                if(mergedRow != null) {
                    // It returns null if there is a common key with different values.
                    resultSolution.add(mergedRow);
                }
            }
        }
        return resultSolution;
    }
}
