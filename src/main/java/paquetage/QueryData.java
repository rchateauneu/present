package paquetage;

import java.util.*;
import java.util.stream.Collectors;

/**
 * To be used when reconstructing a WQL query.
 */
public class QueryData {
    String className;

    // Name of the Sparql variable containing the object. Its rdf:type is a CIM class.
    String mainVariable;

    // After sorting the QueryData in the right order before recursive evaluation, this indicates
    // that the the main variable - the variable representing the object - is evaluated by the nesting queries.
    boolean isMainVariableAvailable;

    // It maps a column name to a Sparql variable and is used to copy the column values to the variables.
    // This sorted container guarantees the order to help comparison in tests.
    SortedMap<String, String> queryColumns;

    // To be appended in the WHERE clause of a WMI query.
    List<WhereEquality> queryWheres;

    static public class WhereEquality {
        // This is a member of WMI class and used to build the WHERE clause of a WQL query.
        public String predicate;

        // This is the value or a variable name, compared with a WMI class member in a WQL query.
        public String value;

        // Tells if this is a Sparql variable (which must be evaluated in the nesting WQL queries) or a constant.
        boolean isVariable;

        public WhereEquality(String predicateArg, String valueStr, boolean isVariableArg) throws Exception {
            if(predicateArg.contains("#")) {
                // This ensures that the IRI of the RDF node is stripped of its prefix.
                throw new Exception("Invalid class:" + predicateArg);
            }

            predicate = predicateArg;
            value = valueStr;
            isVariable = isVariableArg;
        }

        public WhereEquality(String predicateArg, String valueStr) throws Exception {
            this(predicateArg, valueStr, false);
        }

        /**
         * This is useful for building a WMI query only.
         * @return
         */
        public String ToEqualComparison() {
            // Real examples in Powershell - they are quite fast:
            // PS C:> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Antecedent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\DriverStore\\\\FileRepository\\\\iigd_dch.inf_amd64_ea63d1eddd5853b5\\\\igdinfo64.dll\""'
            // PS C:> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Dependent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"32308\""'

            // key = "http://www.primhillcomputers.com/ontology/survol#Win32_Process"

            if(value == null) {
                // This should not happen.
                System.out.println("Value of " + predicate + " is null");
            }
            String escapedValue = value.replace("\\", "\\\\").replace("\"", "\\\"");
            return "" + predicate + "" + " = \"" + escapedValue + "\"";
        }
    };

    public boolean CompatibleQuery(String whereClassName, Set<String> whereColumns)
    {
        if(! className.equals(whereClassName)) {
            return false;
        }
        Set<String> queryWhereColumns = queryWheres.stream().map(x -> x.predicate).collect(Collectors.toSet());

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
        return selectedColumns.containsAll(requiredColumns);
    }



    /** There should be a handful of elements so looping is OK.
     *
     * @param columnName "Handle", "Name", "PartComponent" etc...
     * @return
     */
    public String GetWhereValue(String columnName) {
        for(WhereEquality whereElement : queryWheres) {
            if(whereElement.predicate.equals(columnName)) {
                return whereElement.value;
            }
        }
        return null;
    }

    public String ColumnToVariable(String columnName) {
        for( Map.Entry<String, String> column : queryColumns.entrySet()) {
            if (column.getKey().equals(columnName)) {
                return column.getValue();
            }
        }
        return null;
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
        long startTime;

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
                if((currentValue.elapsed >= maxElapsed) || (currentValue.count >= maxCount))
                    System.out.println(entry.getKey() + " " + (currentValue.elapsed / 1000.0) + " secs " + currentValue.count + " calls");
            }
            System.out.println("TOTAL" + " " + (totalElapsed / 1000.0) + " secs " + totalCount + " calls " + statistics.size() + " lines");
        }
    }

    public Statistics statistics = new Statistics();

    QueryData(String wmiClassName, String variable, boolean mainVariableAvailable, Map<String, String> columns, List<QueryData.WhereEquality> wheres) throws Exception {
        mainVariable = variable;
        isMainVariableAvailable = mainVariableAvailable;
        if(wmiClassName.contains("#")) {
            throw new Exception("Invalid class:" + wmiClassName);
        }
        className = wmiClassName;
        // Uniform representation of no columns selected (except the path).
        if(columns == null)
            queryColumns = new TreeMap<>();
        else
            queryColumns = new TreeMap<>(columns);
        // Uniform representation of an empty where clause.
        if(wheres == null)
            queryWheres = new ArrayList<>();
        else
            // This sorts the where tests so the order is always the same and helps comparisons.
            // This is not a problem performance-wise because there is only one such element per WQL query.
            queryWheres = wheres.stream().sorted(Comparator.comparing(x -> x.predicate)).collect(Collectors.toList());
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

        if( (queryWheres != null) && (! queryWheres.isEmpty())) {
            wqlQuery += " where ";
            String whereClause = (String)queryWheres.stream()
                    .map(QueryData.WhereEquality::ToEqualComparison)
                    .collect(Collectors.joining(" and "));
            wqlQuery += whereClause;
        }
        return wqlQuery;
    }
}

