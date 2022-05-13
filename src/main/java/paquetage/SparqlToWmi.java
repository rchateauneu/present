package paquetage;

import java.util.*;

public class SparqlToWmi {
    SparqlBGPExtractor extractor;
    ArrayList<WmiSelecter.Row> current_rows;
    WmiSelecter wmiSelecter;
    //List<ObjectPattern> patterns_array;
    HashMap<String, String> variablesContext;

    /**
     * To be used when reconstructing a WQL query.
     */
    class QueryData {
        String className;
        //ObjectPattern pattern;
        List<String> queryColumns;
        List<WmiSelecter.KeyValue> queryWheres;

        QueryData(String wmiClassName, List<String> columns, List<WmiSelecter.KeyValue> wheres) {
            className = wmiClassName;
            //pattern = sorted_pattern;
            queryColumns = columns;
            queryWheres = wheres;
        }
    }
    List<QueryData> prepared_queries;

    public SparqlToWmi(SparqlBGPExtractor input_extractor)
    {
        extractor = input_extractor;
        prepared_queries = new ArrayList<QueryData>();
        wmiSelecter = new WmiSelecter();
        variablesContext = new HashMap<String, String>();

        for(ObjectPattern pattern: extractor.patternsAsArray())  {
            List<WmiSelecter.KeyValue> wheres = new ArrayList<>();
            List<String> selected_variables = new ArrayList<>();

            // Now, split the variables of this object,
            // between:
            // - the variables known at this stage from the previous queries,
            //   and which can be used in the "WHERE" clause,
            // - the variables which are not known yet, and returned by this WQL query.
            for(ObjectPattern.KeyValue keyValue: pattern.Members) {
                if(!keyValue.isVariable()) {
                    // If the value of the predicate is known because it is a constant.
                    WmiSelecter.KeyValue wmiKeyValue = new WmiSelecter.KeyValue(keyValue.Predicate(), keyValue.Content());
                    wheres.add(wmiKeyValue);
                }
                else {
                    if(variablesContext.containsKey(keyValue.Predicate()))  {
                        // If it is a vriable is calculated in the previous queries.
                        WmiSelecter.KeyValue wmiKeyValue = new WmiSelecter.KeyValue(keyValue.Predicate(), keyValue.Content());
                        wheres.add(wmiKeyValue);
                    }
                    else {
                        selected_variables.add(keyValue.Content());
                    }
                }
            }

            // The same variables might be added several times.
            for(String variable_name : selected_variables) {
                variablesContext.put(variable_name, null);
            }
            QueryData queryData = new QueryData(pattern.className, selected_variables, wheres);
            prepared_queries.add(queryData);
        }
    }

    void CreateCurrentRow()
    {
        WmiSelecter.Row new_row = wmiSelecter.new Row();
        for(String binding : extractor.bindings)
        {
            new_row.Elements.add(variablesContext.get(binding));
        }
        current_rows.add(new_row);
    }

    void ExecuteOneLevel(int index)
    {
        if(index == extractor.patternsMap.size())
        {
            CreateCurrentRow();
        }
        else
        {
            ExecuteOneLevel(index + 1);
            WmiSelecter wmiSelecter = new WmiSelecter();
            QueryData queryData = prepared_queries.get(index);
            ArrayList<WmiSelecter.Row> rows = wmiSelecter.Select(queryData.className, queryData.queryColumns, queryData.queryWheres);
        }
    }

    public ArrayList<WmiSelecter.Row> Execute()
    {
        wmiSelecter = new WmiSelecter();
        current_rows = new ArrayList<WmiSelecter.Row>();

        ExecuteOneLevel(0);

        return current_rows;
    }
}
