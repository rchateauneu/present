package paquetage;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PresentUtils {

    /** This is used to build a path, and is needed in tests.
     *
     * @return On Windows, for example "LAPTOP-R89KG6V1"
     */

    private static String getComputerName()
    {
        Map<String, String> env = System.getenv();
        if (env.containsKey("COMPUTERNAME"))
            return env.get("COMPUTERNAME");
        else if (env.containsKey("HOSTNAME"))
            return env.get("HOSTNAME");
        else
            return "Unknown Computer";
    }

    public static String computerName = getComputerName();

    // This is temporarily changed by some tests.
    public static String prefixComputer = "\\\\" + getComputerName();

    /** It prefixes a path and assumes that the namespace is ROOT/CIMv2. Used for tests only.
     *
     * @param shortPath : A Wbem path without the hostname and the namespace.
     * @return The complete path as usable by WMI.
     */
    public static String PrefixCimv2Path(String shortPath) {
        return prefixComputer + "\\ROOT\\CIMV2:" + shortPath;
    }

    /** This is used in tests, to check that the current binary of the current process is detected.
     * Otherwise it is not needed by objects from WMI, because their paths contain the host name and the namespace.
     *
     * @return The path name of the current binary, which can only be Java.
     */
    public static String CurrentJavaBinary() {
        String javaHome = System.getProperty("java.home");
        return javaHome + "\\bin\\java.exe";
    }

    /** This is used only for tests. */
    static private ProcessHandle currentProcessHandle = ProcessHandle.current();

    /** This is used only for tests. */
    static private ProcessHandle parentProcess = currentProcessHandle.parent().get();

    /** This is used only for tests. */
    public static String ParentProcessId() throws Exception
    {
        return Long.toString(parentProcess.pid());
    }

    /** This is used only for tests. */
    public static String ParentProcessCommand() throws Exception
    {
        return parentProcess.info().command().orElse(null);
    }

    static public String LongToXml(long longNumber) {
        return "\"" + longNumber + "\"^^<http://www.w3.org/2001/XMLSchema#long>";
    }

    static public String IntToXml(int intNumber) {
        return "\"" + intNumber + "\"^^<http://www.w3.org/2001/XMLSchema#integer>";
    }

    /** This is used to extract the significant part for "\"41\"^^<http://www.w3.org/2001/XMLSchema#integer>",
     * for example. The XSD type is not checked.
     */
    static private String regexQuotes = "\"(.*)\".*";
    static private Pattern patternQuotesXML = Pattern.compile(regexQuotes);

    /** This returns the string enclosed in quites in a XML-formatted value. */
    static private String extractStringXML(String xmlString) {
        Matcher matcher = patternQuotesXML.matcher(xmlString);
        matcher.find();
        String stringOnly = matcher.group(1);
        return stringOnly;
    }

    /** For example longStr = "\"41\"^^<http://www.w3.org/2001/XMLSchema#integer>" */
    static public long XmlToLong(String longStr) {
        String longOnly = extractStringXML(longStr);
        return Long.parseLong(longOnly);
    }

    static public double XmlToDouble(String doubleStr) {
        String doubleOnly = extractStringXML(doubleStr);
        return Double.parseDouble(doubleOnly);
    }

    // Example: "false"^^<http://www.w3.org/2001/XMLSchema#boolean>
    static public boolean XmlToBoolean(String booleanStr) {
        String booleanOnly = extractStringXML(booleanStr);
        if(booleanOnly.equals("true"))
            return true;
        if(booleanOnly.equals("false"))
            return false;
        throw new RuntimeException("Invalid boolean value:" + booleanOnly);
    }

    /** Transforms a RDF date into a string.
     *
     * @param theDate Example: '"2022-02-11T00:44:44.730519"^^<http://www.w3.org/2001/XMLSchema#dateTime>'
     * @return Example: '2022-02-11T00:44:44.730519'
     * @throws Exception
     */
    static public XMLGregorianCalendar ToXMLGregorianCalendar(String theDate) throws Exception {
        // https://docs.microsoft.com/en-us/windows/win32/wmisdk/cim-datetime
        // yyyymmddHHMMSS.mmmmmmsUUU
        // "20220720101048.502446+060"

        // '"2022-07-20"^^<http://www.w3.org/2001/XMLSchema#date>'
        DatatypeFactory dataTypeFactory = DatatypeFactory.newInstance();
        String dateOnly = extractStringXML(theDate);

        XMLGregorianCalendar xmlDate = dataTypeFactory.newXMLGregorianCalendar(dateOnly);
        return xmlDate;
    }

    static String toCIMV2(String term) {
        return NamespaceTermToIRI("ROOT\\CIMV2", term);
    }

    static String NamespaceTermToIRI(String namespace, String term) {
        return WmiOntology.NamespaceUrlPrefix(namespace) + term;
    }

    /** This is used to check that a WMI value cannot be possibly a node. */
    static boolean hasWmiReferenceSyntax(String refString) {
        /*
            Here, "?my3_dir" is a reference but it does not have the syntax.

            select ?my_dir_name
            where {
                ?my3_dir cimv2:Win32_Directory.Name ?my_dir_name .
                ?my2_assoc cimv2:Win32_MountPoint.Volume ?my1_volume .
                ?my2_assoc cimv2:Directory ?my3_dir .
                ?my1_volume cimv2:Win32_Volume.DriveLetter ?my_drive .
                ?my1_volume cimv2:DeviceID ?device_id .
                ?my0_dir cimv2:Name "C:\\Program Files (x86)" .
                ?my0_dir cimv2:Win32_Directory.Drive ?my_drive .
            }

            valueType='Win32_Directory.Name="C:\\"'

            Or:
            Win32_Volume.DeviceID="\\\\?\\Volume{e88d2f2b-332b-4eeb-a420-20ba76effc48}\\"

            Very rough test which should be generalised to all WMI paths.

            FIXME: If these are paths, the prefix with the machine name should be added.
         */
        if(refString.startsWith("Win32_Directory.Name=")
        || refString.startsWith("Win32_Volume.DeviceID=")) return true;
        if(refString.startsWith("\\\\") || refString.startsWith("\\\\?\\")) return true;
        return false;
    }

    static boolean hasUrlSyntax(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /* this trims the first and last characters of a string, and is used when Sparql returns strings
    between quotes.
     */
    static public String trimQuotes(String inString) {
        return inString.substring(1, inString.length() - 1);
    }

    static private Pattern patternVariableName = Pattern.compile("^[_a-zA-Z][_a-zA-Z0-9]*$", Pattern.CASE_INSENSITIVE);

    static boolean ValidSparqlVariable(String variableName) {
        if(variableName == null) {
            throw new RuntimeException("Cannot test validity of null variable name.");
        }
        Matcher matcher = patternVariableName.matcher(variableName);
        return matcher.find();
    }
}
