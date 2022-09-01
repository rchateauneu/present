package paquetage;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;

import java.util.Iterator;
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

    private RdfSolution ExecuteQueryWithStatements(String sparqlQuery, Set<String> expectedBindings) throws Exception {
        // Now, execute the sparql query in the repository which contains the ontology
        // and the result of the WQL executions.
        RdfSolution listRows = new RdfSolution();
        TupleQuery tupleQuery = localRepositoryConnection.prepareTupleQuery(sparqlQuery);
        boolean checkedBindingsExecution = false;
        try (TupleQueryResult result = tupleQuery.evaluate()) {
            while (result.hasNext()) {
                BindingSet bindingSet = result.next();
                bindingSet.getBindingNames();
                RdfSolution.Tuple newTuple = new RdfSolution.Tuple(bindingSet);
                if(!checkedBindingsExecution) {
                    logger.debug("Selected row:" + newTuple);
                    Set<String> bindingNames = bindingSet.getBindingNames();
                    if(!expectedBindings.equals(bindingNames)) {
                        throw new RuntimeException("Different bindings:" + expectedBindings + " vs " + bindingNames);
                    }
                    if(!expectedBindings.equals(newTuple.KeySet())) {
                        throw new RuntimeException("Bindings different of row keys:" + expectedBindings + " vs " + newTuple.KeySet());
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
     * @param sparqlQuery
     * @throws Exception
     */
    public RdfSolution ExecuteQuery(String sparqlQuery) throws Exception
    {
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        logger.debug("bindings=" + extractor.bindings);

        SparqlTranslation sparqlTranslator = new SparqlTranslation(extractor);

        Solution translatedRows = sparqlTranslator.ExecuteToRows();
        logger.debug("Translated rows:" + translatedRows.size());

        List<Statement> statements = extractor.GenerateStatements(translatedRows);

        localRepositoryConnection.add(statements);

        RdfSolution listRows = ExecuteQueryWithStatements(sparqlQuery, extractor.bindings);
        return listRows;
    }

    public RdfSolution ExecuteQueryOptimized(String sparqlQuery) throws Exception {
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        logger.debug("bindings=" + extractor.bindings);

        SparqlTranslation sparqlTranslator = new SparqlTranslation(extractor);

        Solution translatedRows = sparqlTranslator.ExecuteToRowsOptimized();
        logger.debug("Translated rows:" + translatedRows.size());

        List<Statement> statements = extractor.GenerateStatements(translatedRows);

        localRepositoryConnection.add(statements);

        RdfSolution listRows = ExecuteQueryWithStatements(sparqlQuery, extractor.bindings);
        return listRows;
    }


}
