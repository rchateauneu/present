package paquetage;

import java.util.*;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

public class GenericProviderTest extends TestCase {
    static String currentPidStr = String.valueOf(ProcessHandle.current().pid());

    GenericProvider genericProvider;
    protected void setUp() throws Exception {
        genericProvider = new GenericProvider();
    }

    @Override
    protected void tearDown() throws Exception {
        genericProvider = null;
    }

    private void CheckProvider(QueryData queryData, String nameSelecterExpected, String nameGetterExpected) {
        BaseSelecter baseSelecter = genericProvider.FindCustomSelecter(queryData);
        String nameSelecterActual = (baseSelecter == null) ? null : baseSelecter.getClass().getCanonicalName();
        Assert.assertEquals(nameSelecterActual, nameSelecterExpected);

        BaseGetter baseGetter = genericProvider.FindCustomGetter(queryData);
        String nameGetterActual = (baseGetter == null) ? null : baseGetter.getClass().getCanonicalName();
        Assert.assertEquals(nameGetterActual, nameGetterExpected);
    }

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

    /** Checks that there is no provider for the attribute Caption and class Win32_Process.
     *
     * @throws Exception
     */
    @Test
    public void testCompareProvider_Win32_Process_1() throws Exception {
        QueryData queryData = new QueryData(
                "ROOT\\CIMV2",
                "Win32_Process",
                "the_process",
                false,
                Map.of(
                        "Handle", "the_handle"),
                Arrays.asList(new QueryData.WhereEquality("Caption", "the_caption")));

        // Checks that the custom provider cannot be found.
        CheckProvider(queryData, null, "paquetage.BaseGetter_Win32_Process_Handle");
    }

    /** Checks that there is a provider for the attribute Handle and class Win32_Process.
     *
     * @throws Exception
     */
    @Test
    public void testCompareProvider_Win32_Process_2() throws Exception {
        QueryData queryData = new QueryData(
                "ROOT\\CIMV2",
                "Win32_Process",
                "the_process",
                false,
                Map.of(
                        "Handle", "the_handle"),
                Arrays.asList(new QueryData.WhereEquality("Handle", currentPidStr)));

        CheckProvider(queryData, null, "paquetage.BaseGetter_Win32_Process_Handle");
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
    public void testCompareProvider_Provider_CIM_DataFile_Name_Several() throws Exception {
        QueryData queryData = new QueryData(
                "ROOT\\CIMV2",
                "CIM_DataFile",
                "the_file",
                false,
                Map.of(
                        "Caption", "var_caption",
                        "FileName", "var_filename",
                        "FileSize", "var_filesize",
                        "Path", "var_path"),
                Arrays.asList(new QueryData.WhereEquality("Name", "C:\\WINDOWS\\SYSTEM32\\ntdll.dll")));

        CheckProvider(queryData, "paquetage.BaseSelecter_CIM_DataFile_Name", "paquetage.BaseGetter_CIM_DataFile_Name");

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
    public void testCompareProvider_Provider_CIM_DataFile_Name() throws Exception {
        QueryData queryData = new QueryData(
                "ROOT\\CIMV2",
                "CIM_DataFile",
                "the_file",
                false,
                Map.of(
                        "Name", "var_name"),
                Arrays.asList(new QueryData.WhereEquality("Name", "C:\\WINDOWS\\SYSTEM32\\ntdll.dll")));

        CheckProvider(queryData, "paquetage.BaseSelecter_CIM_DataFile_Name", "paquetage.BaseGetter_CIM_DataFile_Name");

        ArrayList<GenericProvider.Row> rowsProviderCustom = genericProvider.SelectVariablesFromWhere(queryData, true);
        ArrayList<GenericProvider.Row> rowsProviderGeneric = genericProvider.SelectVariablesFromWhere(queryData, false);

        Assert.assertEquals(rowsProviderGeneric.size(), rowsProviderCustom.size());
        for(int index = 0; index <rowsProviderGeneric.size(); ++index) {
            CompareRows(rowsProviderGeneric.get(index), rowsProviderCustom.get(index));
        }
    }

    /** Checks that there is no provider for the attribute CreationDate and class CIM_DataFile.
     *
     * @throws Exception
     */
    @Test
    public void testCompareProvider_Provider_CIM_DataFile_Name_CreationDate() throws Exception {
        QueryData queryData = new QueryData(
                "ROOT\\CIMV2",
                "CIM_DataFile",
                "the_file",
                false,
                Map.of(
                        "CreationDate", "creation_date",
                        "Name", "var_name"),
                Arrays.asList(new QueryData.WhereEquality("Name", "C:\\WINDOWS\\SYSTEM32\\ntdll.dll")));

        // Checks that there is a custom provider for the attribute "CreationDate".
        CheckProvider(queryData, null, null);
    }

    /** Checks that there is no provider for the attribute InUseCount and class CIM_DataFile.
     * The other columns which are available do not count, because one is missing.
     *
     * @throws Exception
     */
    @Test
    public void testCompareProvider_Provider_CIM_DataFile_Name_InUseCount() throws Exception {
        QueryData queryData = new QueryData(
                "ROOT\\CIMV2",
                "CIM_DataFile",
                "the_file",
                false,
                Map.of(
                        "FileName", "var_filename", // Available.
                        "FileSize", "var_filesize", // Available
                        "InUseCount", "in_use_count", // Not available.
                        "Name", "var_name"),
                Arrays.asList(new QueryData.WhereEquality("Name", "C:\\WINDOWS\\SYSTEM32\\ntdll.dll")));

        // Checks that there are no custom providers.
        CheckProvider(queryData, null, null);
    }

    /** This checks the presence of a custom provider emulating a WMI query similar to:
     * "select GroupComponent from CIM_DirectoryContainsFile where PartComponent="something"
     * @throws Exception
     */
    @Test
    public void testCompareProvider_Provider_CIM_DirectoryContainsFile_PartComponent() throws Exception {
        QueryData queryData = new QueryData(
                "ROOT\\CIMV2",
                "CIM_DirectoryContainsFile",
                "uvw",
                false,
                Map.of("GroupComponent", "xyz"),
                Arrays.asList(new QueryData.WhereEquality("PartComponent", "abc")));

        CheckProvider(queryData, "paquetage.BaseSelecter_CIM_DirectoryContainsFile_PartComponent", null);
    }

    /** This checks the presence of a custom provider emulating a WMI query similar to:
     * "select PartComponent from CIM_DirectoryContainsFile where GroupComponent="something"
     * Custom providers do the same as a generic WMI query, but faster.
     * @throws Exception
     */
    @Test
    public void testCompareProvider_Provider_CIM_DirectoryContainsFile_GroupComponent() throws Exception {
        QueryData queryData = new QueryData(
                "ROOT\\CIMV2",
                "CIM_DirectoryContainsFile",
                "uvw",
                false,
                Map.of("PartComponent", "xyz"),
                Arrays.asList(new QueryData.WhereEquality("GroupComponent", "abc")));

        CheckProvider(queryData, "paquetage.BaseSelecter_CIM_DirectoryContainsFile_GroupComponent", null);
    }

    @Test
    public void testCompareProvider_Provider_CIM_ProcessExecutable_Dependent() throws Exception {
        QueryData queryData = new QueryData(
                "ROOT\\CIMV2",
                "CIM_ProcessExecutable",
                "uvw",
                false,
                Map.of("Antecedent", "xyz"),
                Arrays.asList(new QueryData.WhereEquality("Dependent", "abc")));

        CheckProvider(queryData, "paquetage.BaseSelecter_CIM_ProcessExecutable_Dependent", null);
    }

    @Test
    public void testCompareProvider_Provider_CIM_ProcessExecutable_Antecedent() throws Exception {
        QueryData queryData = new QueryData(
                "ROOT\\CIMV2",
                "CIM_ProcessExecutable",
                "uvw",
                false,
                Map.of("Dependent", "xyz"),
                Arrays.asList(new QueryData.WhereEquality("Antecedent", "abc")));

        CheckProvider(queryData, "paquetage.BaseSelecter_CIM_ProcessExecutable_Antecedent", null);
    }

    /** This instantiates object associated to a file, by two ways:
     * - Calling GetObject() from a WMI path, which is generic but not very fast.
     * - And with a custom getter, which emulates the generic function, and is faster.
     * The results are then compared. It just compares the most common columns.
     * @throws Exception
     */
    @Test
    public void testCompareGetter_CIM_DataFile() throws Exception {
        String filePath = "C:\\WINDOWS\\SYSTEM32\\ntdll.dll";
        QueryData queryData = new QueryData(
                "ROOT\\CIMV2",
                "CIM_DataFile",
                "the_file",
                true,
                Map.of(
                        "Caption", "var_caption",
                        "FileName", "var_filename",
                        "FileSize", "var_filesize",
                        "Path", "var_path"),
                Arrays.asList(new QueryData.WhereEquality("Name", filePath)));

        CheckProvider(queryData, "paquetage.BaseSelecter_CIM_DataFile_Name", "paquetage.BaseGetter_CIM_DataFile_Name");

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
    public void testCompareGetter_Win32_Process() throws Exception {
        QueryData queryData = new QueryData(
                "ROOT\\CIMV2",
                "Win32_Process",
                "the_process",
                true,
                Map.of(
                        "Handle", "var_caption",
                        "Name", "var_name",
                        "ProcessId", "var_processid",
                        "WindowsVersion", "var_windowsversion"),
                Arrays.asList(new QueryData.WhereEquality("Handle", currentPidStr)));

        CheckProvider(queryData, null, "paquetage.BaseGetter_Win32_Process_Handle");

        String objectPath = ObjectPath.BuildPathWbem("Win32_Process", Map.of("Handle", currentPidStr));
        GenericProvider.Row rowGetterCustom = genericProvider.GetObjectFromPath(objectPath, queryData, true);
        GenericProvider.Row rowGetterGeneric = genericProvider.GetObjectFromPath(objectPath, queryData, false);
        CompareRows(rowGetterCustom, rowGetterGeneric);
    }
}