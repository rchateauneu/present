package paquetage;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

class SparqlToWmiPlan extends SparqlToWmiAbstract {
    public SparqlToWmiPlan(List<ObjectPattern> patterns) throws Exception {
        super(patterns);
    }

    /**
     * This is for testing only a gives a symbolic representation of nested WQL queries
     * created from a Sparql query.
     * @return A multi-line string.
     */
    String SymbolicQuery() throws Exception
    {
        String result = "";
        String margin = "";
        for(int index = 0; index < prepared_queries.size(); ++index) {
            WmiSelecter.QueryData queryData = prepared_queries.get(index);
            String line = margin + queryData.BuildWqlQuery() + "\n";
            result += line;
            margin += "\t";
        }
        return result;
    }
}

public class SparqlToWmiTest {
    void CompareQueryData(WmiSelecter.QueryData expected, WmiSelecter.QueryData actual)
    {
        Assert.assertEquals(expected.className, actual.className);
        Assert.assertEquals(expected.queryColumns.size(), actual.queryColumns.size());
        for(String key: expected.queryColumns.keySet()) {
            Assert.assertEquals(expected.queryColumns.get(key), actual.queryColumns.get(key));
        }
        if((expected.queryWheres == null) || (actual.queryWheres == null))
        {
            Assert.assertEquals(null, actual.queryWheres);
        }
        else {
            Assert.assertEquals(expected.queryWheres.size(), actual.queryWheres.size());
            for (int index = 0; index < expected.queryWheres.size(); ++index) {
                WmiSelecter.KeyValue kv_expected = expected.queryWheres.get(index);
                WmiSelecter.KeyValue kv_actual = actual.queryWheres.get(index);
                Assert.assertEquals(kv_expected.key, kv_actual.key);
                Assert.assertEquals(kv_expected.value, kv_actual.value);
            }
        }
    }

    @Test
    /**
     * This manually builds an object pattern and checks the intermediate query data necessary to create the WQL query,
     * are properly created.
     */
    public void SymbolicQuery1Test() throws Exception {
        ObjectPattern objectPattern = new ObjectPattern(
                "my_process", WmiOntology.survol_url_prefix + "Win32_Process");
        objectPattern.AddKeyValue(WmiOntology.survol_url_prefix + "Handle", false,"123");

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(Arrays.asList(objectPattern));
        String symbolicQuery = patternSparql.SymbolicQuery();
        // This selects as few columns as possible (but the keys are returned anyway, and the path).
        Assert.assertEquals("Select __PATH from Win32_Process where Handle = \"123\"\n", symbolicQuery);
    }

    @Test
    public void SymbolicQuery2Test() throws Exception {
        ObjectPattern objectPattern = new ObjectPattern(
                "my_process",
                WmiOntology.survol_url_prefix + "CIM_DataFile");
        objectPattern.AddKeyValue(WmiOntology.survol_url_prefix + "Name", false,"C:");
        objectPattern.AddKeyValue(WmiOntology.survol_url_prefix + "Caption", true,"any_variable");

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(Arrays.asList(objectPattern));
        String symbolicQuery = patternSparql.SymbolicQuery();
        Assert.assertEquals("Select Caption, __PATH from CIM_DataFile where Name = \"C:\"\n", symbolicQuery);
    }

    @Test
    public void SymbolicQuery3Test() throws Exception {
        ObjectPattern objectPattern0 = new ObjectPattern(
                "my_process",
                WmiOntology.survol_url_prefix + "Win32_Process");
        objectPattern0.AddKeyValue(WmiOntology.survol_url_prefix + "Name", false,"C:");

        ObjectPattern objectPattern1 = new ObjectPattern(
                "my_assoc",
                WmiOntology.survol_url_prefix + "CIM_ProcessExecutable");
        objectPattern1.AddKeyValue(WmiOntology.survol_url_prefix + "Dependent", true,"my_process");
        objectPattern1.AddKeyValue(WmiOntology.survol_url_prefix + "Antecedent", true,"my_file");

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(Arrays.asList(objectPattern0, objectPattern1));
        String symbolicQuery = patternSparql.SymbolicQuery();
        System.out.println("symbolicQuery=" + symbolicQuery);
        Assert.assertEquals(
                "Select __PATH from Win32_Process where Name = \"C:\"\n" +
                    "\tSelect Antecedent, __PATH from CIM_ProcessExecutable where Dependent = \"my_process\"\n",
                symbolicQuery);
    }

    @Test
    /**
     * This manually builds an object pattern and checks the intermediate query data necessary to create the WQL query,
     * are properly created.
     */
    public void InternalQueryDataTest() throws Exception {
        ObjectPattern objectPattern = new ObjectPattern("my_process", WmiOntology.survol_url_prefix + "Win32_Process");
        objectPattern.AddKeyValue(WmiOntology.survol_url_prefix + "Handle", false,"123");

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(Arrays.asList(objectPattern));

        WmiSelecter.QueryData queryData0 = new WmiSelecter.QueryData(
                "Win32_Process",
                "my_process",
                null,
                Arrays.asList(new WmiSelecter.KeyValue("Handle", "123"))
        );

        List<WmiSelecter.QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 1);
        CompareQueryData(preparedQueries.get(0), queryData0);
    }

    @Test
    /**
     * This tests the creation of internal query data from a Sparql query.
     */
    public void Plan1Test() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_directory
            where {
                ?my_directory rdf:type cim:Win32_Directory .
                ?my_directory cim:Name "C:" .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_directory"));

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(extractor.patternsAsArray());

        WmiSelecter.QueryData queryData0 = new WmiSelecter.QueryData(
                "Win32_Directory",
                "my_directory",
                null,
                Arrays.asList(new WmiSelecter.KeyValue("Name", "C:"))
        );

        List<WmiSelecter.QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 1);
        CompareQueryData(queryData0, preparedQueries.get(0));
    }

    @Test
    public void Plan1_1Test() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_process_name ?my_process_handle
            where {
                ?my_process rdf:type cim:CIM_Process .
                ?my_process cim:Handle ?my_process_handle .
                ?my_process cim:Name ?my_process_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_name", "my_process_handle"));

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(extractor.patternsAsArray());

        WmiSelecter.QueryData queryData0 = new WmiSelecter.QueryData(
                "CIM_Process",
                "my_process",
                Map.of("Handle", "my_process_handle", "Name", "my_process_name"),
                null
        );

        List<WmiSelecter.QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 1);
        CompareQueryData(queryData0, preparedQueries.get(0));
    }

    @Test
    public void Plan1_2Test() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_process_name ?my_process_handle
            where {
                ?my_process rdf:type cim:CIM_Process .
                ?my_process cim:Handle "12345" .
                ?my_process cim:Name ?my_process_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_name", "my_process_handle"));

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(extractor.patternsAsArray());

        WmiSelecter.QueryData queryData0 = new WmiSelecter.QueryData(
                "CIM_Process",
                "my_process",
                Map.of("Name", "my_process_name"),
                Arrays.asList(
                        new WmiSelecter.KeyValue("Handle", "12345")
                )
        );

        List<WmiSelecter.QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 1);
        CompareQueryData(queryData0, preparedQueries.get(0));
    }

    @Test
    public void Plan2_1Test() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_file
            where {
                ?my2_assoc rdf:type cim:CIM_ProcessExecutable .
                ?my2_assoc cim:Dependent ?my1_process .
                ?my2_assoc cim:Antecedent ?my_file .
                ?my1_process rdf:type cim:Win32_Process .
                ?my1_process cim:Handle "123" .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        //System.out.println("bindings="+extractor.bindings.toString());
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file"));

        // Without optimisation, the patterns are sorted based on the variable name which is unique by definition,
        // because the RDF triples are grouped by subject.
        // Therefore, this order is deterministic, and it is possible to know how the WQL queries are generated:
        // Their order in the nested loops, which input variables they need for the WHERE clause,
        // which variables they can produce.
        // The variable names of the subject fo the triples are chosen to force their order.
        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(extractor.patternsAsArray());

        WmiSelecter.QueryData queryData0 = new WmiSelecter.QueryData(
                "Win32_Process",
                "my1_process",
                null,
                Arrays.asList(
                        new WmiSelecter.KeyValue("Handle", "123")
                )
        );

        WmiSelecter.QueryData queryData1 = new WmiSelecter.QueryData(
                "CIM_ProcessExecutable",
                "my2_assoc",
                Map.of("Antecedent", "my_file"),
                Arrays.asList(
                        new WmiSelecter.KeyValue("Dependent", "my1_process")
                )
        );

        List<WmiSelecter.QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 2);
        CompareQueryData(queryData0, preparedQueries.get(0));
        CompareQueryData(queryData1, preparedQueries.get(1));
    }

    @Test
    public void Plan2_2Test() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_file
            where {
                ?my1_assoc rdf:type cim:CIM_ProcessExecutable .
                ?my1_assoc cim:Dependent ?my0_process .
                ?my1_assoc cim:Antecedent ?my_file .
                ?my0_process rdf:type cim:Win32_Process .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        //System.out.println("bindings="+extractor.bindings.toString());
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file"));

        // Without optimisation, the patterns are sorted based on the variable name which is unique by definition,
        // because the RDF triples are grouped by subject.
        // Therefore, this order is deterministic, and it is possible to know how the WQL queries are generated:
        // Their order in the nested loops, which input variables they need for the WHERE clause,
        // which variables they can produce.
        // The variable names of the subject fo the triples are chosen to force their order.
        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(extractor.patternsAsArray());

        WmiSelecter.QueryData queryData0 = new WmiSelecter.QueryData(
                "Win32_Process",
                "my0_process",
                null,
                null
        );

        WmiSelecter.QueryData queryData1 = new WmiSelecter.QueryData(
                "CIM_ProcessExecutable",
                "my1_assoc",
                Map.of("Antecedent", "my_file"),
                Arrays.asList(
                        new WmiSelecter.KeyValue("Dependent", "my0_process")
                )
        );

        List<WmiSelecter.QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 2);
        CompareQueryData(queryData0, preparedQueries.get(0));
        CompareQueryData(queryData1, preparedQueries.get(1));
    }

    @Test
    public void Plan3_1Test() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_file_name
            where {
                ?my0_assoc rdf:type cim:CIM_ProcessExecutable .
                ?my0_assoc cim:Dependent ?my2_process .
                ?my0_assoc cim:Antecedent ?my1_file .
                ?my2_process rdf:type cim:Win32_Process .
                ?my2_process cim:Handle "123" .
                ?my1_file rdf:type cim:CIM_DataFile .
                ?my1_file cim:Name ?my_file_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        // Without optimisation, the patterns are sorted based on the variable name which is unique by definition,
        // because the RDF triples are grouped by subject.
        // Therefore, this order is deterministic, and it is possible to know how the WQL queries are generated:
        // Their order in the nested loops, which input variables they need for the WHERE clause,
        // which variables they can produce.
        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(extractor.patternsAsArray());

        WmiSelecter.QueryData queryData0 = new WmiSelecter.QueryData(
                "CIM_ProcessExecutable",
                "my0_assoc",
                Map.of("Dependent", "my2_process", "Antecedent", "my1_file"),
                null
        );

        WmiSelecter.QueryData queryData1 = new WmiSelecter.QueryData(
                "CIM_DataFile",
                "my1_file",
                Map.of("Name", "my_file_name"),
                null
        );

        // Here, there is no WMI selection but the instantiation of the COM object with the moniker.
        WmiSelecter.QueryData queryData2 = new WmiSelecter.QueryData(
                "Win32_Process",
                "my2_process",
                null,
                Arrays.asList(
                        new WmiSelecter.KeyValue("Handle", "123")
                )
        );

        List<WmiSelecter.QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 3);
        CompareQueryData(queryData0, preparedQueries.get(0));
        CompareQueryData(queryData1, preparedQueries.get(1));
        CompareQueryData(queryData2, preparedQueries.get(2));
    }

    @Test
    public void Plan3_2Test() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_file_name
            where {
                ?my0_assoc rdf:type cim:CIM_ProcessExecutable .
                ?my0_assoc cim:Dependent ?my1_process .
                ?my0_assoc cim:Antecedent ?my2_file .
                ?my1_process rdf:type cim:Win32_Process .
                ?my1_process cim:Handle "123" .
                ?my2_file rdf:type cim:CIM_DataFile .
                ?my2_file cim:Name ?my_file_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(extractor.patternsAsArray());

        WmiSelecter.QueryData queryData0 = new WmiSelecter.QueryData(
                "CIM_ProcessExecutable",
                "my0_assoc",
                Map.of("Dependent", "my1_process", "Antecedent", "my2_file"),
                null
        );

        // Here, there is no WMI selection but the instantiation of the COM object with the moniker.
        WmiSelecter.QueryData queryData1 = new WmiSelecter.QueryData(
                "Win32_Process",
                "my1_process",
                null,
                Arrays.asList(
                        new WmiSelecter.KeyValue("Handle", "123")
                )
        );

        WmiSelecter.QueryData queryData2 = new WmiSelecter.QueryData(
                "CIM_DataFile",
                "my2_file",
                Map.of("Name", "my_file_name"),
                null
        );

        List<WmiSelecter.QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 3);
        CompareQueryData(queryData0, preparedQueries.get(0));
        CompareQueryData(queryData1, preparedQueries.get(1));
        CompareQueryData(queryData2, preparedQueries.get(2));
    }

    @Test
    public void Plan3_3Test() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_file_name
            where {
                ?my1_assoc rdf:type cim:CIM_ProcessExecutable .
                ?my1_assoc cim:Dependent ?my0_process .
                ?my1_assoc cim:Antecedent ?my2_file .
                ?my0_process rdf:type cim:Win32_Process .
                ?my0_process cim:Handle "123" .
                ?my2_file rdf:type cim:CIM_DataFile .
                ?my2_file cim:Name ?my_file_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(extractor.patternsAsArray());

        WmiSelecter.QueryData queryData0 = new WmiSelecter.QueryData(
                "Win32_Process",
                "my0_process",
                null,
                Arrays.asList(
                        new WmiSelecter.KeyValue("Handle", "123")
                )
        );

        WmiSelecter.QueryData queryData1 = new WmiSelecter.QueryData(
                "CIM_ProcessExecutable",
                "my1_assoc",
                Map.of("Antecedent", "my2_file"),
                Arrays.asList(
                        new WmiSelecter.KeyValue("Dependent", "my0_process")
                )
        );

        // No need to select anything because the object is already known.
        WmiSelecter.QueryData queryData2 = new WmiSelecter.QueryData(
                "CIM_DataFile",
                "my2_file",
                Map.of("Name", "my_file_name"),
                null
        );

        List<WmiSelecter.QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 3);
        CompareQueryData(queryData0, preparedQueries.get(0));
        CompareQueryData(queryData1, preparedQueries.get(1));
        CompareQueryData(queryData2, preparedQueries.get(2));
    }

    @Test
    public void Plan3_4Test() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_process_caption
            where {
                ?my1_assoc rdf:type cim:CIM_ProcessExecutable .
                ?my1_assoc cim:Dependent ?my2_process .
                ?my1_assoc cim:Antecedent ?my0_file .
                ?my2_process rdf:type cim:Win32_Process .
                ?my2_process cim:Caption ?my_process_caption .
                ?my0_file rdf:type cim:CIM_DataFile .
                ?my0_file cim:Name "C:" .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_caption"));

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(extractor.patternsAsArray());

        // No need to select anything because the object is already known.
        WmiSelecter.QueryData queryData0 = new WmiSelecter.QueryData(
                "CIM_DataFile",
                "my0_file",
                null,
                Arrays.asList(
                        new WmiSelecter.KeyValue("Name", "C:")
                )
        );

        WmiSelecter.QueryData queryData1 = new WmiSelecter.QueryData(
                "CIM_ProcessExecutable",
                "my1_assoc",
                Map.of("Dependent", "my2_process"),
                Arrays.asList(
                        new WmiSelecter.KeyValue("Antecedent", "my0_file")
                )
        );

        WmiSelecter.QueryData queryData2 = new WmiSelecter.QueryData(
                "Win32_Process",
                "my2_process",
                Map.of("Caption", "my_process_caption"),
                null
        );

        List<WmiSelecter.QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 3);
        CompareQueryData(queryData0, preparedQueries.get(0));
        CompareQueryData(queryData1, preparedQueries.get(1));
        CompareQueryData(queryData2, preparedQueries.get(2));
    }

    @Test
    public void Execution1_1Test() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_process_caption
            where {
                ?my_process rdf:type cim:Win32_Process .
                ?my_process cim:Handle ?my_process_caption .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_caption"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        //System.out.println("Received rows=" + the_rows);
        //System.out.println("number of rows=" + the_rows.size());
        boolean foundCurrentPid = false;
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);
        //System.out.println("pidString=" + pidString);
        for(WmiSelecter.Row row: the_rows) {
            //System.out.println("PID=" + row.Elements.get("my_process_caption"));
            if(row.Elements.get("my_process_caption").equals(pidString)) {
                foundCurrentPid = true;
                break;
            }
            // System.out.println("Row=" + row.toString());
        }
        Assert.assertTrue(foundCurrentPid);
    }
}
