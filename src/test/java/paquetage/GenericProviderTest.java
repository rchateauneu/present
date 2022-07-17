package paquetage;

import java.util.*;

import org.junit.Assert;
import org.junit.Test;

public class GenericProviderTest {
    static String currentPidStr = String.valueOf(ProcessHandle.current().pid());

    static void CompareRows(GenericProvider.Row row1, GenericProvider.Row row2) {
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

        GenericProvider genericProvider = new GenericProvider();
        // Checks that the custom provider can be found.
        Assert.assertEquals("paquetage.BaseSelecter_CIM_DataFile_Name", genericProvider.FindCustomSelecter(queryData).getClass().getCanonicalName());

        ArrayList<GenericProvider.Row> rowsProviderCustom = genericProvider.SelectVariablesFromWhere(queryData, true);
        ArrayList<GenericProvider.Row> rowsProviderGeneric = genericProvider.SelectVariablesFromWhere(queryData, false);

        Assert.assertEquals(rowsProviderGeneric.size(), rowsProviderCustom.size());
        for(int index = 0; index <rowsProviderGeneric.size(); ++index) {
            CompareRows(rowsProviderGeneric.get(index), rowsProviderCustom.get(index));
        }
    }

    /** Corner case if only the property "Name" is also selected.
     *
     */
    @Test
    public void CompareProvider_Provider_CIM_DataFile_Name_2() throws Exception {
        QueryData queryData = new QueryData(
                "CIM_DataFile",
                "the_file",
                false,
                Map.of(
                        "Name", "var_name"),
                Arrays.asList(new QueryData.WhereEquality("Name", "C:\\WINDOWS\\SYSTEM32\\ntdll.dll")));

        GenericProvider genericProvider = new GenericProvider();
        // Checks that the custom provider can be found.
        Assert.assertEquals("paquetage.BaseSelecter_CIM_DataFile_Name", genericProvider.FindCustomSelecter(queryData).getClass().getCanonicalName());

        ArrayList<GenericProvider.Row> rowsProviderCustom = genericProvider.SelectVariablesFromWhere(queryData, true);
        ArrayList<GenericProvider.Row> rowsProviderGeneric = genericProvider.SelectVariablesFromWhere(queryData, false);

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

        GenericProvider genericProvider = new GenericProvider();
        Assert.assertEquals("paquetage.BaseSelecter_CIM_DirectoryContainsFile_PartComponent", genericProvider.FindCustomSelecter(queryData).getClass().getCanonicalName());
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

        GenericProvider genericProvider = new GenericProvider();
        Assert.assertTrue(genericProvider.FindCustomSelecter(queryData) != null);
    }

    @Test
    public void CompareProvider_Provider_CIM_ProcessExecutable_Dependent() throws Exception {
        QueryData queryData = new QueryData(
                "CIM_ProcessExecutable",
                "uvw",
                false,
                Map.of("Antecedent", "xyz"),
                Arrays.asList(new QueryData.WhereEquality("Dependent", "abc")));

        GenericProvider genericProvider = new GenericProvider();
        Assert.assertTrue(genericProvider.FindCustomSelecter(queryData) != null);
    }

    @Test
    public void CompareProvider_Provider_CIM_ProcessExecutable_Antecedent() throws Exception {
        QueryData queryData = new QueryData(
                "CIM_ProcessExecutable",
                "uvw",
                false,
                Map.of("Dependent", "xyz"),
                Arrays.asList(new QueryData.WhereEquality("Antecedent", "abc")));

        GenericProvider genericProvider = new GenericProvider();
        Assert.assertTrue(genericProvider.FindCustomSelecter(queryData) != null);
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

        GenericProvider genericProvider = new GenericProvider();
        Assert.assertTrue(genericProvider.FindCustomGetter(queryData) != null);

        String objectPath = ObjectPath.BuildPathWbem("CIM_DataFile", Map.of("Name", filePath));
        GenericProvider.Row rowGetterCustom = genericProvider.GetObjectFromPath(objectPath, queryData, true);
        GenericProvider.Row rowGetterGeneric = genericProvider.GetObjectFromPath(objectPath, queryData, false);
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

        GenericProvider genericProvider = new GenericProvider();
        // This ensures that the custom function getter is found.
        Assert.assertTrue(genericProvider.FindCustomGetter(queryData) != null);

        String objectPath = ObjectPath.BuildPathWbem("Win32_Process", Map.of("Handle", currentPidStr));
        GenericProvider.Row rowGetterCustom = genericProvider.GetObjectFromPath(objectPath, queryData, true);
        GenericProvider.Row rowGetterGeneric = genericProvider.GetObjectFromPath(objectPath, queryData, false);
        CompareRows(rowGetterCustom, rowGetterGeneric);
    }
}
