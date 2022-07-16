package paquetage;

import org.apache.log4j.Logger;

import java.util.*;


/** This implements the nested execution of queries based on a list of BGP patterns.
 */
public class SparqlTranslation {
    final static private Logger logger = Logger.getLogger(SparqlTranslation.class);
    DependenciesBuilder dependencies;
    ArrayList<GenericSelecter.Row> current_rows;
    GenericSelecter genericSelecter = new GenericSelecter();
    Set<String> bindings;

    public SparqlTranslation(SparqlBGPExtractor input_extractor) throws Exception {
        // TODO: Optimize QueryData list here. Providers are necessary.
        List<ObjectPattern> patterns = input_extractor.patternsAsArray();
        bindings = input_extractor.bindings;

        if( 1 == 2) {
            // Il faut le recreer a chaque vois meme si variableContext est le meme.
            PatternsOptimizer optimizer = new PatternsOptimizer();
            optimizer.ReorderPatterns(patterns);
        }
        dependencies = new DependenciesBuilder(patterns);
    }

    /**
     * This is used only for testing because what is important is to created RDF triples which are inserted
     * in the target repository.
     * It could use a different data type because "Row" is used for different purpose.
     */
    void CreateCurrentRow()
    {
        GenericSelecter.Row new_row = new GenericSelecter.Row();
        // It does not return only the variables in bindings, but all of them because they are
        // needed for to generate the triples for further Sparql execution
        for(Map.Entry<String, GenericSelecter.Row.ValueTypePair> pairKeyValue: dependencies.variablesContext.entrySet())
        {
            // PresentUtils.WbemPathToIri( ? The type should not be lost, especially for IRIs
            new_row.PutValueType(pairKeyValue.getKey(), pairKeyValue.getValue());
        }
        current_rows.add(new_row);
    }

    /** It uses the generated rows and the BGPs to created RDF triples which are inserted in the targer repository.
     * It would be faster to insert them on the fly.
     * It is possible to create a triple each time the context is filled with a new variable value:
     * At this moment, it is possible to find the patterns which need this variable.
     * But first, the patterns must be keyed in a special way which gives the list of triples in the BGPs
     * when one of their input variables are set. Special conditions:
     * - Some triples in the BGPs are constant and are initialised once only.
     * - Some triples might depend on two or three variables (this is not the case now, only the value can change)
     * - Each variable triple must point to its variables if it depends on several variables.
     * - Possibly have one container for BGP triples which depend on one variable only: This is implicitly
     *   what is done here. Have a variable subject or predicate requires a different processing.
     *
     * However, this function is slower but simpler because there is an intermediate stage where all combinations
     * of variables are exposed.
     */
    void GenerateTriples()
    {}

    void RowToContext(GenericSelecter.Row singleRow) throws Exception {
        for(String variableName : singleRow.KeySet()) {
            if(!dependencies.variablesContext.containsKey(variableName)){
                throw new Exception("Variable " + variableName + " from selection not in context");
            }
            // Or generates new triples for all BGP triples depending on this variable.
            dependencies.variablesContext.put(variableName, singleRow.GetValueType(variableName));
        }
    }

    void ExecuteOneLevel(int index) throws Exception
    {
        if(index == dependencies.prepared_queries.size())
        {
            // The most nested WQL query is reached. Store data then return.
            // It returns rows of key-value paris made with the variables and thei values.
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
            if(! queryData.whereVariable.isEmpty()) {
                // This error happens if this query aims at evaluating a variable whose value is already available,
                // but the presence "Where" clauses implies that there is a constraint on this variable.
                // This cannot work and indicates that the patterns are not executed in the right order.
                logger.debug("Index=" + Integer.toString(index) + " QueryData=" + queryData.toString());
                for(QueryData.WhereEquality oneWhere: queryData.whereVariable) {
                    logger.debug("    predicate=" + oneWhere.predicate + " value=" + oneWhere.value + " isvar=" + oneWhere.isVariable);
                }
                throw new Exception("Where clauses must be empty if main variable is available:" + queryData.mainVariable);
            }
            // Only the value representation is needed.
            String objectPath = dependencies.variablesContext.get(queryData.mainVariable).Value();
            GenericSelecter.Row singleRow = genericSelecter.GetObjectFromPath(objectPath, queryData, true);
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
            for(QueryData.WhereEquality kv : queryData.whereVariable) {
                // This is not strictly the same type because the value of KeyValue is:
                // - either a variable name of type string,
                // - or the context value of this variable, theoretically of any type.
                if(kv.isVariable) {
                    // Only the value representation is needed.
                    String variableValue = dependencies.variablesContext.get(kv.value).Value();
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

            ArrayList<GenericSelecter.Row> rows = genericSelecter.SelectVariablesFromWhere(queryData.className, queryData.mainVariable, queryData.queryColumns, substitutedWheres);
            // We do not have the object path so the statistics can only be updated with the class name.
            queryData.FinishSampling();

            int numColumns = queryData.queryColumns.size();
            for(GenericSelecter.Row row: rows) {
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

    public ArrayList<GenericSelecter.Row> ExecuteToRows() throws Exception
    {
        current_rows = new ArrayList<GenericSelecter.Row>();
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
        logger.debug("Rows generated:" + Long.toString(current_rows.size()));
        if(current_rows.size() > 0)
            logger.debug("Header:" + current_rows.get(0).KeySet());
        logger.debug("Context keys:" + dependencies.variablesContext.keySet());

    return current_rows;
    }

    void DryRun() throws Exception {
        // For performance evaluation and optimisation.
    }
}
