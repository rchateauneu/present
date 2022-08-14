package paquetage;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
    static public String ToXml(long longNumber) {
        return "\"" + longNumber + "\"^^<http://www.w3.org/2001/XMLSchema#long>";
    }

    /** This is used for testing.
     *
     * @param listRows
     * @param variable_name
     * @return
     */
    static Set<String> StringValuesSet(List<GenericProvider.Row> listRows, String variable_name) {
        return listRows.stream().map(row->row.GetStringValue(variable_name)).collect(Collectors.toSet());
    }

}
