package paquetage;

import java.util.List;

public class SparqlFrontEnd {
    /**
     * - Receives a Sparql query.
     * - Extracts BGPs.
     * - Optimizes BGP order.
     * - Fetches triples based on BGPs.
     * - Insert the triples in the repository.
     * - Run the Sparql query.
     * - Return the triples.
     */

    class TripleSomething {}
    public List<TripleSomething> Execute(String sparqlQuery) throws Exception
    {
        // But it would be faster to insert them directly in a repository.
        List<TripleSomething> ret = null;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);

        SparqlToWmi towmi = new SparqlToWmi(extractor);

        towmi.Execute();

        return ret;
    }
}
