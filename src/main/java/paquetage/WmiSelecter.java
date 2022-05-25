package paquetage;

import COM.Wbemcli;
import COM.WbemcliUtil;
import com.sun.jna.platform.win32.COM.COMUtils;

//import com.sun.jna.platform.win32.COM.Wbemcli;
//import com.sun.jna.platform.win32.COM.WbemcliUtil;

import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.OleAuto;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.ptr.IntByReference;

import java.util.*;
// import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This selects from WMI elements of a class, optionally with a WHERE clause made of key-value pairs.
 */
public class WmiSelecter {
    //static String TruncateNode(String nodeValue) {
        // nodeValue = "http://www.primhillcomputers.com/ontology/survol#Win32_Process"
        // Prefix is WmiOntology.survol_url_prefix
   //     return nodeValue.split("#")[1];
    //}
    static public class KeyValue {
        public String key;
        public String value;
        public KeyValue(String keyStr, String valueStr) throws Exception {
            if(keyStr.contains("#")) {
                throw new Exception("Invalid class:" + keyStr);
            }

            key = keyStr;
            value = valueStr;
        }
        public String ToEqualComparison() {
            // Real examples in Powershell - they are quite fast:
            // PS C:> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Antecedent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\DriverStore\\\\FileRepository\\\\iigd_dch.inf_amd64_ea63d1eddd5853b5\\\\igdinfo64.dll\""'
            // PS C:> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Dependent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"32308\""'

            // key = "http://www.primhillcomputers.com/ontology/survol#Win32_Process"
            //String truncatedKey = TruncateNode(key);

            String escapedValue = value.replace("\\", "\\\\").replace("\"", "\\\"");
            return "" + key + "" + " = \"" + escapedValue + "\"";
        }
    };

    /**
     * This is a row returned by a WMI select query.
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

    /**
     * To be used when reconstructing a WQL query.
     */
    public static class QueryData {
        String className;
        String mainVariable;
        // Map<String, String> queryColumns;
        // It maps a column name to a variable and is used to copy the column values to the variables.
        // This sorted container guarantees the order to help comparison.
        SortedMap<String, String> queryColumns;
        List<WmiSelecter.KeyValue> queryWheres;

        QueryData(String wmiClassName, String variable, Map<String, String> columns, List<WmiSelecter.KeyValue> wheres) throws Exception {
            mainVariable = variable;
            if(wmiClassName.contains("#")) {
                throw new Exception("Invalid class:" + wmiClassName);
            }
            className = wmiClassName;
            // Uniform representation of no columns selected (except the path).
            if(columns == null)
                queryColumns = new TreeMap<String, String>();
            else
                queryColumns = new TreeMap<String, String>(columns);
            // Uniform representation of an empty where clause.
            if(wheres == null)
                queryWheres = new ArrayList<WmiSelecter.KeyValue>();
            else
                // This sorts the where tests so the order is always the same and helps comparisons.
                // This is not a problem performance-wise because there is only one such element per WQL query.
                queryWheres = wheres.stream().sorted(Comparator.comparing(x -> x.key)).collect(Collectors.toList());
        }

        public String BuildWqlQuery() {
            // The order of select columns is not very important because the results can be mapped to variables.
            String columns = String.join(",", queryColumns.keySet());

            // If the keys of the class are given, __RELPATH is not calculated.
            // Anyway, it seems that __PATH is calculated only when explicitely requested.
            if (queryColumns.size() == 0)
                columns += "__PATH";
            else
                columns += ", __PATH";
            String wqlQuery = "Select " + columns + " from " + className;

            if( (queryWheres != null) && (! queryWheres.isEmpty())) {
                wqlQuery += " where ";
                String whereClause = (String)queryWheres.stream()
                        .map(KeyValue::ToEqualComparison)
                        .collect(Collectors.joining(" and "));
                wqlQuery += whereClause;
            }
            System.out.println("wqlQuery " + wqlQuery);
            return wqlQuery;
        }
    }

    public ArrayList<Row> WqlSelect(QueryData queryData) {
        // TODO: Maybe this could be done once only in this object.
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);

        // Connect to the server
        Wbemcli.IWbemServices svc = WbemcliUtil.connectServer("ROOT\\CIMV2");

        ArrayList<Row> resultRows = new ArrayList<Row>();
        String wqlQuery = queryData.BuildWqlQuery();

        try {
            Wbemcli.IEnumWbemClassObject enumerator = svc.ExecQuery("WQL", wqlQuery,
                    Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_RETURN_WBEM_COMPLETE, null);
            try {
                Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
                IntByReference pType = new IntByReference();
                IntByReference plFlavor = new IntByReference();
                while(true) {
                    /**
                     * When selecting a single column "MyColumn", the returned effective columns are:
                     *     __GENUS, __CLASS, __SUPERCLASS, __DYNASTY, __RELPATH, __PROPERTY_COUNT,
                     *     __DERIVATION, __SERVER, __NAMESPACE, __PATH, MyColumn
                     */
                    Wbemcli.IWbemClassObject[] wqlResult = enumerator.Next(0, 1);
                    if(wqlResult.length == 0) {
                        break;
                    }
                    Row oneRow = new Row();
                    // The path is always returned if the key is selected.
                    // This path should never be recalculated to ensure consistency with WMI.
                    // All values are NULL except, typically:
                    //     __CLASS=Win32_Process
                    //     __RELPATH=Win32_Process.Handle="4"
                    if(false) {
                        String[] names = wqlResult[0].GetNames(null, 0, null);
                        System.out.println("names=" + String.join("+", names));

                        for (String col : names) {
                            COMUtils.checkRC(wqlResult[0].Get(col, 0, pVal, pType, plFlavor));
                            if (pVal.getValue() == null)
                                System.out.println(col + "=" + "NULL");
                            else
                                System.out.println(col + "=" + pVal.getValue().toString());
                            OleAuto.INSTANCE.VariantClear(pVal);
                        }
                    }

                    BiConsumer<String, String> storeValue = (String lambda_column, String lambda_variable) ->  {
                        COMUtils.checkRC(wqlResult[0].Get(lambda_column, 0, pVal, pType, plFlavor));
                        Object currentValue = pVal.getValue();
                        if(currentValue == null)
                        {
                            System.out.println("Value for "+lambda_column+" and "+lambda_variable+" is NULL");
                            oneRow.Elements.put(lambda_variable, "NULL:"+lambda_variable+":"+lambda_variable);
                        }
                        else
                        {
                            //System.out.println("Value for "+lambda_column+" and "+lambda_variable+" is "+currentValue.toString());
                            oneRow.Elements.put(lambda_variable, currentValue.toString());
                        }
                        OleAuto.INSTANCE.VariantClear(pVal);
                    };

                    var wrapper = new Object(){ int ordinal = 0; };

                    queryData.queryColumns.forEach(storeValue);
                    storeValue.accept("__PATH", queryData.mainVariable);
                    wqlResult[0].Release();
                    //System.out.println("Added row=" + oneRow);
                    resultRows.add(oneRow);
                }
            } finally {
                enumerator.Release();
            }
        } finally {
            svc.Release();
        }

        Ole32.INSTANCE.CoUninitialize();
        //System.out.println("Added rows=" + resultRows);
        //System.out.println("number of rows=" + resultRows.size());
        return resultRows;
    }

    public ArrayList<Row> WqlSelect(String className, String variable, Map<String, String> columns, List<KeyValue> wheres) throws Exception {
        return WqlSelect(new QueryData(className, variable, columns, wheres));
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
        // TODO: Maybe this could be done once only in this object.
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);

        int wbemFlagUseAmendedQualifiers = 0x20000;

        // Connect to the server
        Wbemcli.IWbemServices svc = WbemcliUtil.connectServer("ROOT\\CIMV2");

        Map<String, WmiClass> resultClasses = new HashMap<String, WmiClass>();
        int count = 0;
        try {
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
                    WmiClass newClass = new WmiClass(pVal.getValue().toString());
                    OleAuto.INSTANCE.VariantClear(pVal);

                    COMUtils.checkRC(result[0].Get("__SUPERCLASS", 0, pVal, pType, plFlavor));
                    Object baseClass = pVal.getValue();
                    if(baseClass != null) {
                        newClass.BaseName = baseClass.toString();
                    }
                    OleAuto.INSTANCE.VariantClear(pVal);

                    if(names != null) {
                        /*
                            one_name:__GENUS
                            one_name:__CLASS
                            one_name:__SUPERCLASS
                            one_name:__DYNASTY
                            one_name:__RELPATH
                            one_name:__PROPERTY_COUNT
                            one_name:__DERIVATION
                            one_name:__SERVER
                            one_name:__NAMESPACE
                            one_name:__PATH
                         */
                        for (String one_name : names) {
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
        } finally {
            svc.Release();
        }
        Ole32.INSTANCE.CoUninitialize();
        return resultClasses;
    }

    public Wbemcli.IWbemClassObject GetObjectNode(String objectPath)
    {
        // TODO: Maybe this could be done once only in this object.
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);

        Wbemcli.IWbemServices svc_wgo = WbemcliUtil.connectServer("ROOT\\CIMV2");
        return svc_wgo.GetObject(objectPath);
    }

    String GetObjectProperty(Wbemcli.IWbemClassObject obj, String propertyName)
    {
        Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
        IntByReference pType = new IntByReference();
        IntByReference plFlavor = new IntByReference();
        COMUtils.checkRC(obj.Get(propertyName, 0, pVal, pType, plFlavor));
        Object objectValue = pVal.getValue();
        String value = objectValue != null ? objectValue.toString() : null;
        OleAuto.INSTANCE.VariantClear(pVal);
        return value;
    }


    /*
    // http://win32easy.blogspot.com/2011/03/wmi-in-c-query-everyting-from-your-os.html

    public ArrayList<String> Classes() {
    }

    // IWBEMClassObject::BeginEnumeration
    // https://stackoverflow.com/questions/18992717/list-all-properties-of-wmi-class-in-c
    public record Property(String Name, String Description) {}

    public ArrayList<Property> Properties(String className) {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);

        // Connect to the server
        Wbemcli.IWbemServices svc = WbemcliUtil.connectServer("ROOT\\CIMV2");

        // IWbemClassObject* pClass = NULL;
        Wbemcli.IWbemClassObject pClass;

        //Ole32.INSTANCE.
        svc.
                ;

        pClass.;
        Wbemcli.

        hres = svc. pSvc->GetObject(className, 0, NULL, &pClass, NULL);

        Variant.VARIANT.ByReference pQualifierVal = new Variant.VARIANT.ByReference();
        String[] members = pClass.GetNames(null, 0, pQualifierVal);
    }
*/


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
