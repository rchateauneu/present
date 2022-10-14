package paquetage;

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RdfSolution implements Iterable<RdfSolution.Tuple> {
    Set<Long> LongValuesSet(String variable) {
        return stream().map(tuple-> PresentUtils.XmlToLong(tuple.GetStringValue(variable))).collect(Collectors.toSet());
    }

    /** This is used for testing.
     * Strings are returned by Sparql, enclosed in double-quotes.
     *
     * @param variable
     * @return
     */
    Set<String> StringValuesSet(String variable) {
        return stream().map(tuple-> PresentUtils.trimQuotes(tuple.GetStringValue(variable))).collect(Collectors.toSet());
    }

    /** Nodes are not enclosed in double-quotes. */
    Set<String> NodeValuesSet(String variable) {
        return stream().map(tuple-> tuple.GetStringValue(variable)).collect(Collectors.toSet());
    }

    public static class Tuple {
        private Map<String, String> KeyValuePairs = new HashMap<>();

        public Set<String> KeySet() {
            return KeyValuePairs.keySet();
        }

        public String TryValueType(String key) {
            return KeyValuePairs.get(key);
        }

        public String GetValueType(String key) {
            String value = TryValueType(key);
            if(value == null) {
                throw new RuntimeException("Unknown variable " + key + ". Vars=" + KeySet());
            }
            return value;
        }

        public String GetStringValue(String key) {
            return GetValueType(key);
        }

        /** This is used to insert the result of a Sparql query execution.
         * TODO: This might as well return a Binding object.
         *
         * @param bindingSet
         */
        public Tuple(BindingSet bindingSet) {
            for(Binding binding : bindingSet) {
                Value bindingValue = binding.getValue();
                // TODO: If the value is a literal, it is formatted as in XML,
                // TODO: for example '"0"^^<http://www.w3.org/2001/XMLSchema#long>"
                KeyValuePairs.put(binding.getName(), bindingValue.toString());
            }
        }

        public String toString() {
            return KeyValuePairs.toString();
        }
    }

    private List<Tuple> tuplesList = new ArrayList<>();

    int size() {
        return tuplesList.size();
    }

    Tuple get(int index) {
        return tuplesList.get(index);
    }

    Stream<Tuple> stream() {
        return tuplesList.stream();
    }

    void add(Tuple tuple) {
        tuplesList.add(tuple);
    }

    public Iterator<Tuple> iterator() {
        return tuplesList.iterator();
    }
}
