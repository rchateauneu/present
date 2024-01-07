package paquetage;

import org.junit.*;

import java.util.*;

public class RdfsPredicatesCIMV2Test {
    static long currentPid = ProcessHandle.current().pid();
    static String currentPidStr = String.valueOf(currentPid);

    private RepositoryWrapper repositoryWrapper = null;

    @Before
    public void setUp() throws Exception {
        repositoryWrapper = new RepositoryWrapper("ROOT\\CIMV2");
    }

    @After
    public void tearDown() throws Exception {
        repositoryWrapper = null;
    }

    /*
    This ensures that the rdfs label is created.
    TODO: It should be possible to select with the label.
     */
    @Test
    public void testSelect_Win32_Directory_RdfsLabel() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?directory_label
                    where {
                        ?my_dir rdfs:label ?directory_label .
                        ?my_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                    }
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("directory_label"), singleRow.KeySet());

        String actualLabel = singleRow.GetAsLiteral("directory_label");
        System.out.println("actualLabel=" + actualLabel);

        Assert.assertEquals("\"C:\\\\Windows\"@en", actualLabel);
    }

    @Test
    public void testSelect_Win32_Directory_RdfsLabel_NameSelected() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_label ?my_name
                    where {
                        ?my1_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                        ?my2_assoc cimv2:GroupComponent ?my1_dir .
                        ?my2_assoc cimv2:CIM_DirectoryContainsFile.PartComponent ?my3_file .
                        ?my3_file  rdfs:label ?my_label .
                        ?my3_file  cimv2:CIM_DataFile.Name ?my_name .
                    }

                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Set<String> setLabels = listRows.StringValuesSet("my_label");
        System.out.println("setLabels=" + setLabels.toString());

        Set<String> setNames = listRows.StringValuesSet("my_name");
        System.out.println("setNames=" + setNames.toString());

        Assert.assertTrue(setNames.contains("C:\\Windows\\notepad.exe"));
        Assert.assertTrue(setNames.contains("C:\\Windows\\system.ini"));
    }

    @Test
    public void testSelect_Win32_Directory_RdfsLabel_NameNotSelected() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_label
                    where {
                        ?my1_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                        ?my2_assoc cimv2:GroupComponent ?my1_dir .
                        ?my2_assoc cimv2:CIM_DirectoryContainsFile.PartComponent ?my3_file .
                        ?my3_file  rdfs:label ?my_label .
                        ?my3_file  cimv2:CIM_DataFile.Name ?my_name .
                    }

                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Assert.assertTrue( listRows.size() > 0);

        Set<String> setLabels = listRows.StringValuesSet("my_label");
        System.out.println("setLabels=" + setLabels.toString());

        Assert.assertTrue(setLabels.contains("C:\\Windows\\notepad.exe"));
        Assert.assertTrue(setLabels.contains("C:\\Windows\\system.ini"));
    }

}
