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
    WmiProvider wmiselecter = new WmiProvider();

    final static private Logger logger = Logger.getLogger(WmiGetter.class);

    public boolean MatchGetter(QueryData queryData) {
        return true;
    }

    public Wbemcli.IWbemClassObject GetObjectNode(String objectPath) throws Exception {
        try {
            // FIXME: This will not work with other namespaces than "ROOT\\CIMV2".
            Wbemcli.IWbemClassObject objectNode = wmiselecter.wbemServiceRootCimv2.GetObject(objectPath, Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, null);
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

    private Wbemcli.IWbemClassObject GetObjectNodeCached(String objectPath) throws Exception {
        Wbemcli.IWbemClassObject objectNode = cacheWbemClassObject.get(objectPath);
        if (objectNode == null) {
            objectNode = GetObjectNode(objectPath);
            cacheWbemClassObject.put(objectPath, objectNode);
        }
        return objectNode;
    }

    /** This gets a WMI object and its properties mong the input list of properties.
     * This should be faster than fetching all of them.
     * @param objectPath
     * @param properties
     * @return
     */
    private Wbemcli.IWbemClassObject GetObjectNodePartial(String objectPath, Set<String> properties) {
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
                    //Variant.VARIANT.ByReference vProperty = new Variant.VARIANT.ByReference();
                    //vProperty.setValue(VT_BSTR, strPropertyBSTR);
                    psaProperties.putElement(strPropertyBSTR, indexProperties);
                    //OleAuto.INSTANCE.VariantClear(vProperty);
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

        return wmiselecter.wbemServiceRootCimv2.GetObject(objectPath, Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, pctxDrive);
    }

    GenericProvider.Row.ValueTypePair GetObjectProperty(Wbemcli.IWbemClassObject obj, String propertyName) {
        Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
        IntByReference pType = new IntByReference();
        IntByReference plFlavor = new IntByReference(); //  Maybe this is not needed.
        try {
            COMUtils.checkRC(obj.Get(propertyName, 0, pVal, pType, plFlavor));
        } catch (Exception exc) {
            // So it is easier to debug.
            throw exc;
        }

        GenericProvider.Row.ValueTypePair rowValueType = WmiProvider.VariantToValueTypePair(propertyName, "n/a", pType, pVal);
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
    private Wbemcli.IWbemClassObject PathToNode(String objectPath, Set<String> columns) throws Exception {
        Wbemcli.IWbemClassObject objectNode = null;
        if (false) {
            objectNode = GetObjectNode(objectPath);
        } else {
            if (false) {
                // This works but this is not faster.
                objectNode = GetObjectNodePartial(objectPath, columns);
            } else {
                // Not faster if all objects have different path.
                objectNode = GetObjectNodeCached(objectPath);
            }
        }
        return objectNode;
    }
    /** This returns in a Row the properties of a WMI instance specified in a WMI path.
     * The derisred properties are given in the QueryData columns.
     * @param objectPath
     * @param queryData
     * @return
     * @throws Exception
     */
    public GenericProvider.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception {

        Set<String> columns = queryData.queryColumns.keySet();
        Wbemcli.IWbemClassObject objectNode = PathToNode(objectPath, columns);
        // Maybe the object does not exist.
        if(objectNode == null) {
            logger.error("Cannot find object:" + objectPath);
            return null;
        }

        GenericProvider.Row singleRow = new GenericProvider.Row();
        for (Map.Entry<String, String> entry : queryData.queryColumns.entrySet()) {
            String variableName = entry.getValue();
            if(variableName == null) {
                throw new Exception("Null variable name for objectPath=" + objectPath);
            }
            if(objectNode == null)
                singleRow.PutString(variableName, "Object " + objectPath + " is null");
            else
                singleRow.PutValueType(variableName, GetObjectProperty(objectNode, entry.getKey()));
            /*
            String objectProperty = objectNode == null
                    ? "Object " + objectPath + " is null"
                    : GetObjectProperty(objectNode, entry.getKey());
            // PresentUtils.WbemPathToIri( ?? Et le type ??
            singleRow.Elements.put(variableName, objectProperty);
            */
        }
        // We are sure this is a node.
        GenericProvider.Row.ValueTypePair wbemPath = GetObjectProperty(objectNode, "__PATH");
        if(wbemPath.Type() != GenericProvider.ValueType.NODE_TYPE) {
            throw new Exception("GetSingleObject objectPath should be a node:" + objectPath);
        }
        singleRow.PutValueType(queryData.mainVariable, wbemPath);
        return singleRow;
    }

}
