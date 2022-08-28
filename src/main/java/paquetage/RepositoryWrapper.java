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


    RepositoryWrapper(String namespace)
    {
        localRepositoryConnection = WmiOntology.CloneToMemoryConnection(namespace);
    }



    /** TODO: For performance, consider using Statement instead of Triple.
     * This might avoid this explicit loop.
     * https://rdf4j.org/javadoc/latest/org/eclipse/rdf4j/model/Triple.html
     * "Unlike Statement, a triple never has an associated context."
     *
     * @param triples
     */
    private void InsertTriples(List<Triple> triples) {
        for (Triple triple : triples) {
            localRepositoryConnection.add(triple.getSubject(), triple.getPredicate(), triple.getObject());
        }
    }

    /** This transforms a Sparql query into a stack of WQL-like queries,
     * which are executed and their results inserted in the repository.
     *
     * @param sparqlQuery
     * @throws Exception
     */
    public RdfSolution ExecuteQuery(String sparqlQuery) throws Exception
    {
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        logger.debug("bindings=" + extractor.bindings);

        // On remplace ceci.
        SparqlTranslation sparqlTranslator = new SparqlTranslation(extractor);

        Solution translatedRows = sparqlTranslator.ExecuteToRows();
        logger.debug("Translated rows:" + translatedRows.size());

        List<Triple> triples = extractor.GenerateTriples(translatedRows);

        InsertTriples(triples);

        // Now, execute the sparql query in the repository which contains the ontology
        // and the result of the WQL executions.
        RdfSolution listRows = new RdfSolution();
        TupleQuery tupleQuery = localRepositoryConnection.prepareTupleQuery(sparqlQuery);
        boolean checkedBindingsExecution = false;
        try (TupleQueryResult result = tupleQuery.evaluate()) {
            while (result.hasNext()) {
                BindingSet bindingSet = result.next();
                bindingSet.getBindingNames();
                RdfSolution.Tuple newRow = new RdfSolution.Tuple(bindingSet);
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
