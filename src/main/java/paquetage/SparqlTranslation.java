package paquetage;

import org.apache.log4j.Logger;

import java.util.*;


/** This implements the nested execution of queries based on a list of BGP patterns.
 */
public class SparqlTranslation {
    final static private Logger logger = Logger.getLogger(SparqlTranslation.class);
    private DependenciesBuilder dependencies;
    private Solution solution;
    private GenericProvider genericSelecter = new GenericProvider();
    private Set<String> bindings;

    public SparqlTranslation(SparqlBGPExtractor input_extractor) throws Exception {
        // TODO: Optimize QueryData list here. Providers are necessary.
        List<ObjectPattern> patterns = input_extractor.patternsAsArray();
        bindings = input_extractor.bindings;

        dependencies = new DependenciesBuilder(patterns);
    }

    /**
     * This is used only for testing because what is important is to created RDF triples which are inserted
     * in the target repository.
     * It could use a different data type because "Row" is used for different purpose.
     */
    void CreateCurrentRow()
    {
        Solution.Row new_row = new Solution.Row();
        // It does not return only the variables in bindings, but all of them because they are
        // needed to generate the statements for further Sparql execution
        for(Map.Entry<String, Solution.Row.ValueTypePair> pairKeyValue: dependencies.variablesContext.entrySet())
        {
            // PresentUtils.WbemPathToIri( ? The type should not be lost, especially for IRIs
            new_row.PutValueType(pairKeyValue.getKey(), pairKeyValue.getValue());
        }
        solution.add(new_row);
    }

    void RowToContext(Solution.Row singleRow) throws Exception {
        for(String variableName : singleRow.KeySet()) {
            if(!dependencies.variablesContext.containsKey(variableName)){
                throw new Exception("Variable " + variableName + " from selection not in context");
            }
            // Or generates new statements for all BGP triples depending on this variable.
            dependencies.variablesContext.put(variableName, singleRow.GetValueType(variableName));
        }
    }

    /***
     *
     * TODO: Optimisation: If a "QueryData" does not depend from the lowest-level one,
     * TODO: then it does not need to be re-executed.
     * TODO: In other words, if "SwapWheres" does not change anything, then the previous rows can be reused ???
     * Ce n'est pas vraiment le concept, mais en fait on veut optimiser ceci:
     * where {
     *     ?_2_tcp_connection standard_cimv2:MSFT_NetTCPConnection.OwningProcess ?owning_process .
     *     ?_1_process cimv2:Win32_Process.ProcessId ?owning_process .
     *     ?_1_process cimv2:Win32_Process.Name ?process_name .
     * }
     * On pourrait dans certains cas supprimer un "where" dans une requete interieure, s'il porte sur une colonne
     * ou la plupart des rows seront retournees. Dans cas, on n'executerait la requete interieure qu'une seule fois.
     *
     * Pour commencer, voir s'il y a des cas ou on execute plusieurs fois une requete interieure pour rien. Exemple:
     * where {
     *     ?a propa1 ?vala
     *     ?b propb1 ?valb
     *     ?c propc1 ?vala
     *     ?c propc2 ?valb
     * }
     * Execution pour un ordre donne:
     * select propa1 from a
     *     select propb1 from b
     *         select propc1, propc2 from c where propc1=propa1 and propc2=propb1
     * ... devient alors:
     * select propa1 from a
     * select propb1 from b
     *     select propc1, propc2 from c where propc1=propa1 and propc2=propb1
     *
     * Execution pour un autre ordre:
     * select propc1, propc2
     *     select propa1 from a where propc1=propa1
     *         select propb1 from b where propc2=propb1
     *
     * ... devient alors:
     * select propc1, propc2
     *     select propa1 from a where propc1=propa1
     *     select propb1 from b where propc2=propb1
     *
     * Il faut integrer cette logique dans le calcul du meilleur ordre possible.
     * TODO: S'inspirer du execution plan de Sparql.
     *
     * @param index
     * @throws Exception
     */
    void ExecuteOneLevel(int index) throws Exception
    {
        if(index == dependencies.prepared_queries.size())
        {
            // The most nested WQL query is reached. Store data then return.
            // It returns rows of key-value paris made with the variables and their values.
            // Later, these are used to generate triples, which are inserted in a repository,
            // and the Sparql query is run again - and now, the needed triples are here, ready to be selected.
            // In other words, they are virtually here.
            // TODO: For performance, consider calculating triples right now.
            CreateCurrentRow();
            return;
        }
        QueryData queryData = dependencies.prepared_queries.get(index);
        queryData.StartSampling();
        if(queryData.isMainVariableAvailable) {
            if(! queryData.whereTests.isEmpty()) {
                // This error happens if this query aims at evaluating a variable whose value is already available,
                // but the presence "Where" clauses implies that there is a constraint on this variable.
                // This cannot work and indicates that the patterns are not executed in the right order.
                logger.debug("Index=" + Integer.toString(index) + " QueryData=" + queryData.toString());
                for(QueryData.WhereEquality oneWhere: queryData.whereTests) {
                    logger.debug("    predicate=" + oneWhere.predicate + " value=" + oneWhere.value + " isvar=" + oneWhere.isVariable);
                }
                throw new Exception("Where clauses must be empty if main variable is available:" + queryData.mainVariable);
            }
            // Only the value representation is needed.
            String objectPath = dependencies.variablesContext.get(queryData.mainVariable).Value();
            Solution.Row singleRow = genericSelecter.GetObjectFromPath(objectPath, queryData, true);
            queryData.FinishSampling(objectPath);

            if(singleRow == null)
            {
                // Object does not exist: Maybe a CIM_FataFile is protected, or a CIM_Process exited ?
                logger.error("Cannot get row for objectPath=" + objectPath);
            }
            else {
                RowToContext(singleRow);
                // New WQL query for this row only.
                ExecuteOneLevel(index + 1);
            }
        } else {
            ArrayList<QueryData.WhereEquality> substitutedWheres = new ArrayList<>();
            for(QueryData.WhereEquality kv : queryData.whereTests) {
                // This is not strictly the same type because the value of KeyValue is:
                // - either a variable name of type string,
                // - or the context value of this variable, theoretically of any type.
                if(kv.isVariable) {
                    // Only the value representation is needed.
                    Solution.Row.ValueTypePair pairValue = dependencies.variablesContext.get(kv.value);
                    if(pairValue == null) {
                        throw new RuntimeException("Null value for:" + kv.value);
                    }
                    String variableValue = pairValue.Value();
                    if (variableValue == null) {
                        // This should not happen.
                        logger.error("Value of " + kv.predicate + " variable=" + kv.value + " is null");
                    }
                    substitutedWheres.add(new QueryData.WhereEquality(kv.predicate, variableValue));
                } else {
                    // No change because the "where" value is not a variable.
                    substitutedWheres.add(kv);
                }
            }

            List<QueryData.WhereEquality> oldWheres = queryData.SwapWheres(substitutedWheres);
            Solution rows = genericSelecter.SelectVariablesFromWhere(queryData, true);
            // restore to patterns wheres clauses (that is, with variable values).
            queryData.SwapWheres(oldWheres);
            // We do not have the object path so the statistics can only be updated with the class name.
            queryData.FinishSampling();

            int numColumns = queryData.queryColumns.size();
            for(Solution.Row row : rows) {
                // An extra column contains the path.
                if(row.ElementsSize() != numColumns + 1) {
                    /*
                    This is a hint that the values of some required variables were not found.
                    TODO: Do this once only, the result set should separately contain the header.
                    */
                    throw new Exception("Inconsistent size between returned results " + row.ElementsSize()
                            + " and columns:" + numColumns);
                }
                RowToContext(row);
                // New WQL query for this row.
                ExecuteOneLevel(index + 1);
            } //  Next fetched row.
        }
    }

    /** TODO: This should not return the same "Row" as ExecuteQuery because here, the Row are created by this ...
     * TODO: ... local code, not by the Sparql engine. This is confusing. */
    public Solution ExecuteToRows() throws Exception
    {
        solution = new Solution();
        for(QueryData queryData : dependencies.prepared_queries) {
            queryData.ResetStatistics();
        }
        if(!dependencies.prepared_queries.isEmpty()) {
            ExecuteOneLevel(0);
        }
        logger.debug("Queries levels:" + dependencies.prepared_queries.size());
        logger.debug("Statistics:");
        for(int indexQueryData = 0; indexQueryData < dependencies.prepared_queries.size(); ++indexQueryData) {
            QueryData queryData = dependencies.prepared_queries.get(indexQueryData);
            logger.debug("Query " + indexQueryData);
            queryData.DisplayStatistics();
        }
        logger.debug("Rows generated:" + solution.size());
        logger.debug("Header:" + solution.Header);
        logger.debug("Context keys:" + dependencies.variablesContext.keySet());

    return solution;
    }

    public Solution ExecuteToRowsOptimized() throws Exception {
        return null;
    }

}
