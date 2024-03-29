package paquetage;

import COM.Wbemcli;
import COM.WbemcliUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.COMUtils;

import com.sun.jna.ptr.IntByReference;
import org.apache.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;


/**
 * This selects from WMI elements of a class, optionally with a WHERE clause made of key-value pairs.
 */
public class WmiProvider {
    final static private Logger logger = Logger.getLogger(WmiProvider.class);

    private static Map<String, Wbemcli.IWbemServices> wbemServices = new HashMap<>();
    // These two variables are temporary.
    private static Wbemcli.IWbemServices wbemServiceRoot = null;
    public static Wbemcli.IWbemServices wbemServiceRootCimv2 = null;
    // For "\\\\LAPTOP-R89KG6V1\\ROOT\\StandardCimv2:MSFT_Net..."
    //  or '\\LAPTOP-R89KG6V1\ROOT\CIMV2:Win32_Process.Handle="12456"'
    static private String regexReferenceNamespace = "\\\\\\\\[-\\dA-Z\\._]+\\\\(ROOT[-A-Z\\d_\\\\]*):.*";
    static private Pattern patternReferenceNamespace = Pattern.compile(regexReferenceNamespace, Pattern.CASE_INSENSITIVE);

    static private String regexReferenceNamespaceClassname = "\\\\\\\\[-\\dA-Z\\._]+\\\\(ROOT[-A-Z\\d_\\\\]*):([A-Z\\d_]*)\\..*";
    static private Pattern patternReferenceNamespaceClassname = Pattern.compile(regexReferenceNamespaceClassname, Pattern.CASE_INSENSITIVE);
    static private String regexNamespace = "^ROOT[\\\\_A-Z\\d]*$";
    static private Pattern patternNamespace = Pattern.compile(regexNamespace, Pattern.CASE_INSENSITIVE);

    static private String regexWmiIdentifier = "^[_A-Z\\d]*$";
    static private Pattern patternWmiIdentifier = Pattern.compile(regexWmiIdentifier, Pattern.CASE_INSENSITIVE);


    static {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);
        /*
        HRESULT=0x80041003 with connectServer
        https://social.msdn.microsoft.com/Forums/vstudio/en-US/4d54adf4-56d0-4261-ac74-5c5fe736d505/wmi-call-from-c-results-in-hresult0x80041003?forum=vcgeneral
         on Windows XP replace RPC_C_IMP_LEVEL_IMPERSONATE with RPC_C_IMP_LEVEL_DELEGATE in the calls to CoInitializeSecurity() and CoSetProxyBlanket().
         */

        // For documentation only. Not needed, see connectServer().
        if(false) {
            Ole32.INSTANCE.CoInitializeSecurity(
                    null,
                    -1,
                    null,
                    null,
                    Ole32.RPC_C_AUTHN_LEVEL_DEFAULT,
                    Ole32.RPC_C_IMP_LEVEL_IMPERSONATE,
                    null,
                    Ole32.EOAC_NONE,
                    null
            );
        } else {
            logger.info("Not calling CoInitializeSecurity");
        }

        // These namespaces are practically always needed. Other namespaces are loaded on demand.
        wbemServiceRoot = getWbemService("ROOT");
        wbemServiceRootCimv2 = getWbemService("ROOT\\CIMV2");
    }

    public WmiProvider() {
    }

    static Wbemcli.IWbemServices getWbemService(String namespace) {
        checkValidNamespace(namespace);
        Wbemcli.IWbemServices wbemService = wbemServices.get(namespace);
        if(wbemService == null) {
            try {
                // This may throw : "com.sun.jna.platform.win32.COM.COMException: (HRESULT: 80041003)"
                // 80041003: The current user does not have permission to perform the action.
                // In this case, set the service to null.

                /* To sort out access rights, consider:
                    From the Start Menu, choose "Run"
                    Enter wmimgmt.msc
                    Right-click "WMI-Control (Local)" and choose Properties
                    Go to "Security" tab.
                            Navigate to: root/microsoft/windows/storage/
                            Check permissions at this level by clicking to highlight "Storage",
                            and then click "Security" button in the lower right. Dig deeper if needed.
                 */
                wbemService = WbemcliUtil.connectServer(namespace);
                if(wbemService == null) {
                    throw new RuntimeException("Cannot create wbemService to namespace=" + namespace);
                }

                // For documentation only. Not needed, see connectServer().
                if(false) {
                    Ole32.INSTANCE.CoSetProxyBlanket(
                            wbemService, /* IWbemServices *pSvc */
                            Ole32.RPC_C_AUTHN_WINNT,
                            Ole32.RPC_C_AUTHZ_NONE,
                            null,
                            Ole32.RPC_C_AUTHN_LEVEL_CALL,
                            Ole32.RPC_C_IMP_LEVEL_IMPERSONATE,
                            null,
                            Ole32.EOAC_NONE
                    );
                } else {
                    logger.info("Not calling CoSetProxyBlanket for namespace=" + namespace);
                }
            }
            catch(Exception exception) {
                // This might throw 80041003=wbemErrAccessDenied with 5 seconds delay.
                // Unfortunately, no other Wbem service creation can be made at this time, by another thread.
                logger.error("Caught:" + exception + " namespace=" + namespace);
                wbemService = null;
            }
            wbemServices.put(namespace, wbemService);
        }
        return wbemService;
    }

    /** This is used to check that a WMI value cannot be possibly a node.
     * Example:
     * '\\LAPTOP-R89KG6V1\ROOT\StandardCimv2:MSFT_NetIPAddress.CreationClassName="",Name="poB:DD;C:@D<n>nD==:@DB=:m/;@55;@55;55;",SystemCreationClassName="",SystemName=""'
     */
    static public String extractNamespaceFromRef(String refString) {
        Matcher matcher = patternReferenceNamespace.matcher(refString);
        boolean matchFound = matcher.find();
        if (!matchFound) {
            logger.debug("No namespace in:" + refString);
            return null;
        }
        String namespace = matcher.group(1);
        checkValidNamespace(namespace);
        return namespace;
    }

    static public void checkValidNamespace(String namespace) {
        if(namespace == null) {
            throw new RuntimeException("namespace is null");
        }
        Matcher matcher = patternNamespace.matcher(namespace);
        boolean matchFound = matcher.find();
        if (!matchFound) {
            throw new RuntimeException("Invalid namespace:" + namespace);
        }
    }

    // '\\LAPTOP-R89KG6V1\ROOT\CIMV2:Win32_Process.Handle="12456"'
    static public String extractClassnameFromRef(String refString) {
        Matcher matcher = patternReferenceNamespaceClassname.matcher(refString);
        boolean matchFound = matcher.find();
        if (!matchFound) {
            return null;
        }
        String classname = matcher.group(2);
        return classname;
    }

    /** This is exclusively used for tests, because this is a very common namespace. */
    static String toCIMV2(String term) {
        return WmiOntology.namespaceTermToIRI("ROOT\\CIMV2", term);
    }

    static public void checkValidClassname(String wmiClassName) {
        if(wmiClassName == null) {
            throw new RuntimeException("class is null");
        }
        Matcher matcher = patternWmiIdentifier.matcher(wmiClassName);
        boolean matchFound = matcher.find();
        if (!matchFound) {
            throw new RuntimeException("Invalid class:" + wmiClassName);
        }
    }

    static public void checkValidPredicate(String wmiPredicateName) {
        String[] splitPredicate = wmiPredicateName.split("\\.");
        if(splitPredicate.length != 2) {
            throw new RuntimeException("Invalid predicate (No dot):" + wmiPredicateName);
        }
        checkValidClassname(splitPredicate[0]);
        checkValidShortPredicate(splitPredicate[1]);
    }

    static public void checkValidShortPredicate(String wmiPredicateName) {
        if(wmiPredicateName == null) {
            throw new RuntimeException("predicate is null");
        }
        Matcher matcher = patternWmiIdentifier.matcher(wmiPredicateName);
        boolean matchFound = matcher.find();
        if (!matchFound) {
            throw new RuntimeException("Invalid predicate:" + wmiPredicateName);
        }
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

    public static class WmiProperty {
        public String propertyName;
        public String propertyDescription;
        public String propertyType;

        public WmiProperty(String inputPropertyName) {
            propertyName = inputPropertyName;
            propertyDescription = "No description available yet for property " + propertyName;
            propertyType = "string";
        }

        // For Json deserialization.
        public WmiProperty() {
            super();
        }

        boolean isWbemPathRef() {
            return propertyType.startsWith("ref:");
        }
    }

    public static class WmiClass {
        public String className;
        public String classBaseName;
        public String classDescription;
        public Map<String, WmiProperty> classProperties;

        public WmiClass(String inputClassName) {
            className = inputClassName;
            classProperties = new HashMap<>();
            classDescription = "No description available yet for class " + className;
        }

        // For Json deserialization.
        public WmiClass() {
            super();
        }
    }

    // This will never change when a machine is running, so storing it in a cache makes tests faster.
    private static HashMap<String, Map<String, WmiClass>> cacheClassesMap = new HashMap<>();

    /** Excluding Localization Namespaces : https://powershell.one/wmi/root
     * To find real namespaces with potentially interesting classes in them,
     * exclude any namespace name that starts with “ms_” followed by at least two numbers.
     */
    static private Pattern patternLocalizationNamespaces = Pattern.compile("^ms_\\d\\d", Pattern.CASE_INSENSITIVE);

    private static Set<String> flattenNamespaces(Wbemcli.IWbemServices wbemService, String namespace) {
        checkValidNamespace(namespace);
        Wbemcli.IEnumWbemClassObject enumerator = wbemService.ExecQuery(
                "WQL",
                "SELECT Name FROM __NAMESPACE",
                Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, null);
        // Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_RETURN_IMMEDIATELY, null);
        // Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_USE_AMENDED_QUALIFIERS, null);

        Set<String> namespacesFlat = new HashSet<>();
        try {
            Wbemcli.IWbemClassObject[] result;
            Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
            IntByReference pType = new IntByReference();
            IntByReference plFlavor = new IntByReference();

            while (true) {
                result = enumerator.Next(Wbemcli.WBEM_INFINITE, 1);
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
        return namespacesFlat;
    }

    private static void buildNamespacesList(Set<String> namespacesHierarchical, Wbemcli.IWbemServices wbemService, String namespace) {

        Set<String> namespacesFlat = flattenNamespaces(wbemService, namespace);

        for(String namespaceSub : namespacesFlat) {
            String namespaceFull = namespace + "\\" + namespaceSub;
            logger.debug("namespaceFull=" + namespaceFull);
            // This may throw : "com.sun.jna.platform.win32.COM.COMException: (HRESULT: 80041003)"
            // 80041003: The current user does not have permission to perform the action.
            // In this case, do not add the namespace to the list.
            Wbemcli.IWbemServices wbemServiceSub = getWbemService(namespaceFull);
            if(wbemServiceSub != null) {
                namespacesHierarchical.add(namespaceFull);
                buildNamespacesList(namespacesHierarchical, wbemServiceSub, namespaceFull);
            }
        }
    }

    /** This will never change when a machine is running, so storing it in a cache makes tests faster.
     * There is no benefit multi-threading connections because it seems that the creation of WBEM connections
     * is serialized.
     */
    private static HashSet<String> cacheNamespaces = null;

    public static Set<String> namespacesList() throws Exception {
        if(cacheNamespaces == null) {
            cacheNamespaces = new HashSet<>();
            Path pathCacheNamespaces = Paths.get(CacheManager.ontologiesPathCache + "\\" + "namespaces.json");
            boolean fileExists = Files.exists(pathCacheNamespaces);
            ObjectMapper mapperObj = new ObjectMapper();
            if(fileExists) {
                logger.debug("Loading namespaces from:" + pathCacheNamespaces);
                cacheNamespaces = mapperObj.readValue(pathCacheNamespaces.toFile(), HashSet.class);
                logger.debug("Number of namespaces=" + cacheNamespaces.size());
            } else {
                logger.debug("Filling namespaces cache to:" + pathCacheNamespaces);
                buildNamespacesList(cacheNamespaces, wbemServiceRoot, "ROOT");
                logger.debug("End. Number of namespaces=" + cacheNamespaces.size());

                // Maybe the cache directory does not exist.
                CacheManager.checkCacheDirectoryExists();
                mapperObj.writeValue(pathCacheNamespaces.toFile(), cacheNamespaces);
                logger.debug("Written namespaces to:" + pathCacheNamespaces);
            }
        }
        return cacheNamespaces;
    }

    // Very commonly used for tests.
    public Map<String, WmiClass> classesCIMV2() {
        return classesMap("ROOT\\CIMV2");
    }


    public Map<String, WmiClass> classesMap(String namespace) {
        Map<String, WmiClass> cacheClasses = cacheClassesMap.get(namespace);

        if(cacheClasses == null) {
            File fileCacheClasses = CacheManager.ClassesCacheFile(namespace);
            boolean fileExists = Files.exists(fileCacheClasses.toPath());
            ObjectMapper mapperObj = new ObjectMapper();
            if (fileExists) {
                logger.debug("Loading classes from classes cache file:" + fileCacheClasses);
                try {
                    // java.lang.ClassCastException: class java.util.LinkedHashMap
                    // cannot be cast to class paquetage.WmiProvider$WmiClass
                    // (java.util.LinkedHashMap is in module java.base of loader 'bootstrap';
                    // paquetage.WmiProvider$WmiClass is in unnamed module of loader 'app')
                    // cacheClasses = mapperObj.readValue(fileCacheClasses, HashMap.class);

                    /*
                    java.lang.RuntimeException: com.fasterxml.jackson.databind.exc.InvalidDefinitionException:
                    Cannot construct instance of `paquetage.WmiProvider$WmiClass`:
                    non-static inner classes like this can only by instantiated using default, no-argument constructor
                    at [Source: (File); line: 1, column: 30]
                    (through reference chain: java.util.LinkedHashMap["CIM_ManagedSystemElement"])
                     */
                    cacheClasses = mapperObj.readValue(fileCacheClasses, new TypeReference<Map<String, WmiClass>>(){});
                } catch (Exception exc) {
                    logger.error("Cannot read classes cache:" + fileCacheClasses);
                    throw new RuntimeException(exc);
                }
            } else {
                cacheClasses = classesNoCache(namespace);
                try {
                    logger.debug("Writing classes cache file:" + fileCacheClasses);
                    mapperObj.writeValue(fileCacheClasses, cacheClasses);
                } catch (Exception exc) {
                    logger.error("Cannot write classes cache file:" + fileCacheClasses);
                    throw new RuntimeException(exc);
                }
                logger.debug("Written classes to:" + fileCacheClasses);
            }
            cacheClassesMap.put(namespace, cacheClasses);
            logger.debug("Number of namespaces=" + cacheClasses.size());
        }
        return cacheClasses;
    }

    /** This returns a map containing the WMI classes. This is calculated with a WQL query.
     * This is rather a task whose result does not often change, so it must be cached.
     * */
    private Map<String, WmiClass> classesNoCache(String namespace) {
        Wbemcli.IWbemServices wbemService = getWbemService(namespace);
        if(wbemService == null) {
            throw new RuntimeException("WBEM service is null for namespace=" + namespace);
        }
        logger.debug("Getting classes for namespace=" + namespace);

        // Classes are indexed with their names.
        Map<String, WmiClass> resultClasses = new HashMap<>();
        logger.debug("About to select from meta_class");
        Wbemcli.IEnumWbemClassObject enumerator = wbemService.ExecQuery(
                "WQL",
                "SELECT * FROM meta_class",
                Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_USE_AMENDED_QUALIFIERS, null);
                // Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, null);
                // Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_RETURN_IMMEDIATELY, null);
        logger.debug("After query");

        try {
            Wbemcli.IWbemClassObject[] result;
            Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
            IntByReference pType = new IntByReference();
            IntByReference plFlavor = new IntByReference();

            while (true) {
                result = enumerator.Next(Wbemcli.WBEM_INFINITE, 1);
                if (result.length == 0) {
                    break;
                }

                Wbemcli.IWbemClassObject classObject = result[0];
                Variant.VARIANT.ByReference pQualifierVal = new Variant.VARIANT.ByReference();
                String[] propertyNames = classObject.GetNames(null, 0, pQualifierVal);

                COMUtils.checkRC(classObject.Get("__CLASS", 0, pVal, pType, plFlavor));
                String className = pVal.stringValue();
                if(className.equals("__NAMESPACE")) {
                    logger.debug("Do not store className=" + className);
                    continue;
                }
                WmiClass newClass = new WmiClass(className);
                if(className != newClass.className) {
                    throw new RuntimeException("Error building class:" + className);
                }
                OleAuto.INSTANCE.VariantClear(pVal);

                COMUtils.checkRC(classObject.Get("__SUPERCLASS", 0, pVal, pType, plFlavor));
                Object baseClass = pVal.getValue();
                if (baseClass != null) {
                    newClass.classBaseName = baseClass.toString();
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
                    newClass.classDescription = classDescription;
                } else {
                    logger.error("Error getting Description qualifiers of " + className);
                }
                if(false) {
                    // Debugging purpose, maybe for later use
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
                                // Debugging purpose, maybe for later use
                                String isKey = classQualifiersSet.Get("key");
                            }

                            // The property is in the qualifier "CIMTYPE" and is a string starting with "ref:"
                            String CIMTYPE = propertyQualifiersSet.Get("CIMTYPE");
                            if(CIMTYPE != null)
                            {
                                newProperty.propertyType = CIMTYPE;
                            }

                            if(false) {
                                // Debugging purpose.
                                String[] propertyQualifierNames = propertyQualifiersSet.GetNames();
                                System.out.println("propertyQualifierNames=" + String.join("+", propertyQualifierNames));
                                for (String propertyQualifierName : propertyQualifierNames) {
                                    if (propertyQualifierName.equals("CIMTYPE")) continue;
                                    String propertyQualifierValue = propertyQualifiersSet.Get(propertyQualifierName);
                                    System.out.println("propertyQualifierValue=" + propertyQualifierValue);
                                }
                            }
                            String propertyDescription = propertyQualifiersSet.Get("Description");
                            if(propertyDescription != null) {
                                newProperty.propertyDescription = propertyDescription;
                            }
                            newClass.classProperties.put(propertyName, newProperty);
                        }
                    }
                }

                resultClasses.put(className, newClass);
                classObject.Release();
            }
        } finally {
            enumerator.Release();
        }
        logger.debug("Number of cached classes:" + resultClasses.size());
        return resultClasses;
    }

    static private Map<Integer, String> mapVariantTypes = Map.ofEntries(
            entry(Variant.VT_EMPTY, "VT_EMPTY"),
            entry(Variant.VT_NULL, "VT_NULL"),
            entry(Variant.VT_I2, "VT_I2"),
            entry(Variant.VT_I4, "VT_I4"),
            entry(Variant.VT_R4, "VT_R4"),
            entry(Variant.VT_R8, "VT_R8"),
            entry(Variant.VT_CY, "VT_CY"),
            entry(Variant.VT_DATE, "VT_DATE"),
            entry(Variant.VT_BSTR, "VT_BSTR"),
            entry(Variant.VT_DISPATCH, "VT_DISPATCH"),
            entry(Variant.VT_ERROR, "VT_ERROR"),
            entry(Variant.VT_BOOL, "VT_BOOL"),
            entry(Variant.VT_VARIANT, "VT_VARIANT"),
            entry(Variant.VT_UNKNOWN, "VT_UNKNOWN"),
            entry(Variant.VT_DECIMAL, "VT_DECIMAL"),
            entry(Variant.VT_I1, "VT_I1"),
            entry(Variant.VT_UI1, "VT_UI1"),
            entry(Variant.VT_UI2, "VT_UI2"),
            entry(Variant.VT_UI4, "VT_UI4"),
            entry(Variant.VT_I8, "VT_I8"),
            entry(Variant.VT_UI8, "VT_UI8"),
            entry(Variant.VT_INT, "VT_INT"),
            entry(Variant.VT_UINT, "VT_UINT"),
            entry(Variant.VT_VOID, "VT_VOID"),
            entry(Variant.VT_HRESULT, "VT_HRESULT"),
            entry(Variant.VT_PTR, "VT_PTR"),
            entry(Variant.VT_SAFEARRAY, "VT_SAFEARRAY"),
            entry(Variant.VT_CARRAY, "VT_CARRAY"),
            entry(Variant.VT_USERDEFINED, "VT_USERDEFINED"),
            entry(Variant.VT_LPSTR, "VT_LPSTR"),
            entry(Variant.VT_LPWSTR, "VT_LPWSTR"),
            entry(Variant.VT_RECORD, "VT_RECORD"),
            entry(Variant.VT_INT_PTR, "VT_INT_PTR"),
            entry(Variant.VT_UINT_PTR, "VT_UINT_PTR"),
            entry(Variant.VT_FILETIME, "VT_FILETIME"),
            entry(Variant.VT_BLOB, "VT_BLOB"),
            entry(Variant.VT_STREAM, "VT_STREAM"),
            entry(Variant.VT_STORAGE, "VT_STORAGE"),
            entry(Variant.VT_STREAMED_OBJECT, "VT_STREAMED_OBJECT"),
            entry(Variant.VT_STORED_OBJECT, "VT_STORED_OBJECT"),
            entry(Variant.VT_BLOB_OBJECT, "VT_BLOB_OBJECT"),
            entry(Variant.VT_CF, "VT_CF"),
            entry(Variant.VT_CLSID, "VT_CLSID"),
            entry(Variant.VT_VERSIONED_STREAM, "VT_VERSIONED_STREAM"),
            // entry(Variant.VT_BSTR_BLOB, "VT_BSTR_BLOB"), // Duplicate 4095
            entry(Variant.VT_VECTOR, "VT_VECTOR"),
            entry(Variant.VT_ARRAY, "VT_ARRAY"),
            entry(Variant.VT_BYREF, "VT_BYREF"),
            entry(Variant.VT_RESERVED, "VT_RESERVED"),
            entry(Variant.VT_ILLEGAL, "VT_ILLEGAL"),
            // entry(Variant.VT_ILLEGALMASKED, "VT_ILLEGALMASKED"), // Duplicate 4095
            entry(Variant.VT_TYPEMASK, "VT_TYPEMASK")
            );

    static private Map<Integer, String> mapCimTypes = Map.ofEntries(
            entry(Wbemcli.CIM_ILLEGAL, "CIM_ILLEGAL"),
            entry(Wbemcli.CIM_EMPTY, "CIM_EMPTY"),
            entry(Wbemcli.CIM_SINT8, "CIM_SINT8"),
            entry(Wbemcli.CIM_UINT8, "CIM_UINT8"),
            entry(Wbemcli.CIM_SINT16, "CIM_SINT16"),
            entry(Wbemcli.CIM_UINT16, "CIM_UINT16"),
            entry(Wbemcli.CIM_SINT32, "CIM_SINT32"),
            entry(Wbemcli.CIM_UINT32, "CIM_UINT32"),
            entry(Wbemcli.CIM_SINT64, "CIM_SINT64"),
            entry(Wbemcli.CIM_UINT64, "CIM_UINT64"),
            entry(Wbemcli.CIM_REAL32, "CIM_REAL32"),
            entry(Wbemcli.CIM_REAL64, "CIM_REAL64"),
            entry(Wbemcli.CIM_BOOLEAN, "CIM_BOOLEAN"),
            entry(Wbemcli.CIM_STRING, "CIM_STRING"),
            entry(Wbemcli.CIM_DATETIME, "CIM_DATETIME"),
            entry(Wbemcli.CIM_REFERENCE, "CIM_REFERENCE"),
            entry(Wbemcli.CIM_CHAR16, "CIM_CHAR16"),
            entry(Wbemcli.CIM_OBJECT, "CIM_OBJECT"),
            entry(Wbemcli.CIM_FLAG_ARRAY, "CIM_FLAG_ARRAY")
    );

    /** Conversion of a WMI value to a string plus its type.
     *
     * @param lambdaColumn
     * @param pType
     * @param pVal
     * @return
     */
    static ValueTypePair convertVariantToValueTypePair(
            String lambdaColumn,
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

        Object valObject = pVal.getValue();
        if(valObject == null) {
            String valueTypeStr = mapCimTypes.get(valueType);
            //logger.debug("Value is null. lambdaColumn=" + lambdaColumn + " type=" + valueType + "/" + valueTypeStr);
        }

        String rowValue;
        ValueTypePair.ValueType rowType;
        if(lambdaColumn.equals("__PATH")) {
            // Not consistent for Win32_Product.
            if(valueType != Wbemcli.CIM_STRING) {
                // FIXME: Theoretically, it should only be CIM_REFERENCE ...
                throw new RuntimeException(
                        "Should be CIM_STRING lambdaColumn=" + lambdaColumn + " valueType=" + valueType);
            }
            rowValue = pVal.stringValue();
            rowType = ValueTypePair.ValueType.NODE_TYPE;
        }
        else {
            switch(valueType) {
                case Wbemcli.CIM_REFERENCE:
                    rowValue = pVal.stringValue();
                    rowType = ValueTypePair.ValueType.NODE_TYPE;
                    if(rowValue != null) {
                        if(!PresentUtils.hasWmiReferenceSyntax(rowValue)) {
                            logger.warn("lambdaColumn=" + lambdaColumn + " has not reference syntax:" + rowValue);
                        }
                    }
                    break;
                case Wbemcli.CIM_STRING:
                    rowValue = pVal.stringValue();
                    // FIXME: Maybe if a WMI error returning a node as a string ?
                    if(rowValue != null) {
                        if (rowValue.startsWith("\\\\") && !rowValue.startsWith("\\\\?\\")) {
                            logger.warn("lambdaColumn=" + lambdaColumn + " cannot be a string:" + rowValue);
                        }
                    }
                    rowType = ValueTypePair.ValueType.STRING_TYPE;
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
                        longValue = lambdaColumn + "_IS_NULL";
                        rowType = ValueTypePair.ValueType.STRING_TYPE;
                    } else {
                        rowType = ValueTypePair.ValueType.INT_TYPE;
                        if(valueType == valueTypeUnknown) {
                            // This should work because no contradiction.
                            longValue = Long.toString(pVal.longValue());
                        } else {
                            // Some corner cases do not work, i.e. Win32_Process.InstallDate
                            Consumer<String> warner = (String msg) -> {
                                String valueTypeUnknownStr = mapVariantTypes.get(valueTypeUnknown);
                                String valueTypeStr = mapCimTypes.get(valueType);
                                logger.warn(msg + valueType + "/" + valueTypeStr
                                        + " [" + pType + "]"
                                        + " != " + valueTypeUnknown + "/" + valueTypeUnknownStr);
                            };

                            // FIXME: Obscure behaviour in some corner cases ??
                            switch(valueTypeUnknown) {
                                case Wbemcli.CIM_REFERENCE:
                                    warner.accept("Should be reference");
                                    longValue = pVal.stringValue();
                                    break;
                                case Wbemcli.CIM_STRING:
                                    warner.accept("Should be string");
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
                                    warner.accept("Should be floating-point");
                                    longValue = pVal.toString();
                                    break;
                                case Wbemcli.CIM_DATETIME:
                                    warner.accept("Should be date/time");
                                    longValue = pVal.toString();
                                    break;
                                default:
                                    warner.accept("Should be something");
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
                    rowType = ValueTypePair.ValueType.FLOAT_TYPE;
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
                    rowType = ValueTypePair.ValueType.DATE_TYPE;
                    break;
                case Wbemcli.CIM_BOOLEAN:
                    // True is a non-zero value.
                    rowValue = pVal.booleanValue() ? "1" : "0";
                    rowType = ValueTypePair.ValueType.BOOL_TYPE;
                    break;
                default:
                    String valStringValue = pVal.stringValue();
                    if (valStringValue == null) {
                        logger.error("Null when converting lambdaColumn=" + lambdaColumn + " type=" + valueType);
                    }
                    rowValue = valStringValue;
                    rowType = ValueTypePair.ValueType.STRING_TYPE;
                    break;
            } // switch
        }
        ValueTypePair rowValueType = new ValueTypePair(rowValue, rowType);
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
