package paquetage;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This does not optimize the queries by changing the order of queried WMI classes.
 */
abstract class SparqlToWmiAbstract {
    HashMap<String, String> variablesContext;

    /** It represented the nested WQL queries.
    There is one such query for each object exposed in a Sparql query.
     */
    public List<QueryData> prepared_queries;

    /** This takes as input a list of object patterns, and assumes that each of them represents a WQL query,
     * there queries being nested into one another (top-level first).
     * The executino of WQL queries can be optimising be changing the order of the patterns list.
     * @param patterns
     * @throws Exception
     */
    public SparqlToWmiAbstract(List<ObjectPattern> patterns) throws Exception
    {
        prepared_queries = new ArrayList<QueryData>();

        // In this constructor, it is filled with all variables and null values.
        variablesContext = new HashMap<>();

        for(ObjectPattern pattern: patterns)  {
            List<QueryData.WhereEquality> wheres = new ArrayList<>();
            Map<String, String> selected_variables = new HashMap<>();

            // Now, split the variables of this object, between:
            // - the variables known at this stage from the previous queries, which can be used in the "WHERE" clause,
            // - the variables which are not known yet, and returned by this WQL query.
            // The variable representing the object is selected anyway and contains the WMI relative path.
            for(ObjectPattern.PredicateObject keyValue: pattern.Members) {
                String predicateName = keyValue.Predicate();
                String valueContent = keyValue.Content();
                if(! predicateName.contains("#")) {
                    throw new Exception("Invalid predicate:" + predicateName);
                }
                String shortPredicate = predicateName.split("#")[1];

                QueryData.WhereEquality wmiKeyValue = new QueryData.WhereEquality(shortPredicate, valueContent, keyValue.isVariable());

                if(keyValue.isVariable()) {
                    if(variablesContext.containsKey(valueContent))  {
                        // If it is a variable calculated in the previous queries, its value is known when executing.
                        wheres.add(wmiKeyValue);
                    } else {
                        selected_variables.put(shortPredicate, valueContent);
                    }
                } else {
                    // If the value of the predicate is known because it is a constant.
                    wheres.add(wmiKeyValue);
                }
            }

            // The same variables might be added several times.
            for(String variable_name : selected_variables.values()) {
                variablesContext.put(variable_name, null);
            }

            // The variable which defines the object will receive a value with the execution of this WQL query,
            // but maybe it is already known because of an association request done before.
            boolean isMainVariableAvailable = variablesContext.containsKey(pattern.VariableName);
            if(!isMainVariableAvailable) {
                variablesContext.put(pattern.VariableName, null);
            }

            if(! pattern.className.contains("#")) {
                throw new Exception("Invalid class name:" + pattern.className);
            }
            String shortClassName = pattern.className.split("#")[1];

            QueryData queryData = new QueryData(shortClassName, pattern.VariableName, isMainVariableAvailable, selected_variables, wheres);
            prepared_queries.add(queryData);
        }
        if(prepared_queries.size() != patterns.size()) {
            throw new Exception("Inconsistent QueryData creation");
        }
    }
}

public class SparqlToWmi extends SparqlToWmiAbstract {
    ArrayList<MetaSelecter.Row> current_rows;
    MetaSelecter metaSelecter;
    SparqlBGPExtractor extractor;

    public SparqlToWmi(SparqlBGPExtractor input_extractor) throws Exception {
        super(input_extractor.patternsAsArray());
        metaSelecter = new MetaSelecter();
        extractor = input_extractor;
    }

    void CreateCurrentRow()
    {
        MetaSelecter.Row new_row = new MetaSelecter.Row();
        for(String binding : extractor.bindings)
        {
            new_row.Elements.put(binding, variablesContext.get(binding));
        }
        current_rows.add(new_row);
    }

    void ExecuteOneLevel(int index) throws Exception
    {
        if(index == prepared_queries.size())
        {
            // The most nested WQL query is reached. Store data then return.
            CreateCurrentRow();
            return;
        }
        QueryData queryData = prepared_queries.get(index);
        queryData.statistics.StartSample();
        if(queryData.isMainVariableAvailable) {
            if(! queryData.queryWheres.isEmpty()) {
                throw new Exception("Where clauses should be empty if the main variable is available");
            }
            String objectPath = variablesContext.get(queryData.mainVariable);
            metaSelecter.GetVariablesFromNodePath(objectPath, queryData, variablesContext);
            queryData.statistics.FinishSample(objectPath, queryData.queryColumns.keySet());
            // New WQL query for this row only.
            ExecuteOneLevel(index + 1);
        } else {
            ArrayList<QueryData.WhereEquality> substitutedWheres = new ArrayList<>();
            for(QueryData.WhereEquality kv : queryData.queryWheres) {
                // This is not strictly the same type because the value of KeyValue is:
                // - either a variable name of type string,
                // - or the context value of this variable, theoretically of any type.
                if(kv.isVariable) {
                    String variableValue = variablesContext.get(kv.value);
                    if (variableValue == null) {
                        // This should not happen.
                        System.out.println("Value of " + kv.predicate + " variable=" + kv.value + " is null");
                    }
                    substitutedWheres.add(new QueryData.WhereEquality(kv.predicate, variableValue));
                } else {
                    substitutedWheres.add(new QueryData.WhereEquality(kv.predicate, kv.value));
                }
            }

            ArrayList<MetaSelecter.Row> rows = metaSelecter.WqlSelect(queryData.className, queryData.mainVariable, queryData.queryColumns, substitutedWheres);
            // We do not have the object path so the statistics can only be updated with the class name.
            Set<String> columnsWhere = queryData.queryWheres.stream()
                    .map(entry -> entry.predicate)
                    .collect(Collectors.toSet());
            queryData.statistics.FinishSample(queryData.className, columnsWhere);

            int numColumns = queryData.queryColumns.size();
            for(MetaSelecter.Row row: rows) {
                // An extra column contains the path.
                if(row.Elements.size() != numColumns + 1) {
                    throw new Exception("Inconsistent size between returned results " + row.Elements.size()
                            + " and columns:" + numColumns);
                }
                for(Map.Entry<String, String> entry : row.Elements.entrySet()) {
                    String variableName = entry.getKey();
                    if(!variablesContext.containsKey(variableName)){
                        throw new Exception("Variable " + variableName + " from selection not in context");
                    }
                    variablesContext.put(variableName, entry.getValue());
                }
                // New WQL query for this row.
                ExecuteOneLevel(index + 1);
            } //  Next fetched row.
        }
    }

    public ArrayList<MetaSelecter.Row> Execute() throws Exception
    {
        current_rows = new ArrayList<MetaSelecter.Row>();
        for(QueryData queryData : prepared_queries) {
            queryData.statistics.ResetAll();
        }
        ExecuteOneLevel(0);
        System.out.println("Queries levels:" + prepared_queries.size());
        System.out.println("Statistics:");
        for(int indexQueryData = 0; indexQueryData < prepared_queries.size(); ++indexQueryData) {
            QueryData queryData = prepared_queries.get(indexQueryData);
            System.out.println("Query " + indexQueryData);
            queryData.statistics.DisplayAll();
        }
        return current_rows;
    }
}
