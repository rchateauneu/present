package paquetage;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Values;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** In this library, only the string value is used because this is what is needed for WQL.
 * WQL does not really manipulate floats or dates, so string conversion is OK.
 *
 * However, the type is needed when converting these values to RDF, specifically because of WBEM paths which
 * are transformed into RDF IRIs. Also, it might be needed to convert string values to RDF types: ints etc...
 * and for this, their original type is needed.
 *
 */
class ValueTypePair {
    final static private Logger logger = Logger.getLogger(ValueTypePair.class);
    private String vtpValue;
    private ValueType vtpType;

    public String getValue() {
        return vtpValue;
    }

    public ValueType getType() {
        return vtpType;
    }

    public boolean equals(Object otherObject) {
        ValueTypePair other = (ValueTypePair)otherObject;
        return vtpValue.equals(other.vtpValue) && vtpType == other.vtpType;
    }

    /** This checks if the type is correlated with the value. There might be false positive,
     * if a plain string has the same syntax as a node, or contains an integer.
     * @return
     */
    boolean isValid()
    {
        if(vtpValue == null) {
            return true;
        }
        switch(vtpType) {
            case NODE_TYPE:
                // It could be "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process"
                /*
                Non, c'est plus subtil:
                Ca peut commencer par http si c'est un predicat,
                mais si c'est un IRI qui ca etre utilise par WBEM, ca doit etre transforme en path wbem
                et commencer par "\\\\machine\\"
                */
                return PresentUtils.hasWmiReferenceSyntax(vtpValue) || PresentUtils.hasUrlSyntax(vtpValue);
            case INT_TYPE:
                try {
                    Long l = Long.parseLong(vtpValue);
                }
                catch(Exception exc) {
                    logger.error("Invalid integer:" + vtpValue);
                    return false;
                }
                return true;
            default:
                return true;
        }
    }

    ValueTypePair(String value, ValueType type) {
        vtpValue = value;
        vtpType = type;
        if(!isValid()) {
            throw new RuntimeException("Wrong type:" + vtpValue + " : " + vtpType);
        }
    }

    // Very common usage in tests/
    static public ValueTypePair fromString(String value) {
        return new ValueTypePair(value, ValueType.STRING_TYPE);
    }

    public String toDisplayString() {
        return "{" + vtpValue + " -> " + vtpType + "}";
    }

    public String toValueString() {
        return vtpValue;
    }

    /** This should be disabled because there is an ambiguity between display the content for informational
     * purpose, when debugging, and processing the value as a string.
     * @return
     */
    public String toString() {
        return "DEBUG_ONLY:" + vtpValue;
    }

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

    public static ValueTypePair factoryValueTypePair(String value) {
        return ValueTypePair.fromString(value);
    }

    public static ValueTypePair factoryValueTypePair(long value) {
        return new ValueTypePair(Long.toString(value), ValueType.INT_TYPE);
    }

    private Value toGregorian(String strValue) {
        if (strValue == null) {
            return Values.literal("NULL_DATE");
        }
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

        String offsetInMinutesAsString = strValue.substring(22);
        long offsetInMinutes = Long.parseLong(offsetInMinutesAsString);
        LocalTime offsetAsLocalTime = LocalTime.MIN.plusMinutes(offsetInMinutes);
        String offsetAsString = offsetAsLocalTime.format(DateTimeFormatter.ISO_LOCAL_TIME);
        String inputModified = strValue.substring(0, 22) + offsetAsString;

        // "20191207144812.111594+000" => "2019-12-07T14:48:12.111594" for CIM_DataFile.CreationDate
        DateTimeFormatter formatterInput = DateTimeFormatter.ofPattern("yyyyMMddHHmmss.SSSSSSZZZZZ");
        LocalDateTime dateFromGmtString = formatterInput.parse(inputModified, Instant::from).atZone(zone).toLocalDateTime();

        // It does not use LocalDateTime.toString because : "The format used will be the shortest
        // that outputs the full value of the time where the omitted parts are implied to be zero."
        // https://stackoverflow.com/questions/50786482/java-8-localdatetime-dropping-00-seconds-value-when-parsing-date-string-value-wi

        DateTimeFormatter formatterOutput = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
        String strDate=dateFromGmtString.format(formatterOutput);

        logger.debug("strValue=" + strValue);
        logger.debug("strDate=" + strDate);
        // Input representation : "2000-01-15T00:00:00"
        // See https://www.w3.org/TR/xmlschema-2/#dateTime-order
        XMLGregorianCalendar dateGregorian = datatypeFactory.newXMLGregorianCalendar(strDate);

        // literal(ValueFactory vf, String lexicalValue, IRI datatype)
        /*
        Mais pourquoi ca marchait avant ? Et je me souviens qu on avait vire l'extension XSD.

        "2024-03-25T00:03:23.323227"^^<http://www.w3.org/2001/XMLSchema#dateTime>

        retValue = {SimpleLiteral@3885} ""2024-03-25T00:03:23.323227"^^<http://www.w3.org/2001/XMLSchema#dateTime>"
            label = "2024-03-25T00:03:23.323227"
            language = null
            datatype = {Vocabularies$2@3889} "http://www.w3.org/2001/XMLSchema#dateTime"
            xsdDatatype = null
        */




        // METTRE CA DANS LA FONCTION APPELANTE, CA SERA PLUS PROPRE.
        Value retValue = Values.literal(factory, dateGregorian, true);
        return retValue;
    }

    /** This transforms a ValueType (as calculated from WMI) into a literal usable by RDF.
     * The original data type is preserved in the literal because the value is not blindly converted to a string.
     * @return
     */
    Value convertValueTypeToLiteral() {
        ValueType valueType = vtpType;
        if(valueType == null) {
            logger.warn("Invalid null type of literal value.");
            Object nullObject = new Object();
            return Values.literal(nullObject);
        }
        String strValue = vtpValue;
        if(strValue == null) {
            logger.warn("Invalid null literal value.");
            return Values.literal("Unexpected null value. Type=\" + valueType");
        }
        switch(valueType) {
            case BOOL_TYPE:
                Long longBool = Long.parseLong(strValue);
                boolean valueBool = longBool != 0L;
                return Values.literal(valueBool);
            case INT_TYPE:
                return Values.literal(Long.parseLong(strValue));
            case FLOAT_TYPE:
                return Values.literal(Double.parseDouble(strValue));
            case DATE_TYPE:
                return toGregorian(strValue);
            case STRING_TYPE:
                return Values.literal(strValue);
            case NODE_TYPE:
                // Or prefix it with "http://" like PrefixCimv2Path ??
                return Values.literal(strValue);
        }
        throw new RuntimeException("Data type not handled:" + valueType);
    }

    static boolean identical(ValueTypePair one, ValueTypePair other) {
        if (one == null && other == null) return true;
        if (one == null || other == null) return false;
        if (one.vtpType != other.vtpType) return false;
        if(one.vtpValue == null && other.vtpValue == null) return true;
        if(one.vtpValue == null || other.vtpValue == null) return false;
        return one.vtpValue.equals(other.vtpValue);
    }

    /** This is a special value type for this software, to bridge data types between WMI/WBEM and RDF.
     * The most important feature is NODE_TYPE which models a WBEM path and an IRI.
     */
    public enum ValueType {
        STRING_TYPE,
        DATE_TYPE,
        INT_TYPE,
        FLOAT_TYPE,
        BOOL_TYPE,
        NODE_TYPE
    }
};
