package paquetage;

import COM.Wbemcli;

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
    public List<WmiSelecter.QueryData> prepared_queries;

    /** This takes as input a list of object patterns, and assumes that each of them represents a WQL query,
     * there queries being nested into one another (top-level first).
     * The executino of WQL queries can be optimising be changing the order of the patterns list.
     * @param patterns
     * @throws Exception
     */
    public SparqlToWmiAbstract(List<ObjectPattern> patterns) throws Exception
    {
        prepared_queries = new ArrayList<WmiSelecter.QueryData>();

        // In this constructor, it is filled with all variables and null values.
        variablesContext = new HashMap<>();

        for(ObjectPattern pattern: patterns)  {
            List<WmiSelecter.WhereEquality> wheres = new ArrayList<>();
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

                WmiSelecter.WhereEquality wmiKeyValue = new WmiSelecter.WhereEquality(shortPredicate, valueContent, keyValue.isVariable());

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

            WmiSelecter.QueryData queryData = new WmiSelecter.QueryData(shortClassName, pattern.VariableName, isMainVariableAvailable, selected_variables, wheres);
            prepared_queries.add(queryData);
        }
        if(prepared_queries.size() != patterns.size()) {
            throw new Exception("Inconsistent QueryData creation");
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

    /** This returns the object with the given WMI path. At least the specified properties must be set.
     * This is slow and can be optimized with different ways:
     * - Take from WMI only the required members.
     * - Use a cache keyed by the path, if the same object is requested several times.
     * - Extract the property values from the path if these are keys. For example a filename or process handle.
     * - Calculate directory the properties without calling WMI.
     *
     * @param objectPath A string like '\\LAPTOP-R89KG6V1\root\cimv2:Win32_Process.Handle="22292"'
     * @param columns Set of properties, like ["Handle", "Caption"]
     * @return
     * @throws Exception
     */
    Wbemcli.IWbemClassObject PathToNode(String objectPath, Set<String> columns) throws Exception
    {
        Wbemcli.IWbemClassObject objectNode = null;
        try {
            if (false) {
                objectNode = wmiSelecter.GetObjectNode(objectPath);
            } else {
                if (false) {
                    // This works but this is not faster.
                    objectNode = wmiSelecter.GetObjectNodePartial(objectPath, columns);
                } else {
                    // Not faster if all objects have different path.
                    objectNode = wmiSelecter.GetObjectNodeCached(objectPath);
                }
            }
        } catch(com.sun.jna.platform.win32.COM.COMException exc)  {
            System.out.println("objectPath=" + objectPath + " Caught=" + exc);
        }
        return objectNode;
    }

    void ExecuteOneLevel(int index) throws Exception
    {
        if(index == prepared_queries.size())
        {
            // The most nested WQL query is reached. Store data then return.
            CreateCurrentRow();
            return;
        }
        WmiSelecter.QueryData queryData = prepared_queries.get(index);
        queryData.statistics.Start();
        if(queryData.isMainVariableAvailable) {
            if(! queryData.queryWheres.isEmpty()) {
                throw new Exception("Where clauses should be empty if the main variable is available");
            }
            String objectPath = variablesContext.get(queryData.mainVariable);
            Set<String> columns = queryData.queryColumns.keySet();
            Wbemcli.IWbemClassObject objectNode = PathToNode(objectPath, columns);

            // Now takes the values needed from the members of this object.
            for( Map.Entry<String, String> entry: queryData.queryColumns.entrySet()) {
                String variableName = entry.getValue();
                if(!variablesContext.containsKey(variableName)){
                    throw new Exception("Variable " + variableName + " from object not in context");
                }
                String objectProperty = objectNode == null
                        ? "Object " + objectPath + " is null"
                        : wmiSelecter.GetObjectProperty(objectNode, entry.getKey());
                variablesContext.put(variableName, objectProperty);
            }
            queryData.statistics.Update(objectPath, columns);
            // New WQL query for this row only.
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
                        // This should not happen.
                        System.out.println("Value of " + kv.predicate + " variable=" + kv.value + " is null");
                    }
                    substitutedWheres.add(new WmiSelecter.WhereEquality(kv.predicate, variableValue));
                } else {
                    substitutedWheres.add(new WmiSelecter.WhereEquality(kv.predicate, kv.value));
                }
            }

            ArrayList<WmiSelecter.Row> rows = wmiSelecter.WqlSelect(queryData.className, queryData.mainVariable, queryData.queryColumns, substitutedWheres);
            // We do not have the object path so the statistics can only be updated with the class name.
            Set<String> columnsWhere = queryData.queryWheres.stream()
                    .map(entry -> entry.predicate)
                    .collect(Collectors.toSet());
            queryData.statistics.Update(queryData.className, columnsWhere);

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
            } //  Next fetched row.
        }
    }

    public ArrayList<WmiSelecter.Row> Execute() throws Exception
    {
        current_rows = new ArrayList<WmiSelecter.Row>();
        for(WmiSelecter.QueryData queryData : prepared_queries) {
            queryData.statistics.Reset();
        }
        ExecuteOneLevel(0);
        System.out.println("Queries levels:" + prepared_queries.size());
        System.out.println("Statistics:");
        for(int indexQueryData = 0; indexQueryData < prepared_queries.size(); ++indexQueryData) {
            WmiSelecter.QueryData queryData = prepared_queries.get(indexQueryData);
            System.out.println("Query " + indexQueryData);
            queryData.statistics.Display();
        }
        return current_rows;
    }
}
