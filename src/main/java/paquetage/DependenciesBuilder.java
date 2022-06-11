package paquetage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DependenciesBuilder {
    /**
     * This bever changes whatever the order of input BGPs is.
     */
    HashMap<String, String> variablesContext;

    /** It represented the nested WQL queries.
     There is one such query for each object exposed in a Sparql query.
     */
    public List<QueryData> prepared_queries;

    /** This takes as input a list of object patterns, and assumes that each of them represents a WQL query,
     * there queries being nested into one another (top-level first).
     * The execution of WQL queries is optimised be changing the order of the patterns list.
     * @param patterns
     * @throws Exception
     */
    public DependenciesBuilder(List<ObjectPattern> patterns) throws Exception
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

    /**
     * This is for testing only a gives a symbolic representation of nested WQL queries
     * created from a Sparql query.
     * @return A multi-line string.
     */
    String SymbolicQuery() throws Exception
    {
        String result = "";
        String margin = "";
        for(int index = 0; index < prepared_queries.size(); ++index) {
            QueryData queryData = prepared_queries.get(index);
            String line = margin + queryData.BuildWqlQuery() + "\n";
            result += line;
            margin += "\t";
        }
        return result;
    }

}
