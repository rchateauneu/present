package paquetage;

import java.util.*;

import org.junit.Assert;
import org.junit.Test;

public class GenericSelecterTest {
    static String currentPidStr = String.valueOf(ProcessHandle.current().pid());

    static void CompareRows(GenericSelecter.Row row1, GenericSelecter.Row row2) {
        System.out.println("row1=" + row1.KeySet());
        System.out.println("row2=" + row2.KeySet());
        Assert.assertEquals(row1.ElementsSize(), row2.ElementsSize());
        Assert.assertEquals(row1.KeySet(), row2.KeySet());
        for(String oneKey:  row1.KeySet()) {
            System.out.println("oneKey=" + oneKey);
            Assert.assertEquals(row1.GetStringValue(oneKey), row2.GetStringValue(oneKey));
        }
    }

    /** This tests the results of files object associated to a file, loaded via Sparql mechanism, by two ways:
     * - Selecting from WMI, which is generic but not very fast.
     * - And with a custom provider, which emulates the generic function, and is faster.
     * The results are then compared. It just compares the most common columns.
     *
     * Custom providers do the same as a generic WMI query, but faster.
     * @throws Exception
     */
    @Test
    public void CompareProvider_Provider_CIM_DataFile_Name_1() throws Exception {
        QueryData queryData = new QueryData(
                "CIM_DataFile",
                "the_file",
                false,
                Map.of(
                        "Caption", "var_caption",
                        "FileName", "var_filename",
                        "FileSize", "var_filesize",
                        "Path", "var_path"),
                Arrays.asList(new QueryData.WhereEquality("Name", "C:\\WINDOWS\\SYSTEM32\\ntdll.dll")));

        GenericSelecter genericSelecter = new GenericSelecter();
        // Checks that the custom provider can be found.
        Assert.assertEquals("paquetage.Provider_CIM_DataFile_Name", genericSelecter.FindCustomProvider(queryData).getClass().getCanonicalName());

        ArrayList<GenericSelecter.Row> rowsProviderCustom = genericSelecter.SelectVariablesFromWhere(queryData, true);
        ArrayList<GenericSelecter.Row> rowsProviderGeneric = genericSelecter.SelectVariablesFromWhere(queryData, false);

        Assert.assertEquals(rowsProviderGeneric.size(), rowsProviderCustom.size());
        for(int index = 0; index <rowsProviderGeneric.size(); ++index) {
            CompareRows(rowsProviderGeneric.get(index), rowsProviderCustom.get(index));
        }
    }

    /** Corner case if only the property "Name" is also selected.
     *
     */
    public void CompareProvider_Provider_CIM_DataFile_Name_2() throws Exception {
        QueryData queryData = new QueryData(
                "CIM_DataFile",
                "the_file",
                false,
                Map.of(
                        "Name", "var_name"),
                Arrays.asList(new QueryData.WhereEquality("Name", "C:\\WINDOWS\\SYSTEM32\\ntdll.dll")));

        GenericSelecter genericSelecter = new GenericSelecter();
        // Checks that the custom provider can be found.
        Assert.assertEquals("GenericSelecter.ObjectGetter_CIM_DataFile_Name", genericSelecter.FindCustomProvider(queryData).getClass().getCanonicalName());

        ArrayList<GenericSelecter.Row> rowsProviderCustom = genericSelecter.SelectVariablesFromWhere(queryData, true);
        ArrayList<GenericSelecter.Row> rowsProviderGeneric = genericSelecter.SelectVariablesFromWhere(queryData, false);

        Assert.assertEquals(rowsProviderGeneric.size(), rowsProviderCustom.size());
        for(int index = 0; index <rowsProviderGeneric.size(); ++index) {
            CompareRows(rowsProviderGeneric.get(index), rowsProviderCustom.get(index));
        }
    }

    /** This checks the presence of a custom provider emulating a WMI query similar to:
     * "select GroupComponent from CIM_DirectoryContainsFile where PartComponent="something"
     * @throws Exception
     */
    @Test
    public void CompareProvider_Provider_CIM_DirectoryContainsFile_PartComponent() throws Exception {
        QueryData queryData = new QueryData(
                "CIM_DirectoryContainsFile",
                "uvw",
                false,
                Map.of("GroupComponent", "xyz"),
                Arrays.asList(new QueryData.WhereEquality("PartComponent", "abc")));

        GenericSelecter genericSelecter = new GenericSelecter();
        // Assert.assertTrue(genericSelecter.FindCustomProvider(queryData) != null);
        Assert.assertEquals("paquetage.Provider_CIM_DirectoryContainsFile_PartComponent", genericSelecter.FindCustomProvider(queryData).getClass().getCanonicalName());
    }

    /** This checks the presence of a custom provider emulating a WMI query similar to:
     * "select PartComponent from CIM_DirectoryContainsFile where GroupComponent="something"
     * Custom providers do the same as a generic WMI query, but faster.
     * @throws Exception
     */
    @Test
    public void CompareProvider_Provider_CIM_DirectoryContainsFile_GroupComponent() throws Exception {
        QueryData queryData = new QueryData(
                "CIM_DirectoryContainsFile",
                "uvw",
                false,
                Map.of("PartComponent", "xyz"),
                Arrays.asList(new QueryData.WhereEquality("GroupComponent", "abc")));

        GenericSelecter genericSelecter = new GenericSelecter();
        Assert.assertTrue(genericSelecter.FindCustomProvider(queryData) != null);
    }

    @Test
    public void CompareProvider_Provider_CIM_ProcessExecutable_Dependent() throws Exception {
        QueryData queryData = new QueryData(
                "CIM_ProcessExecutable",
                "uvw",
                false,
                Map.of("Antecedent", "xyz"),
                Arrays.asList(new QueryData.WhereEquality("Dependent", "abc")));

        GenericSelecter genericSelecter = new GenericSelecter();
        Assert.assertTrue(genericSelecter.FindCustomProvider(queryData) != null);
    }

    @Test
    public void CompareProvider_Provider_CIM_ProcessExecutable_Antecedent() throws Exception {
        QueryData queryData = new QueryData(
                "CIM_ProcessExecutable",
                "uvw",
                false,
                Map.of("Dependent", "xyz"),
                Arrays.asList(new QueryData.WhereEquality("Antecedent", "abc")));

        GenericSelecter genericSelecter = new GenericSelecter();
        Assert.assertTrue(genericSelecter.FindCustomProvider(queryData) != null);
    }

    /** This instantiates object associated to a file, by two ways:
     * - Calling GetObject() from a WMI path, which is generic but not very fast.
     * - And with a custom getter, which emulates the generic function, and is faster.
     * The results are then compared. It just compares the most common columns.
     * @throws Exception
     */
    @Test
    public void CompareGetter_CIM_DataFile() throws Exception {
        String filePath = "C:\\WINDOWS\\SYSTEM32\\ntdll.dll";
        QueryData queryData = new QueryData(
                "CIM_DataFile",
                "the_file",
                true,
                Map.of(
                        "Caption", "var_caption",
                        "FileName", "var_filename",
                        "FileSize", "var_filesize",
                        "Path", "var_path"),
                Arrays.asList(new QueryData.WhereEquality("Name", filePath)));

        GenericSelecter genericSelecter = new GenericSelecter();
        Assert.assertTrue(genericSelecter.FindCustomGetter(queryData) != null);

        String objectPath = ObjectPath.BuildPathWbem("CIM_DataFile", Map.of("Name", filePath));
        GenericSelecter.Row rowGetterCustom = genericSelecter.GetObjectFromPath(objectPath, queryData, true);
        GenericSelecter.Row rowGetterGeneric = genericSelecter.GetObjectFromPath(objectPath, queryData, false);
        CompareRows(rowGetterCustom, rowGetterGeneric);
    }

    /** This instantiates object associated to a process, by two ways:
     * - Calling GetObject() from a WMI path, which is generic but not very fast.
     * - And with a custom getter, which emulates the generic function, and is faster.
     * The results are then compared. It just compares the most common columns.
     * @throws Exception
     */
    @Test
    public void CompareGetter_Win32_Process() throws Exception {
        QueryData queryData = new QueryData(
                "Win32_Process",
                "the_process",
                true,
                Map.of(
                        "Handle", "var_caption",
                        "Name", "var_name",
                        "ProcessId", "var_processid",
                        "WindowsVersion", "var_windowsversion"),
                Arrays.asList(new QueryData.WhereEquality("Handle", currentPidStr)));

        GenericSelecter genericSelecter = new GenericSelecter();
        // This ensures that the custom function getter is found.
        Assert.assertTrue(genericSelecter.FindCustomGetter(queryData) != null);

        String objectPath = ObjectPath.BuildPathWbem("Win32_Process", Map.of("Handle", currentPidStr));
        GenericSelecter.Row rowGetterCustom = genericSelecter.GetObjectFromPath(objectPath, queryData, true);
        GenericSelecter.Row rowGetterGeneric = genericSelecter.GetObjectFromPath(objectPath, queryData, false);
        CompareRows(rowGetterCustom, rowGetterGeneric);
    }
}
