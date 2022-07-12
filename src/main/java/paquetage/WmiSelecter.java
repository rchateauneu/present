package paquetage;

import COM.Wbemcli;
import COM.WbemcliUtil;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.COMUtils;

import com.sun.jna.ptr.IntByReference;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.function.BiConsumer;

import static com.sun.jna.platform.win32.Variant.VT_ARRAY;
import static com.sun.jna.platform.win32.Variant.VT_BSTR;

/**
 * This selects from WMI elements of a class, optionally with a WHERE clause made of key-value pairs.
 */
public class WmiSelecter {
    final static private Logger logger = Logger.getLogger(WmiSelecter.class);
    private Wbemcli.IWbemServices svc = null;

    public WmiSelecter() {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);
        svc = WbemcliUtil.connectServer("ROOT\\CIMV2");
    }

    protected void finalize() throws Throwable {
        svc.Release();
        Ole32.INSTANCE.CoUninitialize();
    }

    ArrayList<GenericSelecter.Row> EffectiveSelect(QueryData queryData) throws Exception {
        ArrayList<GenericSelecter.Row> resultRows = new ArrayList<>();
        String wqlQuery = queryData.BuildWqlQuery();
        // Temporary debugging purpose.
        logger.debug("wqlQuery=" + wqlQuery);

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
                    GenericSelecter.Row oneRow = new GenericSelecter.Row();
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

    // This will never change when a machine is running, so storing it in a cache makes tests faster.
    private static Map<String, WmiClass> cacheClasses = null;

    public Map<String, WmiClass> Classes() {
        if(cacheClasses == null) {
            cacheClasses = ClassesCached();
        }
        return cacheClasses;
    }

    /** This returns a map containing the WMI classes. */
    private Map<String, WmiClass> ClassesCached() {
        logger.debug("Start");
        // Classes are indexed with their names.
        Map<String, WmiClass> resultClasses = new HashMap<>();
        Wbemcli.IEnumWbemClassObject enumerator = svc.ExecQuery(
                "WQL",
                "SELECT * FROM meta_class",
                Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_USE_AMENDED_QUALIFIERS, null);

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

                Wbemcli.IWbemClassObject classObject = result[0];
                Variant.VARIANT.ByReference pQualifierVal = new Variant.VARIANT.ByReference();
                // String[] propertyNames = result[0].GetNames(null, Wbemcli.WBEM_CONDITION_FLAG_TYPE.WBEM_FLAG_NONSYSTEM_ONLY, pQualifierVal);
                //String[] propertyNames = result[0].GetNames(null, 0, pQualifierVal);
                String[] propertyNames = classObject.GetNames(null, 0, pQualifierVal);

                COMUtils.checkRC(classObject.Get("__CLASS", 0, pVal, pType, plFlavor));
                //logger.debug("Class name=" + pVal.stringValue());
                WmiClass newClass = new WmiClass(pVal.stringValue());
                OleAuto.INSTANCE.VariantClear(pVal);

                COMUtils.checkRC(classObject.Get("__SUPERCLASS", 0, pVal, pType, plFlavor));
                Object baseClass = pVal.getValue();
                if (baseClass != null) {
                    newClass.BaseName = baseClass.toString();
                }
                OleAuto.INSTANCE.VariantClear(pVal);

                Wbemcli.IWbemQualifierSet classQualifiersSet = classObject.GetQualifierSet();


                if(false) {
                    String[] classQualifiersNames = classQualifiersSet.GetNames();
                    System.out.println("class=" + newClass.Name + " classQualifiersNames=" + String.join("+", classQualifiersNames));
                    for (String classQualifierName : classQualifiersNames) {
                        String qualifierValue = classQualifiersSet.Get(classQualifierName);
                        System.out.println("class=" + newClass.Name + " qualifierValue=" + qualifierValue);
                    }
                }
                String classDescription = classQualifiersSet.Get("Description");
                if(classDescription != null) {
                    //System.out.println("classDescription=" + classDescription);
                    newClass.Description = classDescription;
                }
                if(false) {
                    String isAssociation = classQualifiersSet.Get("Association");
                    //System.out.println("class=" + newClass.Name + " isAssociation=" + isAssociation);
                }

                if (propertyNames != null) {
                    for (String propertyName : propertyNames) {
                        /* Filter properties such as __GENUS, __CLASS, __SUPERCLASS, __DYNASTY, __RELPATH
                        __PROPERTY_COUNT, __DERIVATION, __SERVER, __NAMESPACE, __PATH */
                        if (!propertyName.startsWith("__")) {
                            // Different properties may have the same name but belong to different classes: Thus, they are different.
                            WmiProperty newProperty = new WmiProperty(propertyName);

                            Wbemcli.IWbemQualifierSet propertyQualifiersSet = classObject.GetPropertyQualifierSet(propertyName);

                            // If the class is an association and the property is a key, we can assume it points to an object.
                            if(false) {
                                String isKey = classQualifiersSet.Get("key");
                            }

                            // The property is in the qualifier "CIMTYPE" and is a string starting with "ref:"
                            String CIMTYPE = propertyQualifiersSet.Get("CIMTYPE");
                            if(CIMTYPE != null)
                            {
                                newProperty.Type = CIMTYPE;
                            }

                            if(false) {
                                String[] propertyQualifierNames = propertyQualifiersSet.GetNames();
                                System.out.println("propertyQualifierNames=" + String.join("+", propertyQualifierNames));
                                for (String propertyQualifierName : propertyQualifierNames) {
                                    if (propertyQualifierName == "CIMTYPE") continue;
                                    String propertyQualifierValue = propertyQualifiersSet.Get(propertyQualifierName);
                                    System.out.println("propertyQualifierValue=" + propertyQualifierValue);
                                }
                            }
                            String propertyDescription = propertyQualifiersSet.Get("Description");
                            if(propertyDescription != null) {
                                //System.out.println("property=" + propertyName + " propertyDescription=" + propertyDescription);
                                newProperty.Description = propertyDescription;
                            }
                            newClass.Properties.put(propertyName, newProperty);
                        }
                    }
                }

                resultClasses.put(newClass.Name, newClass);

                // o_class = conn_wmi.Get("Win32_Process", win32com.client.constants.wbemFlagUseAmendedQualifiers)
                //print("o_class.Qualifiers_=", o_class.Qualifiers_("Description"))

                OleAuto.INSTANCE.VariantClear(pVal);
                result[0].Release();
            }
        } finally {
            enumerator.Release();
        }
        logger.debug("End");
        return resultClasses;
    }

    public Wbemcli.IWbemClassObject GetObjectNode(String objectPath) throws Exception {
        try {
            Wbemcli.IWbemClassObject objectNode = svc.GetObject(objectPath, Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, null);
            return objectNode;
        } catch (com.sun.jna.platform.win32.COM.COMException exc) {
            // Error Code 80041002 – Object Not Found
            // Possibly a file protection problem.
            System.err.println("GetObjectNode objectPath=" + objectPath + " Caught=" + exc);
            return null;
        }
    }

    // TODO: This should be thread-safe.
    static HashMap<String, Wbemcli.IWbemClassObject> cacheWbemClassObject = new HashMap<>();

    Wbemcli.IWbemClassObject GetObjectNodeCached(String objectPath) throws Exception {
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
        IntByReference pType = new IntByReference();
        try {
            COMUtils.checkRC(obj.Get(propertyName, 0, pVal, pType, null));
        } catch (Exception exc) {
            // So it is easier to debug.
            throw exc;
        }
        try {
            String value = null;
            if(pType.getValue() == Wbemcli.CIM_UINT32) {
                // Needed for example for Win32_Process.ProcessId.
                value = Integer.toString(pVal.intValue());
            } else {
                value = pVal.stringValue();
            }
            OleAuto.INSTANCE.VariantClear(pVal);
            return value;
        } catch (ClassCastException exc) {
            // So it is easier to debug.
            throw exc;
        }
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

    GenericSelecter.Row GetSingleObject(String objectPath, QueryData queryData) throws Exception {

        Set<String> columns = queryData.queryColumns.keySet();
        Wbemcli.IWbemClassObject objectNode = PathToNode(objectPath, columns);
        // Maybe the object does not exist.
        if(objectNode == null) {
            logger.error("Cannot find object:" + objectPath);
            return null;
        }

        GenericSelecter.Row singleRow = new GenericSelecter.Row();
        for (Map.Entry<String, String> entry : queryData.queryColumns.entrySet()) {
            String variableName = entry.getValue();
            if(variableName == null) {
                throw new Exception("Null variable name for objectPath=" + objectPath);
            }
            String objectProperty = objectNode == null
                    ? "Object " + objectPath + " is null"
                    : GetObjectProperty(objectNode, entry.getKey());
            singleRow.Elements.put(variableName, objectProperty);
        }
        // TODO: Is it necessary ?
        singleRow.Elements.put(queryData.mainVariable, GetObjectProperty(objectNode, "__PATH"));
        return singleRow;
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
