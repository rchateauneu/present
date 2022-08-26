package paquetage;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;

import java.util.*;
import java.util.stream.Stream;

public class Solution {
    final static private Logger logger = Logger.getLogger(Solution.class);

    public List<String> Header; // Not used yet.

    /**
     * This is a row returned by a WMI select query.
     * For simplicity, each row contains all column names.
     * This is equivalent to a RDF4J BindingSet : a set of named value bindings, used to represent a query solution.
     * Values are indexed by name of the binding corresponding to the variables names in the projection of the query.
     *
     * TODO: When returning List<Row>, store the variable names once only, in a header. All rows have the same binding.
     */
    public static class Row {
        /** In this library, only the string value is used because this is what is needed for WQL.
         * WQL does not really manipulate floats or dates, so string conversion is OK.
         *
         * However, the type is needed when converting these values to RDF, specifically because of WBEM paths which
         * are transformed into RDF IRIs. Also, it might be needed to convert string values to RDF types: ints etc...
         * and for this, their original type is needed.
         *
         * @param Value
         * @param Type
         */
        public record ValueTypePair (String Value, GenericProvider.ValueType Type) {
            public static ValueTypePair Factory(String value) {
                return new ValueTypePair(value, GenericProvider.ValueType.STRING_TYPE);
            }

            public static ValueTypePair Factory(long value) {
                return new ValueTypePair(Long.toString(value), GenericProvider.ValueType.INT_TYPE);
            }
        };

        private Map<String, ValueTypePair> Elements;

        public ValueTypePair TryValueType(String key) {
            ValueTypePair vtp = Elements.get(key);
            // This is just a hint to check that wbem paths are correctly typed.
            if(vtp != null && vtp.Type != GenericProvider.ValueType.NODE_TYPE && vtp.Value != null
                    && vtp.Value.startsWith("\\\\")
                    && ! vtp.Value.startsWith("\\\\?\\")
            ) {
                throw new RuntimeException("TryValueType: Key=" + key + " looks like a node:" + vtp.Value);
            }
            return vtp;
        }

        public ValueTypePair GetValueType(String key) {
            ValueTypePair value = TryValueType(key);
            if(value == null) {
                throw new RuntimeException("Unknown variable " + key + ". Vars=" + KeySet());
            }
            return value;
        }

        public String GetStringValue(String key) {
            return GetValueType(key).Value;
        }

        public double GetDoubleValue(String key) {
            return Double.parseDouble(GetValueType(key).Value);
        }

        public long GetLongValue(String key) {
            return Long.parseLong(GetValueType(key).Value);
        }

        public void PutString(String key, String str) {
            if(str == null) {
                logger.warn("PutString: Key=" + key + " null value");
            } else if(str.startsWith("\\\\")) {
                // This is a hint which might not always work, but helps finding problems.
                throw new RuntimeException("PutString: Key=" + key + " looks like a node:" + str);
            }
            Elements.put(key, new ValueTypePair(str, GenericProvider.ValueType.STRING_TYPE));
        }
        public void PutNode(String key, String str) {
            Elements.put(key, new ValueTypePair(str, GenericProvider.ValueType.NODE_TYPE));
        }
        public void PutLong(String key, String str) {
            Elements.put(key, new ValueTypePair(str, GenericProvider.ValueType.INT_TYPE));
        }
        public void PutDate(String key, String str) {
            Elements.put(key, new ValueTypePair(str, GenericProvider.ValueType.DATE_TYPE));
        }
        public void PutFloat(String key, String str) {
            Elements.put(key, new ValueTypePair(str, GenericProvider.ValueType.FLOAT_TYPE));
        }

        public void PutValueType(String key, ValueTypePair pairValueType) {
            // This is just a hint to detect that Wbem paths are correctly typed.
            // It also checks for "\\?\Volume{e88d2f2b-332b-4eeb-a420-20ba76effc48}\" which is not a path.
            if(pairValueType != null && pairValueType.Type != GenericProvider.ValueType.NODE_TYPE && pairValueType.Value != null
                    && pairValueType.Value.startsWith("\\\\")
                    && ! pairValueType.Value.startsWith("\\\\?\\")
            ) {
                throw new RuntimeException("PutValueType: Key=" + key + " looks like a node:" + pairValueType.Value);
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

        /** This is used to insert the result of a Sparql query execution.
         *
         * @param bindingSet
         */
        public Row(BindingSet bindingSet) {
            Elements = new HashMap<>();
            for (Iterator<Binding> it = bindingSet.iterator(); it.hasNext(); ) {
                Binding binding = it.next();
                Value bindingValue = binding.getValue();
                // TODO: If the value is a literal, it is formatted as in XML,
                // TODO: for example '"0"^^<http://www.w3.org/2001/XMLSchema#long>"
                GenericProvider.ValueType valueType = bindingValue.isIRI() ? GenericProvider.ValueType.NODE_TYPE : GenericProvider.ValueType.STRING_TYPE;
                PutValueType(binding.getName(), new ValueTypePair(bindingValue.toString(), valueType));
            }
        }

        public String toString() {
            return Elements.toString();
        }
    }

    List<Row> Rows;

    Solution( /*List<String> header */ ) {
        //Header = header;
        Rows = new ArrayList<>();
    }

    Iterator<Row> iterator() {
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
}
