package paquetage;

import COM.Wbemcli;
import COM.WbemcliUtil;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.COMUtils;

//import com.sun.jna.platform.win32.COM.Wbemcli;
//import com.sun.jna.platform.win32.COM.WbemcliUtil;

import com.sun.jna.ptr.IntByReference;

import java.util.*;
// import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.sun.jna.platform.win32.Variant.VT_ARRAY;
import static com.sun.jna.platform.win32.Variant.VT_BSTR;

/**
 * This selects from WMI elements of a class, optionally with a WHERE clause made of key-value pairs.
 */
public class WmiSelecter {
    static public class WhereEquality {
        public String predicate;
        public String value;
        boolean isVariable;
        public WhereEquality(String predicateArg, String valueStr, boolean isVariableArg) throws Exception {
            if(predicateArg.contains("#")) {
                throw new Exception("Invalid class:" + predicateArg);
            }

            predicate = predicateArg;
            value = valueStr;
            isVariable = isVariableArg;
        }

        public WhereEquality(String predicateArg, String valueStr) throws Exception {
            this(predicateArg, valueStr, false);
        }

        public String ToEqualComparison() {
            // Real examples in Powershell - they are quite fast:
            // PS C:> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Antecedent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\DriverStore\\\\FileRepository\\\\iigd_dch.inf_amd64_ea63d1eddd5853b5\\\\igdinfo64.dll\""'
            // PS C:> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Dependent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"32308\""'

            // key = "http://www.primhillcomputers.com/ontology/survol#Win32_Process"

            if(value == null) {
                // This should not happen.
                System.out.println("Value of " + predicate + " is null");
            }
            String escapedValue = value.replace("\\", "\\\\").replace("\"", "\\\"");
            return "" + predicate + "" + " = \"" + escapedValue + "\"";
        }
    };

    /**
     * This is a row returned by a WMI select query.
     * For simplicity, each row contains all column names.
     */
    public class Row {
        Map<String, String> Elements;

        public Row() {
            Elements = new HashMap<String, String>();
        }

        public String toString() {
            return Elements.toString();
        }
    }

    Wbemcli.IWbemServices svc = null;

    public WmiSelecter() {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);
        svc = WbemcliUtil.connectServer("ROOT\\CIMV2");
    }

    protected void finalize() throws Throwable {
        svc.Release();
        Ole32.INSTANCE.CoUninitialize();
    }

    /** This is used to evaluate the cost of accessing a single object given its path.
     * The keys are the class name and the fetched columns, because this information can be used
     * to create custom functions.
     *
     */
    static public class Statistics {
        class Sample {
            public long elapsed;
            public int count;
            public Sample() {
                elapsed = System.currentTimeMillis();
                count = 0;
            }
        };
        HashMap<String, Sample> statistics;
        long startTime;

        Statistics() {
            ResetAll();
        }

        void ResetAll() {
            statistics = new HashMap<>();
        }

        void StartSample() {
            startTime = System.currentTimeMillis();
        }

        void FinishSample(String objectPath, Set<String> columns) {
            String key = objectPath + ":" + String.join(",", columns);
            Sample sample = statistics.get(key);
            if(sample != null) {
                sample.elapsed += System.currentTimeMillis() - startTime;
                sample.count++;
            } else {
                sample = new Sample();
                sample.elapsed = System.currentTimeMillis() - startTime;
                sample.count = 1;
                statistics.put(key, sample);
            }
            startTime = -1;
        }

        void DisplayAll() {
            long totalElapsed = 0;
            int totalCount = 0;

            long maxElapsed = 0;
            int maxCount = 2; // One occurrence is not interesting.

            for(HashMap.Entry<String, Sample> entry: statistics.entrySet()) {
                Sample currentValue = entry.getValue();
                totalElapsed += currentValue.elapsed;
                totalCount += currentValue.count;
                if(currentValue.elapsed >= maxElapsed)
                    maxElapsed = currentValue.elapsed;
                if(currentValue.count >= maxCount)
                    maxCount = currentValue.count;
            }
            // Only display the most expensive fetches.
            for(HashMap.Entry<String, Sample> entry: statistics.entrySet()) {
                Sample currentValue = entry.getValue();
                if((currentValue.elapsed >= maxElapsed) || (currentValue.count >= maxCount))
                    System.out.println(entry.getKey() + " " + (currentValue.elapsed / 1000.0) + " secs " + currentValue.count + " calls");
            }
            System.out.println("TOTAL" + " " + (totalElapsed / 1000.0) + " secs " + totalCount + " calls " + statistics.size() + " lines");
        }
    }

    /**
     * To be used when reconstructing a WQL query.
     */
    public static class QueryData {
        String className;
        String mainVariable;
        boolean isMainVariableAvailable;
        // It maps a column name to a variable and is used to copy the column values to the variables.
        // This sorted container guarantees the order to help comparison.
        SortedMap<String, String> queryColumns;
        List<WhereEquality> queryWheres;
        public Statistics statistics = new Statistics();

        QueryData(String wmiClassName, String variable, boolean mainVariableAvailable, Map<String, String> columns, List<WhereEquality> wheres) throws Exception {
            mainVariable = variable;
            isMainVariableAvailable = mainVariableAvailable;
            if(wmiClassName.contains("#")) {
                throw new Exception("Invalid class:" + wmiClassName);
            }
            className = wmiClassName;
            // Uniform representation of no columns selected (except the path).
            if(columns == null)
                queryColumns = new TreeMap<>();
            else
                queryColumns = new TreeMap<>(columns);
            // Uniform representation of an empty where clause.
            if(wheres == null)
                queryWheres = new ArrayList<>();
            else
                // This sorts the where tests so the order is always the same and helps comparisons.
                // This is not a problem performance-wise because there is only one such element per WQL query.
                queryWheres = wheres.stream().sorted(Comparator.comparing(x -> x.predicate)).collect(Collectors.toList());
        }

        public String BuildWqlQuery() {
            // The order of select columns is not very important because the results can be mapped to variables.
            String columns = String.join(",", queryColumns.keySet());

            // If the keys of the class are given, __RELPATH is not calculated.
            // Anyway, it seems that __PATH is calculated only when explicitely requested.
            if (queryColumns.isEmpty())
                columns += "__PATH";
            else
                columns += ", __PATH";
            String wqlQuery = "Select " + columns + " from " + className;

            if( (queryWheres != null) && (! queryWheres.isEmpty())) {
                wqlQuery += " where ";
                String whereClause = (String)queryWheres.stream()
                        .map(WhereEquality::ToEqualComparison)
                        .collect(Collectors.joining(" and "));
                wqlQuery += whereClause;
            }
            return wqlQuery;
        }
    }

    ArrayList<Row> WqlSelectWMI(QueryData queryData) throws Exception {
        ArrayList<Row> resultRows = new ArrayList<>();
        String wqlQuery = queryData.BuildWqlQuery();
        // Temporary debugging purpose.
        System.out.println("wqlQuery=" + wqlQuery);

        if(queryData.isMainVariableAvailable) {
            throw new Exception("Main variable should not be available in a WQL query.");
        }

        int countRows = 100;

        // Not always necessary to add __PATH in the selected fields. Possibly consider WBEM_FLAG_ENSURE_LOCATABLE.
        Wbemcli.IEnumWbemClassObject enumerator = svc.ExecQuery("WQL", wqlQuery,
                Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, null);
        try {
            Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
            IntByReference pType = new IntByReference();
            IntByReference plFlavor = null; new IntByReference();
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
                for(int indexRow = 0; indexRow < wqlResults.length; ++indexRow) {
                    Wbemcli.IWbemClassObject wqlResult = wqlResults[indexRow];
                    Row oneRow = new Row();
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

    public ArrayList<Row> WqlSelect(QueryData queryData) throws Exception {
        if(queryData.className == "CMI_DataFile")
        {

        }
        return WqlSelectWMI(queryData);
    }

    public ArrayList<Row> WqlSelect(String className, String variable, Map<String, String> columns, List<WhereEquality> wheres) throws Exception {
        return WqlSelect(new QueryData(className, variable, false,columns, wheres));
    }

    public ArrayList<Row> WqlSelect(String className, String variable, Map<String, String> columns) throws Exception {
        return WqlSelect(className, variable, columns, null);
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

            while(true) {
                result = enumerator.Next(0, 1);
                if(result.length == 0) {
                    break;
                }

                Variant.VARIANT.ByReference pQualifierVal = new Variant.VARIANT.ByReference();
                // String[] names = result[0].GetNames(null, Wbemcli.WBEM_CONDITION_FLAG_TYPE.WBEM_FLAG_NONSYSTEM_ONLY, pQualifierVal);
                //String[] names = result[0].GetNames(null, 0, pQualifierVal);
                String[] names = result[0].GetNames(null,0, pQualifierVal);

                COMUtils.checkRC(result[0].Get("__CLASS", 0, pVal, pType, plFlavor));
                WmiClass newClass = new WmiClass(pVal.stringValue());
                OleAuto.INSTANCE.VariantClear(pVal);

                COMUtils.checkRC(result[0].Get("__SUPERCLASS", 0, pVal, pType, plFlavor));
                Object baseClass = pVal.getValue();
                if(baseClass != null) {
                    newClass.BaseName = baseClass.toString();
                }
                OleAuto.INSTANCE.VariantClear(pVal);

                if(names != null) {
                    for (String one_name : names) {
                        /* Filter properties such as __GENUS, __CLASS, __SUPERCLASS, __DYNASTY, __RELPATH
                        __PROPERTY_COUNT, __DERIVATION, __SERVER, __NAMESPACE, __PATH */
                        if(!one_name.startsWith("__")) {
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

    public Wbemcli.IWbemClassObject GetObjectNode(String objectPath)
    {
        return svc.GetObject(objectPath, Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, null);
    }

    // TODO: This should be thread-safe.
    static HashMap<String, Wbemcli.IWbemClassObject> cacheWbemClassObject = new HashMap<>();

    public Wbemcli.IWbemClassObject GetObjectNodeCached(String objectPath) {
        Wbemcli.IWbemClassObject ret = cacheWbemClassObject.get(objectPath);
        if(ret == null) {
            ret = GetObjectNode(objectPath);
            cacheWbemClassObject.put(objectPath, ret);
        }
        return ret;
    }

    public Wbemcli.IWbemClassObject GetObjectNodePartial(String objectPath, Set<String> properties)
    {
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

    String GetObjectProperty(Wbemcli.IWbemClassObject obj, String propertyName)
    {
        Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
        COMUtils.checkRC(obj.Get(propertyName, 0, pVal, null, null));
        String value = pVal.stringValue();
        OleAuto.INSTANCE.VariantClear(pVal);
        return value;
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
