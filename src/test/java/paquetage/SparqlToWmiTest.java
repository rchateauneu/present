package paquetage;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

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
    void CompareQueryData(WmiSelecter.QueryData expected, WmiSelecter.QueryData actual) {
        Assert.assertEquals(expected.className, actual.className);
        Assert.assertEquals(expected.mainVariable, actual.mainVariable);
        Assert.assertEquals(expected.isMainVariableAvailable, actual.isMainVariableAvailable);
        Assert.assertEquals(expected.queryColumns.size(), actual.queryColumns.size());
        for (String key : expected.queryColumns.keySet()) {
            Assert.assertEquals(expected.queryColumns.get(key), actual.queryColumns.get(key));
        }
        if ((expected.queryWheres == null) || (actual.queryWheres == null)) {
            Assert.assertEquals(null, actual.queryWheres);
        } else {
            Assert.assertEquals(expected.queryWheres.size(), actual.queryWheres.size());
            for (int index = 0; index < expected.queryWheres.size(); ++index) {
                WmiSelecter.WhereEquality kv_expected = expected.queryWheres.get(index);
                WmiSelecter.WhereEquality kv_actual = actual.queryWheres.get(index);
                Assert.assertEquals(kv_expected.predicate, kv_actual.predicate);
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
        objectPattern.AddKeyValue(WmiOntology.survol_url_prefix + "Handle", false, "123");

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
        objectPattern.AddKeyValue(WmiOntology.survol_url_prefix + "Name", false, "C:");
        objectPattern.AddKeyValue(WmiOntology.survol_url_prefix + "Caption", true, "any_variable");

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(Arrays.asList(objectPattern));
        String symbolicQuery = patternSparql.SymbolicQuery();
        Assert.assertEquals("Select Caption, __PATH from CIM_DataFile where Name = \"C:\"\n", symbolicQuery);
    }

    @Test
    public void SymbolicQuery3Test() throws Exception {
        ObjectPattern objectPattern0 = new ObjectPattern(
                "my_process",
                WmiOntology.survol_url_prefix + "Win32_Process");
        objectPattern0.AddKeyValue(WmiOntology.survol_url_prefix + "Name", false, "C:");

        ObjectPattern objectPattern1 = new ObjectPattern(
                "my_assoc",
                WmiOntology.survol_url_prefix + "CIM_ProcessExecutable");
        objectPattern1.AddKeyValue(WmiOntology.survol_url_prefix + "Dependent", true, "my_process");
        objectPattern1.AddKeyValue(WmiOntology.survol_url_prefix + "Antecedent", true, "my_file");

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
        objectPattern.AddKeyValue(WmiOntology.survol_url_prefix + "Handle", false, "123");

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(Arrays.asList(objectPattern));

        WmiSelecter.QueryData queryData0 = new WmiSelecter.QueryData(
                "Win32_Process",
                "my_process",
                false,
                null,
                Arrays.asList(new WmiSelecter.WhereEquality("Handle", "123", false))
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
                false,
                null,
                Arrays.asList(new WmiSelecter.WhereEquality("Name", "C:", false))
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
                false,
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
                false,
                Map.of("Name", "my_process_name"),
                Arrays.asList(
                        new WmiSelecter.WhereEquality("Handle", "12345", false)
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
                false,
                null,
                Arrays.asList(
                        new WmiSelecter.WhereEquality("Handle", "123", false)
                )
        );

        WmiSelecter.QueryData queryData1 = new WmiSelecter.QueryData(
                "CIM_ProcessExecutable",
                "my2_assoc",
                false,
                Map.of("Antecedent", "my_file"),
                Arrays.asList(
                        new WmiSelecter.WhereEquality("Dependent", "my1_process", true)
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
                false,
                null,
                null
        );

        WmiSelecter.QueryData queryData1 = new WmiSelecter.QueryData(
                "CIM_ProcessExecutable",
                "my1_assoc",
                false,
                Map.of("Antecedent", "my_file"),
                Arrays.asList(
                        new WmiSelecter.WhereEquality("Dependent", "my0_process", true)
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
                false,
                Map.of("Dependent", "my2_process", "Antecedent", "my1_file"),
                null
        );

        WmiSelecter.QueryData queryData1 = new WmiSelecter.QueryData(
                "CIM_DataFile",
                "my1_file",
                true,
                Map.of("Name", "my_file_name"),
                null
        );

        // Here, there is no WMI selection but the instantiation of the COM object with the moniker.
        WmiSelecter.QueryData queryData2 = new WmiSelecter.QueryData(
                "Win32_Process",
                "my2_process",
                true,
                null,
                Arrays.asList(
                        new WmiSelecter.WhereEquality("Handle", "123", false)
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
                false,
                Map.of("Dependent", "my1_process", "Antecedent", "my2_file"),
                null
        );

        // Here, there is no WMI selection but the instantiation of the COM object with the moniker.
        WmiSelecter.QueryData queryData1 = new WmiSelecter.QueryData(
                "Win32_Process",
                "my1_process",
                true,
                null,
                Arrays.asList(
                        new WmiSelecter.WhereEquality("Handle", "123", false)
                )
        );

        WmiSelecter.QueryData queryData2 = new WmiSelecter.QueryData(
                "CIM_DataFile",
                "my2_file",
                true,
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
                false,
                null,
                Arrays.asList(
                        new WmiSelecter.WhereEquality("Handle", "123", false)
                )
        );

        WmiSelecter.QueryData queryData1 = new WmiSelecter.QueryData(
                "CIM_ProcessExecutable",
                "my1_assoc",
                false,
                Map.of("Antecedent", "my2_file"),
                Arrays.asList(
                        new WmiSelecter.WhereEquality("Dependent", "my0_process", true)
                )
        );

        // In fact, no need to select anything because the object variable is already known.
        // However, this variable must be in the WHERE clause, so a WQL selection will work.
        WmiSelecter.QueryData queryData2 = new WmiSelecter.QueryData(
                "CIM_DataFile",
                "my2_file",
                true,
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
                false,
                null,
                Arrays.asList(
                        new WmiSelecter.WhereEquality("Name", "C:", false)
                )
        );

        WmiSelecter.QueryData queryData1 = new WmiSelecter.QueryData(
                "CIM_ProcessExecutable",
                "my1_assoc",
                false,
                Map.of("Dependent", "my2_process"),
                Arrays.asList(
                        new WmiSelecter.WhereEquality("Antecedent", "my0_file", true)
                )
        );

        WmiSelecter.QueryData queryData2 = new WmiSelecter.QueryData(
                "Win32_Process",
                "my2_process",
                true,
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
    public void Execution_Win32_Process_1() throws Exception {
        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_process_handle
                    where {
                        ?my_process rdf:type cim:Win32_Process .
                        ?my_process cim:Handle ?my_process_handle .
                    }
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_handle"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        boolean foundCurrentPid = false;
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);
        for (WmiSelecter.Row row : the_rows) {
            if (row.Elements.get("my_process_handle").equals(pidString)) {
                foundCurrentPid = true;
                break;
            }
        }
        Assert.assertTrue(foundCurrentPid);
    }

    @Test
    public void Execution_Win32_Process_2() throws Exception {
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);

        String sparql_query = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_process_caption
                    where {
                        ?my_process rdf:type cim:Win32_Process .
                        ?my_process cim:Caption ?my_process_caption .
                        ?my_process cim:Handle "%s" .
                    }
                """, pidString);
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_caption"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        Assert.assertEquals(1, the_rows.size());
        Assert.assertEquals("java.exe", the_rows.get(0).Elements.get("my_process_caption"));
    }

    @Test
    public void Execution_Forced_CIM_ProcessExecutable_1() throws Exception {
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);

        String sparql_query = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file_name
                    where {
                        ?my1_assoc rdf:type cim:CIM_ProcessExecutable .
                        ?my1_assoc cim:Dependent ?my0_process .
                        ?my1_assoc cim:Antecedent ?my2_file .
                        ?my0_process rdf:type cim:Win32_Process .
                        ?my0_process cim:Handle "%s" .
                        ?my2_file rdf:type cim:CIM_DataFile .
                        ?my2_file cim:Name ?my_file_name .
                    }
                """, pidString);

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        Set<String> libsSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_file_name")).collect(Collectors.toSet());
        // This tests the presence of some libraries which are used by the current Java process.
        Assert.assertTrue(libsSet.contains("C:\\Program Files\\Java\\jdk-17.0.2\\bin\\java.exe"));
        Assert.assertTrue(libsSet.contains("C:\\WINDOWS\\SYSTEM32\\ntdll.dll"));
        Assert.assertTrue(libsSet.contains("C:\\WINDOWS\\System32\\USER32.dll"));
    }

    @Test
    /**
     * The current pid must be found in the processes using a specific library.
     * The order of evaluation, i.e. the order of object patterns, is forced with the alphabetical order
     * of main variables.
     *
     * TODO: This test might fail is a process unexpectedly leaves in the middle of the query.
     */
    public void Execution_Forced_CIM_ProcessExecutable_2() throws Exception {
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);

        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_handle
                    where {
                        ?my1_assoc rdf:type cim:CIM_ProcessExecutable .
                        ?my1_assoc cim:Dependent ?my2_process .
                        ?my1_assoc cim:Antecedent ?my0_file .
                        ?my2_process rdf:type cim:Win32_Process .
                        ?my2_process cim:Handle ?my_handle .
                        ?my0_file rdf:type cim:CIM_DataFile .
                        ?my0_file cim:Name "C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_handle"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();

        Set<String> libsSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_handle")).collect(Collectors.toSet());
        // The current pid must be there because it uses this library.
        Assert.assertTrue(libsSet.contains(pidString));
    }

    @Test
    /**
     * This gets the handle and caption of all processes using a specific library.
     * The current pid must be found in the processes using a specific library.
     * This checks that two columns are properly returned.
     * The order of evaluation, i.e. the order of object patterns, is forced with the alphabetical order
     * of main variables.
     */
    public void Execution_Forced_CIM_ProcessExecutable_3() throws Exception {
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);

        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_handle ?my_caption
                    where {
                        ?my1_assoc rdf:type cim:CIM_ProcessExecutable .
                        ?my1_assoc cim:Dependent ?my2_process .
                        ?my1_assoc cim:Antecedent ?my0_file .
                        ?my2_process rdf:type cim:Win32_Process .
                        ?my2_process cim:Handle ?my_handle .
                        ?my2_process cim:Caption ?my_caption .
                        ?my0_file rdf:type cim:CIM_DataFile .
                        ?my0_file cim:Name "C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_caption", "my_handle"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();

        Set<String> captionsSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_caption")).collect(Collectors.toSet());
        // The current caption must be there because it uses this library.
        Assert.assertTrue(captionsSet.contains("java.exe"));
        Set<String> libsSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_handle")).collect(Collectors.toSet());
        // The current pid must be there because it uses this library.
        Assert.assertTrue(libsSet.contains(pidString));
    }

    @Test
    /***
     * This gets the parent-directory of a file.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_1() throws Exception {
        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_dir_name
                    where {
                        ?my1_assoc rdf:type cim:CIM_DirectoryContainsFile .
                        ?my1_assoc cim:GroupComponent ?my2_dir .
                        ?my1_assoc cim:PartComponent ?my0_file .
                        ?my2_dir rdf:type cim:Win32_Directory .
                        ?my2_dir cim:Name ?my_dir_name .
                        ?my0_file rdf:type cim:CIM_DataFile .
                        ?my0_file cim:Name "C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_dir_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        Assert.assertEquals(the_rows.size(), 1);
        Assert.assertEquals(the_rows.get(0).Elements.get("my_dir_name"), "C:\\WINDOWS\\SYSTEM32");
    }

    @Test
    /**
     * This gets the list of files in a directory.
     * Possibly because accessing CIM_DataFile is slow anyway.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_2() throws Exception {
        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file_name
                    where {
                        ?my1_assoc rdf:type cim:CIM_DirectoryContainsFile .
                        ?my1_assoc cim:GroupComponent ?my0_dir .
                        ?my1_assoc cim:PartComponent ?my2_file .
                        ?my0_dir rdf:type cim:Win32_Directory .
                        ?my0_dir cim:Name "C:\\\\WINDOWS\\\\SYSTEM32" .
                        ?my2_file rdf:type cim:CIM_DataFile .
                        ?my2_file cim:Name ?my_file_name .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        Set<String> filesSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_file_name").toUpperCase()).collect(Collectors.toSet());
        // These files must be in this directory.
        // Filename cases in system directories are not stable, therefore uppercase.
        Assert.assertTrue(filesSet.contains("C:\\WINDOWS\\SYSTEM32\\NTDLL.DLL"));
        Assert.assertTrue(filesSet.contains("C:\\WINDOWS\\SYSTEM32\\USER32.DLL"));
    }

    @Test
    /***
     * This gets the parent-parent-directory of a file.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_Win32_SubDirectory_1() throws Exception {
        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_dir_name
                    where {
                        ?my0_file rdf:type cim:CIM_DataFile .
                        ?my0_file cim:Name "C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll" .
                        ?my1_assoc rdf:type cim:CIM_DirectoryContainsFile .
                        ?my1_assoc cim:PartComponent ?my0_file .
                        ?my1_assoc cim:GroupComponent ?my2_dir .
                        ?my2_dir rdf:type cim:Win32_Directory .
                        ?my3_assoc rdf:type cim:Win32_SubDirectory .
                        ?my3_assoc cim:PartComponent ?my2_dir .
                        ?my3_assoc cim:GroupComponent ?my4_dir .
                        ?my4_dir rdf:type cim:Win32_Directory .
                        ?my4_dir cim:Name ?my_dir_name .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_dir_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        Assert.assertEquals(the_rows.size(), 1);
        Assert.assertEquals(the_rows.get(0).Elements.get("my_dir_name"), "C:\\WINDOWS");
    }

    @Test
    /**
     * This gets sub-sub-files of a directory.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_Win32_SubDirectory_2() throws Exception {
        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file_name
                    where {
                        ?my4_file rdf:type cim:CIM_DataFile .
                        ?my4_file cim:Name ?my_file_name .
                        ?my3_assoc rdf:type cim:CIM_DirectoryContainsFile .
                        ?my3_assoc cim:PartComponent ?my4_file .
                        ?my3_assoc cim:GroupComponent ?my2_dir .
                        ?my2_dir rdf:type cim:Win32_Directory .
                        ?my1_assoc rdf:type cim:Win32_SubDirectory .
                        ?my1_assoc cim:PartComponent ?my2_dir .
                        ?my1_assoc cim:GroupComponent ?my0_dir .
                        ?my0_dir rdf:type cim:Win32_Directory .
                        ?my0_dir cim:Name "C:\\\\Program Files\\\\Internet Explorer" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        Set<String> filesSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_file_name")).collect(Collectors.toSet());
        // These files must be in this directory.
        Assert.assertTrue(filesSet.contains("C:\\Program Files\\Internet Explorer\\en-US\\hmmapi.dll.mui"));
        Assert.assertTrue(filesSet.contains("C:\\Program Files\\Internet Explorer\\images\\bing.ico"));
    }


    @Test
    /**
     * This gets the directories of all executables and libraries used by running processes.
     * The order of evaluation is forced with the alphabetical order of main variables.
     */
    public void Execution_Forced_CIM_ProcessExecutable_CIM_DirectoryContainsFile_1() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_dir_name
            where {
                ?my0_process rdf:type cim:Win32_Process .
                ?my1_assoc rdf:type cim:CIM_ProcessExecutable .
                ?my1_assoc cim:Dependent ?my0_process .
                ?my1_assoc cim:Antecedent ?my2_file .
                ?my2_file rdf:type cim:CIM_DataFile .
                ?my3_assoc rdf:type cim:CIM_DirectoryContainsFile .
                ?my3_assoc cim:PartComponent ?my2_file .
                ?my3_assoc cim:GroupComponent ?my4_dir .
                ?my4_dir rdf:type cim:Win32_Directory .
                ?my4_dir cim:Name ?my_dir_name .
            }
        """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_dir_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        System.out.println("Rows number=" + the_rows.size());

        Set<String> dirsSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_dir_name")).collect(Collectors.toSet());
        for(String oneLib: dirsSet) {
            System.out.println("Lib=" + oneLib);
        }
        /*
        Beware that filename cases are not stable. WMI returns for example:
        "C:\WINDOWS\System32", "c:\windows\system32", "C:\WINDOWS\system32"
         */
        Assert.assertTrue(dirsSet.contains("C:\\WINDOWS"));
        Assert.assertTrue(dirsSet.contains("C:\\WINDOWS\\system32"));
    }


    /**
     *         self.assertEqual(map_attributes["Win32_MountPoint.Directory"],
     *             {"predicate_type": "ref:Win32_Directory", "predicate_domain": ["Win32_Volume"]})
     *         self.assertEqual(map_attributes["Win32_MountPoint.Volume"],
     *             {"predicate_type": "ref:Win32_Volume", "predicate_domain": ["Win32_Directory"]})
     */



    /*
    TODO: Specify the path of an object.
     */
}
