package paquetage;

import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class ObjectPathTest {
    @Test
    public void ParseWbemPath_1() throws Exception {
        Map<String, String> pathMap = ObjectPath.ParseWbemPath(
                "\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"2088\"");

        Assert.assertEquals(1, pathMap.size());
        Assert.assertTrue(pathMap.containsKey("Handle"));
        Assert.assertEquals("2088", pathMap.get("Handle"));
    }

    @Test
    public void ParseWbemPath_2() throws Exception {
        Map<String, String> pathMap = ObjectPath.ParseWbemPath(
                "\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\SYSTEM32\\\\HologramWorld.dll\"");

        Assert.assertEquals(1, pathMap.size());
        Assert.assertTrue(pathMap.containsKey("Name"));
        Assert.assertEquals("C:\\WINDOWS\\SYSTEM32\\HologramWorld.dll", pathMap.get("Name"));
    }

    @Test
    public void ParseWbemPath_3_1() throws Exception {
        Map<String, String> pathMap = ObjectPath.ParseWbemPath(
                "\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:X.Y=\"\\\\\\\\Z\\\\ns:W.N=\\\"C:\\\\\\\\a.dll\\\"\"");

        Assert.assertEquals(1, pathMap.size());
        Assert.assertTrue(pathMap.containsKey("Y"));
        Assert.assertEquals(
                "\\\\Z\\ns:W.N=\"C:\\\\a.dll\"",
                pathMap.get("Y"));
    }

    @Test
    public void ParseWbemPath_3_2() throws Exception {
        Map<String, String> pathMap = ObjectPath.ParseWbemPath(
                "\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:CIM_ProcessExecutable.Antecedent=\"\\\\\\\\LAPTOP-R89KG6V1\\\\root\\\\cimv2:CIM_DataFile.Name=\\\"C:\\\\\\\\WINDOWS\\\\\\\\System32\\\\\\\\msvcp_win.dll\\\"\",Dependent=\"\\\\\\\\LAPTOP-R89KG6V1\\\\root\\\\cimv2:Win32_Process.Handle=\\\"2088\\\"\"");

        Assert.assertEquals(2, pathMap.size());
        Assert.assertTrue(pathMap.containsKey("Antecedent"));
        Assert.assertTrue(pathMap.containsKey("Dependent"));
        Assert.assertEquals(
                "\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\msvcp_win.dll\"",
                pathMap.get("Antecedent"));
        Assert.assertEquals(
                "\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"2088\"",
                pathMap.get("Dependent"));
    }

    @Test
    public void BuildPathWbem_1() throws Exception {
        String createdPath = ObjectPath.BuildCimv2PathWbem(
                "Win32_Process",
                Map.of("Handle", "1234"));
        Assert.assertEquals(
                PresentUtils.PrefixCimv2Path("Win32_Process.Handle=\"1234\""),
                createdPath
        );
    }

    @Test
    public void BuildPathWbem_2() throws Exception {
        String createdPath = ObjectPath.BuildCimv2PathWbem(
                "CIM_DataFile",
                Map.of("Name", "C:\\WINDOWS\\SYSTEM32\\HologramWorld.dll"));
        Assert.assertEquals(
                PresentUtils.PrefixCimv2Path("CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\SYSTEM32\\\\HologramWorld.dll\""),
                createdPath
        );
    }

    @Test
    public void BuildPathWbem_3() throws Exception {
        String currentHost = PresentUtils.getComputerName();
        String createdPath = ObjectPath.BuildCimv2PathWbem(
                "CIM_ProcessExecutable",
                Map.of(
                        "Antecedent",
                        String.format("\\\\%s\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\msvcp_win.dll\"", currentHost),
                        "Dependent",
                                String.format("\\\\%s\\root\\cimv2:Win32_Process.Handle=\"2088\"", currentHost)
                        )
                );
        Assert.assertEquals(
                String.format(
                        "\\\\%s\\ROOT\\CIMV2:CIM_ProcessExecutable.Antecedent=\"\\\\\\\\%s\\\\root\\\\cimv2:CIM_DataFile.Name=\\\"C:\\\\\\\\WINDOWS\\\\\\\\System32\\\\\\\\msvcp_win.dll\\\"\",Dependent=\"\\\\\\\\%s\\\\root\\\\cimv2:Win32_Process.Handle=\\\"2088\\\"\"",
                        currentHost,
                        currentHost,
                        currentHost),
                createdPath
        );
    }
}