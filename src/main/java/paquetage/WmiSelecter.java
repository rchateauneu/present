package paquetage;

import COM.Wbemcli;
import COM.WbemcliUtil;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.COMUtils;

import com.sun.jna.ptr.IntByReference;

import java.util.*;
import java.util.function.BiConsumer;

import static com.sun.jna.platform.win32.Variant.VT_ARRAY;
import static com.sun.jna.platform.win32.Variant.VT_BSTR;

/**
 * This selects from WMI elements of a class, optionally with a WHERE clause made of key-value pairs.
 */
public class WmiSelecter {
    Wbemcli.IWbemServices svc = null;

    public WmiSelecter() {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);
        svc = WbemcliUtil.connectServer("ROOT\\CIMV2");
    }

    protected void finalize() throws Throwable {
        svc.Release();
        Ole32.INSTANCE.CoUninitialize();
    }

    ArrayList<MetaSelecter.Row> WqlSelectWMI(QueryData queryData) throws Exception {
        ArrayList<MetaSelecter.Row> resultRows = new ArrayList<>();
        String wqlQuery = queryData.BuildWqlQuery();
        // Temporary debugging purpose.
        System.out.println("wqlQuery=" + wqlQuery);

        if (queryData.isMainVariableAvailable) {
            throw new Exception("Main variable should not be available in a WQL query.");
        }

        int countRows = 100;

        // Not always necessary to add __PATH in the selected fields. Possibly consider WBEM_FLAG_ENSURE_LOCATABLE.
        Wbemcli.IEnumWbemClassObject enumerator = svc.ExecQuery("WQL", wqlQuery,
                Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, null);
        try {
            Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
            IntByReference pType = new IntByReference();
            IntByReference plFlavor = null;
            while (true) {
                /**
                 * When selecting a single column "MyColumn", the returned effective columns are:
                 *     __GENUS, __CLASS, __SUPERCLASS, __DYNASTY, __RELPATH, __PROPERTY_COUNT,
                 *     __DERIVATION, __SERVER, __NAMESPACE, __PATH, MyColumn
                 */
                Wbemcli.IWbemClassObject[] wqlResults = enumerator.Next(0, countRows);
                if (wqlResults.length == 0) {
                    break;
                }
                for (int indexRow = 0; indexRow < wqlResults.length; ++indexRow) {
                    Wbemcli.IWbemClassObject wqlResult = wqlResults[indexRow];
                    MetaSelecter.Row oneRow = new MetaSelecter.Row();
                    // The path is always returned if the key is selected.
                    // This path should never be recalculated to ensure consistency with WMI.
                    // All values are NULL except, typically:
                    //     __CLASS=Win32_Process
                    //     __RELPATH=Win32_Process.Handle="4"
                    if (false) {
                        String[] names = wqlResult.GetNames(null, 0, null);
                        System.out.println("names=" + String.join("+", names));

                        for (String col : names) {
                            COMUtils.checkRC(wqlResult.Get(col, 0, pVal, pType, plFlavor));
                            if (pVal.getValue() == null)
                                System.out.println(col + "=" + "NULL");
                            else
                                System.out.println(col + "=" + pVal.getValue().toString());
                            OleAuto.INSTANCE.VariantClear(pVal);
                        }
                    }

                    // This lambda extracts the value of a single column.
                    BiConsumer<String, String> storeValue = (String lambda_column, String lambda_variable) -> {
                        COMUtils.checkRC(wqlResult.Get(lambda_column, 0, pVal, pType, plFlavor));
                        // TODO: If the value is a reference, get the object !
                        /*
                        if(pType.getValue() == Wbemcli.CIM_REFERENCE) ...
                        Reference properties, which have the type CIM_REFERENCE,
                        that contains the "REF:classname" value.
                        The classname value describes the class type of the reference property.
                        There is apparently no way to get the object pointed to by the reference.
                         */
                        oneRow.Elements.put(lambda_variable, pVal.stringValue());
                        OleAuto.INSTANCE.VariantClear(pVal);
                    };

                    queryData.queryColumns.forEach(storeValue);
                    // Also get the path of each returned object.
                    storeValue.accept("__PATH", queryData.mainVariable);
                    wqlResult.Release();
                    resultRows.add(oneRow);
                }
            }
        } finally {
            enumerator.Release();
        }

        return resultRows;
    }

    public class WmiProperty {
        public String Name;
        public String Description;
        public String Type;

        public WmiProperty(String propertyName) {
            Name = propertyName;
            Description = "No description available yet for property " + propertyName;
            Type = "string";
        }
    }

    public class WmiClass {
        public String Name;
        public String BaseName;
        public String Description;
        public Map<String, WmiProperty> Properties;

        public WmiClass(String className) {
            Name = className;
            Properties = new HashMap<String, WmiProperty>();
            Description = "No description available yet for class " + className;
        }
    }

    Map<String, WmiClass> Classes() {
        Map<String, WmiClass> resultClasses = new HashMap<String, WmiClass>();
        Wbemcli.IEnumWbemClassObject enumerator = svc.ExecQuery("WQL", "SELECT * FROM meta_class",
                Wbemcli.WBEM_FLAG_FORWARD_ONLY, null);

        try {
            Wbemcli.IWbemClassObject[] result;
            Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
            IntByReference pType = new IntByReference();
            IntByReference plFlavor = new IntByReference();

            while (true) {
                result = enumerator.Next(0, 1);
                if (result.length == 0) {
                    break;
                }

                Variant.VARIANT.ByReference pQualifierVal = new Variant.VARIANT.ByReference();
                // String[] names = result[0].GetNames(null, Wbemcli.WBEM_CONDITION_FLAG_TYPE.WBEM_FLAG_NONSYSTEM_ONLY, pQualifierVal);
                //String[] names = result[0].GetNames(null, 0, pQualifierVal);
                String[] names = result[0].GetNames(null, 0, pQualifierVal);

                COMUtils.checkRC(result[0].Get("__CLASS", 0, pVal, pType, plFlavor));
                WmiClass newClass = new WmiClass(pVal.stringValue());
                OleAuto.INSTANCE.VariantClear(pVal);

                COMUtils.checkRC(result[0].Get("__SUPERCLASS", 0, pVal, pType, plFlavor));
                Object baseClass = pVal.getValue();
                if (baseClass != null) {
                    newClass.BaseName = baseClass.toString();
                }
                OleAuto.INSTANCE.VariantClear(pVal);

                if (names != null) {
                    for (String one_name : names) {
                        /* Filter properties such as __GENUS, __CLASS, __SUPERCLASS, __DYNASTY, __RELPATH
                        __PROPERTY_COUNT, __DERIVATION, __SERVER, __NAMESPACE, __PATH */
                        if (!one_name.startsWith("__")) {
                            WmiProperty newProperty = new WmiProperty(one_name);
                            newClass.Properties.put(one_name, newProperty);
                        }
                    }
                }

                resultClasses.put(newClass.Name, newClass);
                OleAuto.INSTANCE.VariantClear(pVal);
                result[0].Release();
            }
        } finally {
            enumerator.Release();
        }
        return resultClasses;
    }

    public Wbemcli.IWbemClassObject GetObjectNode(String objectPath) {
        return svc.GetObject(objectPath, Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, null);
    }

    // TODO: This should be thread-safe.
    static HashMap<String, Wbemcli.IWbemClassObject> cacheWbemClassObject = new HashMap<>();

    Wbemcli.IWbemClassObject GetObjectNodeCached(String objectPath) {
        Wbemcli.IWbemClassObject ret = cacheWbemClassObject.get(objectPath);
        if (ret == null) {
            ret = GetObjectNode(objectPath);
            cacheWbemClassObject.put(objectPath, ret);
        }
        return ret;
    }

    Wbemcli.IWbemClassObject GetObjectNodePartial(String objectPath, Set<String> properties) {
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

        return svc.GetObject(objectPath, Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, pctxDrive);
    }

    String GetObjectProperty(Wbemcli.IWbemClassObject obj, String propertyName) {
        Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
        COMUtils.checkRC(obj.Get(propertyName, 0, pVal, null, null));
        String value = pVal.stringValue();
        OleAuto.INSTANCE.VariantClear(pVal);
        return value;
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
    Wbemcli.IWbemClassObject PathToNode(String objectPath, Set<String> columns) throws Exception {
        Wbemcli.IWbemClassObject objectNode = null;
        try {
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
        } catch (com.sun.jna.platform.win32.COM.COMException exc) {
            System.out.println("objectPath=" + objectPath + " Caught=" + exc);
        }
        return objectNode;
    }

    void GetVariablesFromNodePath(String objectPath, QueryData queryData, Map<String, String> variablesContext) throws Exception {

        Set<String> columns = queryData.queryColumns.keySet();
        Wbemcli.IWbemClassObject objectNode = PathToNode(objectPath, columns);

        // Now takes the values needed from the members of this object.
        for (Map.Entry<String, String> entry : queryData.queryColumns.entrySet()) {
            String variableName = entry.getValue();
            if (!variablesContext.containsKey(variableName)) {
                throw new Exception("Variable " + variableName + " from object not in context");
            }
            String objectProperty = objectNode == null
                    ? "Object " + objectPath + " is null"
                    : GetObjectProperty(objectNode, entry.getKey());
            variablesContext.put(variableName, objectProperty);
        }
    }
}



/*
Same feature on Linux:
https://docs.oracle.com/cd/E19455-01/806-6831/6jfoe2ofq/index.html
import java.rmi.*;
import com.sun.wbem.client.CIMClient;
import com.sun.wbem.cim.CIMInstance;
import com.sun.wbem.cim.CIMValue;
import com.sun.wbem.cim.CIMProperty;
import com.sun.wbem.cim.CIMNameSpace;
import com.sun.wbem.cim.CIMObjectPath;
import com.sun.wbem.cim.CIMClass;
import java.util.Enumeration;
 */
