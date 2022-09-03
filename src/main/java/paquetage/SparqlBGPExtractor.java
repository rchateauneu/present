package paquetage;

// See "query explain"
// https://rdf4j.org/documentation/programming/repository/

import java.util.*;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.algebra.*;
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor;

// https://www.programcreek.com/java-api-examples/?api=org.eclipse.rdf4j.query.parser.ParsedQuery

// https://github.com/apache/rya/blob/master/sail/src/main/java/org/apache/rya/rdftriplestore/inference/IntersectionOfVisitor.java#L54

/**
 * This extracts the BGP (basic graph patterns) from a Sparql query.
 */
public class SparqlBGPExtractor {
    final static private Logger logger = Logger.getLogger(SparqlBGPExtractor.class);

    public Set<String> bindings;

    // This should be private except for tests.
    // Ca va etre un expressionTree
    public Map<String, ObjectPattern> patternsMap;

    // These are the raw patterns extracted from the query. They may contain variables which are not defined
    // in the WMI evaluation. In this case, they are copied as is.
    // They might contain one variable defined by WMI, and another one, defined by the second Sparql evaluation.
    // In this case, they are replicated for each value found by WMI.
    private List<StatementPattern> visitorPatternsRaw;

    public SparqlBGPExtractor(String input_query) throws Exception {
        patternsMap = null;
        ParseQuery(input_query);
    }

    /**
     * Returns the BGPs as a list whose order is guaranteed.
     * @return
     */
    List<ObjectPattern> patternsAsArray() {
        ArrayList<ObjectPattern> patternsArray = new ArrayList<ObjectPattern>(patternsMap.values());
        patternsArray.sort(Comparator.comparing(s -> s.VariableName));
        return patternsArray;
    }

    /** This examines all statements of the Sparql query and gathers them based on a common subject.
     *
     * @param sparql_query
     * @throws Exception
     */
    private void ParseQuery(String sparql_query) throws Exception {
        logger.debug("Parsing:\n" + sparql_query);
        SPARQLParser parser = new SPARQLParser();
        ParsedQuery pq = parser.parseQuery(sparql_query, null);
        TupleExpr tupleExpr = pq.getTupleExpr();
        // FIXME: This is an unordered set. What about an union without BIND() statements, if several variables ?
        bindings = tupleExpr.getBindingNames();
        PatternsVisitor myVisitor = new PatternsVisitor();
        tupleExpr.visit(myVisitor);
        visitorPatternsRaw = myVisitor.patterns();
        patternsMap = ObjectPattern.Partition(visitorPatternsRaw);
        logger.debug("Generated patterns: " + Long.toString(patternsMap.size()));
    }

    /** This generates the triples from substituting the variables of the patterns by their values.
     *
     * @param rows List of a map of variables to values, and these are the variables of the patterns.
     * @return Triples ready to be inserted in a repository.
     * @throws Exception
     */
    List<Statement> GenerateStatements(Solution rows) throws Exception {
        List<Statement> generatedStatements = new ArrayList<>();

        logger.debug("Visitor patterns number:" + visitorPatternsRaw.size());
        logger.debug("Rows number:" + rows.size());
        for(StatementPattern myPattern : visitorPatternsRaw) {
            rows.PatternToStatements(generatedStatements, myPattern);
        }

        logger.debug("Generated statements number:" + generatedStatements.size());
        return generatedStatements;
    }

    /** This is used to extract the BGPs of a Sparql query.
     *
     */
    static class PatternsVisitor extends AbstractQueryModelVisitor {

        private List<StatementPattern> visitedStatementPatterns = new ArrayList<StatementPattern>();

        // @Override
        /*
        public void meet(TripleRef node) {
            // FIXME: Why is it disabled and not overriden ?
            logger.warn("TripleRef=" + node);
        }
        */

        @Override
        // public void meet(org.eclipse.rdf4j.query.algebra.Namespace namespaceNode) throws Exception {
        public void meet(Namespace namespaceNode) throws Exception {
            // FIXME: Why is it disabled and not overriden ?
            logger.debug("Namespace=" + namespaceNode);
            super.meet(namespaceNode);
        }

        @Override
        public void meet(ArbitraryLengthPath arbitraryLengthPathNode) {
            /* This correctly parses paths like:
            "?service1 (^cimv2:Win32_DependentService.Dependent/cimv2:Win32_DependentService.Antecedent)+ ?service2"
            and creates anonymous nodes, but this is not allowed yet. Arbitrary length paths need
            a special exploration of WMI instances.
            */
            logger.debug("ArbitraryLengthPath=" + arbitraryLengthPathNode);
            throw new RuntimeException("ArbitraryLengthPath are not allowed yet.");
        }

        @Override
        public void meet(Service serviceNode) {
            // Do not store the statements of the serviceNode,
            // because it does not make sense to preload its content with WMI.
            logger.debug("Service=" + serviceNode);
        }

        @Override
        public void meet(StatementPattern statementPatternNode) {
            /* Store this statement. At the end, they are grouped by subject, and the associated WMI instances
            are loaded then inserted in the repository, and then the original Sparql query is run.

            This correctly parses paths like:
            "?service1 ^cimv2:Win32_DependentService.Dependent/cimv2:Win32_DependentService.Antecedent ?service2"
            and creates anonymous nodes, but this is not allowed yet.
            However, anonymous nodes in fixed-length paths should be processed by creating an anonymous variable.
            */
            visitedStatementPatterns.add(statementPatternNode.clone());
        }

        public List<StatementPattern> patterns() {
            return visitedStatementPatterns;
        }
    }
}
