package paquetage;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RdfSolution implements Iterable<RdfSolution.Tuple> {
    final static private Logger logger = Logger.getLogger(WmiSelecter.class);

    /** This is used only for testing. It returns the content of a labelled column as a vector of longs.
    This can obviously work only if the type of each element is convertible to an integer.
    */
    Set<Long> longValuesSet(String variable) {
        return stream().map(tuple-> PresentUtils.xmlToLong(tuple.getAsLiteral(variable))).collect(Collectors.toSet());
    }

    /** This is used for testing.
     * Strings are returned by Sparql, enclosed in double-quotes.
     * These double-quotes are stripped.
     * It returns the content of a column as a vector.
     *
     * @param variable
     * @return
     */
    Set<String> stringValuesSet(String variable) {
        return stream().map(tuple-> tuple.getAsLiteral(variable)).collect(Collectors.toSet());
    }

    /** Nodes are not enclosed in double-quotes. */
    Set<String> nodeValuesSet(String variable) {
        return stream().map(tuple-> tuple.getAsUri(variable)).collect(Collectors.toSet());
    }

    public static class Tuple {
        public record RdfValue(boolean isUri, String value)
        {
            public JSONObject toJSONObject(Boolean withSchema) {
                // Possible values: "2023-08-15T01:37:31.308650"^^<http://www.w3.org/2001/XMLSchema#dateTime>
                JSONObject jsonRdfValue = new JSONObject();
                jsonRdfValue.put("type", isUri ? "uri" : "literal");

                PresentUtils.ParsedXMLTag parsedXml = new PresentUtils.ParsedXMLTag(value);

                // FIXME: Not sure: We have experimented only with Json output
                if(withSchema) {
                    // FIXME: Maybe this mode is not needed.
                    jsonRdfValue.put("value", value);
                } else {
                    jsonRdfValue.put("value", parsedXml.value);
                }
                /*
                See SELF.prototype.isEntityUri in FormatterHelper.js
                Wikidata gui is hacked to accept uri starting with "http://www.primhillcomputers.com/ontology"
                and not only ending with wikidata syntax like ".../entity/Q123456".
                        SELF.prototype.isEntityUri = function( uri ) {
                            return typeof uri === 'string'
                                && /\/entity\/(Q|P|L|M)[0-9]+$/.test( uri );
                */

                /*
                Possible values for optional "datatype":
                 "datatype" : "http://www.w3.org/1998/Math/MathML",
                 "datatype" : "http://www.w3.org/2001/XMLSchema#dateTime"
                */
                if(parsedXml.datatype != null) {
                    jsonRdfValue.put("datatype", parsedXml.datatype);
                }

                /* TODO: Should add
                "xml:lang" : "en"
                 */
                return jsonRdfValue;
            }
        };
        private Map<String, RdfValue> mapKeyValuePairs = new HashMap<>();

        public Set<String> keySet() {
            return mapKeyValuePairs.keySet();
        }

        private RdfValue getValueType(String key) {
            RdfValue rdfValue = mapKeyValuePairs.get(key);
            if(rdfValue == null) {
                throw new RuntimeException("Unknown variable " + key + ". Vars=" + keySet());
            }
            return rdfValue;
        }

        public String getAsLiteral(String key) {
            RdfValue rdfValue = getValueType(key);
            if(rdfValue.isUri) {
                throw new RuntimeException("Should not be an Uri: " + key + ". value=" + rdfValue.value);
            }
            return rdfValue.value;
        }

        public String getAsUri(String key) {
            RdfValue rdfValue = getValueType(key);
            if(!rdfValue.isUri) {
                throw new RuntimeException("Should be an Uri: " + key + ". value=" + rdfValue.value);

            }
            return rdfValue.value;
        }

        public Tuple addKeyValue(String key, boolean valueIsUri, String valueString) {
            // FIXME: Where is it created ? "2023-08-15T01:37:31.308650"^^<http://www.w3.org/2001/XMLSchema#dateTime>
            mapKeyValuePairs.put(key, new RdfValue(valueIsUri, valueString));
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
                // If the value is a literal, it is formatted by Value.toString as in XML,
                // for example '"0"^^<http://www.w3.org/2001/XMLSchema#long>'
                // or '"2023-08-15T01:37:31.308650"^^<http://www.w3.org/2001/XMLSchema#dateTime>'
                // String valueString = bindingValue.toString() wraps the value. For example:
                // '"2023-08-15T01:37:31.308650"^^<http://www.w3.org/2001/XMLSchema#dateTime>'
                // And a string is wrapped within quotes.
                // It should be cleaned to be used in Wikidata Sparql GUI, for example.

                // Beware: toString() wraps the value between quotes.
                String valueString = bindingValue.stringValue();
                logger.error("bindingValue=" + bindingValue + " valueString=" + valueString);

                if(bindingValue.isTriple()) {
                    throw new Exception("Value should not be a Triple:" + valueString);
                }
                if(bindingValue.isBNode()) {
                    throw new Exception("Value should not be a BNode:" + valueString);
                }

                boolean isIri = bindingValue.isIRI() || bindingValue.isResource();
                if(!isIri) {
                    if (!bindingValue.isLiteral()) {
                        throw new Exception("Value should not be IRI or literal:" + valueString);
                    }
                }
                // FIXME: Where is it created ?
                addKeyValue(binding.getName(), isIri, valueString);
            }
        }

        /* For tests. */
        public static Tuple tupleFactory() {
            return new Tuple();
        }

        private Tuple() {}

        /** This is for testing and debugging only. */
        public String toString() {
            return mapKeyValuePairs.toString();
        }

        public JSONObject toJSONObject(Boolean withSchema) {
            JSONObject jsonTuple = new JSONObject();
            for(Map.Entry<String, RdfValue> kv : mapKeyValuePairs.entrySet()) {
                jsonTuple.put(kv.getKey(), kv.getValue().toJSONObject(withSchema));
            }
            return jsonTuple;
        }
    }

    /** This is a temporary implementation.
     * TODO: Store the bindings once only.
     *
     * @return
     */
    Set<String> bindingsSet() {
        Set<String> bindings = new HashSet<>();
        for(Tuple tuple: tuplesList) {
            bindings.addAll(tuple.keySet());
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
    String toJson(Boolean withSchema) {
        JSONObject jsonTop = new JSONObject();

        JSONObject jsonHead = new JSONObject();
        jsonHead.put("vars", bindingsSet());
        jsonTop.put("head", jsonHead);

        JSONObject jsonResults = new JSONObject();

        List<JSONObject> jsonBindings = new ArrayList<>();
        for(Tuple tuple: tuplesList) {
            JSONObject jsonTuple = tuple.toJSONObject(withSchema);
            jsonBindings.add(jsonTuple);
        }
        jsonResults.put("bindings", jsonBindings);
        jsonTop.put("results", jsonResults);

        return jsonTop.toString(4);
    }
}
