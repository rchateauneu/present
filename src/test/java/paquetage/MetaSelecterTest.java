package paquetage;

import java.util.*;

import org.junit.Assert;
import org.junit.Test;

public class MetaSelecterTest {
    static void CompareRows(MetaSelecter.Row row1, MetaSelecter.Row row2) {
        System.out.println("row1=" + row1.Elements.keySet());
        System.out.println("row2=" + row2.Elements.keySet());
        Assert.assertEquals(row1.Elements.size(), row2.Elements.size());
        Assert.assertEquals(row1.Elements.keySet(), row2.Elements.keySet());
        for(String oneKey:  row1.Elements.keySet()) {
            System.out.println("oneKey=" + oneKey);
            Assert.assertEquals(row1.Elements.get(oneKey), row2.Elements.get(oneKey));
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
    public void CompareProvider_Provider_CIM_DataFile_Name() throws Exception {
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

        MetaSelecter metaSelecter = new MetaSelecter();
        // Checks that the custom provider can be found.
        Assert.assertTrue(metaSelecter.FindCustomProvider(queryData) != null);

        ArrayList<MetaSelecter.Row> rowsProviderCustom = metaSelecter.SelectVariablesFromWhere(queryData, true);
        ArrayList<MetaSelecter.Row> rowsProviderGeneric = metaSelecter.SelectVariablesFromWhere(queryData, false);

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

        MetaSelecter metaSelecter = new MetaSelecter();
        Assert.assertTrue(metaSelecter.FindCustomProvider(queryData) != null);
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

        MetaSelecter metaSelecter = new MetaSelecter();
        Assert.assertTrue(metaSelecter.FindCustomProvider(queryData) != null);
    }

    @Test
    public void CompareProvider_Provider_CIM_ProcessExecutable_Dependent() throws Exception {
        QueryData queryData = new QueryData(
                "CIM_ProcessExecutable",
                "uvw",
                false,
                Map.of("Antecedent", "xyz"),
                Arrays.asList(new QueryData.WhereEquality("Dependent", "abc")));

        MetaSelecter metaSelecter = new MetaSelecter();
        Assert.assertTrue(metaSelecter.FindCustomProvider(queryData) != null);
    }

    @Test
    public void CompareProvider_Provider_CIM_ProcessExecutable_Antecedent() throws Exception {
        QueryData queryData = new QueryData(
                "CIM_ProcessExecutable",
                "uvw",
                false,
                Map.of("Dependent", "xyz"),
                Arrays.asList(new QueryData.WhereEquality("Antecedent", "abc")));

        MetaSelecter metaSelecter = new MetaSelecter();
        Assert.assertTrue(metaSelecter.FindCustomProvider(queryData) != null);
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

        MetaSelecter metaSelecter = new MetaSelecter();
        Assert.assertTrue(metaSelecter.FindCustomGetter(queryData) != null);

        String objectPath = ObjectPath.BuildPathWbem("CIM_DataFile", Map.of("Name", filePath));
        MetaSelecter.Row rowGetterCustom = metaSelecter.GetObjectFromPath(objectPath, queryData, true);
        MetaSelecter.Row rowGetterGeneric = metaSelecter.GetObjectFromPath(objectPath, queryData, false);
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
        String pidString = String.valueOf(ProcessHandle.current().pid());
        QueryData queryData = new QueryData(
                "Win32_Process",
                "the_process",
                true,
                Map.of(
                        "Handle", "var_caption",
                        "Name", "var_name",
                        "ProcessId", "var_processid",
                        "WindowsVersion", "var_windowsversion"),
                Arrays.asList(new QueryData.WhereEquality("Handle", pidString)));

        MetaSelecter metaSelecter = new MetaSelecter();
        // This ensures that the custom function getter is found.
        Assert.assertTrue(metaSelecter.FindCustomGetter(queryData) != null);

        String objectPath = ObjectPath.BuildPathWbem("Win32_Process", Map.of("Handle", pidString));
        MetaSelecter.Row rowGetterCustom = metaSelecter.GetObjectFromPath(objectPath, queryData, true);
        MetaSelecter.Row rowGetterGeneric = metaSelecter.GetObjectFromPath(objectPath, queryData, false);
        CompareRows(rowGetterCustom, rowGetterGeneric);
    }
}
