package paquetage;

import org.apache.log4j.Logger;

import java.util.*;

public class DependenciesBuilder {
    final static private Logger logger = Logger.getLogger(DependenciesBuilder.class);
    /**
     * This never changes whatever the order of input BGPs is.
     */
    public HashMap<String, GenericProvider.Row.ValueTypePair> variablesContext;

    /** It represented the nested WQL queries.
     There is one such query for each object exposed in a Sparql query.
     The order, and input and output columns, and the performance depend highly on the order of the input BGPs.
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
        for(ObjectPattern pattern: patterns)  {
            List<QueryData.WhereEquality> wheres = new ArrayList<>();
            Map<String, String> selected_variables = new HashMap<>();

            // This will always be null if the properties are not prefixed with the class name.
            // This is OK of the type is given with a triple with rdf:type as predicate.
            String deducedClassName = null;

            // Now, split the variables of this object, between:
            // - the variables known at this stage from the previous queries, which can be used in the "WHERE" clause,
            // - the variables which are not known yet, and returned by this WQL query.
            // The variable representing the object is selected anyway and contains the WMI relative path.
            for(ObjectPattern.PredicateObjectPair keyValue: pattern.Members) {
                String predicateName = keyValue.Predicate();
                String valueContent = keyValue.Content();
                if(! predicateName.contains("#")) {
                    throw new Exception("Invalid predicate:" + predicateName);
                }
                String shortPredicate = predicateName.split("#")[1];

                // Maybe the predicate is prefixed with the class name, for example "CIM_Process.Handle".
                // If so, the class name is deduced and will be compared.
                String[] splitPredicate = shortPredicate.split("\\.");
                if(splitPredicate.length > 1) {
                    if (splitPredicate.length == 2) {
                        // Without the prefix.
                        shortPredicate = splitPredicate[1];
                        String predicatePrefix = splitPredicate[0];
                        if(deducedClassName == null)
                            deducedClassName = predicatePrefix;
                        else {
                            if(!deducedClassName.equals(predicatePrefix)) {
                                throw new Exception("Different predicates prefixes:" + shortPredicate + "/" + deducedClassName);
                            }
                        }
                    } else {
                        throw new Exception("Too many dots in invalid predicate:" + shortPredicate);
                    }
                }

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

            String shortClassName = null;
            if(pattern.className == null) {
                // Maybe the class is not given.
                if(deducedClassName == null) {
                    // This is acceptable because it might work in a further Sparql execution.
                    logger.debug("Class name is null and cannot be deduced.");
                    shortClassName = null;
                } else {
                    logger.debug("Short class name deduced to " + deducedClassName);
                    shortClassName = deducedClassName;
                }
            }  else {
                // If the class is explicitly given.
                if (!pattern.className.contains("#")) {
                    throw new Exception("Invalid class name:" + pattern.className);
                }
                shortClassName = pattern.className.split("#")[1];
                if (deducedClassName != null) {
                    // If the class is explicitly given, and also is the prefix of some attributes.
                    if (!shortClassName.equals(deducedClassName)) {
                        throw new Exception("Different short class=" + shortClassName + " and deduced=" + deducedClassName);
                    }
                }
            }

            if(shortClassName != null) {
                // A class name is need to run WQL queries.
                QueryData queryData = new QueryData(shortClassName, pattern.VariableName, isMainVariableAvailable, selected_variables, wheres);
                prepared_queries.add(queryData);
            }
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
