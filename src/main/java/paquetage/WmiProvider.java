package paquetage;

import COM.Wbemcli;
import COM.WbemcliUtil;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.COMUtils;

import com.sun.jna.ptr.IntByReference;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This selects from WMI elements of a class, optionally with a WHERE clause made of key-value pairs.
 */
public class WmiProvider {
    final static private Logger logger = Logger.getLogger(WmiProvider.class);

    private static Map<String, Wbemcli.IWbemServices> wbemServices = new HashMap<>();
    // These two variables are temporary.
    private static Wbemcli.IWbemServices wbemServiceRoot = null;
    public static Wbemcli.IWbemServices wbemServiceRootCimv2 = null;

    static {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);

        // These namespaces are always needed. Other namespaces are loaded on demand.
        wbemServiceRoot = GetWbemService("ROOT");
        wbemServiceRootCimv2 = GetWbemService("ROOT\\CIMV2");
    }

    public WmiProvider() {
        /*
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);

        // These namespaces are always needed. Other namespaces are loaded on demand.
        wbemServiceRoot = GetWbemService("");
        wbemServiceRootCimv2 = GetWbemService("CIMV2");
        */

        //wbemServiceRoot = WbemcliUtil.connectServer("ROOT");
        //wbemServiceRootCimv2 = WbemcliUtil.connectServer("ROOT\\CIMV2");

        //wbemServices.put("", wbemServiceRoot);
        //wbemServices.put("CIMV2", wbemServiceRootCimv2);

        //
        // More well-known namespaces. This is temporary, for tests.
        //wbemServices.put("Microsoft", WbemcliUtil.connectServer("ROOT\\Microsoft"));
        //wbemServices.put("Interop", WbemcliUtil.connectServer("ROOT\\Interop"));
        //wbemServices.put("Cli", WbemcliUtil.connectServer("ROOT\\Cli"));
        //wbemServices.put("aspnet", WbemcliUtil.connectServer("ROOT\\aspnet"));
        //wbemServices.put("CIMV2\\Security", WbemcliUtil.connectServer("ROOT\\CIMV2\\Security"));
    }

    static Wbemcli.IWbemServices GetWbemService(String namespace) {
        Wbemcli.IWbemServices wbemService = wbemServices.get(namespace);
        if(wbemService == null) {
            // String prefixedNamespace = namespace.equals("") ? "ROOT" : "ROOT\\" + namespace;
            try {
                // This may throw : "com.sun.jna.platform.win32.COM.COMException: (HRESULT: 80041003)"
                // 80041003: The current user does not have permission to perform the action.
                // In this case, set the service to null.
                wbemService = WbemcliUtil.connectServer(namespace);
            }
            catch(Exception exception) {
                logger.error("Caught:" + exception + " namespace=" + namespace);
                wbemService = null;
            }
            wbemServices.put(namespace, wbemService);
        }
        return wbemService;
    }

    /*
    protected void finalize() throws Throwable {
        // Release WbemServices which were actually used.
        for(Map.Entry<String, Wbemcli.IWbemServices> entry : wbemServices.entrySet()) {
            logger.debug("Releasing WBEM service to namespace:" + entry.getKey());
            entry.getValue().Release();
        }
        Ole32.INSTANCE.CoUninitialize();
    }
    */


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
    private static HashMap<String, Map<String, WmiClass>> cacheClassesMap = new HashMap<>();

    // This will never change when a machine is running, so storing it in a cache makes tests faster.
    private static HashSet<String> cacheNamespaces = null;

    /** Excluding Localization Namespaces : https://powershell.one/wmi/root
     * To find real namespaces with potentially interesting classes in them,
     * exclude any namespace name that starts with “ms_” followed by at least two numbers.
     */
    static private Pattern patternLocalizationNamespaces = Pattern.compile("^ms_\\d\\d", Pattern.CASE_INSENSITIVE);

    private void Namespaces(Set<String> namespacesHierarchical, Wbemcli.IWbemServices wbemService, String namespace) {
        logger.debug("namespace=" + namespace);
        if(! namespace.startsWith("ROOT")) {
            throw new RuntimeException("Invalid namespace:" + namespace);
        }
        Wbemcli.IEnumWbemClassObject enumerator = wbemService.ExecQuery(
        "WQL",
                "SELECT Name FROM __NAMESPACE",
                Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_USE_AMENDED_QUALIFIERS, null);

        Set<String> namespacesFlat = new HashSet<>();
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
                WinNT.HRESULT hr = classObject.Get("Name", 0, pVal, pType, plFlavor);
                COMUtils.checkRC(hr);
                String namespaceSub = pVal.stringValue();

                // Excluding Localization Namespaces : https://powershell.one/wmi/root
                Matcher matcher = patternLocalizationNamespaces.matcher(namespaceSub);
                boolean matchFound = matcher.find();
                if(matchFound) {
                    logger.debug("Excluding Localization Namespace:" + namespaceSub);
                } else {
                    namespacesFlat.add(namespaceSub);
                }
                classObject.Release();
            }
            OleAuto.INSTANCE.VariantClear(pVal);
        } finally {
            enumerator.Release();
        }

        for(String namespaceSub : namespacesFlat) {
            String namespaceFull = namespace + "\\" + namespaceSub;
            logger.debug("namespaceFull=" + namespaceFull);
            // This may throw : "com.sun.jna.platform.win32.COM.COMException: (HRESULT: 80041003)"
            // 80041003: The current user does not have permission to perform the action.
            // In this case, do not add the namespace to the list.
            Wbemcli.IWbemServices wbemServiceSub = GetWbemService(namespaceFull);
            if(wbemServiceSub != null) {
                // Strip string "ROOT\\" at the beginning.
                // namespacesHierarchical.add(namespaceFull.substring(5));
                namespacesHierarchical.add(namespaceFull);
                Namespaces(namespacesHierarchical, wbemServiceSub, namespaceFull);
            }
        }
    }

    public Set<String> Namespaces() {
        if(cacheNamespaces == null) {
            cacheNamespaces = new HashSet<>();
            logger.debug("Filling namespaces cache");
            Namespaces(cacheNamespaces, wbemServiceRoot, "ROOT");
            logger.debug("End. Number of namespaces=" + cacheNamespaces.size());
        }
        return cacheNamespaces;
    }

    // Very commonly used.
    public Map<String, WmiClass> ClassesCIMV2() {
        return Classes("ROOT\\CIMV2");
    }

    public Map<String, WmiClass> Classes(String namespace) {
        Map<String, WmiClass> cacheClasses = cacheClassesMap.get(namespace);
        if(cacheClasses == null) {
            logger.debug("Getting IWbemServices for namespace=" + namespace);
            Wbemcli.IWbemServices wbemService = GetWbemService(namespace);
            logger.debug("Getting classes for namespace=" + namespace);
            cacheClasses = ClassesCached(wbemService);
            logger.debug("End. Number of classes=" + cacheClasses.size());
            cacheClassesMap.put(namespace, cacheClasses);
        }
        return cacheClasses;
    }

    /** This returns a map containing the WMI classes. This is calculated with a WQL query.
     * This is rather task whose result does not often change, so it must be cached.
     * */
    private Map<String, WmiClass> ClassesCached(Wbemcli.IWbemServices wbemService) {
        // Classes are indexed with their names.
        Map<String, WmiClass> resultClasses = new HashMap<>();
        Wbemcli.IEnumWbemClassObject enumerator = wbemService.ExecQuery(
                "WQL",
                "SELECT * FROM meta_class",
                Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_USE_AMENDED_QUALIFIERS, null);
        logger.debug("After query");

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
                String[] propertyNames = classObject.GetNames(null, 0, pQualifierVal);

                COMUtils.checkRC(classObject.Get("__CLASS", 0, pVal, pType, plFlavor));
                String className = pVal.stringValue();
                WmiClass newClass = new WmiClass(className);
                if(className.equals("__NAMESPACE")) {
                    logger.debug("Do not store className=" + className);
                    continue;
                }
                if(className != newClass.Name) {
                    throw new RuntimeException("Error building class:" + className);
                }
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
                    System.out.println("class=" + className + " classQualifiersNames=" + String.join("+", classQualifiersNames));
                    for (String classQualifierName : classQualifiersNames) {
                        String qualifierValue = classQualifiersSet.Get(classQualifierName);
                        System.out.println("class=" + className + " qualifierValue=" + qualifierValue);
                    }
                }
                String classDescription = classQualifiersSet.Get("Description");
                if(classDescription != null) {
                    newClass.Description = classDescription;
                }
                if(false) {
                    String isAssociation = classQualifiersSet.Get("Association");
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
                                // Debugging purpose.
                                String isKey = classQualifiersSet.Get("key");
                            }

                            // The property is in the qualifier "CIMTYPE" and is a string starting with "ref:"
                            String CIMTYPE = propertyQualifiersSet.Get("CIMTYPE");
                            if(CIMTYPE != null)
                            {
                                newProperty.Type = CIMTYPE;
                            }

                            if(false) {
                                // Debugging purpose.
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
                                newProperty.Description = propertyDescription;
                            }
                            newClass.Properties.put(propertyName, newProperty);
                        }
                    }
                }

                resultClasses.put(className, newClass);
                classObject.Release();
            }
        } finally {
            enumerator.Release();
        }
        return resultClasses;
    }

    static GenericProvider.Row.ValueTypePair VariantToValueTypePair(
            String lambda_column,
            String lambda_variable,
            IntByReference pType,
            Variant.VARIANT.ByReference pVal) {
        /** TODO: If the value is a reference, get the object if possible ! Or is it simply a string ?
        if(pType.getValue() == Wbemcli.CIM_REFERENCE) ...
            Reference properties, which have the type CIM_REFERENCE,
            that contains the "REF:classname" value.
            The classname value describes the class type of the reference property.
            There is apparently no way to get the object pointed to by the reference.
         */
        WTypes.VARTYPE wtypesValueType = pVal.getVarType();
        // The value is sometimes different. Take the supposedly good one.
        int valueTypeUnknown = wtypesValueType.intValue();
        int valueType = pType.getValue();
        if(valueType != valueTypeUnknown) {
            logger.warn("Different types:" + valueType + " != " + valueTypeUnknown + "=" + pType.toString());
        }

        Object valObject = pVal.getValue();
        if(valObject == null) {
            logger.debug("Value is null. lambda_column=" + lambda_column
                    + " lambda_variable=" + lambda_variable + " type=" + valueType);
        }

        String rowValue;
        GenericProvider.ValueType rowType;
        if(lambda_column.equals("__PATH")) {
            // Not consistent for Win32_Product.
            if(valueType != Wbemcli.CIM_STRING) {
                // FIXME: Theoretically, it should only be CIM_REFERENCE ...
                throw new RuntimeException(
                        "Should be CIM_STRING lambda_column=" + lambda_column
                                + " lambda_variable=" + lambda_variable + " valueType=" + valueType);
            }
            rowValue = pVal.stringValue();
            rowType = GenericProvider.ValueType.NODE_TYPE;
        }
        else {
            switch(valueType) {
                case Wbemcli.CIM_REFERENCE:
                case Wbemcli.CIM_STRING:
                    rowValue = pVal.stringValue();
                    rowType = GenericProvider.ValueType.NODE_TYPE;
                    // logger.debug("pVal.stringValue()=" + pVal.stringValue() + " pType=" + pType);
                    break;
                case Wbemcli.CIM_SINT8:
                case Wbemcli.CIM_UINT8:
                case Wbemcli.CIM_SINT16:
                case Wbemcli.CIM_UINT16:
                case Wbemcli.CIM_UINT32:
                case Wbemcli.CIM_SINT32:
                case Wbemcli.CIM_UINT64:
                case Wbemcli.CIM_SINT64:
                    // Mandatory conversion for Win32_Process.ProcessId, for example.
                    String longValue;
                    if(valObject == null) {
                        // "Win32_Process.ExecutionState" might be null.
                        // This is temporarily indicated with a special string for later debugging.
                        // TODO: Some values are null. Why ?
                        longValue = lambda_column + "_IS_NULL";
                        rowType = GenericProvider.ValueType.STRING_TYPE;
                    } else {
                        rowType = GenericProvider.ValueType.INT_TYPE;
                        if(valueType == valueTypeUnknown) {
                            // This should work because no contradiction.
                            longValue = Long.toString(pVal.longValue());
                        } else {
                            // Some corner cases do not work, i.e. Win32_Process.InstallDate
                            logger.warn("Different types:" + valueType
                                    + " != " + valueTypeUnknown
                                    + "=" + pType);

                            // FIXME: Obscure behaviour in some corner cases ??
                            switch(valueTypeUnknown) {
                                case Wbemcli.CIM_REFERENCE:
                                case Wbemcli.CIM_STRING:
                                    longValue = pVal.stringValue();
                                    break;
                                case Wbemcli.CIM_SINT8:
                                case Wbemcli.CIM_UINT8:
                                case Wbemcli.CIM_SINT16:
                                case Wbemcli.CIM_UINT16:
                                case Wbemcli.CIM_UINT32:
                                case Wbemcli.CIM_SINT32:
                                case Wbemcli.CIM_UINT64:
                                case Wbemcli.CIM_SINT64:
                                    longValue = Long.toString(pVal.longValue());
                                    break;
                                case Wbemcli.CIM_REAL32:
                                case Wbemcli.CIM_REAL64:
                                case Wbemcli.CIM_DATETIME:
                                default:
                                    longValue = pVal.toString();
                                    break;
                            } // switch
                        }
                    }
                    rowValue = longValue;
                    break;
                case Wbemcli.CIM_REAL32:
                case Wbemcli.CIM_REAL64:
                    rowValue = Double.toString(pVal.doubleValue());
                    rowType = GenericProvider.ValueType.FLOAT_TYPE;
                    break;
                case Wbemcli.CIM_DATETIME:
                    if(false) {
                        // FIXME : This crashes:
                        // java.lang.ClassCastException: class com.sun.jna.platform.win32.WTypes$BSTR
                        // cannot be cast to class com.sun.jna.platform.win32.OaIdl$DATE
                        // (com.sun.jna.platform.win32.WTypes$BSTR and
                        // com.sun.jna.platform.win32.OaIdl$DATE are in unnamed module of loader 'app')
                        Date dateValueDate = pVal.dateValue();
                    }
                    String dateValue = pVal.stringValue();
                    logger.debug("dateValue=" + dateValue);
                    rowValue = dateValue;
                    rowType = GenericProvider.ValueType.DATE_TYPE;
                    break;
                default:
                    String valStringValue = pVal.stringValue();
                    if (valStringValue == null) {
                        logger.error("Null when converting lambda_column=" + lambda_column
                                + " lambda_variable=" + lambda_variable + " type=" + valueType);
                    }
                    rowValue = valStringValue;
                    rowType = GenericProvider.ValueType.STRING_TYPE;
                    break;
            } // switch
        }
        GenericProvider.Row.ValueTypePair rowValueType = new GenericProvider.Row.ValueTypePair(rowValue, rowType);
        return rowValueType;
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
