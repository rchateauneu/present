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
    // that the the main variable - the variable representing the object - is evaluated by the nesting queries.
    boolean isMainVariableAvailable;

    // It maps a column name to a Sparql variable and is used to copy the column values to the variables.
    // This sorted container guarantees the order to help comparison in tests.
    SortedMap<String, String> queryColumns;

    // Patterns of the WHERE clause of a WMI query. The values are variables.
    List<WhereEquality> whereTests;

    // Provider class used to execute a query similar to WQL. Used to evaluate performance.
    BaseSelecter classBaseSelecter = null;

    // Getter class used for a query similar to GetOebject. Used to evaluate performance.
    BaseGetter classGetter = null;

    // Used only for WMI.
    // The query must be stored because the values in the "where" clause change.
    // However, the main variable must not change.
    // Consider a LRU cache to limit memory usage.
    private HashMap<String, Solution > CacheQueries = null;

    public Solution GetCachedQueryResults(String wqlQuery) {
        if(CacheQueries == null) {
            return null;
        }
        return CacheQueries.get(wqlQuery);
    }

    public void StoreCachedQueryResults(String wqlQuery, Solution resultRows) {
        if(CacheQueries == null) {
            CacheQueries = new HashMap<>();
        }
        CacheQueries.put(wqlQuery, resultRows);
    }

    // This is just for debugging.
    public String toString() {
        String cols = String.join("+", queryColumns.keySet());
        String wheres =  (String) whereTests.stream()
                .map(w -> w.predicate)
                .collect(Collectors.joining("/"));
        return "C=" + className + " V=" + mainVariable + " Cols=" + cols + " W=" + wheres;
    }

    static public class WhereEquality {
        // This is a member of WMI class and used to build the WHERE clause of a WQL query.
        public String predicate;

        // This is the value or a variable name, compared with a WMI class member in a WQL query.
        public ValueTypePair value;

        // Tells if this is a Sparql variable (which must be evaluated in the nesting WQL queries) or a constant.
        // boolean isVariable;
        String variableName; // null if constant,

        public WhereEquality(String predicateArg, ValueTypePair pairValueType, String variable) throws Exception {
            if(predicateArg.contains("#")) {
                // This ensures that the IRI of the RDF node is stripped of its prefix.
                throw new Exception("Invalid class:" + predicateArg);
            }
            if((variable != null) && !PresentUtils.ValidSparqlVariable(variable)) {
                throw new Exception("Invalid Sparql variable:" + variable);
            }

            predicate = predicateArg;
            value = pairValueType;
            // isVariable = isVariableArg;
            variableName = variable;
        }

        // This is a helper for tests.
        /*
        public WhereEquality(String predicateArg, String valueStr, String variable) throws Exception {
            this(predicateArg, new ValueTypePair(valueStr, Solution.ValueType.STRING_TYPE), variable);
        }
        */

        public WhereEquality(String predicateArg, String variable) throws Exception {
            this(predicateArg, (ValueTypePair)null, variable);
        }

        /**
         * This is useful for building a WMI query only.
         * TODO: Move this is WMI-specific code.
         * @return
         */
        public String ToEqualComparison() {
            // Real examples in Powershell - they are quite fast:
            // PS C:> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Antecedent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\DriverStore\\\\FileRepository\\\\iigd_dch.inf_amd64_ea63d1eddd5853b5\\\\igdinfo64.dll\""'
            // PS C:> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Dependent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"32308\""'

            if(value == null) {
                // This should not happen.
                logger.debug("Value of " + predicate + " is null");
            }
            String escapedValue = value.toValueString().replace("\\", "\\\\").replace("\"", "\\\"");
            return "" + predicate + "" + " = \"" + escapedValue + "\"";
        }
    };

    /** Checks if a selecter or a getter can be used for a QueryData. */
    public boolean CompatibleQuery(String whereClassName, Set<String> whereColumns, Set<String> availableColumns)
    {
        // It must be for the goo=d class, and be able to return the needed columns.
        if(! ColumnsSubsetOf(whereClassName, availableColumns)) {
            return false;
        }

        // The lookup is based on properties given in a "where" test. So this column must be given.
        Set<String> queryWhereColumns = whereTests.stream().map(x -> x.predicate).collect(Collectors.toSet());
        if(! queryWhereColumns.equals(whereColumns)) {
            return false;
        }
        return true;
    }

    public boolean ColumnsSubsetOf(String whereClassName, Set<String> selectedColumns) {
        if(! className.equals(whereClassName)) {
            return false;
        }
        Set<String> requiredColumns = queryColumns.keySet();
        logger.debug("selectedColumns="+selectedColumns);
        logger.debug("requiredColumns="+requiredColumns);
        return selectedColumns.containsAll(requiredColumns);
    }



    /** There should be a handful of elements so looping is OK.
     *
     * @param columnName "Handle", "Name", "PartComponent" etc...
     * @return
     */
    public ValueTypePair GetWhereValue(String columnName) {
        for(WhereEquality whereElement : whereTests) {
            if(whereElement.predicate.equals(columnName)) {
                return whereElement.value;
            }
        }
        return null;
    }

    public String ColumnToVariable(String columnName) {
        return queryColumns.get(columnName);
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
        HashMap<String, Sample> statistics;
        long startTime = -1;

        Statistics() {
            ResetAll();
        }

        void ResetAll() {
            statistics = new HashMap<>();
        }

        void StartSample() {
            startTime = System.currentTimeMillis();
        }

        void FinishSample(String objectPath, Set<String> columns) {
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

        void DisplayAll() {
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
                    // Writing one extreme performance issue is enough.
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
            List<QueryData.WhereEquality> wheres) throws Exception {
        WmiOntology.CheckValidNamespace(wmiNamespace);
        mainVariable = variable;
        isMainVariableAvailable = mainVariableAvailable;
        if(wmiClassName.contains("#")) {
            throw new Exception("Invalid class:" + wmiClassName);
        }
        namespace = wmiNamespace;
        className = wmiClassName;
        // Uniform representation of no columns selected (except the path).
        if(columns == null)
            queryColumns = new TreeMap<>();
        else
            queryColumns = new TreeMap<>(columns);
        SwapWheres(wheres);

        if(isMainVariableAvailable)
            classGetter = GenericProvider.FindGetter(this);
        else
            classBaseSelecter = GenericProvider.FindSelecter(this);
    }

    /** This is used for initialising when adding the WHERE tests without values,
     * and later when calculting a QueryData for a specific context.
     * @param wheres
     * @return
     */
    List<QueryData.WhereEquality> SwapWheres(List<QueryData.WhereEquality> wheres) {
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

    public String BuildWqlQuery() {
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
                    .map(QueryData.WhereEquality::ToEqualComparison)
                    .collect(Collectors.joining(" and "));
            wqlQuery += whereClause;
        }
        return wqlQuery;
    }

    public void StartSampling() {
        statistics.StartSample();
    }

    public void FinishSampling() {
        // This adds some extra information about the execution.
        Set<String> columnsWhere = whereTests.stream()
                .map(entry -> entry.predicate)
                .collect(Collectors.toSet());
        statistics.FinishSample(className, columnsWhere);
    }

    public void FinishSampling(String objectPath) {
        statistics.FinishSample(objectPath, queryColumns.keySet());
    }

    public void DisplayStatistics() {
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
        statistics.DisplayAll();
    }

    public void ResetStatistics() {
        statistics.ResetAll();
    }
}

