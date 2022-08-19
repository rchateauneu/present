package paquetage;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/** This wraps a repository connection, the logic of transforming a Sparql query in several WQL queries from WMI,
 * and the adding of the WMI ontology.
 */
public class RepositoryWrapper {
    final static private Logger logger = Logger.getLogger(RepositoryWrapper.class);
    private RepositoryConnection localRepositoryConnection;

    private static WmiOntology ontology = new WmiOntology("ROOT\\CIMV2", true);

    RepositoryWrapper(RepositoryConnection repositoryConnection)
    {
        this.localRepositoryConnection = repositoryConnection;
        InsertOntology();
    }

    public boolean IsValid() {
        return localRepositoryConnection != null;
    }

    public static RepositoryWrapper CreateSailRepositoryFromMemory() throws Exception {
        Repository repo = new SailRepository(new MemoryStore());
        RepositoryConnection repositoryConnect = repo.getConnection();
        return new RepositoryWrapper(repositoryConnect);
    }

    void InsertOntology() {
        logger.debug("Inserting ontology triples");
        long countInit = localRepositoryConnection.size();
        RepositoryResult<Statement> result = ontology.repositoryConnection.getStatements(null, null, null, true);
        localRepositoryConnection.add(result);
        long countEnd = localRepositoryConnection.size();
        logger.debug("Inserted " + (countEnd - countInit) + " triples from " + countInit);
    }

    void InsertTriples(List<Triple> triples) {
        for (Triple triple : triples) {
            localRepositoryConnection.add(triple.getSubject(), triple.getPredicate(), triple.getObject());
        }
    }

    /** This transforms a Sparql query into a stack of WQL-like queries,
     * which are executed and their results inserted in the repository.
     * @param sparqlQuery
     * @throws Exception
     */
    public List<GenericProvider.Row> ExecuteQuery(String sparqlQuery) throws Exception
    {
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        logger.debug("bindings=" + extractor.bindings);

        SparqlTranslation sparqlTranslator = new SparqlTranslation(extractor);

        ArrayList<GenericProvider.Row> translatedRows = sparqlTranslator.ExecuteToRows();
        logger.debug("Translated rows:" + translatedRows.size());

        List<Triple> triples = extractor.GenerateTriples(translatedRows);

        InsertTriples(triples);

        // Now, execute the sparql query in the repository which contains the ontology
        // and the result of the WQL executions.
        List<GenericProvider.Row> listRows = new ArrayList<>();
        TupleQuery tupleQuery = localRepositoryConnection.prepareTupleQuery(sparqlQuery);
        boolean checkedBindingsExecution = false;
        try (TupleQueryResult result = tupleQuery.evaluate()) {
            while (result.hasNext()) {  // iterate over the result
                BindingSet bindingSet = result.next();
                bindingSet.getBindingNames();
                GenericProvider.Row newRow = new GenericProvider.Row(bindingSet);
                if(!checkedBindingsExecution) {
                    logger.debug("Selected row:" + newRow);
                    Set<String> bindingNames = bindingSet.getBindingNames();
                    if(!extractor.bindings.equals(bindingNames)) {
                        throw new RuntimeException("Different bindings:" + extractor.bindings + " vs " + bindingNames);
                    }
                    if(!extractor.bindings.equals(newRow.KeySet())) {
                        throw new RuntimeException("Bindings different of row keys:" + extractor.bindings + " vs " + newRow.KeySet());
                    }
                    checkedBindingsExecution = true;
                }
                listRows.add(newRow);
            }
        }
        return listRows;
    }
}
