package paquetage;

import java.util.Map;

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

    public static String PrefixPath(String shortPath) {
        return "\\\\" + getComputerName() + "\\ROOT\\CIMV2:" + shortPath;
    }

    public static String CurrentJavaBinary() {
        String javaHome = System.getProperty("java.home");
        return javaHome + "\\bin\\java.exe";

    }
}
