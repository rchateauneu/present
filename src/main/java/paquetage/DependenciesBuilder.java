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

    // TODO: ?? Here, RDFS.LABEL and RDFS.COMMENT to which variables ? SEEALSO AND ISDEFINEDBY ??
    public void ListeRenommage(Solution solution) {
        /*
        SeeAlso et isDefinedBy ne sont pas associes a des colonnes WMI comme Name et Description.
         */
        logger.debug("ListeRenommage keys solution.Rows.size()=" + String.valueOf(solution.Rows.size()));
        if(solution.Rows.size() > 0) {
            for (String oneKey : solution.Rows.get(0).KeySet()) {
                logger.debug("    oneKey=" + oneKey);
            }
        }
        // En principe, ca contient "Name" et "Description" et on va remplacer par "label" et "comment",
        // eventuellement les laisser si c etait specifie.

        /*
        PSEUDO-COLONNES:
        On peut avoir plus de colonnes que ce qui ete ajoute en entree.
        Les traitements sont differents:
        RDFS.LABEL / RDFS.COMMENT
            Inconnu:
                Avant: Ajouter "Name" ou "Caption" / "Description" s'il n'y est pas deja.
                       FIXME: S'il y est deja, ca va faire des problemes car la meme valeur de colonne WMI
                              doit remplir deux variables differentes ?
                                  ?x rdfs:label ?l
                                  ?x CIMV2:Win32_Process ?c
                              Il faut donc creer une pseudo colonne dans la solution.
                              Si on a du ajouter "Name" ou autre, verifier que cette colonne ne gene pas.
                Apres: Suffixer avec "@en"
            Connu:
                Calculer "Name", "Caption" etc... mais sans grand espoir.
        RDFS.SEEALSO / RDFS.ISDEFINEDBY
            Inconnu:
                Avant: Rien.
                Avant: Renvoyer un URL specifique.
            Connu:
                IMPOSSIBLE.
         */
    }

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
    public DependenciesBuilder(List<ObjectPattern> patterns) //throws Exception
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

        for(int patternCounter = 0;patternCounter < patterns.size(); ++patternCounter)
        {
            ObjectPattern pattern = patterns.get(patternCounter);
            List<QueryData.WhereEquality> wheres = new ArrayList<>();
            Map<String, String> selected_variables = new HashMap<>();
            List<ObjectPattern.PredicateObjectPair> rdfsPredicates = new ArrayList<>();

            // Now, split the variables of this object, between:
            // - the variables known at this stage from the previous queries, which can be used in the "WHERE" clause,
            // - the variables which are not known yet, and returned by this WQL query.
            // The variable representing the object is selected anyway and contains the WMI relative path.
            for(ObjectPattern.PredicateObjectPair predicateObjectPair: pattern.Members) {
                if(predicateObjectPair.ShortPredicate == null) {
                    /*
                    This is a Sparql "predicate" which does not exist in WMI, and cannot be selected
                    or used for filtering, but is a property of the object.
                    It could be RDFS.LABEL, RDFS.COMMENT, RDFS.SEEALSO, RDFS.ISDEFINEDBY etc...
                    Depending on the pseudo-column, it adds a real WMI column, if it is not already there.

                    */
                    rdfsPredicates.add(predicateObjectPair);
                    logger.debug("Not a WMI column: predicateObjectPair.variableName=" + predicateObjectPair.variableName
                            + " predicateObjectPair.RawPredicate=" + predicateObjectPair.RawPredicate
                    );
                    continue;
                }
                QueryData.WhereEquality wmiKeyValue = new QueryData.WhereEquality(
                        predicateObjectPair.ShortPredicate,
                        predicateObjectPair.ObjectContent,
                        predicateObjectPair.variableName);

                if(predicateObjectPair.variableName != null) {
                    if(variablesContext.containsKey(predicateObjectPair.variableName))  {
                        // If it is a variable calculated in the previous queries, its value is known when executing.
                        wheres.add(wmiKeyValue);
                    } else {
                        selected_variables.put(predicateObjectPair.ShortPredicate, predicateObjectPair.variableName);
                    }
                } else {
                    // If the value of the predicate is known because it is a constant.
                    wheres.add(wmiKeyValue);
                }
            }

            /*
            Plusieurs cas:
                ?my_file rdfs:label ?directory_label .
                ?my_file cimv2:Win32_Directory.Name ?name .
                ==>> Inutile d'ajouter la colonne "Name" car elle doit deja se trouver la.

                ?my_file rdfs:label ?directory_label .
                ?my_file cimv2:Win32_Directory.Name "C:\\Windows" .
                ==>> Inutile d'ajouter "Name" car on a deja la valeur, c'est une constante.

                ?my_file rdfs:label ?directory_label .
                ==>> Il faut ajouter "Name" et le retirer des resultats par la suite.
            */
            logger.debug("RDFS predicate object pairs");
            for(ObjectPattern.PredicateObjectPair rdfsPredicateObjectPair : rdfsPredicates) {
                String wmiPredicate = ObjectPattern.RDFSToWMI(rdfsPredicateObjectPair.RawPredicate);
                logger.debug("    rdfs2wmi:" + wmiPredicate);
                if(selected_variables.get(wmiPredicate) != null) {
                    logger.debug("        Already selected. No need to add it");
                }
                // TODO: wheres should be indexed with the column, but there are very few of them.
                QueryData.WhereEquality whereEqFound = null;
                for(QueryData.WhereEquality whereEq : wheres) {
                    if(whereEq.predicate.equals(wmiPredicate)) {
                        whereEqFound = whereEq;
                        break;
                    }
                }
                if(whereEqFound == null) {
                    logger.debug("        Not in where clause. Must be added if not already selected.");
                } else {
                    logger.debug("        Implicit because in where clause");
                }
            }

            // The same variables might be added several times, and duplicates will be eliminated.
            for(String variable_name : selected_variables.values()) {
                if(variable_name == null) {
                    throw new RuntimeException("Should not add null variable in context");
                }
                variablesContext.put(variable_name, null);
            }

            // The variable which defines the object will receive a value with the execution of this WQL query,
            // but maybe it is already known because of an association request done before.
            boolean isMainVariableAvailable = variablesContext.containsKey(pattern.VariableName);
            /*
            if(!isMainVariableAvailable) {
                if(pattern.VariableName == null) {
                    throw new RuntimeException("Should not add null pattern.VariableName in context");
                }
                variablesContext.put(pattern.VariableName, null);
            }
            */

            if(isMainVariableAvailable) {
                assert pattern.ConstantSubject == null;
                // If the main variable is known, it will use a getter. However, if there are "where" tests,
                // the values of the columns must be known for extra filtering. Therefore, they must be fetched.
                Set<String> nonSelectedColumns = wheres.stream().map(w->w.predicate).collect(Collectors.toSet());
                nonSelectedColumns.removeAll(selected_variables.keySet());
                for(String nonSelectedColumn: nonSelectedColumns) {
                    // The counter is used to avoid an ambiguity if the same class and the same column are used
                    // several times in this Sparql query.
                    String internalVariable = pattern.ClassName + "." + nonSelectedColumn + "." + patternCounter + ".internal";
                    selected_variables.put(nonSelectedColumn, internalVariable);
                    variablesContext.put(internalVariable, null);
                }
            } else {
                /*
                if(pattern.VariableName == null) {
                    throw new RuntimeException("Should not add null pattern.VariableName in context");
                }
                variablesContext.put(pattern.VariableName, null);
                */

                // This has nothing to do with a constant subject.
                logger.debug("NOT isMainVariableAvailable VariableName=" + pattern.VariableName
                        + " ClassName=" + pattern.ClassName);
            }

            if(pattern.ConstantSubject != null) {
                logger.warn("isMainVariableAvailable=" + isMainVariableAvailable
                        + " VariableName=" + pattern.VariableName
                        + " ConstantSubject=" + pattern.ConstantSubject
                        + " ClassName=" + pattern.ClassName);
                assert pattern.VariableName == null;

                // This is an artificial variable whose value is the subject given as a constant.
                // It has a unique number associated to the pattern index.
                String constantVariable = "PseudoVariableForConstantSubject_" + String.valueOf(patternCounter);
                pattern.VariableName = constantVariable;
                assert variablesContext.containsKey(constantVariable) == false;
                // The subject must be a node.
                ValueTypePair vtp = new ValueTypePair(pattern.ConstantSubject, ValueTypePair.ValueType.NODE_TYPE);
                variablesContext.put(constantVariable, vtp);

                // Maybe, this is not needed anymore.
                isMainVariableAvailable = true;
            } else {
                if(pattern.VariableName == null) {
                    throw new RuntimeException("Should not add null pattern.VariableName in context");
                }
                variablesContext.put(pattern.VariableName, null);
            }

            if(pattern.ClassName != null) {
                // A class name is needed to run WQL queries, and also its WMI namespace.
                WmiProvider.CheckValidNamespace(pattern.CurrentNamespace);
                WmiProvider.CheckValidClassname(pattern.ClassName);
                QueryData queryData = new QueryData(pattern.CurrentNamespace, pattern.ClassName, pattern.VariableName, isMainVariableAvailable, selected_variables, wheres);
                prepared_queries.add(queryData);
            }
        } // Next ObjectPattern
    } // DependenciesBuilder

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
