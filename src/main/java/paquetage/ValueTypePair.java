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
    private String m_Value;
    private ValueType m_Type;

    public String Value() {
        return m_Value;
    }

    public ValueType Type() {
        return m_Type;
    }

    public boolean equals(Object otherObject) {
        ValueTypePair other = (ValueTypePair)otherObject;
        return m_Value.equals(other.m_Value) && m_Type == other.m_Type;
    }

    /** This checks if the type is correlated with the value. There might be false positive,
     * if a plain string has the same syntax as a node, or contains an integer.
     * @return
     */
    boolean IsValid()
    {
        if(m_Value == null) {
            return true;
        }
        switch(m_Type) {
            case NODE_TYPE:
                // It could be "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process"
                return PresentUtils.hasWmiReferenceSyntax(m_Value) || PresentUtils.hasUrlSyntax(m_Value);
            case INT_TYPE:
                try {
                    Long l = Long.parseLong(m_Value);
                }
                catch(Exception exc) {
                    logger.error("Invalid integer:" + m_Value);
                    return false;
                }
                return true;
            default:
                return true;
        }
    }

    ValueTypePair(String value, ValueType type) {
        m_Value = value;
        m_Type = type;
        if(!IsValid()) {
            throw new RuntimeException("Wrong type:" + m_Value + " : " + m_Type);
        }
    }

    // Very common usage in tests/
    static public ValueTypePair FromString(String value) {
        return new ValueTypePair(value, ValueType.STRING_TYPE);
    }

    public String toDisplayString() {
        return "{" + m_Value + " -> " + m_Type + "}";
    }

    public String toValueString() {
        return m_Value;
    }

    /** This should be disabled because there is an ambiguity between display the content for informational
     * purpose, when debugging, and processing the value as a string.
     * @return
     */
    public String toString() {
        throw new RuntimeException("Should not happen");
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

    public static ValueTypePair Factory(String value) {
        return new ValueTypePair(value, ValueType.STRING_TYPE);
    }

    public static ValueTypePair Factory(long value) {
        return new ValueTypePair(Long.toString(value), ValueType.INT_TYPE);
    }

    /** This transforms a ValueType (as calculated from WMI) into a literal usable by RDF.
     * The original data type is preserved in the literal because the value is not blindly converted to a string.
     * @return
     */
    Value ValueTypeToLiteral() {
        ValueType valueType = m_Type;
        if(valueType == null) {
            logger.warn("Invalid null type of literal value.");
            Object nullObject = new Object();
            return Values.literal(nullObject);
        }
        String strValue = m_Value;
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

                    String offsetInMinutesAsString = strValue.substring(22);
                    long offsetInMinutes = Long.parseLong(offsetInMinutesAsString);
                    LocalTime offsetAsLocalTime = LocalTime.MIN.plusMinutes(offsetInMinutes);
                    String offsetAsString = offsetAsLocalTime.format(DateTimeFormatter.ISO_LOCAL_TIME);
                    String inputModified = strValue.substring(0, 22) + offsetAsString;

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

    static boolean identical(ValueTypePair one, ValueTypePair other) {
        if (one == null && other == null) return true;
        if (one == null || other == null) return false;
        if (one.m_Type != other.m_Type) return false;
        if(one.m_Value == null && other.m_Value == null) return true;
        if(one.m_Value == null || other.m_Value == null) return false;
        return one.m_Value.equals(other.m_Value);
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
