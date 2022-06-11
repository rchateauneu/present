package paquetage;

import java.util.List;

public class PatternsOptimizer {
    double PatternsCost(List<ObjectPattern> patterns) throws Exception {
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
