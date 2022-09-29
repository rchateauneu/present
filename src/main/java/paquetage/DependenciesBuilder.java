package paquetage;

import org.apache.log4j.Logger;

import java.util.*;

public class DependenciesBuilder {
    final static private Logger logger = Logger.getLogger(DependenciesBuilder.class);
    /**
     * This never changes whatever the order of input BGPs is.
     * It is used in the recursive execution of WQL queries, to store the values of variables evaluated
     * in the lowest levels.
     */
    public HashMap<String, Solution.Row.ValueTypePair> variablesContext;

    /** It represented the nested WQL queries.
     There is one such query for each object exposed in a Sparql query.
     The order, and input and output columns, and the performance depend highly on the order of the input BGPs.
     */
    public List<QueryData> prepared_queries;

    /** This takes as input a list of object patterns, and assumes that each of them represents a WQL query,
     * the queries being nested into one another (top-level first).
     * Each ObjectPattern instances contains all the triples related to the same RDF subject.
     *
     * TODO: The execution of WQL queries could be optimised be changing the order of the patterns list.
     * @param patterns
     * @throws Exception
     */
    public DependenciesBuilder(List<ObjectPattern> patterns) throws Exception
    {
        prepared_queries = new ArrayList<>();

        // In this constructor, it is filled with all variables and null values.
        // It is built and also needed when building the dependencies, so this cannot be done in two separate steps.
        variablesContext = new HashMap<>();

        /*
        This strips the class IRI of its prefix.
        * It also does a consistency check of some predicates contain the class name as a prefix, for example
        * "CIM_Process.Caption" or "CIM_DataFile.Name".
        * These prefixes must be identical if they are present.
        * If the class is not null, they must be equal.
        * If the class is not given, it can implicitly be deduced from the predicates prefixes.
        */
        for(ObjectPattern pattern: patterns)
        {
            pattern.PreparePattern();
            List<QueryData.WhereEquality> wheres = new ArrayList<>();
            Map<String, String> selected_variables = new HashMap<>();

            // Now, split the variables of this object, between:
            // - the variables known at this stage from the previous queries, which can be used in the "WHERE" clause,
            // - the variables which are not known yet, and returned by this WQL query.
            // The variable representing the object is selected anyway and contains the WMI relative path.
            for(ObjectPattern.PredicateObjectPair predicateObjectPair: pattern.Members) {
                QueryData.WhereEquality wmiKeyValue = new QueryData.WhereEquality(
                        predicateObjectPair.ShortPredicate,
                        predicateObjectPair.ObjectContent,
                        predicateObjectPair.IsVariableObject);

                if(predicateObjectPair.IsVariableObject) {
                    if(variablesContext.containsKey(predicateObjectPair.ObjectContent))  {
                        // If it is a variable calculated in the previous queries, its value is known when executing.
                        wheres.add(wmiKeyValue);
                    } else {
                        selected_variables.put(predicateObjectPair.ShortPredicate, predicateObjectPair.ObjectContent);
                    }
                } else {
                    // If the value of the predicate is known because it is a constant.
                    wheres.add(wmiKeyValue);
                }
            }

            // The same variables might be added several times, and duplicates will be eliminated.
            for(String variable_name : selected_variables.values()) {
                variablesContext.put(variable_name, null);
            }

            // The variable which defines the object will receive a value with the execution of this WQL query,
            // but maybe it is already known because of an association request done before.
            boolean isMainVariableAvailable = variablesContext.containsKey(pattern.VariableName);
            if(!isMainVariableAvailable) {
                variablesContext.put(pattern.VariableName, null);
            }

            if(pattern.ShortClassName != null) {
                // A class name is need to run WQL queries, and also its WMI namespace.
                WmiOntology.CheckValidNamespace(pattern.CurrentNamespace);
                QueryData queryData = new QueryData(pattern.CurrentNamespace, pattern.ShortClassName, pattern.VariableName, isMainVariableAvailable, selected_variables, wheres);
                prepared_queries.add(queryData);
            }
        } // Next ObjectPattern
    }

    /**
     * This is for testing only a gives a symbolic representation of nested WQL queries
     * created from a Sparql query.
     *
     * TODO: Display an execution plan similar to RDF4J execution plan.
     *
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
