package paquetage;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class ObjectPathTest {

    @Test
    public void testParseWbemPathToNamespaceClass_1() throws Exception {
        Pair<String, String> pairNamespaceClass = ObjectPath.parseWbemPathToNamespaceClass("\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"2088\"");
        Assert.assertEquals("root\\cimv2", pairNamespaceClass.getLeft());
        Assert.assertEquals("Win32_Process", pairNamespaceClass.getRight());
    }

    @Test
    public void testParseWbemPath_1() throws Exception {
        Map<String, String> pathMap = ObjectPath.parseWbemPath(
                "\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"2088\"");

        Assert.assertEquals(1, pathMap.size());
        Assert.assertTrue(pathMap.containsKey("Handle"));
        Assert.assertEquals("2088", pathMap.get("Handle"));
    }

    @Test
    public void testParseWbemPath_2() throws Exception {
        Map<String, String> pathMap = ObjectPath.parseWbemPath(
                "\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\SYSTEM32\\\\HologramWorld.dll\"");

        Assert.assertEquals(1, pathMap.size());
        Assert.assertTrue(pathMap.containsKey("Name"));
        Assert.assertEquals("C:\\WINDOWS\\SYSTEM32\\HologramWorld.dll", pathMap.get("Name"));
    }

    @Test
    public void testParseWbemPath_3_1() throws Exception {
        Map<String, String> pathMap = ObjectPath.parseWbemPath(
                "\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:X.Y=\"\\\\\\\\Z\\\\ns:W.N=\\\"C:\\\\\\\\a.dll\\\"\"");

        Assert.assertEquals(1, pathMap.size());
        Assert.assertTrue(pathMap.containsKey("Y"));
        Assert.assertEquals(
                "\\\\Z\\ns:W.N=\"C:\\\\a.dll\"",
                pathMap.get("Y"));
    }

    @Test
    public void testParseWbemPath_3_2() throws Exception {
        Map<String, String> pathMap = ObjectPath.parseWbemPath(
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
    public void testBuildPathWbem_1() throws Exception {
        String createdPath = ObjectPath.buildCimv2PathWbem(
                "Win32_Process",
                Map.of("Handle", "1234"));
        Assert.assertEquals(
                PresentUtils.prefixCimv2Path("Win32_Process.Handle=\"1234\""),
                createdPath
        );
    }

    @Test
    public void testBuildPathWbem_2() throws Exception {
        String createdPath = ObjectPath.buildCimv2PathWbem(
                "CIM_DataFile",
                Map.of("Name", "C:\\WINDOWS\\SYSTEM32\\HologramWorld.dll"));
        Assert.assertEquals(
                PresentUtils.prefixCimv2Path("CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\SYSTEM32\\\\HologramWorld.dll\""),
                createdPath
        );
    }

    @Test
    public void testBuildPathWbem_3() throws Exception {
        String currentHost = PresentUtils.computerName;
        String createdPath = ObjectPath.buildCimv2PathWbem(
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