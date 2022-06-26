package paquetage;

import java.util.*;
import java.util.stream.Collectors;
import java.lang.Object;

import COM.Wbemcli;
import org.junit.Assert;
import org.junit.Test;

public class WmiSelecterTest {
    @Test
    public void TestBuildQuery() throws Exception {
    QueryData queryData = new QueryData(
                "CIM_Process",
                "any_variable",
                false,
                Map.of("Handle", "var_handle"),
                Arrays.asList(new QueryData.WhereEquality("Handle", "123", false)));
        String wqlQuery = queryData.BuildWqlQuery();
        Assert.assertEquals("Select Handle, __PATH from CIM_Process where Handle = \"123\"", wqlQuery);
    }

    @Test
    public void TestCIM_Process() throws Exception {
        MetaSelecter selecter = new MetaSelecter();
        ArrayList<MetaSelecter.Row> listResults = selecter.SelectVariablesFromWhere(
                "CIM_Process",
                "any_variable",
                Map.of("Handle", "var_handle"));

        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);
        boolean isIn = false;
        for(MetaSelecter.Row aRow : listResults)
        {
            if(aRow.Elements.get("var_handle").equals(pidString))
            {
                isIn = true;
                break;
            }
        }
        Assert.assertTrue(isIn);
    }

    @Test
    public void TestCIM_ProcessCurrent() throws Exception {
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);

        MetaSelecter selecter = new MetaSelecter();
        ArrayList<MetaSelecter.Row> listResults = selecter.SelectVariablesFromWhere(
                "CIM_Process",
                "any_variable",
                Map.of("Handle", "var_handle"),
                Arrays.asList(new QueryData.WhereEquality("Handle", pidString)));
        String stringsResults = (String)listResults.stream().map(Object::toString)
                .collect(Collectors.joining(", "));
        System.out.println(stringsResults);

        Assert.assertEquals(listResults.size(), 1);
        Assert.assertEquals(listResults.get(0).Elements.get("var_handle"), pidString);
    }

    @Test
    public void TestCIM_ProcessExecutable() throws Exception{
        long pid = ProcessHandle.current().pid();

        // Antecedent = \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\System32\\clbcatq.dll"
        // Precedent = \\LAPTOP-R89KG6V1\root\cimv2:Win32_Process.Handle="2588"
        MetaSelecter selecter = new MetaSelecter();
        ArrayList<MetaSelecter.Row> listResults = selecter.SelectVariablesFromWhere(
                "CIM_ProcessExecutable",
                "any_variable",
                Map.of("Dependent", "var_dependent"));
        String stringsResults = (String)listResults.stream().map(Object::toString)
                .collect(Collectors.joining(", "));
        System.out.println(stringsResults);
        System.out.println(listResults.size());
        // Many elements.
        Assert.assertTrue(listResults.size() > 10);
    }

    @Test
    public void TestCIM_ProcessExecutableDependent() throws Exception {
        long pid = ProcessHandle.current().pid();
        String dependentString = String.format("\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"%d\"", pid);

        // Antecedent = \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\System32\\clbcatq.dll"
        // Dependent = \\LAPTOP-R89KG6V1\root\cimv2:Win32_Process.Handle="2588"
        MetaSelecter selecter = new MetaSelecter();
        ArrayList<MetaSelecter.Row> listResults = selecter.SelectVariablesFromWhere(
                "CIM_ProcessExecutable",
                "any_variable",
                Map.of("Antecedent", "var_antecedent"),
                Arrays.asList(new QueryData.WhereEquality("Dependent", dependentString)));
        String stringsResults = (String)listResults.stream().map(Object::toString)
                .collect(Collectors.joining(", "));
        System.out.println(stringsResults);
        System.out.println(listResults.size());
        // Many elements.
        Assert.assertTrue(listResults.size() > 5);
    }

    //  \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\SYSTEM32\\ntdll.dll"
    @Test
    public void TestCIM_ProcessExecutableAntecedent() throws Exception {
        String antecedentString = "\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll\"";

        MetaSelecter selecter = new MetaSelecter();
        ArrayList<MetaSelecter.Row> listResults = selecter.SelectVariablesFromWhere(
                "CIM_ProcessExecutable",
                "any_variable",
                Map.of("Dependent", "var_dependent"),
                Arrays.asList(new QueryData.WhereEquality("Antecedent", antecedentString)));
        String stringsResults = (String)listResults.stream().map(Object::toString)
                .collect(Collectors.joining(", "));
        System.out.println(stringsResults);
        System.out.println(listResults.size());
        // Many elements.
        Assert.assertTrue(listResults.size() > 5);
        for(MetaSelecter.Row row : listResults) {
            Assert.assertEquals(row.Elements.size(), 2);
            Assert.assertTrue(row.Elements.containsKey("any_variable"));
            Assert.assertTrue(row.Elements.containsKey("var_dependent"));
        }
    }

    /** This loads all WMI classes and checks the presence of some classes and their properties, arbitrarily chosen.
     *
     */
    @Test
    public void TestClassesList() {
        WmiSelecter selecter = new WmiSelecter();
        Map<String, WmiSelecter.WmiClass> classes = selecter.Classes();
        Assert.assertTrue(classes.containsKey("Win32_Process"));
        System.out.println("BaseName=" + classes.get("Win32_Process").BaseName);
        Map<String, WmiSelecter.WmiProperty> properties = classes.get("Win32_Process").Properties;
        System.out.println("Properties=" + properties);
        Assert.assertEquals(classes.get("Win32_Process").BaseName, "CIM_Process");
        Assert.assertTrue(properties.containsKey("Handle"));
    }

    /** This creates an CIM_DataFile object based on its path only,
     * and checks that some of its properties are defined.
     */
    @Test
    public void TestGetObject_CIM_DataFile() {
        String objectPath = "\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\clbcatq.dll\"";
        WmiSelecter selecter = new WmiSelecter();
        Wbemcli.IWbemClassObject obj = selecter.GetObjectNode(objectPath);
        List<String> namesList = Arrays.stream(obj.GetNames(null, 0, null)).toList();
        Assert.assertTrue(namesList.contains("Name"));
        Assert.assertTrue(namesList.contains("FileName"));
        Assert.assertEquals(selecter.GetObjectProperty(obj, "Caption"), "C:\\WINDOWS\\System32\\clbcatq.dll");
        Assert.assertEquals(selecter.GetObjectProperty(obj, "CreationClassName"), "CIM_LogicalFile");
    }

    /** This creates an Win32_Process object based on its path only,
     * and checks that some of its properties are defined.
     */    @Test
    public void TestGetObject_Win32_Process() {
        long pid = ProcessHandle.current().pid();
        String objectPath = "\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"" + pid + "\"";
        WmiSelecter selecter = new WmiSelecter();
        Wbemcli.IWbemClassObject obj = selecter.GetObjectNode(objectPath);
        List<String> namesList = Arrays.stream(obj.GetNames(null, 0, null)).toList();
        Assert.assertTrue(namesList.contains("Handle"));
        Assert.assertTrue(namesList.contains("Caption"));
        Assert.assertTrue(namesList.contains("Description"));
        Assert.assertEquals(selecter.GetObjectProperty(obj, "Handle"), Long.toString(pid));
        Assert.assertEquals(selecter.GetObjectProperty(obj, "Caption"), "java.exe");
    }
    // Antecedent = \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\System32\\clbcatq.dll"
    // Dependent = \\LAPTOP-R89KG6V1\root\cimv2:Win32_Process.Handle="2588"

}

