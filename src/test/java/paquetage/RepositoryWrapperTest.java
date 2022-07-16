package paquetage;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/** This tests Sparql selection from a repository containing the ontology plus the result of a WQL selection. */
public class RepositoryWrapperTest extends TestCase {
    static String currentPidStr = String.valueOf(ProcessHandle.current().pid());
    @Test
    public static void testFromMemory() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());
    }

    /** This tests that at least some triples are there, coming from the ontology. */
    @Test
    public static void testSelectAnyTriples() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = "SELECT ?x WHERE { ?x ?y ?z } limit 10";
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(10, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(1, singleRow.ElementsSize());
        Assert.assertTrue(singleRow.ContainsKey("x"));
    }

    /** This tests the presence of element from the ontology. */
    @Test
    public static void testSelectFromOntology() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?label
                    where {
                        cim:Win32_Process.Handle rdfs:label ?label .
                    }
                """;
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(1, singleRow.ElementsSize());
        Assert.assertTrue(singleRow.ContainsKey("label"));
    }

    @Test
    public static void testSelect_Win32_Process_WithClass_WithoutOntology() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?caption ?process
                    where {
                        ?process rdf:type cim:Win32_Process .
                        ?process cim:Handle "%s" .
                        ?process cim:Win32_Process.Caption ?caption .
                    }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(2, singleRow.ElementsSize());
        Assert.assertTrue(singleRow.ContainsKey("caption"));
        Assert.assertTrue(singleRow.ContainsKey("process"));
    }

    @Test
    public static void testSelect_Win32_Process_WithClass_WithOntology() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?label ?caption
                    where {
                        ?process rdf:type cim:Win32_Process .
                        ?process cim:Handle "%s" .
                        ?process cim:Win32_Process.Caption ?caption .
                        cim:Win32_Process.Handle rdfs:label ?label .
                    }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(2, singleRow.ElementsSize());
        Assert.assertTrue(singleRow.ContainsKey("caption"));
        Assert.assertTrue(singleRow.ContainsKey("label"));
    }

    @Test
    public static void testSelect_Win32_Process_WithoutClass_WithoutOntology() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process
                    where {
                        ?process cim:Win32_Process.Handle "%s" .
                        ?process cim:Win32_Process.Caption ?caption .
                    }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(1, singleRow.ElementsSize());
        Assert.assertTrue(singleRow.ContainsKey("process"));
    }

    @Test
    public static void testSelect_Win32_Process_WithoutClass_WithOntology() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?label ?caption
                    where {
                        ?process cim:Win32_Process.Handle "%s" .
                        ?process cim:Win32_Process.Caption ?caption .
                        cim:Win32_Process.Handle rdfs:label ?label .
                    }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(2, singleRow.ElementsSize());
        Assert.assertTrue(singleRow.ContainsKey("caption"));
        Assert.assertTrue(singleRow.ContainsKey("label"));
    }

    /** Patterns order, therefore the order of queries, is forced with alphabetical order and no optimisation.
     * Optimising the object patterns implies changing their order with some constraints due
     * to variable dependencies.
     * With this, the test can focus on the results of the evaluation : This is simpler to test.
     *
     * @throws Exception
     */
    @Test
    public static void testSelect_CIM_ProcessExecutable_NoFilter() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?file_name
                    where {
                        ?_1_process rdf:type cim:Win32_Process .
                        ?_1_process cim:Handle "%s" .
                        ?_1_process cim:Win32_Process.Caption ?caption .
                        ?_2_assoc rdf:type cim:CIM_ProcessExecutable .
                        ?_2_assoc cim:Dependent ?_1_process .
                        ?_2_assoc cim:Antecedent ?_3_file .
                        ?_3_file rdf:type cim:CIM_DataFile .
                        ?_3_file cim:Name ?file_name .
                   }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        //Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(1, singleRow.ElementsSize());
        Assert.assertTrue(singleRow.ContainsKey("file_name"));
        System.out.println("Exec=" + singleRow.GetStringValue("file_name"));
        String expectedBin = "\"" + PresentUtils.CurrentJavaBinary() + "\"";
        Assert.assertEquals(expectedBin, singleRow.GetStringValue("file_name"));
    }

    /** Patterns order, therefore the order of queries, is forced with alphabetical order and no optimisation.
     * Optimising the object patterns implies changing their order with some constraints due
     * to variable dependencies.
     * With this, the test can focus on the results of the evaluation : This is simpler to test.
     *
     * @throws Exception
     */
    @Test
    public static void testSelect_CIM_ProcessExecutable_Filter() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?file_name
                    where {
                        ?_1_process rdf:type cim:Win32_Process .
                        ?_1_process cim:Handle "%s" .
                        ?_1_process cim:Win32_Process.Caption ?caption .
                        ?_2_assoc rdf:type cim:CIM_ProcessExecutable .
                        ?_2_assoc cim:Dependent ?_1_process .
                        ?_2_assoc cim:Antecedent ?_3_file .
                        ?_3_file rdf:type cim:CIM_DataFile .
                        ?_3_file cim:Name ?file_name .
                        filter(regex(?file_name, "java.exe", "i" )) 
                   }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(1, singleRow.ElementsSize());
        Assert.assertTrue(singleRow.ContainsKey("file_name"));
        System.out.println("Exec=" + singleRow.GetStringValue("file_name"));
        String expectedBin = "\"" + PresentUtils.CurrentJavaBinary() + "\"";
        Assert.assertEquals(expectedBin, singleRow.GetStringValue("file_name"));
    }

    /** Only gets the node of the associated instances.
     *
     * @throws Exception
     */
    @Test
    public static void testSelect_CIM_ProcessExecutable_Assoc() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?_3_file
                    where {
                        ?_1_process cim:Handle "%s" .
                        ?_1_process cim:Win32_Process.Caption ?caption .
                        ?_2_assoc cim:CIM_ProcessExecutable.Dependent ?_1_process .
                        ?_2_assoc cim:CIM_ProcessExecutable.Antecedent ?_3_file .
                        ?_3_file cim:CIM_DataFile.Name ?file_name .
                        filter(regex(?file_name, "java.exe", "i" )) 
                   }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        //Assert.assertEquals(1, listRows.size());
        Assert.assertTrue(listRows.size() > 0);
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(1, singleRow.ElementsSize());
        Assert.assertTrue(singleRow.ContainsKey("_3_file"));
        System.out.println("Exec=" + singleRow.GetStringValue("_3_file"));
    }
}