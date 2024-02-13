package paquetage;

import COM.Wbemcli;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.OaIdl;
import com.sun.jna.platform.win32.OleAuto;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.ptr.IntByReference;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.sun.jna.platform.win32.Variant.VT_ARRAY;
import static com.sun.jna.platform.win32.Variant.VT_BSTR;

public class WmiGetter extends BaseGetter {
    // TODO: Try a singleton.
    WmiProvider wmiProvider = new WmiProvider();

    final static private Logger logger = Logger.getLogger(WmiGetter.class);

    public boolean matchGetter(QueryData queryData) {
        return true;
    }

    public Wbemcli.IWbemClassObject getObjectNode(String objectPath) {
        try {
            // FIXME: This will not work with other namespaces than "ROOT\\CIMV2".
            Wbemcli.IWbemClassObject objectNode = wmiProvider.wbemServiceRootCimv2.GetObject(objectPath, Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, null);
            return objectNode;
        } catch (com.sun.jna.platform.win32.COM.COMException exc) {
            // Error Code 80041002 – Object Not Found
            // Possibly a file protection problem.
            logger.error("GetObjectNode objectPath=" + objectPath + " Caught=" + exc);
            return null;
        }
    }

    // TODO: This should be thread-safe.
    private static HashMap<String, Wbemcli.IWbemClassObject> cacheWbemClassObject = new HashMap<>();

    private Wbemcli.IWbemClassObject getObjectNodeCached(String objectPath) {
        Wbemcli.IWbemClassObject objectNode = cacheWbemClassObject.get(objectPath);
        if (objectNode == null) {
            objectNode = getObjectNode(objectPath);
            cacheWbemClassObject.put(objectPath, objectNode);
        }
        return objectNode;
    }

    /** This gets a WMI object and its properties among the input list of properties.
     * This should be faster than fetching all of them.
     * @param objectPath
     * @param properties
     * @return
     */
    private Wbemcli.IWbemClassObject getObjectNodePartial(String objectPath, Set<String> properties) {
        Wbemcli.IWbemContext pctxDrive = new Wbemcli.IWbemContext().create();

        // Add named values to the context object.
        pctxDrive.SetValue("__GET_EXTENSIONS", 0, true);

        pctxDrive.SetValue("__GET_EXT_CLIENT_REQUEST", 0, true);

        // Create an array of properties to return.
        OaIdl.SAFEARRAY psaProperties = OaIdl.SAFEARRAY.createSafeArray(new WTypes.VARTYPE(VT_BSTR), properties.size());

        int indexProperties = 0;
        OleAuto.INSTANCE.SafeArrayLock(psaProperties);
        try {
            for (String strProperty : properties) {
                WTypes.BSTR strPropertyBSTR = OleAuto.INSTANCE.SysAllocString(strProperty);
                try {
                    psaProperties.putElement(strPropertyBSTR, indexProperties);
                    ++indexProperties;
                } finally {
                    OleAuto.INSTANCE.SysFreeString(strPropertyBSTR);
                }
            }
        } finally {
            OleAuto.INSTANCE.SafeArrayUnlock(psaProperties);
        }

        Variant.VARIANT.ByReference vPropertyList = new Variant.VARIANT.ByReference();
        vPropertyList.setVarType((short) (VT_ARRAY | VT_BSTR));
        vPropertyList.setValue(psaProperties);
        pctxDrive.SetValue("__GET_EXT_PROPERTIES", 0, vPropertyList);
        psaProperties.destroy();

        return wmiProvider.wbemServiceRootCimv2.GetObject(objectPath, Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, pctxDrive);
    }

    ValueTypePair getObjectProperty(Wbemcli.IWbemClassObject obj, String propertyName) {
        Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
        IntByReference pType = new IntByReference();
        IntByReference plFlavor = new IntByReference(); //  Maybe this is not needed.
        try {
            COMUtils.checkRC(obj.Get(propertyName, 0, pVal, pType, plFlavor));
        } catch (Exception exc) {
            // So it is easier to debug.
            throw exc;
        }

        ValueTypePair rowValueType = WmiProvider.convertVariantToValueTypePair(propertyName, pType, pVal);
        return rowValueType;
    }

    /**
     * This returns the object with the given WMI path. At least the specified properties must be set.
     * This is slow and can be optimized with different ways:
     * - Take from WMI only the required members.
     * - Use a cache keyed by the path, if the same object is requested several times.
     * - Extract the property values from the path if these are keys. For example a filename or process handle.
     * - Calculate directory the properties without calling WMI.
     *
     * @param objectPath A string like '\\LAPTOP-R89KG6V1\root\cimv2:Win32_Process.Handle="22292"'
     * @param columns    Set of properties, like ["Handle", "Caption"]
     * @return
     * @throws Exception
     */
    private Wbemcli.IWbemClassObject pathToNode(String objectPath, Set<String> columns) {
        Wbemcli.IWbemClassObject objectNode = null;
        if (false) {
            objectNode = getObjectNode(objectPath);
        } else {
            if (false) {
                // This works but this is not faster.
                objectNode = getObjectNodePartial(objectPath, columns);
            } else {
                // Not faster if all objects have different path.
                objectNode = getObjectNodeCached(objectPath);
            }
        }
        return objectNode;
    }
    /** This returns in a Solution.Row the properties of a WMI instance specified in a WMI path.
     * The desired properties are given in the QueryData columns.
     * @param objectPath
     * @param mainVariable
     * @param queryColumns
     * @return
     * @throws Exception
     */
    public Solution.Row getSingleObject(String objectPath, String mainVariable, Map<String, String> queryColumns) {

        Set<String> columns = queryColumns.keySet();
        //logger.debug("objectPath=" + objectPath + " queryColumns=" + queryColumns);
        Wbemcli.IWbemClassObject objectNode = pathToNode(objectPath, columns);
        // Maybe the object does not exist.
        if(objectNode == null) {
            logger.error("Cannot find object:" + objectPath);
            return null;
        }

        Solution.Row singleRow = new Solution.Row();
        for (Map.Entry<String, String> entry : queryColumns.entrySet()) {
            String variableName = entry.getValue();
            if(variableName == null) {
                throw new RuntimeException("Null variable name for objectPath=" + objectPath);
            }
            if(objectNode == null)
                singleRow.putString(variableName, "Object " + objectPath + " is null");
            else
                singleRow.putValueType(variableName, getObjectProperty(objectNode, entry.getKey()));
        }
        // We are sure this is a node.
        ValueTypePair wbemPath = getObjectProperty(objectNode, "__PATH");
        if(wbemPath.getType() != ValueTypePair.ValueType.NODE_TYPE) {
            throw new RuntimeException("GetSingleObject objectPath should be a node:" + objectPath);
        }
        singleRow.putValueType(mainVariable, wbemPath);
        return singleRow;
    }

}
