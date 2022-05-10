package paquetage;

import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Wbemcli;
import com.sun.jna.platform.win32.COM.WbemcliUtil;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.OleAuto;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.ptr.IntByReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

/**
 * This selects from WMI elements of a class, optionally with a WHERE clause made of key-value pairs.
 */
public class WmiSelecter {
    record KeyValue(String key, String value) {
        public String ToTest() {
            // Real examples in Powershell - they are quite fast:
            // PS C:\Users\rchat> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Antecedent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\DriverStore\\\\FileRepository\\\\iigd_dch.inf_amd64_ea63d1eddd5853b5\\\\igdinfo64.dll\""'
            // PS C:\Users\rchat> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Dependent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"32308\""'
            String escapedValue = value.replace("\\", "\\\\").replace("\"", "\\\"");
            return "" + key + "" + " = \"" + escapedValue + "\"";
        }
    };

    public class Row {
        ArrayList<String> Elements;

        public Row() {
            Elements = new ArrayList<String>();
        }

        public String toString() {
            return String.join(",", Elements);
        }
    }

    public ArrayList<Row> Select(String className, List<String> columns, List<KeyValue> wheres) {
        System.out.println("Select " + className);

        // TODO: Maybe this could be done once only in this object.
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);

        // Connect to the server
        Wbemcli.IWbemServices svc = WbemcliUtil.connectServer("ROOT\\CIMV2");

        ArrayList<Row> resultRows = new ArrayList<Row>();
        String wqlQuery = "Select " + String.join(",", columns) + " from " + className;

        if( (wheres != null) && (! wheres.isEmpty())) {
            wqlQuery += " where ";
            String whereClause = (String)wheres.stream().map(KeyValue::ToTest)
                    .collect(Collectors.joining(" and "));
            wqlQuery += whereClause;
        }
        System.out.println("wqlQuery " + wqlQuery);

        try {
            Wbemcli.IEnumWbemClassObject enumerator = svc.ExecQuery("WQL", wqlQuery,
                    Wbemcli.WBEM_FLAG_FORWARD_ONLY, null);
            try {
                Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
                IntByReference pType = new IntByReference();
                IntByReference plFlavor = new IntByReference();
                while(true) {
                    Wbemcli.IWbemClassObject[] wqlResult = enumerator.Next(0, 1);
                    if(wqlResult.length == 0) {
                        break;
                    }
                    Row oneRow = new Row();
                    for(String oneColumn : columns) {
                        COMUtils.checkRC(wqlResult[0].Get(oneColumn, 0, pVal, pType, plFlavor));
                        //System.out.println("---------" + pVal.getValue() + "-------------");
                        oneRow.Elements.add(pVal.getValue().toString());
                        OleAuto.INSTANCE.VariantClear(pVal);
                    }
                    wqlResult[0].Release();
                    resultRows.add(oneRow);
                }
            } finally {
                enumerator.Release();
            }
        } finally {
            svc.Release();
        }

        Ole32.INSTANCE.CoUninitialize();
        return resultRows;
    }

    public ArrayList<Row> Select(String className, List<String> columns) {
        return Select(className, columns, null);
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
                System.out.println("In loop");
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

                    // On veut l'object decrivant la classe afin de faire Get("Description", wbemFlagUseAmendedQualifiers)

                    /*
                    IWbemServices *pSvc
                    IWbemClassObject* pClass = NULL;
                    hres = pSvc->GetObject(L"Win32_Process", 0, NULL, &pClass, NULL);
                    QueryInterface ?
                     */
                    // Wbemcli.IWbemClassObject




                    /*
                    COMUtils.checkRC(result[0].Get("Description",wbemFlagUseAmendedQualifiers,pVal, pType, plFlavor));
                    Object descr = pVal.getValue();
                    if(descr != null) {
                        System.out.printf("Description=", descr);
                    }
                    OleAuto.INSTANCE.VariantClear(pVal);
                    */

                    /*
                    Const wbemFlagUseAmendedQualifiers = &H20000

                    Set oWMI = GetObject("winmgmts:\\.\root\cimv2")
                    Set oClass = oWMI.Get("Win32_OperatingSystem", wbemFlagUseAmendedQualifiers)

                    WScript.Echo oClass.Properties_("BootDevice").Qualifiers_("Description").Value
                     */

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
                // Cleanup
                enumerator.Release();
            }
        } finally {
            // Cleanup
            svc.Release();
        }
        Ole32.INSTANCE.CoUninitialize();
        return resultClasses;

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
