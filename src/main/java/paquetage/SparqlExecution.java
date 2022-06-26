package paquetage;

import java.util.*;
import java.util.stream.Collectors;


/** This implements the nested execution of queries based on a list of BGP patterns.
 */
public class SparqlExecution {
    DependenciesBuilder dependencies;
    ArrayList<MetaSelecter.Row> current_rows;
    MetaSelecter metaSelecter = new MetaSelecter();
    Set<String> bindings;

    public SparqlExecution(SparqlBGPExtractor input_extractor) throws Exception {
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
     */
    void CreateCurrentRow()
    {
        MetaSelecter.Row new_row = new MetaSelecter.Row();
        for(String binding : bindings)
        {
            new_row.Elements.put(binding, dependencies.variablesContext.get(binding));
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

    void RowToContext(MetaSelecter.Row singleRow) throws Exception {
        for(Map.Entry<String, String> entry : singleRow.Elements.entrySet()) {
            String variableName = entry.getKey();
            if(!dependencies.variablesContext.containsKey(variableName)){
                throw new Exception("Variable " + variableName + " from selection not in context");
            }
            // Or generates new triples for all BGP triples depending on this variable.
            dependencies.variablesContext.put(variableName, entry.getValue());
        }
    }

    void ExecuteOneLevel(int index) throws Exception
    {
        if(index == dependencies.prepared_queries.size())
        {
            // The most nested WQL query is reached. Store data then return.
            // THIS IS WRONG ! It should rather return RDF triples to be inserted in the target repository.
            CreateCurrentRow();
            return;
        }
        QueryData queryData = dependencies.prepared_queries.get(index);
        queryData.statistics.StartSample();
        if(queryData.isMainVariableAvailable) {
            if(! queryData.queryWheres.isEmpty()) {
                throw new Exception("Where clauses should be empty if the main variable is available");
            }
            String objectPath = dependencies.variablesContext.get(queryData.mainVariable);
            MetaSelecter.Row singleRow = metaSelecter.GetObjectFromPath(objectPath, queryData, true);
            queryData.statistics.FinishSample(objectPath, queryData.queryColumns.keySet());

            RowToContext(singleRow);
            // New WQL query for this row only.
            ExecuteOneLevel(index + 1);
        } else {
            ArrayList<QueryData.WhereEquality> substitutedWheres = new ArrayList<>();
            for(QueryData.WhereEquality kv : queryData.queryWheres) {
                // This is not strictly the same type because the value of KeyValue is:
                // - either a variable name of type string,
                // - or the context value of this variable, theoretically of any type.
                if(kv.isVariable) {
                    String variableValue = dependencies.variablesContext.get(kv.value);
                    if (variableValue == null) {
                        // This should not happen.
                        System.out.println("Value of " + kv.predicate + " variable=" + kv.value + " is null");
                    }
                    substitutedWheres.add(new QueryData.WhereEquality(kv.predicate, variableValue));
                } else {
                    substitutedWheres.add(new QueryData.WhereEquality(kv.predicate, kv.value));
                }
            }

            ArrayList<MetaSelecter.Row> rows = metaSelecter.SelectVariablesFromWhere(queryData.className, queryData.mainVariable, queryData.queryColumns, substitutedWheres);
            // We do not have the object path so the statistics can only be updated with the class name.
            Set<String> columnsWhere = queryData.queryWheres.stream()
                    .map(entry -> entry.predicate)
                    .collect(Collectors.toSet());
            queryData.statistics.FinishSample(queryData.className, columnsWhere);

            int numColumns = queryData.queryColumns.size();
            for(MetaSelecter.Row row: rows) {
                // An extra column contains the path.
                if(row.Elements.size() != numColumns + 1) {
                    /*
                    This is a hint that the values of some required variables were not found.
                    TODO: Do this once only, the result set should separately contain the header.
                    */
                    throw new Exception("Inconsistent size between returned results " + row.Elements.size()
                            + " and columns:" + numColumns);
                }
                RowToContext(row);
                // New WQL query for this row.
                ExecuteOneLevel(index + 1);
            } //  Next fetched row.
        }
    }

    public ArrayList<MetaSelecter.Row> ExecuteToRows() throws Exception
    {
        current_rows = new ArrayList<MetaSelecter.Row>();
        for(QueryData queryData : dependencies.prepared_queries) {
            queryData.statistics.ResetAll();
        }
        ExecuteOneLevel(0);
        System.out.println("Queries levels:" + dependencies.prepared_queries.size());
        System.out.println("Statistics:");
        for(int indexQueryData = 0; indexQueryData < dependencies.prepared_queries.size(); ++indexQueryData) {
            QueryData queryData = dependencies.prepared_queries.get(indexQueryData);
            System.out.println("Query " + indexQueryData);
            queryData.statistics.DisplayAll();
        }
        return current_rows;
    }


    void DryRun() throws Exception {
        // For performance evaluation and optimisation.
    }
}
