package paquetage;

import org.apache.log4j.Logger;

import java.util.*;

import static paquetage.WmiSelecter.wmiProvider;


/** This implements the nested execution of queries based on a list of BGP patterns.
 * TODO: Merge SparqlTranslation and DependenciesBuilder
 */
public class SparqlTranslation {
    final static private Logger logger = Logger.getLogger(SparqlTranslation.class);
    private DependenciesBuilder dependencies;
    private Solution solution;
    private GenericProvider genericSelecter = new GenericProvider();
    //private Set<String> bindings;

    public SparqlTranslation(SparqlBGPExtractor input_extractor) throws Exception {
        this(input_extractor.patternsAsArray());
    }

    public SparqlTranslation(List<ObjectPattern> patterns) /*throws Exception */ {
        // TODO: Optimize QueryData list here. Providers are necessary.
        //bindings = inputBindings;

        dependencies = new DependenciesBuilder(patterns);
    }

    /**
     * This is used only for testing because what is important is to created RDF triples which are inserted
     * in the target repository.
     * It could use a different data type because "Row" is used for different purpose.
     */
    void createCurrentRow()
    {
        Solution.Row new_row = new Solution.Row();
        // It does not return only the variables in bindings, but all of them because they are
        // needed to generate the statements for further Sparql execution
        for(Map.Entry<String, ValueTypePair> pairKeyValue: dependencies.variablesContext.entrySet())
        {
            // PresentUtils.WbemPathToIri( ? The type should not be lost, especially for IRIs
            String newKey = pairKeyValue.getKey();
            if(newKey == null) {
                throw new RuntimeException("Key is null, getValue=" + pairKeyValue.getValue());
            }
            new_row.putValueType(newKey, pairKeyValue.getValue());
        }
        solution.add(new_row);
    }

    void rowToContext(Solution.Row singleRow, Map<String, List<String>> variablesSynonyms) {
        for(String variableName : singleRow.keySet()) {
            if(!dependencies.variablesContext.containsKey(variableName)){
                throw new RuntimeException("Variable " + variableName + " from selection not in context");
            }
            // Or generates new statements for all BGP triples depending on this variable.
            if(variableName == null)
            {
                logger.warn("Input variableName is null. singleRow=" + singleRow);
            } else {
                ValueTypePair vtp = singleRow.getValueType(variableName);
                dependencies.variablesContext.put(variableName, vtp);

                List<String> synonyms = variablesSynonyms == null ? null : variablesSynonyms.get(variableName);
                if(synonyms != null) {
                    // For example, "rdfs:Label" => "Name", "rdfs:Comment" => "Description"
                    for (String synonymVariable : synonyms) {
                        dependencies.variablesContext.put(synonymVariable, vtp);
                    }
                }
            }
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
    void executeOneLevel(int index) //throws Exception
    {
        if(index == dependencies.preparedQueries.size())
        {
            // The most nested WQL query is reached. Store data then return.
            // It returns rows of key-value paris made with the variables and their values.
            // Later, these are used to generate triples, which are inserted in a repository,
            // and the Sparql query is run again - and now, the needed triples are here, ready to be selected.
            // In other words, they are virtually here.
            // TODO: For performance, consider calculating triples right now.
            createCurrentRow();
            return;
        }
        QueryData queryData = dependencies.preparedQueries.get(index);
        if(queryData.isMainVariableAvailable) {
            // NO NEED TO CHECK IT EACH TIME.
            if(! queryData.whereTests.isEmpty()) {
                // This happens if this query aims at evaluating a variable whose value is already available,
                // but the "Where" clauses implies that there is a constraint on this variable.
                // This translates into an extra filtering.
                logger.debug("Index=" + index + " QueryData=" + queryData);
                for(QueryData.WhereEquality oneWhere: queryData.whereTests) {
                    logger.debug("    predicate=" + oneWhere.wherePredicate + " value=" + oneWhere.whereValue.toDisplayString() + " variableName=" + oneWhere.whereVariableName);
                }
                logger.debug("CONST_OBJECT:" + queryData.mainVariable);
            }
            // Only the value representation is needed.
            String objectPath = dependencies.variablesContext.get(queryData.mainVariable).getValue();
            // FIXME: It is a pity that handlers are set twice.
            queryData.setHandlers(true);

            if(queryData.queryVariableColumns.isEmpty())  {
                queryData.startSampling();
                Solution.Row singleRow = genericSelecter.getObjectFromPath(objectPath, queryData);
                queryData.finishSampling(objectPath);

                if (singleRow == null) {
                    // Object does not exist or maybe a CIM_FataFile is protected, or a CIM_Process exited ?
                    // FIXME: Maybe this is not an error but a normal behaviour, so should not display an error.
                    logger.error("Cannot get row for objectPath=" + objectPath);
                } else {
                    rowToContext(singleRow, queryData.variablesSynonyms);
                    // New WQL query for this row only.
                    executeOneLevel(index + 1);
                }
            } else {
                if(!queryData.queryColumns.isEmpty()) {
                    throw new RuntimeException("No constant predicates with Variable predicate. queryData.queryColumns=" + queryData.queryColumns);
                }
                if(queryData.queryVariableColumns.size() != 1) {
                    throw new RuntimeException("Variable predicate must be unique (Now). Columns=" + queryData.queryVariableColumns.keySet());
                }
                Map.Entry<String,String> entry = queryData.queryVariableColumns.entrySet().iterator().next();
                String predicateVariable = entry.getKey();
                String valueVariable = entry.getValue();

                Map<String, WmiProvider.WmiClass> classes = wmiProvider.classesMap(queryData.namespace);
                WmiProvider.WmiClass wmiClass = classes.get(queryData.className);
                Set<String> allClassColumns = wmiClass.classProperties.keySet();
                logger.debug("queryData.mainVariable=" + queryData.mainVariable + " allClassColumns=" + allClassColumns);

                for(String onePredicate : allClassColumns) {
                    logger.debug("onePredicate=" + onePredicate + " valueVariable=" + valueVariable);
                    Map<String, String> subQueryColumns = Map.of(onePredicate, valueVariable);
                    Solution.Row returnRow = queryData.classGetter.getSingleObject(objectPath, queryData.mainVariable, subQueryColumns);
                    // This adds the value of the column name.
                    returnRow.putString(predicateVariable, onePredicate);
                    logger.debug("returnRow=" + returnRow);
                    rowToContext(returnRow, queryData.variablesSynonyms);
                    executeOneLevel(index + 1);
                }
            }
        } else {
            ArrayList<QueryData.WhereEquality> substitutedWheres = new ArrayList<>();
            for(QueryData.WhereEquality kv : queryData.whereTests) {
                // This is not strictly the same type because the value of KeyValue is:
                // - either a variable name of type string,
                // - or the context value of this variable, theoretically of any type.
                if(kv.whereVariableName != null) {
                    // Only the value representation is needed.
                    ValueTypePair pairValue = dependencies.variablesContext.get(kv.whereVariableName);
                    if(pairValue == null) {
                        throw new RuntimeException("Null value for:" + kv.whereVariableName);
                    }
                    substitutedWheres.add(new QueryData.WhereEquality(kv.wherePredicate, pairValue));
                } else {
                    // No change because the "where" value is not a variable.
                    substitutedWheres.add(kv);
                }
            }

            queryData.startSampling();
            List<QueryData.WhereEquality> oldWheres = queryData.swapWheres(substitutedWheres);
            Solution rows = genericSelecter.selectVariablesFromWhere(queryData, true);
            // restore to patterns wheres clauses (that is, with variable values).
            queryData.swapWheres(oldWheres);
            // We do not have the object path so the statistics can only be updated with the class name.
            queryData.finishSampling();

            int numColumns = queryData.queryColumns.size();
            for(Solution.Row row : rows) {
                // An extra column contains the path.
                if(row.elementsSize() != numColumns + 1) {
                    /*
                    This is a hint that the values of some required variables were not found.
                    TODO: Do this once only, the result set should separately contain the header.
                    */
                    throw new RuntimeException("Inconsistent size between returned results " + row.elementsSize()
                            + " and columns:" + numColumns + " columns=" + queryData.queryColumns.keySet()
                            + " row.KeySet()=" + row.keySet());
                }
                rowToContext(row, queryData.variablesSynonyms);
                // New WQL query for this row.
                executeOneLevel(index + 1);
            } //  Next fetched row.
        }
    }

    /** TODO: This should not return the same "Row" as ExecuteQuery because here, the Row are created by this ...
     * TODO: ... local code, not by the Sparql engine. This is confusing. */
    public Solution executeToRows() //throws Exception
    {
        solution = new Solution();
        for(QueryData queryData : dependencies.preparedQueries) {
            queryData.resetStatistics();
        }
        if(!dependencies.preparedQueries.isEmpty()) {
            executeOneLevel(0);
        }
        logger.debug("Queries levels:" + dependencies.preparedQueries.size());
        logger.debug("Statistics:");
        for(int indexQueryData = 0; indexQueryData < dependencies.preparedQueries.size(); ++indexQueryData) {
            QueryData queryData = dependencies.preparedQueries.get(indexQueryData);
            logger.debug("Query " + indexQueryData);
            queryData.displayStatistics();
        }
        logger.debug("Rows generated:" + solution.size());
        logger.debug("Header:" + solution.header());
        logger.debug("Context keys:" + dependencies.variablesContext.keySet());

    return solution;
    }

}

