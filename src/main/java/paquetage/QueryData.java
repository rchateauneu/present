package paquetage;

import org.apache.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * To be used when reconstructing a WQL query.
 */
public class QueryData {
    final static Logger logger = Logger.getLogger(QueryData.class);

    String namespace;
    String className;

    // Name of the Sparql variable containing the object. Its rdf:type is a CIM class.
    String mainVariable;

    // After sorting the QueryData in the right order before recursive evaluation, this indicates
    // that the main variable - the variable representing the object - is evaluated by the nesting queries.
    boolean isMainVariableAvailable;

    // It maps a column name to a Sparql variable and is used to copy the column values to the variables.
    // This sorted container guarantees the order to help comparison in tests.
    SortedMap<String, String> queryColumns;

    // The key and the values are Sparql variable name.
    SortedMap<String, String> queryVariableColumns;

    // When several variables are selected with the same predicate, then the predicate appears once only in the query
    // for one of the variables. Once the value is selected, it is copied to the other variables.
    public Map<String, List<String>> variablesSynonyms;

    /** Patterns of the WHERE clause of a WMI query. The values are variables or constants at creation,
    but are transformed into constants before building the query .
    TODO: Consider a Map, instead of a List, for simplicity.
    However, before this change, it must be clarified if it is allowed to have several triples with the same subject
    and predicate, and if it should be allowed, like for example:
         ?instance cimv2:Predicate "value1" .
         ?instance cimv2:Predicate "value2" .
     ... or:
         ?instance cimv2:Predicate ?var1 .
         ?instance cimv2:Predicate ?var2 .
    */
    List<WhereEquality> whereTests;

    // Provider is a class used to execute a query similar to WQL.
    // This attempts to return the same result as a WQL query, but faster.
    BaseSelecter classBaseSelecter = null;

    // Getter is a class used for a query similar to GetObject.
    // It returns the same object, but faster.
    BaseGetter classGetter = null;

    /**
     * This cache is used only for WMI: It maps the query strings to their result.
     * The query must be stored because the values in the "where" clause of the QueryData instance,
     * might temporarily change.
     * However, the main variable must not change, and also the variable names in the where clause.
     * It would be enough to index the results with a tuple containing the values of the where clause.
     * TODO: Consider a LRU cache to limit memory usage.
     */

    /**
     * TODO: Consider reusing parts of the cache for this usage pattern:
     * A first query selects from the class, with wide selection criterias:
     * wqlQuery=Select LocalAddress,LocalPort,OwningProcess,RemoteAddress,RemotePort, __PATH from MSFT_NetTCPConnection
     *
     * Subsequent queries select with stricter tests, data which belong the the initial data set:
     * wqlQuery=Select OwningProcess, __PATH from MSFT_NetTCPConnection where LocalAddress = "::" and LocalPort = "0" and RemoteAddress = "::" and RemotePort = "65360"
     * wqlQuery=Select OwningProcess, __PATH from MSFT_NetTCPConnection where LocalAddress = "::" and LocalPort = "0" and RemoteAddress = "::" and RemotePort = "65344"
     *
     * Conditions: If the select and where columns are part of the selected columns of the initial query,
     * and of the where columns are a superset of the where columns of the initial query,
     * then, it is possible to reuse the results of the initial query, making an appropriate extra filtering
     * on the columns (only the needed columns in the select) and the rows (only the needed rows in the where).
     *
     */
    private HashMap<String, Solution> cacheQueries = null;

    public Solution getCachedQueryResults(String wqlQuery) {
        if(cacheQueries == null) {
            return null;
        }
        return cacheQueries.get(wqlQuery);
    }

    public void storeCachedQueryResults(String wqlQuery, Solution resultRows) {
        if(cacheQueries == null) {
            cacheQueries = new HashMap<>();
        }
        cacheQueries.put(wqlQuery, resultRows);
    }

    // This is just for debugging.
    public String toString() {
        String cols = String.join("+", queryColumns.keySet());
        String wheres =  (String) whereTests.stream()
                .map(w -> w.predicate)
                .collect(Collectors.joining("/"));
        return String.format("C=%s V=%s Cols=%s W=%s", className, mainVariable, cols, wheres);
    }

    // The column "ProcessName" does not exist for all classes but at least "Win32_Process".
    // The role of these special columns is not clear: WMI cannot be selected nor used in "where".
    // The column "PSComputerName" has a special processing in WMI, so it is accepted,
    // but handled differently than plain columns.
    // The column "Path" seems broken for Win32_Process only.
    static private Set<String> specialColumns = Set.of("VM", "WS", "Handles", "ProcessName");

    static public class WhereEquality {
        // This is a member of WMI class and used to build the WHERE clause of a WQL query.
        public String predicate;

        // This is the value or a variable name, compared with a WMI class member in a WQL query.
        public ValueTypePair value;

        // Tells if this is a Sparql variable (which must be evaluated in the nesting WQL queries) or a constant.
        String variableName; // null if constant,

        public WhereEquality(String predicateArg, ValueTypePair pairValueType, String variable) {
            logger.debug("predicateArg=" + predicateArg);
            if(pairValueType != null) logger.debug("pairValueType.toValueString()=" + pairValueType.toValueString());
            if(variable != null) logger.debug("variable=" + variable);

            if(predicateArg.contains("#")) {
                // This ensures that the IRI of the RDF node is stripped of its prefix.
                throw new RuntimeException("Invalid class name:" + predicateArg);
            }
            if(specialColumns.contains(predicateArg)) {
                throw new RuntimeException("This special column is forbidden:" + predicateArg);
            }
            if((variable != null) && !PresentUtils.validSparqlVariable(variable)) {
                throw new RuntimeException("Invalid syntax for Sparql variable:" + variable);
            }

            predicate = predicateArg;
            value = pairValueType;
            variableName = variable;
        }

        public WhereEquality(String predicateArg, String variable) throws Exception {
            this(predicateArg, null, variable);
        }

        public WhereEquality(String predicateArg, ValueTypePair pairValueType) {
            this(predicateArg, pairValueType, null);
        }

        /**
         * This is useful for building a WMI query only.
         * TODO: Move this is WMI-specific code.
         * @return
         */
        public String toEqualComparison() {
            // Real examples in Powershell - they are quite fast:
            // PS C:> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Antecedent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\DriverStore\\\\FileRepository\\\\iigd_dch.inf_amd64_ea63d1eddd5853b5\\\\igdinfo64.dll\""'
            // PS C:> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Dependent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"32308\""'

            if(value == null) {
                // This should not happen.
                logger.debug("Value of " + predicate + " is null");
            }
            /*
            Si le predicat attend un path wbem, verifier que la syntaxe est OK.
            */
            String escapedValue = value.toValueString().replace("\\", "\\\\").replace("\"", "\\\"");
            return String.format("%s = \"%s\"", predicate, escapedValue);
        }
    };

    /** Checks if a selecter or a getter can be used for a QueryData. */
    public boolean isCompatibleQuery(String whereClassName, Set<String> whereColumns, Set<String> availableColumns)
    {
        logger.debug("whereClassName=" + whereClassName + " whereColumns=" + whereColumns + " availableColumns=" + availableColumns);
        // It must be for the good class, and be able to return the needed columns.
        if(! areColumnsSubsetOf(whereClassName, availableColumns)) {
            return false;
        }

        // The lookup is based on properties given in a "where" test. So this column must be given.
        Set<String> queryWhereColumns = whereTests.stream().map(x -> x.predicate).collect(Collectors.toSet());
        if(! queryWhereColumns.equals(whereColumns)) {
            return false;
        }
        return true;
    }

    public boolean areColumnsSubsetOf(String whereClassName, Set<String> selectedColumns) {
        if(! className.equals(whereClassName)) {
            return false;
        }
        Set<String> requiredColumns = queryColumns.keySet();
        logger.debug("whereClassName="+whereClassName);
        logger.debug("queryColumns.keySet()="+queryColumns.keySet());
        logger.debug("selectedColumns="+selectedColumns);
        logger.debug("requiredColumns="+requiredColumns);
        return selectedColumns.containsAll(requiredColumns);
    }

    /** There should be a handful of elements so looping is OK.
     *
     * @param columnName "Handle", "Name", "PartComponent" etc...
     * @return
     */
    public ValueTypePair getWhereValue(String columnName) {
        logger.debug("columnName=" + columnName
                + " whereTests=" + whereTests.stream().map(x -> x.predicate + "/" + x.variableName).collect(Collectors.toList()));
        for(WhereEquality whereElement : whereTests) {
            if(whereElement.predicate.equals(columnName)) {
                return whereElement.value;
            }
        }
        return null;
    }

    public String columnToVariable(String columnName) {
        String variableName = queryColumns.get(columnName);
        return variableName;
    }

    /** This is used to evaluate the cost of accessing a single object given its path.
     * The keys are the class name and the fetched columns, because this information can be used
     * to create custom functions.
     *
     */
    static public class Statistics {
        class Sample {
            public long elapsed;
            public int count;
            public Sample() {
                elapsed = System.currentTimeMillis();
                count = 0;
            }
        };
        private HashMap<String, Sample> statistics;
        private long startTime = -1;

        Statistics() {
            resetAll();
        }

        void resetAll() {
            statistics = new HashMap<>();
        }

        void startSample() {
            startTime = System.currentTimeMillis();
        }

        void finishSample(String objectPath, Set<String> columns) {
            if(startTime == -1) {
                throw new RuntimeException("Sampling not started");
            }
            String key = objectPath + ":" + String.join(",", columns);
            Sample sample = statistics.get(key);
            if(sample != null) {
                sample.elapsed += System.currentTimeMillis() - startTime;
                sample.count++;
            } else {
                sample = new Sample();
                sample.elapsed = System.currentTimeMillis() - startTime;
                sample.count = 1;
                statistics.put(key, sample);
            }
            startTime = -1;
        }

        void displayAll() {
            long totalElapsed = 0;
            int totalCount = 0;

            long maxElapsed = 0;
            int maxCount = 2; // One occurrence is not interesting.

            // Find the most expensive calls or the one mostly called.
            for(HashMap.Entry<String, Sample> entry: statistics.entrySet()) {
                Sample currentValue = entry.getValue();
                totalElapsed += currentValue.elapsed;
                totalCount += currentValue.count;
                if(currentValue.elapsed >= maxElapsed)
                    maxElapsed = currentValue.elapsed;
                if(currentValue.count >= maxCount)
                    maxCount = currentValue.count;
            }
            // Only display the most expensive fetches.
            for(HashMap.Entry<String, Sample> entry: statistics.entrySet()) {
                Sample currentValue = entry.getValue();
                if((currentValue.elapsed >= maxElapsed) || (currentValue.count >= maxCount)) {
                    logger.debug("Most expensive call:" + entry.getKey() + " " + (currentValue.elapsed / 1000.0) + " secs " + currentValue.count + " calls");
                    // Warns just once of the worst performance issue.
                    break;
                }
            }
            logger.debug("TOTAL" + " " + (totalElapsed / 1000.0) + " secs " + totalCount + " calls " + statistics.size() + " lines");
        }
    }

    private Statistics statistics = new Statistics();

    QueryData(
            String wmiNamespace,
            String wmiClassName,
            String variable,
            boolean mainVariableAvailable,
            Map<String, String> columns,
            List<QueryData.WhereEquality> wheres) {
        this(wmiNamespace, wmiClassName, variable, mainVariableAvailable, columns, wheres, null, null);
    }

    QueryData(
            String wmiNamespace,
            String wmiClassName,
            String variable,
            boolean mainVariableAvailable,
            Map<String, String> constantColumns,
            List<QueryData.WhereEquality> wheres,
            Map<String, List<String>> synonyms,
            Map<String, String> variableColumns) {
        WmiProvider.CheckValidNamespace(wmiNamespace);
        // If the subject is a constant, then there is no main variable.
        if(variable == null) {
            logger.debug("Variable is null");
        }
        mainVariable = variable;
        isMainVariableAvailable = mainVariableAvailable;
        WmiProvider.CheckValidClassname(wmiClassName);
        namespace = wmiNamespace;
        className = wmiClassName;
        variablesSynonyms = synonyms;
        // Creates a map even if no columns are selected.
        if(constantColumns == null) {
            queryColumns = new TreeMap<>();
        } else {
            for(String oneColumn: constantColumns.keySet()) {
                if (specialColumns.contains(oneColumn)) {
                    throw new RuntimeException("Selected constant column is forbidden:" + oneColumn);
                }
            }
            queryColumns = new TreeMap<>(constantColumns);
        }
        queryVariableColumns = (variableColumns == null) ? new TreeMap<>() : new TreeMap<>(variableColumns);

        swapWheres(wheres);
        logger.debug("wmiClassName=" + wmiClassName + " variable=" + variable + " queryColumns=" + queryColumns);

        setHandlers(false);
    }

    void setHandlers(boolean forceWmi) {
            if(isMainVariableAvailable)
                classGetter = GenericProvider.FindGetter(this, forceWmi);
            else
                classBaseSelecter = GenericProvider.FindSelecter(this, forceWmi);
    }

    /** This is used for initialising when adding the WHERE tests without values,
     * and later when calculating a QueryData for a specific context.
     * @param wheres
     * @return
     */
    List<QueryData.WhereEquality> swapWheres(List<QueryData.WhereEquality> wheres) {
        List<QueryData.WhereEquality> oldWheres = whereTests;
        if(wheres == null)
            // Guaranteed representation of an empty where clause.
            whereTests = new ArrayList<>();
        else
            // This sorts the where tests so the order is always the same and helps comparisons.
            // This is not a problem performance-wise because usually there is only one element per WQL query.
            whereTests = wheres.stream().sorted(Comparator.comparing(x -> x.predicate)).collect(Collectors.toList());
        return oldWheres;
    }

    public String buildWqlQuery() {
        // The order of select columns is not very important because the results can be mapped to variables.
        String columns = String.join(",", queryColumns.keySet());

        // If the keys of the class are given, __RELPATH is not calculated.
        // Anyway, it seems that __PATH is calculated only when explicitly requested.
        if (queryColumns.isEmpty())
            columns += "__PATH";
        else
            columns += ", __PATH";
        String wqlQuery = "Select " + columns + " from " + className;

        if( (whereTests != null) && (! whereTests.isEmpty())) {
            wqlQuery += " where ";
            String whereClause = whereTests.stream()
                    .map(QueryData.WhereEquality::toEqualComparison)
                    .collect(Collectors.joining(" and "));
            wqlQuery += whereClause;
        }
        return wqlQuery;
    }

    public void startSampling() {
        statistics.startSample();
    }

    public void finishSampling() {
        // This adds some extra information about the execution.
        Set<String> columnsWhere = whereTests.stream()
                .map(entry -> entry.predicate)
                .collect(Collectors.toSet());
        statistics.finishSample(className, columnsWhere);
    }

    public void finishSampling(String objectPath) {
        statistics.finishSample(objectPath, queryColumns.keySet());
    }

    public void displayStatistics() {
        // Consistency check, then display the selecter of getter of data.
        if(classGetter != null) {
            if(classBaseSelecter != null) {
                throw new RuntimeException("QueryData is both a Provider and a Getter");
            }
            if(!isMainVariableAvailable) {
                throw new RuntimeException("QueryData should be a Getter");
            }
            logger.debug("Getter:" + classGetter.getClass().getName());
        }
        else if(classBaseSelecter != null) {
            if(isMainVariableAvailable) {
                throw new RuntimeException("QueryData should be a Provider");
            }
            logger.debug("Provider:" + classBaseSelecter.getClass().getName());
        }
        else {
            logger.debug("No Provider nor Getter set");
        }
        statistics.displayAll();
    }

    public void resetStatistics() {
        statistics.resetAll();
    }
}

