package paquetage;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PresentUtils {

    /** This is used to build a path, and is needed in tests.
     *
     * @return On Windows, for example "LAPTOP-R89KG6V1"
     */
    public static String getComputerName()
    {
        Map<String, String> env = System.getenv();
        if (env.containsKey("COMPUTERNAME"))
            return env.get("COMPUTERNAME");
        else if (env.containsKey("HOSTNAME"))
            return env.get("HOSTNAME");
        else
            return "Unknown Computer";
    }

    /** This can apply to Windows only: It prefixes a path and assumes that the namespace is ROOT/CIMv2.
     *
     * @param shortPath : A Wbem path without the hostname and the namespace.
     * @return The complete path as usable by WMI.
     */
    public static String PrefixPath(String shortPath) {
        return "\\\\" + getComputerName() + "\\ROOT\\CIMV2:" + shortPath;
    }

    /** This is used in tests, to check that the current binary of the current process is detected.
     *
     * @return The path name of the current binary, which can only be Java.
     */
    public static String CurrentJavaBinary() {
        String javaHome = System.getProperty("java.home");
        return javaHome + "\\bin\\java.exe";
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

    /** This is used for testing.
     *
     * @param listRows
     * @param variable_name
     * @return
     */
    static Set<String> StringValuesSet(List<Solution.Row> listRows, String variable_name) {
        return listRows.stream().map(row->row.GetStringValue(variable_name)).collect(Collectors.toSet());
    }

    static Set<Long> LongValuesSet(List<Solution.Row> listRows, String variable_name) {
        return listRows.stream().map(row->XmlToLong(row.GetStringValue(variable_name))).collect(Collectors.toSet());
    }

    static String toCIMV2(String term) {
        return NamespaceTermToIRI("ROOT\\CIMV2", term);
    }

    static String NamespaceTermToIRI(String namespace, String term) {
        return WmiOntology.NamespaceUrlPrefix(namespace) + term;
    }

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

}
