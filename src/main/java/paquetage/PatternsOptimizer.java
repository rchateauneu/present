package paquetage;

import java.util.List;

public class PatternsOptimizer {
    double PatternsCost(List<ObjectPattern> patterns) throws Exception {
        /*
        How to index actual performance of a query ?
        - Save performance of each query before the end: So if the loop is very, very slow, it can be stopped
        on the fly, but performance are already available.
        - Index with the Where columns: A new query must have all of them.
        Each perf sample is index by each of its query.
        So when a new query must be evaluated, take its columns, and do the intersection of the samples
        indexed by these columns.
         */
        return -1.0;
    }

    /**
     * This reorders patterns so their nested execution is faster.
     *
     * Based on providers : The class, input and output columns.
     * The order of BGP patterns determines the order of query data, and also the input and output columns.
     * The columns of each QueryData determines the providers.
     *
     * So, it is not possible in advance to know which providers are chosen.
     * Therefore, it is an iterative process.
     * @param patterns
     * @throws Exception
     */
    public void ReorderPatterns(List<ObjectPattern> patterns) throws Exception {

    }
}
