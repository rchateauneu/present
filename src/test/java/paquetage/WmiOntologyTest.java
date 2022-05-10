package paquetage;

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Formatter;
import java.util.HashSet;

public class WmiOntologyTest {
    static WmiOntology ontology = null;

    @Before
    public void setUp() throws Exception {
        ontology = new WmiOntology();
    }

    @After
    public void tearDown() throws Exception {
        ontology = null;
    }

    HashSet<String> selectColumn(String sparqlQuery, String columnName){
        HashSet<String> variablesSet = new HashSet<String>();
        try (RepositoryConnection conn = ontology.repository.getConnection()) {
            TupleQuery tupleQuery = conn.prepareTupleQuery(sparqlQuery);
            try (TupleQueryResult result = tupleQuery.evaluate()) {
                while (result.hasNext()) {  // iterate over the result
                    BindingSet bindingSet = result.next();
                    Value valueOfX = bindingSet.getValue(columnName);
                    variablesSet.add(valueOfX.toString());
                }
            }
            return variablesSet;
        }
    }

    @Test
    public void TestOntologyClassesAll() {
        HashSet<String> classesSet = selectColumn("SELECT ?x WHERE { ?x ?p ?y }", "x");
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "Win32_Process"));
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "CIM_DataFile"));
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "CIM_ProcessExecutable"));
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "CIM_DirectoryContainsFile"));
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "Win32_SystemServices"));
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "Win32_SubDirectory"));
    }

    @Test
    public void TestOntologyClassesFilter() {
        HashSet<String> classesSet = selectColumn("SELECT ?x WHERE { ?x rdf:type ?y }", "x");
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "Win32_Process"));
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "CIM_ProcessExecutable"));
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "CIM_DirectoryContainsFile"));
    }

    @Test
    public void TestOntologyPropertiesAll() {
        HashSet<String> classesSet = selectColumn("SELECT ?x WHERE { ?x ?p ?y }", "x");
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "Handle"));
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "Name"));
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "Antecedent"));
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "Dependent"));
    }

    @Test
    public void TestOntologyPropertiesFilter() {
        HashSet<String> classesSet = selectColumn("SELECT ?y WHERE { ?y rdf:type rdf:Property }", "y");
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "Handle"));
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "Dependent"));
    }

    @Test
    public void TestOntologyWin32_Process_Handle_Domain_All() {
        String querystring = new Formatter().format(
                "SELECT ?y WHERE { ?y rdfs:domain ?z }").toString( );
        HashSet<String> classesSet = selectColumn(querystring, "y");
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "Handle"));
    }

    @Test
    public void TestOntologyWin32_Process_Handle_Domain_Filter() {
        String querystring = new Formatter().format(
                "SELECT ?x WHERE { <%s> rdfs:domain ?x }", WmiOntology.survol_url_prefix + "Handle").toString( );
        HashSet<String> classesSet = selectColumn(querystring, "x");
        Assert.assertTrue(classesSet.contains(WmiOntology.survol_url_prefix + "Win32_Process"));
    }

    @Test
    public void TestOntologyWin32_Process_Handle_Range_Filter() {
        // Predicates: [
        // http://www.w3.org/2000/01/rdf-schema#label,
        // http://www.w3.org/2000/01/rdf-schema#domain,
        // http://www.w3.org/1999/02/22-rdf-syntax-ns#type,
        // http://www.w3.org/2000/01/rdf-schema#range]
        String querystring = new Formatter().format(
                "SELECT ?x WHERE { <%s> rdfs:range ?x }", WmiOntology.survol_url_prefix + "Handle").toString( );
        HashSet<String> classesSet = selectColumn(querystring, "x");
        Assert.assertTrue(classesSet.contains("http://www.w3.org/2001/XMLSchema#string"));
    }
}
