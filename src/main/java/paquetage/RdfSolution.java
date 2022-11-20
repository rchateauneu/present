package paquetage;

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RdfSolution implements Iterable<RdfSolution.Tuple> {
    /** This is used only for testing. It returns the content of a labelled column as a vector of longs.
    This can obviously work only if the type of each element is convertible to an integer.
    */
    Set<Long> LongValuesSet(String variable) {
        return stream().map(tuple-> PresentUtils.XmlToLong(tuple.GetAsLiteral(variable))).collect(Collectors.toSet());
    }

    /** This is used for testing.
     * Strings are returned by Sparql, enclosed in double-quotes.
     * These double-quotes are stripped.
     * It returns the content of a column as a vector.
     *
     * @param variable
     * @return
     */
    Set<String> StringValuesSet(String variable) {
        return stream().map(tuple-> PresentUtils.trimQuotes(tuple.GetAsLiteral(variable))).collect(Collectors.toSet());
    }

    /** Nodes are not enclosed in double-quotes. */
    Set<String> NodeValuesSet(String variable) {
        return stream().map(tuple-> tuple.GetAsUri(variable)).collect(Collectors.toSet());
    }

    public static class Tuple {
        public record RdfValue(boolean isUri, String value)
        {
            public JSONObject ToJSONObject() {
                JSONObject jsonRdfValue = new JSONObject();
                jsonRdfValue.put("type", isUri ? "uri" : "literal");
                jsonRdfValue.put("value", value);
                return jsonRdfValue;
            }
        };
        private Map<String, RdfValue> KeyValuePairs = new HashMap<>();

        public Set<String> KeySet() {
            return KeyValuePairs.keySet();
        }

        private RdfValue GetValueType(String key) {
            RdfValue rdfValue = KeyValuePairs.get(key);
            if(rdfValue == null) {
                throw new RuntimeException("Unknown variable " + key + ". Vars=" + KeySet());
            }
            return rdfValue;
        }

        public String GetAsLiteral(String key) {
            RdfValue rdfValue = GetValueType(key);
            if(rdfValue.isUri) {
                throw new RuntimeException("Should not be an Uri: " + key + ". value=" + rdfValue.value);

            }
            return rdfValue.value;
        }

        public String GetAsUri(String key) {
            RdfValue rdfValue = GetValueType(key);
            if(!rdfValue.isUri) {
                throw new RuntimeException("Should be an Uri: " + key + ". value=" + rdfValue.value);

            }
            return rdfValue.value;
        }

        public Tuple AddKeyValue(String key, boolean valueIsUri, String valueString) {
            KeyValuePairs.put(key, new RdfValue(valueIsUri, valueString));
            return this;
        }
        /** This is used to insert the result of a Sparql query execution.
         * TODO: This might as well return a Binding object.
         *
         * @param bindingSet
         */
        public Tuple(BindingSet bindingSet) throws Exception {
            for(Binding binding : bindingSet) {
                Value bindingValue = binding.getValue();
                String valueString = bindingValue.toString();

                if(bindingValue.isTriple()) {
                    throw new Exception("Value should not be a Triple:" + valueString);
                }
                if(bindingValue.isBNode()) {
                    throw new Exception("Value should not be a BNode:" + valueString);
                }
                //if(bindingValue.isResource()) {
                //    throw new Exception("Value should not be a Resource:" + valueString);
                //}

                boolean isIri = bindingValue.isIRI() || bindingValue.isResource();
                if(!isIri) {
                    if (!bindingValue.isLiteral()) {
                        throw new Exception("Value should not be IRI or literal:" + valueString);
                    }
                }
                // TODO: If the value is a literal, it is formatted as in XML,
                // TODO: for example '"0"^^<http://www.w3.org/2001/XMLSchema#long>"'
                AddKeyValue(binding.getName(), isIri, valueString);
            }
        }

        /* For tests. */
        public static Tuple Factory() {
            return new Tuple();
        }

        private Tuple() {}

        /** This is for testing and debugging only. */
        public String toString() {
            return KeyValuePairs.toString();
        }

        public JSONObject ToJSONObject() {
            JSONObject jsonTuple = new JSONObject();
            for(Map.Entry<String, RdfValue> kv : KeyValuePairs.entrySet()) {
                jsonTuple.put(kv.getKey(), kv.getValue().ToJSONObject());
            }
            return jsonTuple;
        }
    }

    /** This is a temporary implementation.
     * TODO: Store the bindings once only.
     *
     * @return
     */
    Set<String> Bindings() {
        Set<String> bindings = new HashSet<>();
        for(Tuple tuple: tuplesList) {
            bindings.addAll(tuple.KeySet());
        }
        return bindings;
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

    /*
            {
              "head": { "vars": [ "book" , "title" ]
              } ,
              "results": {
                "bindings": [
                  {
                    "book": { "type": "uri" , "value": "http://example.org/book/book6" } ,
                    "title": { "type": "literal" , "value": "Harry Potter and the Half-Blood Prince" }
                  } ,
                  {
                    "book": { "type": "uri" , "value": "http://example.org/book/book1" } ,
                    "title": { "type": "literal" , "value": "Harry Potter and the Philosopher's Stone" }
                  }
                ]
              }
            }
     */
    String ToJson() {
        JSONObject jsonTop = new JSONObject();

        JSONObject jsonHead = new JSONObject();
        jsonHead.put("vars", Bindings());
        jsonTop.put("head", jsonHead);

        JSONObject jsonResults = new JSONObject();

        List<JSONObject> jsonBindings = new ArrayList<>();
        for(Tuple tuple: tuplesList) {
            JSONObject jsonTuple = tuple.ToJSONObject();
            jsonBindings.add(jsonTuple);
        }
        jsonResults.put("bindings", jsonBindings);
        jsonTop.put("results", jsonResults);

        return jsonTop.toString(4);
    }
}
