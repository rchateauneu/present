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
        System.out.println("expected.className=" + expected.className);
        System.out.println("actual.className=" + actual.className);
        Assert.assertEquals(expected.className, actual.className);
        Assert.assertEquals(expected.queryColumns.size(), actual.queryColumns.size());
        for(int index = 0; index < expected.queryColumns.size(); ++index) {
            Assert.assertEquals(expected.queryColumns.get(index), actual.queryColumns.get(index));
        }
        Assert.assertEquals(expected.queryWheres.size(), actual.queryWheres.size());
        for(int index = 0; index < expected.queryWheres.size(); ++index) {
            WmiSelecter.KeyValue kva = expected.queryWheres.get(index);
            WmiSelecter.KeyValue kvb = actual.queryWheres.get(index);
            Assert.assertEquals(kva.key, kvb.key);
            Assert.assertEquals(kva.value, kvb.value);
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
        Assert.assertEquals("Select my_process from Win32_Process where Handle = \"123\"\n", symbolicQuery);
    }

    public void SymbolicQuery2Test() throws Exception {
        ObjectPattern objectPattern = new ObjectPattern(
                "my_process", WmiOntology.survol_url_prefix + "Win32_Process");
        objectPattern.AddKeyValue(WmiOntology.survol_url_prefix + "Handle", false,"123");

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(Arrays.asList(objectPattern));
        String symbolicQuery = patternSparql.SymbolicQuery();
        Assert.assertEquals("Select my_process from Win32_Process where Handle = \"123\"", symbolicQuery);
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
                Arrays.asList("my_process"),
                Arrays.asList(new WmiSelecter.KeyValue("Handle", "123")));

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
                Arrays.asList("my_directory"),
                Arrays.asList(new WmiSelecter.KeyValue("Name", "C:")));

        List<WmiSelecter.QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 1);
        CompareQueryData(queryData0, preparedQueries.get(0));
    }

    @Test
    public void Plan2Test() throws Exception {
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
                Arrays.asList("my_process_name", "my_process_handle"),
                Arrays.asList(
                        new WmiSelecter.KeyValue("Handle", "my_process_handle"),
                        new WmiSelecter.KeyValue("Name", "my_process_name")
                        ));

        List<WmiSelecter.QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 1);
        CompareQueryData(queryData0, preparedQueries.get(0));
    }

    @Test
    public void Plan3Test() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_file_name
            where {
                ?my_assoc rdf:type cim:CIM_ProcessExecutable .
                ?my_assoc cim:Dependent ?my_process .
                ?my_assoc cim:Antecedent ?my_file .
                ?my_process rdf:type cim:Win32_Process .
                ?my_process cim:Handle "123" .
                ?my_file rdf:type cim:CIM_DataFile .
                ?my_file cim:Name ?my_file_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        SparqlToWmiPlan patternSparql = new SparqlToWmiPlan(extractor.patternsAsArray());

        WmiSelecter.QueryData queryData0 = new WmiSelecter.QueryData(
                "CIM_DataFile",
                Arrays.asList("my_file", "my_file_name"),
                null);

        WmiSelecter.QueryData queryData1 = new WmiSelecter.QueryData(
                "CIM_ProcessExecutable",
                Arrays.asList("my_assoc", "Dependent"),
                Arrays.asList(
                        new WmiSelecter.KeyValue("Antecedent", "my_file")
                ));

        // Here, there is no WMI selection but the instantiation of the COM object with the moniker.
        WmiSelecter.QueryData queryData2 = new WmiSelecter.QueryData(
                "Win32_Process",
                Arrays.asList("my_process", "my_file_name"),
                null);

        List<WmiSelecter.QueryData> preparedQueries = patternSparql.prepared_queries;
        Assert.assertEquals(preparedQueries.size(), 3);
        CompareQueryData(preparedQueries.get(0), queryData0);
        CompareQueryData(preparedQueries.get(1), queryData1);
        CompareQueryData(preparedQueries.get(2), queryData2);
    }
}
