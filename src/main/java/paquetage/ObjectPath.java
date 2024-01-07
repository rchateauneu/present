package paquetage;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

// See also: import javax.cim;
import org.sblim.wbem.cim.CIMDataType;
import org.sblim.wbem.cim.CIMObjectPath;
import org.sblim.wbem.cim.CIMProperty;
import org.sblim.wbem.cim.CIMValue;

/**
 * http://myserver/interop:My_ComputerSystem.Name=mycomputer,CreationClassName=My_ComputerSystem
 * \\LAPTOP-R89KG6V1\root\cimv2:Win32_Process.Handle="2088"
 * \\LAPTOP-R89KG6V1\ROOT\CIMV2:CIM_ProcessExecutable.Antecedent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\msvcp_win.dll\"",Dependent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"2088\""
 *
 */
public class ObjectPath {

    /*
    Example of input: \\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\SYSTEM32\\\\HologramWorld.dll\"");
     */
    public static Map<String, String> ParseWbemPath(String objectPath) {
        if(!PresentUtils.CheckValidWbemPath(objectPath)){
            throw new RuntimeException("Invalid Wbem path:" + objectPath);
        }
        // CIMObjectPath.parse() is deprecated and not implemented. Why ???
        int positionColon = objectPath.indexOf(':');
        String moniker = objectPath.substring(positionColon + 1);
        int positionDot = moniker.indexOf('.');
        String kvs = moniker.substring(positionDot + 1);
        int startProp = 0;
        int indexChar = 0;
        HashMap<String, String> propertiesMap = new HashMap<>();
        while(true)
        {
            if(kvs.charAt(indexChar) == '=') {
                String property = kvs.substring(startProp, indexChar);
                indexChar += 1;
                if(kvs.charAt(indexChar) != '"') {
                    throw new RuntimeException("Missing beginning quote for value of property:" + property);
                }
                indexChar += 1;
                boolean previousSlash = false;
                StringBuffer value = new StringBuffer();
                while(true)
                {
                    char currentChar = kvs.charAt(indexChar);
                    indexChar += 1;
                    if(currentChar == '\\') {
                        if(previousSlash) {
                            previousSlash = false;
                            value.append('\\');
                        }  else {
                            previousSlash = true;
                        }
                    } else {
                        if (currentChar == '"') {
                            if (previousSlash) {
                                previousSlash = false;
                                value.append('"');
                            } else {
                                break;
                            }
                        } else {
                            previousSlash = false;
                            value.append(currentChar);
                        }
                    }
                }
                propertiesMap.put(property, value.toString());
                if(indexChar == kvs.length()) break;
                if(kvs.charAt(indexChar) != ',') {
                    throw new RuntimeException("Inconsistent path:" + objectPath);
                }
                indexChar += 1;
                startProp = indexChar;
            } else {
                indexChar += 1;
                if(indexChar == kvs.length()) break;
            }
        }
        return propertiesMap;
    }

    /** This is used only for classes in namespace Root/Cimv2",
     * to create an object and its path mimicking an object created by WMI.
     * "Pseudo WMI objects" are created by providers classes similar to WMI ones but much faster,
     * */
    public static String BuildCimv2PathWbem(String className, Map<String, String> propertiesMap)
    {
        Vector<CIMProperty> propertyArray = new Vector<CIMProperty>(propertiesMap.size());
        for(Map.Entry<String, String> entry: propertiesMap.entrySet()) {
            Object objectValue = entry.getValue();
            CIMValue cimValue = new CIMValue(objectValue, new CIMDataType(CIMDataType.STRING));
            propertyArray.add(new CIMProperty(entry.getKey(), cimValue));
        }
        CIMObjectPath wbemPath = new CIMObjectPath(className, propertyArray);
        // This adds the host name and the namespace.
        return PresentUtils.PrefixCimv2Path(wbemPath.toString());
    }
}
