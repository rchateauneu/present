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
        Map<String, String> singleRowElements = listRows.get(0).Elements;
        Assert.assertEquals(1, singleRowElements.size());
        Assert.assertTrue(singleRowElements.containsKey("x"));
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
        Map<String, String> singleRowElements = listRows.get(0).Elements;
        Assert.assertEquals(1, singleRowElements.size());
        Assert.assertTrue(singleRowElements.containsKey("label"));
    }

    @Test
    public static void testSelectFromOntologyAndWMI_WithClass() throws Exception {
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
        Map<String, String> singleRowElements = listRows.get(0).Elements;
        Assert.assertEquals(2, singleRowElements.size());
        Assert.assertTrue(singleRowElements.containsKey("caption"));
        Assert.assertTrue(singleRowElements.containsKey("label"));
    }

    @Test
    public static void testSelectFromOntologyAndWMI_WithoutClass() throws Exception {
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
        Map<String, String> singleRowElements = listRows.get(0).Elements;
        Assert.assertEquals(2, singleRowElements.size());
        Assert.assertTrue(singleRowElements.containsKey("caption"));
        Assert.assertTrue(singleRowElements.containsKey("label"));
    }

    // Requete croisee avec les processes et l'ontologie.
}