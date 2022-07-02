package paquetage;

import org.eclipse.rdf4j.model.Triple;

import java.util.ArrayList;
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


    public void Execute(String sparqlQuery) throws Exception
    {
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);

        SparqlExecution toRows = new SparqlExecution(extractor);

        ArrayList<GenericSelecter.Row> rows = toRows.ExecuteToRows();

        List<Triple> triples = extractor.GenerateTriples(rows);

        RepositoryWrapper repositoryWrapper = new RepositoryWrapper();
        repositoryWrapper.InsertTriples(triples);
    }
}
