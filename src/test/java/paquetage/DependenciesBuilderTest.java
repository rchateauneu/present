package paquetage;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class DependenciesBuilderTest {

    void CompareQueryData(QueryData expected, QueryData actual) {
        Assert.assertEquals(expected.className, actual.className);
        Assert.assertEquals(expected.mainVariable, actual.mainVariable);
        Assert.assertEquals(expected.isMainVariableAvailable, actual.isMainVariableAvailable);
        System.out.println("expected.queryColumns=" + expected.queryColumns);
        System.out.println("actual.queryColumns=" + actual.queryColumns);
        Assert.assertEquals(expected.queryColumns.size(), actual.queryColumns.size());
        for (String key : expected.queryColumns.keySet()) {
            Assert.assertEquals(expected.queryColumns.get(key), actual.queryColumns.get(key));
        }
        if ((expected.whereTests == null) || (actual.whereTests == null)) {
            Assert.assertEquals(null, actual.whereTests);
        } else {
            Assert.assertEquals(expected.whereTests.size(), actual.whereTests.size());
            for (int index = 0; index < expected.whereTests.size(); ++index) {
                QueryData.WhereEquality kv_expected = expected.whereTests.get(index);
                QueryData.WhereEquality kv_actual = actual.whereTests.get(index);
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
                "my_process", PresentUtils.toCIMV2("Win32_Process"));
        objectPattern.AddPredicateObjectPairValue(PresentUtils.toCIMV2("Handle"), ValueTypePair.FromString("123"));
        objectPattern.PreparePattern();

        DependenciesBuilder patternSparql = new DependenciesBuilder(Arrays.asList(objectPattern));
        String symbolicQuery = patternSparql.SymbolicQuery();
        // This selects as few columns as possible (but the keys are returned anyway, and the path).
        Assert.assertEquals("Select __PATH from Win32_Process where Handle = \"123\"\n", symbolicQuery);
    }

    @Test
    public void SymbolicQuery2Test() throws Exception {
        ObjectPattern objectPattern = new ObjectPattern(
                "my_process",
                PresentUtils.toCIMV2("CIM_DataFile"));
        objectPattern.AddPredicateObjectPairValue(PresentUtils.toCIMV2("Name"), ValueTypePair.FromString("C:"));
        objectPattern.AddPredicateObjectPairVariable(PresentUtils.toCIMV2("Caption"), "any_variable");
        objectPattern.PreparePattern();

        DependenciesBuilder patternSparql = new DependenciesBuilder(Arrays.asList(objectPattern));
        String symbolicQuery = patternSparql.SymbolicQuery();
        Assert.assertEquals("Select Caption, __PATH from CIM_DataFile where Name = \"C:\"\n", symbolicQuery);
    }

    @Test
    public void SymbolicQuery3Test() throws Exception {
        ObjectPattern objectPattern0 = new ObjectPattern(
                "my_process",
                PresentUtils.toCIMV2("Win32_Process"));
        objectPattern0.AddPredicateObjectPairValue(PresentUtils.toCIMV2("Name"), ValueTypePair.FromString("C:"));
        objectPattern0.PreparePattern();

        ObjectPattern objectPattern1 = new ObjectPattern(
                "my_assoc",
                PresentUtils.toCIMV2("CIM_ProcessExecutable"));
        objectPattern1.AddPredicateObjectPairValue(PresentUtils.toCIMV2("Dependent"), ValueTypePair.FromString("my_process"));
        objectPattern1.PreparePattern();

        DependenciesBuilder patternSparql = new DependenciesBuilder(Arrays.asList(objectPattern0, objectPattern1));
        String symbolicQuery = patternSparql.SymbolicQuery();
        System.out.println("symbolicQuery=" + symbolicQuery);
        Assert.assertEquals(
                "Select __PATH from Win32_Process where Name = \"C:\"\n" +
                        "\tSelect __PATH from CIM_ProcessExecutable where Dependent = \"my_process\"\n",
                symbolicQuery);
    }

    @Test
    /**
     * This manually builds an object pattern and checks the intermediate query data necessary to create the WQL query,
     * are properly created.
     */
    public void InternalQueryDataTest() throws Exception {
        ObjectPattern objectPattern = new ObjectPattern("my_process", PresentUtils.toCIMV2("Win32_Process"));
        objectPattern.AddPredicateObjectPairValue(PresentUtils.toCIMV2("Handle"), ValueTypePair.FromString("123"));
        objectPattern.PreparePattern();

        DependenciesBuilder patternSparql = new DependenciesBuilder(Arrays.asList(objectPattern));

        QueryData queryData0 = new QueryData(
                "ROOT\\CIMV2",
                "Win32_Process",
                "my_process",
                false,
                null,
                Arrays.asList(new QueryData.WhereEquality("Handle", ValueTypePair.FromString("123")))
        );

        List<QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 1);
        CompareQueryData(preparedQueries.get(0), queryData0);
    }

    @Test
    /**
     * This tests the creation of internal query data from a Sparql query.
     */
    public void Plan1Test() throws Exception {
        String sparql_query = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_directory
                    where {
                        ?my_directory rdf:type cimv2:Win32_Directory .
                        ?my_directory cimv2:Name "C:" .
                    }
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_directory"));

        DependenciesBuilder patternSparql = new DependenciesBuilder(extractor.patternsAsArray());

        QueryData queryData0 = new QueryData(
                "ROOT\\CIMV2",
                "Win32_Directory",
                "my_directory",
                false,
                null,
                Arrays.asList(new QueryData.WhereEquality("Name", ValueTypePair.FromString("C:")))
        );

        List<QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 1);
        CompareQueryData(queryData0, preparedQueries.get(0));
    }

    @Test
    public void Plan1_1Test() throws Exception {
        String sparql_query = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_process_name ?my_process_handle
                    where {
                        ?my_process rdf:type cimv2:CIM_Process .
                        ?my_process cimv2:Handle ?my_process_handle .
                        ?my_process cimv2:Name ?my_process_name .
                    }
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_name", "my_process_handle"));

        DependenciesBuilder patternSparql = new DependenciesBuilder(extractor.patternsAsArray());

        QueryData queryData0 = new QueryData(
                "ROOT\\CIMV2",
                "CIM_Process",
                "my_process",
                false,
                Map.of("Handle", "my_process_handle", "Name", "my_process_name"),
                null
        );

        List<QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 1);
        CompareQueryData(queryData0, preparedQueries.get(0));
    }

    @Test
    public void Plan1_2Test() throws Exception {
        String sparql_query = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_process_name ?my_process_handle
                    where {
                        ?my_process rdf:type cimv2:CIM_Process .
                        ?my_process cimv2:Handle "12345" .
                        ?my_process cimv2:Name ?my_process_name .
                    }
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_name", "my_process_handle"));

        DependenciesBuilder patternSparql = new DependenciesBuilder(extractor.patternsAsArray());

        QueryData queryData0 = new QueryData(
                "ROOT\\CIMV2",
                "CIM_Process",
                "my_process",
                false,
                Map.of("Name", "my_process_name"),
                Arrays.asList(
                        new QueryData.WhereEquality("Handle", ValueTypePair.FromString("12345"))
                )
        );

        List<QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 1);
        CompareQueryData(queryData0, preparedQueries.get(0));
    }

    @Test
    public void Plan2_1Test() throws Exception {
        String sparql_query = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file
                    where {
                        ?my2_assoc rdf:type cimv2:CIM_ProcessExecutable .
                        ?my2_assoc cimv2:Dependent ?my1_process .
                        ?my2_assoc cimv2:Antecedent ?my_file .
                        ?my1_process rdf:type cimv2:Win32_Process .
                        ?my1_process cimv2:Handle "123" .
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
        DependenciesBuilder patternSparql = new DependenciesBuilder(extractor.patternsAsArray());

        QueryData queryData0 = new QueryData(
                "ROOT\\CIMV2",
                "Win32_Process",
                "my1_process",
                false,
                null,
                Arrays.asList(
                        new QueryData.WhereEquality("Handle", ValueTypePair.FromString("123"))
                )
        );

        QueryData queryData1 = new QueryData(
                "ROOT\\CIMV2",
                "CIM_ProcessExecutable",
                "my2_assoc",
                false,
                Map.of("Antecedent", "my_file"),
                Arrays.asList(
                        new QueryData.WhereEquality("Dependent", "my1_process")
                )
        );

        List<QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 2);
        CompareQueryData(queryData0, preparedQueries.get(0));
        CompareQueryData(queryData1, preparedQueries.get(1));
    }

    @Test
    public void Plan2_2Test() throws Exception {
        String sparql_query = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file
                    where {
                        ?my1_assoc rdf:type cimv2:CIM_ProcessExecutable .
                        ?my1_assoc cimv2:Dependent ?my0_process .
                        ?my1_assoc cimv2:Antecedent ?my_file .
                        ?my0_process rdf:type cimv2:Win32_Process .
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
        DependenciesBuilder patternSparql = new DependenciesBuilder(extractor.patternsAsArray());

        QueryData queryData0 = new QueryData(
                "ROOT\\CIMV2",
                "Win32_Process",
                "my0_process",
                false,
                null,
                null
        );

        QueryData queryData1 = new QueryData(
                "ROOT\\CIMV2",
                "CIM_ProcessExecutable",
                "my1_assoc",
                false,
                Map.of("Antecedent", "my_file"),
                Arrays.asList(
                        new QueryData.WhereEquality("Dependent", "my0_process")
                )
        );

        List<QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 2);
        CompareQueryData(queryData0, preparedQueries.get(0));
        CompareQueryData(queryData1, preparedQueries.get(1));
    }

    @Test
    public void Plan3_1Test() throws Exception {
        String sparql_query = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file_name
                    where {
                        ?my0_assoc rdf:type cimv2:CIM_ProcessExecutable .
                        ?my0_assoc cimv2:Dependent ?my2_process .
                        ?my0_assoc cimv2:Antecedent ?my1_file .
                        ?my2_process rdf:type cimv2:Win32_Process .
                        ?my2_process cimv2:Handle "123" .
                        ?my1_file rdf:type cimv2:CIM_DataFile .
                        ?my1_file cimv2:Name ?my_file_name .
                    }
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        // Without optimisation, the patterns are sorted based on the variable name which is unique by definition,
        // because the RDF triples are grouped by subject.
        // Therefore, this order is deterministic, and it is possible to know how the WQL queries are generated:
        // Their order in the nested loops, which input variables they need for the WHERE clause,
        // which variables they can produce.
        DependenciesBuilder patternSparql = new DependenciesBuilder(extractor.patternsAsArray());

        QueryData queryData0 = new QueryData(
                "ROOT\\CIMV2",
                "CIM_ProcessExecutable",
                "my0_assoc",
                false,
                Map.of("Dependent", "my2_process", "Antecedent", "my1_file"),
                null
        );

        QueryData queryData1 = new QueryData(
                "ROOT\\CIMV2",
                "CIM_DataFile",
                "my1_file",
                true,
                Map.of("Name", "my_file_name"),
                null
        );

        // Here, there is no WMI selection but the instantiation of the COM object with the moniker.
        QueryData queryData2 = new QueryData(
                "ROOT\\CIMV2",
                "Win32_Process",
                "my2_process",
                true,
                Map.of("Handle", "Win32_Process.Handle.2.internal"),
                Arrays.asList(
                        new QueryData.WhereEquality("Handle", ValueTypePair.FromString("123"))
                )
        );

        List<QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 3);
        CompareQueryData(queryData0, preparedQueries.get(0));
        CompareQueryData(queryData1, preparedQueries.get(1));
        CompareQueryData(queryData2, preparedQueries.get(2));
    }

    @Test
    public void Plan3_2Test() throws Exception {
        String sparql_query = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file_name
                    where {
                        ?my0_assoc rdf:type cimv2:CIM_ProcessExecutable .
                        ?my0_assoc cimv2:Dependent ?my1_process .
                        ?my0_assoc cimv2:Antecedent ?my2_file .
                        ?my1_process rdf:type cimv2:Win32_Process .
                        ?my1_process cimv2:Handle "123" .
                        ?my2_file rdf:type cimv2:CIM_DataFile .
                        ?my2_file cimv2:Name ?my_file_name .
                    }
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        DependenciesBuilder patternSparql = new DependenciesBuilder(extractor.patternsAsArray());

        QueryData queryData0 = new QueryData(
                "ROOT\\CIMV2",
                "CIM_ProcessExecutable",
                "my0_assoc",
                false,
                Map.of("Dependent", "my1_process", "Antecedent", "my2_file"),
                null
        );

        // Here, there is no WMI selection but the instantiation of the COM object with the moniker.
        QueryData queryData1 = new QueryData(
                "ROOT\\CIMV2",
                "Win32_Process",
                "my1_process",
                true,
                Map.of("Handle", "Win32_Process.Handle.1.internal"),
                Arrays.asList(
                        new QueryData.WhereEquality("Handle", ValueTypePair.FromString("123"))
                )
        );

        QueryData queryData2 = new QueryData(
                "ROOT\\CIMV2",
                "CIM_DataFile",
                "my2_file",
                true,
                Map.of("Name", "my_file_name"),
                null
        );

        List<QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 3);
        CompareQueryData(queryData0, preparedQueries.get(0));
        CompareQueryData(queryData1, preparedQueries.get(1));
        CompareQueryData(queryData2, preparedQueries.get(2));
    }

    @Test
    public void Plan3_3Test() throws Exception {
        String sparql_query = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file_name
                    where {
                        ?my1_assoc rdf:type cimv2:CIM_ProcessExecutable .
                        ?my1_assoc cimv2:Dependent ?my0_process .
                        ?my1_assoc cimv2:Antecedent ?my2_file .
                        ?my0_process rdf:type cimv2:Win32_Process .
                        ?my0_process cimv2:Handle "123" .
                        ?my2_file rdf:type cimv2:CIM_DataFile .
                        ?my2_file cimv2:Name ?my_file_name .
                    }
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        DependenciesBuilder patternSparql = new DependenciesBuilder(extractor.patternsAsArray());

        QueryData queryData0 = new QueryData(
                "ROOT\\CIMV2",
                "Win32_Process",
                "my0_process",
                false,
                null,
                Arrays.asList(
                        new QueryData.WhereEquality("Handle", ValueTypePair.FromString("123"))
                )
        );

        QueryData queryData1 = new QueryData(
                "ROOT\\CIMV2",
                "CIM_ProcessExecutable",
                "my1_assoc",
                false,
                Map.of("Antecedent", "my2_file"),
                Arrays.asList(
                        new QueryData.WhereEquality("Dependent", "my0_process")
                )
        );

        // In fact, no need to select anything because the object variable is already known.
        // However, this variable must be in the WHERE clause, so a WQL selection will work.
        QueryData queryData2 = new QueryData(
                "ROOT\\CIMV2",
                "CIM_DataFile",
                "my2_file",
                true,
                Map.of("Name", "my_file_name"),
                null
        );

        List<QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 3);
        CompareQueryData(queryData0, preparedQueries.get(0));
        CompareQueryData(queryData1, preparedQueries.get(1));
        CompareQueryData(queryData2, preparedQueries.get(2));
    }

    @Test
    public void Plan3_4Test() throws Exception {
        String sparql_query = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_process_caption
                    where {
                        ?my1_assoc rdf:type cimv2:CIM_ProcessExecutable .
                        ?my1_assoc cimv2:Dependent ?my2_process .
                        ?my1_assoc cimv2:Antecedent ?my0_file .
                        ?my2_process rdf:type cimv2:Win32_Process .
                        ?my2_process cimv2:Caption ?my_process_caption .
                        ?my0_file rdf:type cimv2:CIM_DataFile .
                        ?my0_file cimv2:Name "C:" .
                    }
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_caption"));

        DependenciesBuilder patternSparql = new DependenciesBuilder(extractor.patternsAsArray());

        // No need to select anything because the object is already known.
        QueryData queryData0 = new QueryData(
                "ROOT\\CIMV2",
                "CIM_DataFile",
                "my0_file",
                false,
                null,
                Arrays.asList(
                        new QueryData.WhereEquality("Name", ValueTypePair.FromString("C:"))
                )
        );

        QueryData queryData1 = new QueryData(
                "ROOT\\CIMV2",
                "CIM_ProcessExecutable",
                "my1_assoc",
                false,
                Map.of("Dependent", "my2_process"),
                Arrays.asList(
                        new QueryData.WhereEquality("Antecedent", "my0_file")
                )
        );

        QueryData queryData2 = new QueryData(
                "ROOT\\CIMV2",
                "Win32_Process",
                "my2_process",
                true,
                Map.of("Caption", "my_process_caption"),
                null
        );

        List<QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 3);
        CompareQueryData(queryData0, preparedQueries.get(0));
        CompareQueryData(queryData1, preparedQueries.get(1));
        CompareQueryData(queryData2, preparedQueries.get(2));
    }

    /** The class must be deduced, and there is a pattern which cannot be processed by WMI.
     *
     * @throws Exception
     */
    @Test
    public void MissingClass() throws Exception {
        String sparql_query = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?label ?caption
                    where {
                        ?process cimv2:Win32_Process.Handle "12345" .
                        ?process cimv2:Win32_Process.Caption ?caption .
                        cimv2:Win32_Process.Handle rdfs:label ?label .
                    }
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        DependenciesBuilder patternSparql = new DependenciesBuilder(extractor.patternsAsArray());

        QueryData queryData0 = new QueryData(
                "ROOT\\CIMV2",
                "Win32_Process",
                "process",
                false,
                Map.of("Caption", "caption"),
                Arrays.asList(
                        new QueryData.WhereEquality("Handle", ValueTypePair.FromString("12345"))
                )
        );

        List<QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 1);
        CompareQueryData(queryData0, preparedQueries.get(0));
    }




}
