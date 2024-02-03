package paquetage;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;

import java.util.List;
import java.util.Set;


/** This wraps a repository connection, the logic of transforming a Sparql query in several WQL queries from WMI,
 * and the adding of the WMI ontology.
 */
public class RepositoryWrapper {
    final static private Logger logger = Logger.getLogger(RepositoryWrapper.class);
    private RepositoryConnection localRepositoryConnection;

    // Load the ontology of one namespace only.
    RepositoryWrapper(String namespace)
    {
        localRepositoryConnection = WmiOntology.cloneToMemoryConnection(namespace);
    }

    // Load all namespaces.
    RepositoryWrapper()
    {
        try {
            localRepositoryConnection = WmiOntology.cloneToMemoryConnection();
        }
        catch(Exception exc) {
            throw new RuntimeException(exc);
        }
    }

    private RdfSolution executeQueryWithStatements(String sparqlQuery, Set<String> expectedBindings) throws Exception {
        // Now, execute the sparql query in the repository which contains the ontology
        // and the result of the WQL executions.
        RdfSolution listRows = new RdfSolution();
        TupleQuery tupleQuery = localRepositoryConnection.prepareTupleQuery(sparqlQuery);
        boolean checkedBindingsExecution = false;
        try (TupleQueryResult result = tupleQuery.evaluate()) {
            while (result.hasNext()) {
                BindingSet bindingSet = result.next();
                RdfSolution.Tuple newTuple = new RdfSolution.Tuple(bindingSet);
                if(!checkedBindingsExecution) {
                    logger.debug("Selected row:" + newTuple);
                    Set<String> bindingNames = bindingSet.getBindingNames();
                    if(!expectedBindings.equals(bindingNames)) {
                        throw new RuntimeException("Different bindings:" + expectedBindings + " vs " + bindingNames);
                    }
                    if(!expectedBindings.equals(newTuple.keySet())) {
                        throw new RuntimeException("Bindings different of row keys:" + expectedBindings + " vs " + newTuple.keySet());
                    }
                    checkedBindingsExecution = true;
                }
                listRows.add(newTuple);
            }
        }
        return listRows;
    }

    /** This transforms a Sparql query into a stack of WQL-like queries,
     * which are executed and their results inserted in the repository.
     *
     * TODO: Add three execution modes for optimization:
     * - Do not change the order of parsed ObjectPatterns which is the alphabetical order of subject variables.
     * - Optimise the order of ObjectPatterns by changing this order.
     * - Do not change the order but checks it is the same as the result of optimization:
     *   This will be the default and will allow to reuse all existing tests for another purpose.
     *
     * @param sparqlQuery
     * @throws Exception
     */
    public RdfSolution executeQuery(String sparqlQuery) throws Exception
    {
        SparqlBGPTreeExtractor treeExtractor = new SparqlBGPTreeExtractor(sparqlQuery);
        logger.debug("sparqlQuery=" + sparqlQuery);
        logger.debug("bindings=" + treeExtractor.bindings);

        Solution translatedRows = treeExtractor.EvaluateSolution();
        logger.debug("Translated rows:" + translatedRows.size());

        List<Statement> statements = treeExtractor.SolutionToStatements();

        localRepositoryConnection.add(statements);

        RdfSolution listRows = executeQueryWithStatements(sparqlQuery, treeExtractor.bindings);
        return listRows;
    }

}
