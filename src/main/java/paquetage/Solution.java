package paquetage;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Values;
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

    private static ValueFactory factory = SimpleValueFactory.getInstance();

    /** This takes a BGP as parsed from the original Sparql query, and replace the variables with the ones calculated
     * from WMI.
     * The resulting triples will be inserted in an RDF repository.
     *
     * TODO: When adding a predicate value in a statement, consider RDF.VALUE (rdf:value) to add more information
     * TODO: such as the unit for a numerical type. Units are sometimes available from WMI.
     *
     * @param generatedTriples
     * @param myPattern
     * @throws Exception
     */
    void PatternToStatements(List<Statement> generatedTriples, StatementPattern myPattern) throws Exception {
        Var subject = myPattern.getSubjectVar();
        String subjectName = subject.getName();
        Var predicate = myPattern.getPredicateVar();
        if(!predicate.isConstant()) {
            logger.debug("Predicate is not constant:" + predicate);
            return;
        }
        IRI predicateIri = Values.iri(predicate.getValue().stringValue());
        Var object = myPattern.getObjectVar();
        String objectName = object.getName();
        Value objectValue = object.getValue();
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

                generatedTriples.add(factory.createStatement(
                        resourceSubject,
                        predicateIri,
                        resourceObject));
            } else {
                // Only the object changes for each row.
                for(Row row : Rows) {
                    ValueTypePair objectWmiValueType = row.TryValueType(objectName);
                    if(objectWmiValueType == null) {
                        // TODO: If this triple contains a variable calculated by WMI, maybe replicate it ?
                        logger.debug("Variable " + objectName + " not defined. Continuing to next pattern.");
                        continue;
                    }
                    String objectString = objectWmiValueType.Value();
                    Value resourceObject = objectWmiValueType.Type() == ValueTypePair.ValueType.NODE_TYPE
                            ? Values.iri(objectString)
                            : objectWmiValueType.ValueTypeToLiteral();
                    generatedTriples.add(factory.createStatement(
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

                for(Row row : Rows) {
                    Resource resourceSubject = row.AsIRI(subjectName);

                    generatedTriples.add(factory.createStatement(
                            resourceSubject,
                            predicateIri,
                            resourceObject));
                }
            } else {
                // The subject and the object change for each row.
                for(Row row: Rows) {
                    Resource resourceSubject = row.AsIRI(subjectName);
                    ValueTypePair objectWmiValue = row.GetValueType(objectName);
                    Value resourceObject;

                    if (objectWmiValue.Type() == ValueTypePair.ValueType.NODE_TYPE) {
                        resourceObject = row.AsIRI(objectName);
                    } else {
                        if (objectWmiValue == null) {
                            throw new RuntimeException("Null value for " + objectName);
                        }
                        resourceObject = objectWmiValue.ValueTypeToLiteral();
                    }

                    generatedTriples.add(factory.createStatement(
                            resourceSubject,
                            predicateIri,
                            resourceObject));
                }
            }
        }
    }

    /** TODO: This should be faster. */
    /** TODO: Maybe not necessary, because ultimately, Solutions which just be created in "Join" nodes. */
    void Append(Solution solution) {
        if(solution.Rows.isEmpty()) {
            return;
        }
        if(Rows.isEmpty()) {
            for(Row row: solution) {
                // TODO: Maybe just point to the solution, which normally should not change.
                add(row);
            }
            return;
        }

        Set<String> oldBindings = Rows.isEmpty() ? null : Rows.get(0).KeySet();
        Set<String> newBindings = solution.Rows.get(0).KeySet();

        Set<String> newColumns = new HashSet<String>(newBindings);
        newColumns.removeAll(oldBindings);
        Set<String> oldColumns = new HashSet<String>(oldBindings);
        oldColumns.removeAll(newBindings);

        for(Solution.Row oldRow : Rows) {
            oldRow.ExtendColumnsWithNull(newColumns);
        }
        for(Row row: solution) {
            // TODO: Find a faster way to compare bindings.
            Row newRow = row.ShallowCopy();
            newRow.ExtendColumnsWithNull(oldColumns);
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


        private Map<String, ValueTypePair> Elements;

        /**
         * IRIS must look like this:
         * objectString=http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process isIRI=true
         *
         * But Wbem path are like that:
         * subjectString=\\LAPTOP-R89KG6V1\ROOT\CIMV2:Win32_Process.Handle="31640"
         *
         * So, Wbem path must be URL-encoded and prefixed.
         *
         * @param varName
         * @return
         * @throws Exception
         */
        Resource AsIRI(String varName) throws Exception {
            ValueTypePair pairValueType = GetValueType(varName);
            if(pairValueType.Type() != ValueTypePair.ValueType.NODE_TYPE) {
                throw new Exception("This should be a NODE:" + varName + "=" + pairValueType.toDisplayString());
            }
            String valueString = pairValueType.Value();

            // Consistency check, for debugging.
            if(valueString.startsWith(WmiOntology.namespaces_url_prefix)) {
                throw new Exception("Double transformation in IRI:" + valueString);
            }
            Resource resourceValue = WmiOntology.WbemPathToIri(valueString);
            return resourceValue;
        }

        public ValueTypePair TryValueType(String key) {
            ValueTypePair vtp = Elements.get(key);
            // This is just a hint to check that wbem paths are correctly typed.
            if(vtp != null && !vtp.IsValid())
            {
                throw new RuntimeException("TryValueType: Key=" + key + " invalid:" + vtp.Value());
            }
            return vtp;
        }

        public ValueTypePair GetValueType(String key) {
            if(key == null) {
                throw new RuntimeException("Input variable is null");
            }
            ValueTypePair value = TryValueType(key);
            if(value == null) {
                throw new RuntimeException("Unknown variable " + key + ". Vars=" + KeySet());
            }
            return value;
        }

        public String GetStringValue(String key) {
            return GetValueType(key).Value();
        }

        public double GetDoubleValue(String key) {
            return Double.parseDouble(GetValueType(key).Value());
        }

        public long GetLongValue(String key) {
            return Long.parseLong(GetValueType(key).Value());
        }

        public void PutString(String key, String str) {
            if(str == null) {
                logger.warn("PutString: Key=" + key + " null value");
            } else if(PresentUtils.hasWmiReferenceSyntax(str)) {
                // This is a hint which might not always work, but helps finding problems.
                throw new RuntimeException("PutString: Key=" + key + " looks like a node:" + str);
            }
            Elements.put(key, new ValueTypePair(str, ValueTypePair.ValueType.STRING_TYPE));
        }
        public void PutNode(String key, String str) {
            Elements.put(key, new ValueTypePair(str, ValueTypePair.ValueType.NODE_TYPE));
        }
        public void PutLong(String key, String str) {
            Elements.put(key, new ValueTypePair(str, ValueTypePair.ValueType.INT_TYPE));
        }
        public void PutDate(String key, String str) {
            Elements.put(key, new ValueTypePair(str, ValueTypePair.ValueType.DATE_TYPE));
        }
        public void PutFloat(String key, String str) {
            Elements.put(key, new ValueTypePair(str, ValueTypePair.ValueType.FLOAT_TYPE));
        }

        public void PutValueType(String key, ValueTypePair pairValueType) {
            // This is just a hint to detect that Wbem paths are correctly typed.
            // It also checks for "\\?\Volume{e88d2f2b-332b-4eeb-a420-20ba76effc48}\" which is not a path.
            if(pairValueType != null) {
                if (!pairValueType.IsValid()) {
                    throw new RuntimeException("PutValueType: Key=" + key + " looks like a node:" + pairValueType.toDisplayString());
                }
            }
            Elements.put(key, pairValueType);
        }

        public long ElementsSize() {
            return Elements.size();
        }

        public Set<String> KeySet() {
            return Elements.keySet();
        }

        public boolean ContainsKey(String key) {
            return Elements.containsKey(key);
        }

        public Row() {
            Elements = new HashMap<>();
        }

        /**
         * This is for testing. An input row is inserted, then triples are created using an existing list of patterns.
         * @param elements
         */
        public Row(Map<String, ValueTypePair> elements) {
            Elements = elements;
        }

        public String toString() {
            return Elements.toString();
        }

        /** It needs a special function to serialize the value. */
        public String toValueString() {
            Function<Map.Entry<String, ValueTypePair>, String> converter = (Map.Entry<String, ValueTypePair> entry)
            -> {
                ValueTypePair entryValue = entry.getValue();
                return entry.getKey() + "=" + (entryValue == null ? "null" : entryValue.toDisplayString());
            };

            String result = "{" + Elements.entrySet()
                    .stream()
                    .map(entry -> converter.apply(entry))
                    .collect(Collectors.joining(", ")) + "}";
            return result;
        }

        void ExtendColumnsWithNull(Set<String> newBindings) {
            for(String newColumn: newBindings) {
                if(Elements.containsKey(newColumn)) {
                    throw new RuntimeException(("Row should not contain column:" + newColumn));
                }
                Elements.put(newColumn, null);
            }
        }

        /** The elements are not copied, only the map is. */
        Row ShallowCopy() {
            Row newRow = new Row();
            newRow.Elements = new HashMap<>(Elements);
            return newRow;
        }

        /** TODO: This is not very efficient and it would be better to merge all BGPs.
         * This happens when several joins and projections are not merged into a single join.
         * @param otherRow
         * @return
         */
        Row Merge(Row otherRow) {
            Row newRow = ShallowCopy();

            for(Map.Entry<String, ValueTypePair> entry : otherRow.Elements.entrySet()) {
                String newKey = entry.getKey();
                ValueTypePair newValue = entry.getValue();
                ValueTypePair previousValue = newRow.Elements.get(newKey);
                if(previousValue == null) {
                    // The existing row does not have this key.
                    newRow.Elements.put(newKey, newValue);
                } else {
                    if( ValueTypePair.identical(previousValue, newValue) ) {
                        logger.debug("Identical values for key:" + newKey);
                    } else {
                        // Duplicate key with different values.
                        logger.debug("Different values for key:" + newKey);
                        return null;
                    }
                }
            }
            return newRow;
        }
    }

    List<Row> Rows;

    /** TODO: This will change with the implementation of Solution. */
    Set<String> header() {
        if(Rows.isEmpty()) {
            return new HashSet<>();
        } else {
            return Rows.get(0).KeySet();
        }
    }

    Solution( /*List<String> header */ ) {
        Rows = new ArrayList<>();
    }

    public Iterator<Row> iterator() {
        return Rows.iterator();
    }

    void add(Row row) {
        Rows.add(row);
    }

    long size() {
        return Rows.size();
    }

    Stream<Row> stream() {
        return Rows.stream();
    }

    Row get(int index) {
        return Rows.get(index);
    }

    public String toString() {
        String result = "Elements:" + Rows.size() + "\n";
        for(Row row : Rows) {
            result += "\t" + row.toString() + "\n";
        }
        return result;
    }

    public Solution CartesianProduct(Solution otherSolution) {
        Solution resultSolution = new Solution();
        for(Row row : Rows) {
            for(Row otherRow : otherSolution.Rows) {
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
