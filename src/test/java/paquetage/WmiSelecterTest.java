package paquetage;

import java.util.*;
import java.util.stream.Collectors;
import java.lang.Object;

import org.junit.Assert;
import org.junit.Test;

public class WmiSelecterTest {
    @Test
    public void TestCIM_Process() {
        WmiSelecter selecter = new WmiSelecter();
        ArrayList<WmiSelecter.Row> listResults = selecter.Select("CIM_Process", Arrays.asList("Handle"));
        String stringsResults = (String)listResults.stream().map(Object::toString)
                .collect(Collectors.joining(", "));
        System.out.println(stringsResults);

        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);
        boolean isIn = false;
        for(WmiSelecter.Row aRow : listResults)
        {
            if(aRow.Elements.get(0).equals(pidString))
            {
                isIn = true;
                break;
            }
        }
        System.out.println("pidString=" + pidString);
        System.out.println("IsIn=" + String.valueOf(isIn));
        Assert.assertTrue(isIn);
    }

    @Test
    public void TestCIM_ProcessCurrent() {
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);

        WmiSelecter selecter = new WmiSelecter();
        ArrayList<WmiSelecter.Row> listResults = selecter.Select(
                "CIM_Process",
                Arrays.asList("Handle"),
                Arrays.asList(new WmiSelecter.KeyValue("Handle", pidString)));
        String stringsResults = (String)listResults.stream().map(Object::toString)
                .collect(Collectors.joining(", "));
        System.out.println(stringsResults);

        Assert.assertEquals(listResults.size(), 1);
        Assert.assertEquals(listResults.get(0).Elements.get(0), pidString);
    }

    @Test
    public void TestCIM_ProcessExecutable() {
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);

        // Antecedent = \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\System32\\clbcatq.dll"
        // Precedent = \\LAPTOP-R89KG6V1\root\cimv2:Win32_Process.Handle="2588"
        WmiSelecter selecter = new WmiSelecter();
        ArrayList<WmiSelecter.Row> listResults = selecter.Select(
                "CIM_ProcessExecutable",
                Arrays.asList("Dependent"));
        String stringsResults = (String)listResults.stream().map(Object::toString)
                .collect(Collectors.joining(", "));
        System.out.println(stringsResults);
        System.out.println(listResults.size());
        // Many elements.
        Assert.assertTrue(listResults.size() > 10);
    }

    @Test
    public void TestCIM_ProcessExecutableDependent() {
        long pid = ProcessHandle.current().pid();
        //String pidString = String.valueOf(pid);
        //String dependentString = "\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"2588\""
        //pid = 7084;
        String dependentString = String.format("\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"%d\"", pid);

        // Antecedent = \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\System32\\clbcatq.dll"
        // Dependent = \\LAPTOP-R89KG6V1\root\cimv2:Win32_Process.Handle="2588"
        WmiSelecter selecter = new WmiSelecter();
        ArrayList<WmiSelecter.Row> listResults = selecter.Select(
                "CIM_ProcessExecutable",
                Arrays.asList("Antecedent"),
                Arrays.asList(new WmiSelecter.KeyValue("Dependent", dependentString)));
        String stringsResults = (String)listResults.stream().map(Object::toString)
                .collect(Collectors.joining(", "));
        System.out.println(stringsResults);
        System.out.println(listResults.size());
        // Many elements.
        Assert.assertTrue(listResults.size() > 5);
    }

    //  \\LAPTOP-R89KG6V1\root\cimv2:CIM_DataFile.Name="C:\\WINDOWS\\SYSTEM32\\ntdll.dll"
    @Test
    public void TestCIM_ProcessExecutableAntecedent() {
        String antecedentString = "\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll\"";

        WmiSelecter selecter = new WmiSelecter();
        ArrayList<WmiSelecter.Row> listResults = selecter.Select(
                "CIM_ProcessExecutable",
                Arrays.asList("Dependent"),
                Arrays.asList(new WmiSelecter.KeyValue("Antecedent", antecedentString)));
        String stringsResults = (String)listResults.stream().map(Object::toString)
                .collect(Collectors.joining(", "));
        System.out.println(stringsResults);
        System.out.println(listResults.size());
        // Many elements.
        Assert.assertTrue(listResults.size() > 5);
        for(WmiSelecter.Row row : listResults)
            Assert.assertEquals(row.Elements.size(), 1);
    }

    @Test
    public void TestClasses() {
        WmiSelecter selecter = new WmiSelecter();
        Map<String, WmiSelecter.WmiClass> classes = selecter.Classes();
        Assert.assertTrue(classes.containsKey("Win32_Process"));
        System.out.println("BaseName=" + classes.get("Win32_Process").BaseName);
        Map<String, WmiSelecter.WmiProperty> properties = classes.get("Win32_Process").Properties;
        System.out.println("Properties=" + properties);
        Assert.assertEquals(classes.get("Win32_Process").BaseName, "CIM_Process");
        Assert.assertTrue(properties.containsKey("Handle"));
    }
}
