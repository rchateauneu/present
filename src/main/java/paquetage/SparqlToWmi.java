package paquetage;

import COM.Wbemcli;

import java.util.*;

/**
 * This does not optimize the queries by changing the order of queried WMI classes.
 */
abstract class SparqlToWmiAbstract {
    HashMap<String, String> variablesContext;

    public List<WmiSelecter.QueryData> prepared_queries;

    public SparqlToWmiAbstract(List<ObjectPattern> patterns) throws Exception
    {
        prepared_queries = new ArrayList<WmiSelecter.QueryData>();

        // In this constructor, it is filled with all variables and null values.
        variablesContext = new HashMap<String, String>();

        for(ObjectPattern pattern: patterns)  {
            List<WmiSelecter.WhereEquality> wheres = new ArrayList<>();
            Map<String, String> selected_variables = new HashMap<>();

            // Now, split the variables of this object, between:
            // - the variables known at this stage from the previous queries,  which can be used in the "WHERE" clause,
            // - the variables which are not known yet, and returned by this WQL query.
            // The variable representing the object is selected anyway and contains the WMI relative path.
            for(ObjectPattern.PredicateObject keyValue: pattern.Members) {
                String predicateName = keyValue.Predicate();
                String valueContent = keyValue.Content();
                if(! predicateName.contains("#")) {
                    throw new Exception("Invalid predicate:" + predicateName);
                }
                String shortPredicate = predicateName.split("#")[1];

                WmiSelecter.WhereEquality wmiKeyValue = new WmiSelecter.WhereEquality(shortPredicate, valueContent, keyValue.isVariable());

                if(keyValue.isVariable()) {
                    if(variablesContext.containsKey(valueContent))  {
                        // If it is a variable calculated in the previous queries, its value is known when executing.
                        wheres.add(wmiKeyValue);
                    } else {
                        selected_variables.put(shortPredicate, valueContent);
                    }
                }
                else {
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

            WmiSelecter.QueryData queryData = new WmiSelecter.QueryData(shortClassName, pattern.VariableName, isMainVariableAvailable, selected_variables, wheres);
            prepared_queries.add(queryData);
        }
    }
}

public class SparqlToWmi extends SparqlToWmiAbstract {
    ArrayList<WmiSelecter.Row> current_rows;
    WmiSelecter wmiSelecter;
    SparqlBGPExtractor extractor;

    public SparqlToWmi(SparqlBGPExtractor input_extractor) throws Exception {
        super(input_extractor.patternsAsArray());
        wmiSelecter = new WmiSelecter();
        extractor = input_extractor;
    }

    void CreateCurrentRow()
    {
        WmiSelecter.Row new_row = wmiSelecter.new Row();
        for(String binding : extractor.bindings)
        {
            new_row.Elements.put(binding, variablesContext.get(binding));
        }
        current_rows.add(new_row);
    }

    void ExecuteOneLevel(int index) throws Exception
    {
        if(index == extractor.patternsMap.size())
        {
            CreateCurrentRow();
        }
        else
        {
            WmiSelecter.QueryData queryData = prepared_queries.get(index);
            if(queryData.isMainVariableAvailable) {
                if(queryData.queryWheres.size() > 0) {
                    throw new Exception("Where clauses should be empty if the main variable is available");
                }
                String objectPath = variablesContext.get(queryData.mainVariable);
                if(objectPath == null) {
                    throw new Exception("Value for " + queryData.mainVariable + " should not be null");
                }

                Wbemcli.IWbemClassObject objectNode = wmiSelecter.GetObjectNode(objectPath);

                // Now takes the values needed from the members of this object.
                for( Map.Entry<String, String> entry: queryData.queryColumns.entrySet()) {
                    String variableName = entry.getValue();
                    if(!variablesContext.containsKey(variableName)){
                        throw new Exception("Variable " + variableName + " from object not in context");
                    }
                    variablesContext.put(variableName, wmiSelecter.GetObjectProperty( objectNode, entry.getKey()));
                }
                // New WQL query for this row.
                ExecuteOneLevel(index + 1);

            } else {
                ArrayList<WmiSelecter.WhereEquality> substitutedWheres = new ArrayList<>();
                for(WmiSelecter.WhereEquality kv : queryData.queryWheres) {
                    // This is not strictly the same type because the value of KeyValue is:
                    // - either a variable name of type string,
                    // - or the context value of this variable, theoretically of any type.
                    if(kv.isVariable) {
                        String variableValue = variablesContext.get(kv.value);
                        if (variableValue == null) {
                            System.out.println("Value of " + kv.predicate + " variable=" + kv.value + " is null");
                        }
                        substitutedWheres.add(new WmiSelecter.WhereEquality(kv.predicate, variableValue));
                    } else {
                        substitutedWheres.add(new WmiSelecter.WhereEquality(kv.predicate, kv.value));
                    }
                }
                ArrayList<WmiSelecter.Row> rows = wmiSelecter.WqlSelect(queryData.className, queryData.mainVariable, queryData.queryColumns, substitutedWheres);
                int numColumns = queryData.queryColumns.size();
                for(WmiSelecter.Row row: rows) {
                    // An extra column contains the path.
                    if(row.Elements.size() != numColumns + 1) {
                        throw new Exception("Inconsistent size between returned results and columns");
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
                }
            }
        }
    }

    public ArrayList<WmiSelecter.Row> Execute() throws Exception
    {
        current_rows = new ArrayList<WmiSelecter.Row>();

        ExecuteOneLevel(0);

        return current_rows;
    }
}
