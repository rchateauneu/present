package paquetage;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.algebra.Var;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

        /**
         * IRIS must look like this:
         * objectString=http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process isIRI=true
         *
         * But Wbem path are like that:
         * subjectString=\\LAPTOP-R89KG6V1\ROOT\CIMV2:Win32_Process.Handle="31640"
         *
         * So, Wbem path must be URL-encoded and prefixed.
         *
         * @param var
         * @return
         * @throws Exception
         */
        Resource AsIRI(Var var) throws Exception {
            String varName = var.getName();
            ValueTypePair pairValueType = GetValueType(varName);
            if(pairValueType.Type() != GenericProvider.ValueType.NODE_TYPE) {
                throw new Exception("This should be a NODE:" + varName + "=" + pairValueType);
            }
            String valueString = pairValueType.Value();
            if(pairValueType.Type() != GenericProvider.ValueType.NODE_TYPE) {
                throw new Exception("Value " + varName + "=" + valueString + " is not a node");
            }
            // Consistency check, for debugging.
            if(valueString.startsWith(WmiOntology.namespaces_url_prefix)) {
                throw new Exception("Double transformation in IRI:" + valueString);
            }
            Resource resourceValue = WmiOntology.WbemPathToIri(valueString);
            return resourceValue;
        }

        ValueTypePair GetVarValue(Var var) throws Exception {
            ValueTypePair pairValueType = GetValueType(var.getName());
            return pairValueType;
        }

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
            private static final DatatypeFactory datatypeFactory ;

            static {
                try {
                    datatypeFactory = DatatypeFactory.newInstance();
                }
                catch (Exception e) {
                    throw new RuntimeException("Cannot initialize DatatypeFactory:", e);
                }
            }

            private static ValueFactory factory = SimpleValueFactory.getInstance();

            public static ValueTypePair Factory(String value) {
                return new ValueTypePair(value, GenericProvider.ValueType.STRING_TYPE);
            }

            public static ValueTypePair Factory(long value) {
                return new ValueTypePair(Long.toString(value), GenericProvider.ValueType.INT_TYPE);
            }

            /** This transforms a ValueType (as calculated from WMI) into a literal usable by RDF.
             * The original data type is preserved in the literal because the value is not blindly converted to a string.
             * @return
             */
            Value ValueTypeToLiteral() {
                GenericProvider.ValueType valueType = Type();
                if(valueType == null) {
                    logger.warn("Invalid null type of literal value.");
                    Object nullObject = new Object();
                    return Values.literal(nullObject);
                }
                String strValue = Value();
                if(strValue == null) {
                    logger.warn("Invalid null literal value.");
                    return Values.literal("Unexpected null value. Type=\" + valueType");
                }
                switch(valueType) {
                    case INT_TYPE:
                        return Values.literal(Long.parseLong(strValue));
                    case FLOAT_TYPE:
                        return Values.literal(Double.parseDouble(strValue));
                    case DATE_TYPE:
                        if (strValue == null) {
                            return Values.literal("NULL_DATE");
                        } else {
                            ZoneId zone = ZoneId.systemDefault();
                            /**
                             * See SWbemDateTime
                             * https://docs.microsoft.com/en-us/windows/win32/wmisdk/swbemdatetime
                             * https://docs.microsoft.com/en-us/windows/win32/wmisdk/cim-datetime
                             *
                             * strValue = '20220720095636.399854+060' for example.
                             * The time zone offset is in minutes.
                             *
                             * https://stackoverflow.com/questions/37308672/parse-cim-datetime-with-milliseconds-to-java-date                     *
                             */
                            //logger.debug("strValue=" + strValue);

                            String offsetInMinutesAsString = strValue.substring ( 22 );
                            long offsetInMinutes = Long.parseLong ( offsetInMinutesAsString );
                            LocalTime offsetAsLocalTime = LocalTime.MIN.plusMinutes ( offsetInMinutes );
                            String offsetAsString = offsetAsLocalTime.format ( DateTimeFormatter.ISO_LOCAL_TIME );
                            String inputModified = strValue.substring ( 0 , 22 ) + offsetAsString;

                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss.SSSSSSZZZZZ");
                            LocalDateTime dateFromGmtString = formatter.parse(inputModified, Instant::from).atZone(zone).toLocalDateTime();

                            String strDate = dateFromGmtString.toString();
                            //logger.debug("strDate=" + strDate);
                            XMLGregorianCalendar dateGregorian = datatypeFactory.newXMLGregorianCalendar(strDate);

                            return Values.literal(factory, dateGregorian, true);
                        }
                    case STRING_TYPE:
                        return Values.literal(strValue);
                    case NODE_TYPE:
                        return Values.literal(strValue);
                }
                throw new RuntimeException("Data type not handled:" + valueType);
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
