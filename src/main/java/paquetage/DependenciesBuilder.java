package paquetage;

import org.apache.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;
/*
 * TODO: Merge SparqlTranslation and DependenciesBuilder
*/

public class DependenciesBuilder {
    final static private Logger logger = Logger.getLogger(DependenciesBuilder.class);
    /**
     * This never changes whatever the order of input BGPs is.
     * It is used in the recursive execution of WQL queries, to store the values of variables evaluated
     * in the lowest levels.
     */
    public HashMap<String, ValueTypePair> variablesContext;

    private void addToVariablesContext(String variable_name) {
        if(variable_name == null) {
            throw new RuntimeException("Should not add null variable in context");
        }
        logger.debug("variablesContext.put variable_name=" + variable_name);
        if(variablesContext.get(variable_name) != null) {
            throw new RuntimeException("Already selected variable_name=" + variable_name
                    + " keys=" + variablesContext.keySet());
        }
        variablesContext.put(variable_name, null);
    }

    /** It represented the nested WQL queries.
     There is one such query for each object exposed in a Sparql query.
     The order, and input and output columns, and the performance depend highly on the order of the input BGPs.
     */
    public List<QueryData> preparedQueries;

    /** This takes as input a list of object patterns, and assumes that each of them represents a WQL query,
     * the queries being nested into one another (top-level first).
     * Each ObjectPattern instances contains all the triples related to the same RDF subject.
     *
     * TODO: The execution of WQL queries could be optimised be changing the order of the patterns list.
     * @param listSortedPatterns
     * @throws Exception
     */
    public DependenciesBuilder(List<ObjectPattern> listSortedPatterns) //throws Exception
    {
        preparedQueries = new ArrayList<>();

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

        for(int patternCounter = 0; patternCounter < listSortedPatterns.size(); ++patternCounter)
        {
            logger.debug("patternCounter=" + patternCounter);
            ObjectPattern pattern = listSortedPatterns.get(patternCounter);
            List<QueryData.WhereEquality> whereKVs = new ArrayList<>();
            Map<String, String> selectedVariablesConstantPredicate = new HashMap<>();
            Map<String, String> selectedVariablesVariablePredicate = new HashMap<>();

            Map<String, List<String>> variablesSynonyms = new HashMap();

            // Now, split the variables of this object, between:
            // - the variables known at this stage from the previous queries, which can be used in the "WHERE" clause,
            // - the variables which are not known yet, and returned by this WQL query.
            // The variable representing the object is selected anyway and contains the WMI relative path.
            String predVarName = null;
            logger.debug("pattern.membersList.size()=" + pattern.membersList.size());
            for(ObjectPattern.PredicateObjectPair predicateObjectPair: pattern.membersList) {
                String predShortPredicate = predicateObjectPair.shortPredicate;
                String objectVariableName = predicateObjectPair.variableName;
                logger.debug("predShortPredicate=" + predShortPredicate + " objectVariableName=" + objectVariableName);
                if(predShortPredicate.equals(ObjectPattern.ALL_PREDICATES)) {
                    if(predicateObjectPair.predicateVariableName == null) {
                        throw new RuntimeException("predicateObjectPair.predVarName is null");
                    }
                    if(predVarName != null) {
                        throw new RuntimeException("Only one Member if variable predicate - yet: "
                                + predVarName + " / " + predicateObjectPair.predicateVariableName);
                    }
                    predVarName = predicateObjectPair.predicateVariableName;
                    logger.debug("All predicates. predVarName=" + predVarName);

                    selectedVariablesVariablePredicate.put(predVarName, objectVariableName);
                } else {
                    QueryData.WhereEquality wmiKeyValue = new QueryData.WhereEquality(
                            predShortPredicate,
                            predicateObjectPair.objectContent,
                            objectVariableName);

                    if (objectVariableName != null) {
                        if (variablesContext.containsKey(objectVariableName)) {
                            // If it is a variable calculated in the previous queries, its value is known when executing.
                            logger.debug("whereKVs gets:" + "P=" + wmiKeyValue.wherePredicate + " N=" + wmiKeyValue.whereVariableName + " V=" + wmiKeyValue.whereValue);
                            whereKVs.add(wmiKeyValue);
                        } else {
                            logger.debug("Selecting variable:" + predShortPredicate + "=>" + objectVariableName);
                            String existingVariable = selectedVariablesConstantPredicate.get(predShortPredicate);
                            if (existingVariable != null) {
                                // Maybe two different variables for the same predicate.
                                List<String> synonymsList = variablesSynonyms.get(existingVariable);
                                if (synonymsList == null) {
                                    synonymsList = new ArrayList<String>();
                                    variablesSynonyms.put(existingVariable, synonymsList);
                                }
                                synonymsList.add(objectVariableName);
                                logger.debug(
                                        "Already in selectedVariablesConstantPredicate"
                                                + " predShortPredicate=" + predShortPredicate
                                                + " objectVariableName=" + objectVariableName
                                                + " selectedVariablesConstantPredicate.keySet=" + selectedVariablesConstantPredicate);
                            } else {
                                logger.debug(
                                        "Adding to selectedVariablesConstantPredicate"
                                                + " predShortPredicate=" + predShortPredicate
                                                + " objectVariableName=" + objectVariableName
                                                + " selectedVariablesConstantPredicate.keySet=" + selectedVariablesConstantPredicate);
                                selectedVariablesConstantPredicate.put(predShortPredicate, objectVariableName);
                            }
                        }
                    } else {
                        // If the value of the predicate is known because it is a constant.
                        logger.debug("whereKVs gets:" + "P=" + wmiKeyValue.wherePredicate + " N=" + wmiKeyValue.whereVariableName + " V=" + wmiKeyValue.whereValue);
                        whereKVs.add(wmiKeyValue);
                    }
                }
            } // Next member of the pattern.
            logger.debug("selectedVariablesConstantPredicate="
                    + selectedVariablesConstantPredicate.entrySet().stream()
                    .map(x -> x.getKey() + "=>" + x.getValue()).collect(Collectors.toList()));

            // The same variables might be added several times, and duplicates will be eliminated.
            for(String variableName : selectedVariablesConstantPredicate.values()) {
                logger.debug("variableName=" + variableName);
                addToVariablesContext(variableName);
            }

            logger.debug("selectedVariablesVariablePredicate="
                    + selectedVariablesVariablePredicate.entrySet().stream()
                    .map(x -> x.getKey() + "=>" + x.getValue()).collect(Collectors.toList()));

            for(Map.Entry<String, String> pairTwoVars : selectedVariablesVariablePredicate.entrySet()) {
                logger.debug("pairTwoVars.getKey()=" + pairTwoVars.getKey() + " pairTwoVars.getValue()=" + pairTwoVars.getValue());
                addToVariablesContext(pairTwoVars.getKey());
                addToVariablesContext(pairTwoVars.getValue());
            }

            // The variable which defines the object will receive a value with the execution of this WQL query,
            // but maybe it is already known because of an association request done before.
            boolean isMainVariableAvailable = variablesContext.containsKey(pattern.variableName);
            logger.debug("pattern.variableName=" + pattern.variableName + " variablesContext.keySet()=" + variablesContext.keySet());

            if(isMainVariableAvailable) {
                assert pattern.constantSubject == null;
                // If the main variable is known, it will use a getter. However, if there are "where" tests,
                // the values of the columns must be known for extra filtering. Therefore, they must be fetched.
                Set<String> nonSelectedColumns = whereKVs.stream().map(w->w.wherePredicate).collect(Collectors.toSet());
                nonSelectedColumns.removeAll(selectedVariablesConstantPredicate.keySet());
                for(String nonSelectedColumn: nonSelectedColumns) {
                    // The counter is used to avoid an ambiguity if the same class and the same column are used
                    // several times in this Sparql query.
                    String internalVariable = pattern.subjectClassname + "." + nonSelectedColumn + "." + patternCounter + ".internal_variable";
                    selectedVariablesConstantPredicate.put(nonSelectedColumn, internalVariable);
                    logger.debug("variablesContext.put internalVariable=" + internalVariable);
                    variablesContext.put(internalVariable, null);
                }
            } else {
                // This has nothing to do with a constant subject.
                logger.debug("NOT isMainVariableAvailable VariableName=" + pattern.variableName
                        + " ClassName=" + pattern.subjectClassname);
            }

            if(pattern.constantSubject != null) {
                logger.warn("isMainVariableAvailable=" + isMainVariableAvailable
                        + " VariableName=" + pattern.variableName
                        + " ConstantSubject=" + pattern.constantSubject
                        + " ClassName=" + pattern.subjectClassname);
                assert pattern.variableName == null;

                // This is an artificial variable whose value is the subject given as a constant.
                // It has a unique number associated to the pattern index.
                String constantSubjectVariable = "PseudoVariableForConstantSubject_" + patternCounter;
                pattern.variableName = constantSubjectVariable;
                assert variablesContext.containsKey(constantSubjectVariable) == false;
                // The subject must be a node.
                ValueTypePair vtp = new ValueTypePair(pattern.constantSubject, ValueTypePair.ValueType.NODE_TYPE);
                logger.debug("variablesContext.put constantSubjectVariable=" + constantSubjectVariable);
                variablesContext.put(constantSubjectVariable, vtp);

                // Maybe, this is not needed anymore.
                isMainVariableAvailable = true;
            } else {
                if(pattern.variableName == null) {
                    throw new RuntimeException("Should not add null pattern.VariableName in context");
                }
                logger.debug("variablesContext.put pattern.VariableName=" + pattern.variableName);
                variablesContext.put(pattern.variableName, null);
            }

            if(pattern.subjectClassname != null) {
                // A class name is needed to run WQL queries, and also its WMI namespace.
                WmiProvider.checkValidNamespace(pattern.subjectNamespace);
                WmiProvider.checkValidClassname(pattern.subjectClassname);

                QueryData queryData = new QueryData(
                        pattern.subjectNamespace, pattern.subjectClassname, pattern.variableName,
                        isMainVariableAvailable, selectedVariablesConstantPredicate,
                        whereKVs, variablesSynonyms,
                        selectedVariablesVariablePredicate);
                preparedQueries.add(queryData);
            } else {
                logger.debug("Create QueryData with null subjectClassName. pattern.variableName="
                        + pattern.variableName + " Members=" + pattern.membersList);

                //On fait qq chose pour accepter que la classe soit nulle.
                //C est ici qu il faut se placer pour calculer plus tard la classe quand on aura la valeur du sujet.
                assert(pattern.subjectClassname == null);
                QueryData queryData = new QueryData(
                        pattern.subjectNamespace, pattern.subjectClassname, pattern.variableName,
                        isMainVariableAvailable, selectedVariablesConstantPredicate,
                        whereKVs, variablesSynonyms,
                        selectedVariablesVariablePredicate);
                preparedQueries.add(queryData);
            }
        } // Next ObjectPattern
        logger.debug("variablesContext.keySet()=" + variablesContext.keySet());
    } // DependenciesBuilder

}
