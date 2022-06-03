package paquetage;

import java.util.*;
import java.util.stream.Collectors;

/**
 * To be used when reconstructing a WQL query.
 */
public class QueryData {
    String className;
    String mainVariable;
    boolean isMainVariableAvailable;
    // It maps a column name to a variable and is used to copy the column values to the variables.
    // This sorted container guarantees the order to help comparison.
    SortedMap<String, String> queryColumns;
    List<WmiSelecter.WhereEquality> queryWheres;

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

    QueryData(String wmiClassName, String variable, boolean mainVariableAvailable, Map<String, String> columns, List<WmiSelecter.WhereEquality> wheres) throws Exception {
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
        // Anyway, it seems that __PATH is calculated only when explicitely requested.
        if (queryColumns.isEmpty())
            columns += "__PATH";
        else
            columns += ", __PATH";
        String wqlQuery = "Select " + columns + " from " + className;

        if( (queryWheres != null) && (! queryWheres.isEmpty())) {
            wqlQuery += " where ";
            String whereClause = (String)queryWheres.stream()
                    .map(WmiSelecter.WhereEquality::ToEqualComparison)
                    .collect(Collectors.joining(" and "));
            wqlQuery += whereClause;
        }
        return wqlQuery;
    }
}

