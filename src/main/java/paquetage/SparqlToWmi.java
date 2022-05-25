package paquetage;

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
        variablesContext = new HashMap<String, String>();

        for(ObjectPattern pattern: patterns)  {
            List<WmiSelecter.KeyValue> wheres = new ArrayList<>();
            Map<String, String> selected_variables = new HashMap<>();

            // Now, split the variables of this object, between:
            // - the variables known at this stage from the previous queries,
            //   and which can be used in the "WHERE" clause,
            // - the variables which are not known yet, and returned by this WQL query.
            // The variable representing the object is selected anyway and contains the WMI relative path.
            for(ObjectPattern.KeyValue keyValue: pattern.Members) {
                String predicateName = keyValue.Predicate();
                if(! predicateName.contains("#")) {
                    throw new Exception("Invalid predicate:" + predicateName);
                }
                String shortPredicate = predicateName.split("#")[1];
                if(!keyValue.isVariable()) {
                    // If the value of the predicate is known because it is a constant.
                    WmiSelecter.KeyValue wmiKeyValue = new WmiSelecter.KeyValue(shortPredicate, keyValue.Content());
                    wheres.add(wmiKeyValue);
                }
                else {
                    // if(variablesContext.containsKey(keyValue.Predicate()))  {
                    if(variablesContext.containsKey(keyValue.Content()))  {
                        // If it is a variable is calculated in the previous queries.
                        WmiSelecter.KeyValue wmiKeyValue = new WmiSelecter.KeyValue(shortPredicate, keyValue.Content());
                        wheres.add(wmiKeyValue);
                    }
                    else {
                        selected_variables.put(shortPredicate, keyValue.Content());
                    }
                }
            }

            // The same variables might be added several times.
            for(String variable_name : selected_variables.values()) {
                variablesContext.put(variable_name, null);
            }
            // The variable which defines the object will receive a value with the execution of this WQL query.
            variablesContext.put(pattern.VariableName, null);
            if(! pattern.className.contains("#")) {
                throw new Exception("Invalid class name:" + pattern.className);
            }
            String shortClassName = pattern.className.split("#")[1];
            WmiSelecter.QueryData queryData = new WmiSelecter.QueryData(shortClassName, pattern.VariableName, selected_variables, wheres);
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
            if(true == false) {
                // TODO: If it is possible to calculate the path of the object, or if it is known
                // TODO: In Python, the path is always selected and never calculated.
                /*
                The WMI path of the variable associated to the object might be known, for example if it is selected
                from an associator, or if it is built as a string.
                In this case, WQL is not necessary. Just instantiate the COM object.
                 */

                // This gives all instances of a class. We just need one if them.
                // query_generator = "win32com.client.GetObject('winmgmts:').InstancesOf('%s')" % class_name

                // https://docs.microsoft.com/en-us/windows/win32/wmisdk/swbemservices-get
                /*
                Wbemcli.IWbemServices svc = WbemcliUtil.connectServer("ROOT\\CIMV2");
                svc.

                SWbemServices
                objWbemObject = .Get( _
                        [ ByVal strObjectPath ], _
                        [ ByVal iFlags ], _
                        [ ByVal objWbemNamedValueSet ] _)

                HRESULT GetObject(
                    [in]  const BSTR       strObjectPath,
                    [in]  long             lFlags,
                    [in]  IWbemContext     *pCtx,
                    [out] IWbemClassObject **ppObject,
                    [out] IWbemCallResult  **ppCallResult
                    );
                */
            } else {
                ArrayList<WmiSelecter.Row> rows = wmiSelecter.WqlSelect(queryData.className, queryData.mainVariable, queryData.queryColumns, queryData.queryWheres);
                int numColumns = queryData.queryColumns.size();
                for(WmiSelecter.Row row: rows) {
                    // An extra column contains the path.
                    if(row.Elements.size() != numColumns + 1) {
                        throw new Exception("Inconsistent size between returned results and columns");
                    }
                    for(Map.Entry<String, String> entry : queryData.queryColumns.entrySet()) {
                        String variableName = entry.getValue();
                        if(!variablesContext.containsKey(variableName)){
                            throw new Exception("Variable not in context");
                        }
                        variablesContext.put(variableName, row.Elements.get(variableName));
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
