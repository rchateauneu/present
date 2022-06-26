package paquetage;

import java.util.*;

import org.junit.Assert;
import org.junit.Test;

public class MetaSelecterTest {
    static void CompareRows(MetaSelecter.Row row1, MetaSelecter.Row row2) {
        Assert.assertEquals(row1.Elements.size(), row2.Elements.size());
        Assert.assertEquals(row1.Elements.keySet(), row2.Elements.keySet());
        for(String oneKey:  row1.Elements.keySet()) {
            System.out.println("oneKey=" + oneKey);
            Assert.assertEquals(row1.Elements.get(oneKey), row2.Elements.get(oneKey));
        }
    }

    @Test
    public void CompareProvider_Provider_CIM_DataFile_Name() throws Exception {
        QueryData queryData = new QueryData(
                "CIM_DataFile",
                "the_file",
                false,
                Map.of("FileName", "var_filename"),
                Arrays.asList(new QueryData.WhereEquality("Name", "C:\\WINDOWS\\SYSTEM32\\ntdll.dll")));

        MetaSelecter metaSelecter = new MetaSelecter();
        ArrayList<MetaSelecter.Row> rowsProviderCustom = metaSelecter.SelectVariablesFromWhere(queryData, true);
        ArrayList<MetaSelecter.Row> rowsProviderGeneric = metaSelecter.SelectVariablesFromWhere(queryData, false);

        Assert.assertEquals(rowsProviderGeneric.size(), rowsProviderCustom.size());
        for(int index = 0; index <rowsProviderGeneric.size(); ++index) {
            CompareRows(rowsProviderGeneric.get(index), rowsProviderCustom.get(index));
        }
    }

    /*
    new Provider_CIM_DataFile_Name(),
    new Provider_CIM_DirectoryContainsFile_PartComponent(),
    new Provider_CIM_DirectoryContainsFile_GroupComponent(),
    new Provider_CIM_ProcessExecutable_Dependent(),
    new Provider_CIM_ProcessExecutable_Antecedent()
    */

    @Test
    public void CompareGetter() throws Exception {
        String filePath = "C:\\WINDOWS\\SYSTEM32\\ntdll.dll";
        QueryData queryData = new QueryData(
                "CIM_DataFile",
                "the_file",
                false,
                Map.of("FileName", "var_filename"),
                Arrays.asList(new QueryData.WhereEquality("Name", filePath)));

        MetaSelecter metaSelecter = new MetaSelecter();

        String objectPath = ObjectPath.BuildPathWbem("CIM_DataFile", Map.of("Name", filePath));
        MetaSelecter.Row rowGetterCustom = metaSelecter.GetObjectFromPath(objectPath, queryData, true);
        MetaSelecter.Row rowGetterGeneric = metaSelecter.GetObjectFromPath(objectPath, queryData, false);
        CompareRows(rowGetterCustom, rowGetterGeneric);
    }
    /*
    new ObjectGetter_Cim_DataFile_Name(),
    new ObjectGetter_Win32_Process_Handle()
    */
}
