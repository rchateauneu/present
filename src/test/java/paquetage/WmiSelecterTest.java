package paquetage;

import java.util.*;
import java.util.stream.Collectors;
import java.lang.Object;

import COM.Wbemcli;
import org.junit.Assert;
import org.junit.Test;

public class WmiSelecterTest {
    static String currentPidStr = String.valueOf(ProcessHandle.current().pid());
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
        GenericSelecter selecter = new GenericSelecter();
        ArrayList<GenericSelecter.Row> listResults = selecter.SelectVariablesFromWhere(
                "CIM_Process",
                "any_variable",
                Map.of("Handle", "var_handle"));

        boolean isIn = false;
        for(GenericSelecter.Row aRow : listResults)
        {
            if(aRow.GetStringValue("var_handle").equals(currentPidStr))
            {
                isIn = true;
                break;
            }
        }
        Assert.assertTrue(isIn);
    }

    @Test
    public void TestCIM_ProcessCurrent() throws Exception {
        GenericSelecter selecter = new GenericSelecter();
        ArrayList<GenericSelecter.Row> listResults = selecter.SelectVariablesFromWhere(
                "CIM_Process",
                "any_variable",
                Map.of("Handle", "var_handle"),
                Arrays.asList(new QueryData.WhereEquality("Handle", currentPidStr)));
        String stringsResults = (String)listResults.stream().map(Object::toString)
                .collect(Collectors.joining(", "));
        System.out.println(stringsResults);

        Assert.assertEquals(listResults.size(), 1);
        Assert.assertEquals(listResults.get(0).GetStringValue("var_handle"), currentPidStr);
    }

    /** This selects, from the associator CIM_ProcessExecutable, all columns "Dependent" which are stored in
     * the variable "var_dependent". It also stores the node of each association in the variable "any_variable".
     * @throws Exception
     */
    @Test
    public void TestCIM_ProcessExecutable() throws Exception{

        // Antecedent = \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\System32\\clbcatq.dll"
        // Precedent = \\LAPTOP-R89KG6V1\root\cimv2:Win32_Process.Handle="2588"
        GenericSelecter selecter = new GenericSelecter();
        ArrayList<GenericSelecter.Row> listResults = selecter.SelectVariablesFromWhere(
                "CIM_ProcessExecutable",
                "any_variable",
                Map.of("Dependent", "var_dependent"));
        Assert.assertTrue(listResults.size() > 10);
        for(GenericSelecter.Row row : listResults) {
            Assert.assertEquals(2, row.ElementsSize());
            // For example: \\LAPTOP-R89KG6V1\ROOT\CIMV2:CIM_ProcessExecutable.Antecedent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\fwpuclnt.dll\"",Dependent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"3156\""
            Assert.assertTrue(row.TryValueType("any_variable") != null);
            // For example: \\LAPTOP-R89KG6V1\root\cimv2:Win32_Process.Handle="3156"
            Assert.assertTrue(row.TryValueType("var_dependent") != null);
        }
    }

    /** This selects, for the associator CIM_ProcessExecutable, all columns "Antecedent" whose "Dependent"
     * points to the current process. In other words, it selects the executable and all libraries used
     * by the current process.
     * @throws Exception
     */
    @Test
    public void TestCIM_ProcessExecutableDependent() throws Exception {
        long pid = ProcessHandle.current().pid();
        String dependentString = String.format("\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"%d\"", pid);

        // Antecedent = \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\System32\\clbcatq.dll"
        // Dependent = \\LAPTOP-R89KG6V1\root\cimv2:Win32_Process.Handle="2588"
        GenericSelecter selecter = new GenericSelecter();
        ArrayList<GenericSelecter.Row> listResults = selecter.SelectVariablesFromWhere(
                "CIM_ProcessExecutable",
                "any_variable",
                Map.of("Antecedent", "var_antecedent"),
                Arrays.asList(new QueryData.WhereEquality("Dependent", dependentString)));
        // Many libraries.
        Assert.assertTrue(listResults.size() > 5);
        for(GenericSelecter.Row row : listResults) {
            Assert.assertEquals(2, row.ElementsSize());
            Assert.assertTrue(row.TryValueType("any_variable") != null);
            Assert.assertTrue(row.TryValueType("var_antecedent") != null);
        }
    }

    /** This selects, for the associator CIM_ProcessExecutable, all columns "Dependent" whose "Antecedent"
     * points to the file \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\SYSTEM32\\ntdll.dll" .
     * @throws Exception
     */
    //  \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\SYSTEM32\\ntdll.dll"
    @Test
    public void TestCIM_ProcessExecutableAntecedent() throws Exception {
        String antecedentString = "\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll\"";

        GenericSelecter selecter = new GenericSelecter();
        ArrayList<GenericSelecter.Row> listResults = selecter.SelectVariablesFromWhere(
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
        for(GenericSelecter.Row row : listResults) {
            Assert.assertEquals(row.ElementsSize(), 2);
            Assert.assertTrue(row.ContainsKey("any_variable"));
            Assert.assertTrue(row.ContainsKey("var_dependent"));
        }
    }

    /** This loads all WMI classes and checks the presence of some classes and their properties, arbitrarily chosen.
     *
     */
    @Test
    public void TestClassesList_Win32_Process() {
        WmiSelecter selecter = new WmiSelecter();
        Map<String, WmiSelecter.WmiClass> classes = selecter.Classes();
        Assert.assertTrue(classes.containsKey("Win32_Process"));
        System.out.println("BaseName=" + classes.get("Win32_Process").BaseName);
        Map<String, WmiSelecter.WmiProperty> properties = classes.get("Win32_Process").Properties;
        System.out.println("Properties=" + properties.keySet());
        Assert.assertTrue(properties.containsKey("Handle"));
        Assert.assertEquals(classes.get("Win32_Process").BaseName, "CIM_Process");
    }

    /** This loads all WMI classes and checks the presence of some classes and their properties, arbitrarily chosen.
     *
     */
    @Test
    public void TestClassesList_CIM_DataFile() {
        WmiSelecter selecter = new WmiSelecter();
        Map<String, WmiSelecter.WmiClass> classes = selecter.Classes();
        Assert.assertTrue(classes.containsKey("CIM_DataFile"));
        Map<String, WmiSelecter.WmiProperty> properties = classes.get("CIM_DataFile").Properties;
        System.out.println("Properties=" + properties.keySet());
        Assert.assertTrue(properties.containsKey("Name"));
    }

    /** This creates an CIM_DataFile object based on its path only,
     * and checks that some of its properties are defined.
     */
    @Test
    public void TestGetObject_CIM_DataFile() throws Exception {
        // For example: "\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\clb.dll\"";
        String objectPath = PresentUtils.PrefixPath("CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\clb.dll\"");
        WmiSelecter selecter = new WmiSelecter();
        Wbemcli.IWbemClassObject obj = selecter.GetObjectNode(objectPath);
        List<String> namesList = Arrays.stream(obj.GetNames(null, 0, null)).toList();
        Assert.assertTrue(namesList.contains("Name"));
        Assert.assertTrue(namesList.contains("FileName"));
        Assert.assertEquals(selecter.GetObjectProperty(obj, "Caption").Value(), "C:\\WINDOWS\\System32\\clb.dll");
        Assert.assertEquals(selecter.GetObjectProperty(obj, "CreationClassName").Value(), "CIM_LogicalFile");
    }

    /** This creates an Win32_Process object based on its path only,
     * and checks that some of its properties are defined.
     */    @Test
    public void TestGetObject_Win32_Process() throws Exception {
        long pid = ProcessHandle.current().pid();
        // For example: "\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"" + pid + "\"";
        String objectPath = PresentUtils.PrefixPath("Win32_Process.Handle=\"" + pid + "\"");
        WmiSelecter selecter = new WmiSelecter();
        Wbemcli.IWbemClassObject obj = selecter.GetObjectNode(objectPath);
        List<String> namesList = Arrays.stream(obj.GetNames(null, 0, null)).toList();
        Assert.assertTrue(namesList.contains("Handle"));
        Assert.assertTrue(namesList.contains("Caption"));
        Assert.assertTrue(namesList.contains("Description"));
        Assert.assertEquals(selecter.GetObjectProperty(obj, "Handle").Value(), Long.toString(pid));
        Assert.assertEquals(selecter.GetObjectProperty(obj, "Caption").Value(), "java.exe");
    }
    // Antecedent = \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\System32\\clbcatq.dll"
    // Dependent = \\LAPTOP-R89KG6V1\root\cimv2:Win32_Process.Handle="2588"

}

