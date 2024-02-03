package paquetage;

// See "query explain"
// https://rdf4j.org/documentation/programming/repository/

import java.util.*;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Statement;

// https://www.programcreek.com/java-api-examples/?api=org.eclipse.rdf4j.query.parser.ParsedQuery

// https://github.com/apache/rya/blob/master/sail/src/main/java/org/apache/rya/rdftriplestore/inference/IntersectionOfVisitor.java#L54

/**
 * This extracts the BGP (basic graph patterns) from a Sparql query.
 *
 * This works only for very simple, flat Sparql queries.
 *
 * FIXME: Obsolete and replaced with SparqlBGPTreeExtractor.
 */
public class SparqlBGPExtractor {
    final static private Logger logger = Logger.getLogger(SparqlBGPExtractor.class);

    public Set<String> bindings;

    // This should be private except for tests.
    public List<ObjectPattern> patternsMap;

    private SparqlBGPTreeExtractor treeExtractor;

    public SparqlBGPExtractor(String input_query, boolean withExecution) throws Exception {
        treeExtractor = new SparqlBGPTreeExtractor(input_query);

        patternsMap = treeExtractor.TopLevelPatternsTestHelper();
        if(patternsMap == null) {
            throw new RuntimeException("Could not get patterns");
        }

        // FIXME: This is an unordered set. What about an union without BIND() statements, if several variables ?
        bindings = treeExtractor.bindings;
    }

    public SparqlBGPExtractor(String input_query) throws Exception {
        this(input_query, false);
    }

    // This is only for testing.
    public ObjectPattern FindObjectPattern(String variable) {
        for(ObjectPattern objPatt : patternsMap) {
            if(variable.equals(objPatt.variableName)) {
                return objPatt;
            }
        }
        return null;
    }

    /**
     * Returns the BGPs as a list whose order is guaranteed.
     * @return
     */
    List<ObjectPattern> patternsAsArray() {
        // FIXME: Duplicate code with JoinExpressionNode
        ArrayList<ObjectPattern> patternsArray = new ArrayList<ObjectPattern>(patternsMap);
        ObjectPattern.Sort(patternsArray);
        // patternsArray.sort(Comparator.comparing(s -> s.VariableName));
        return patternsArray;
    }

    /** This generates the triples from substituting the variables of the patterns by their values.
     * This is only for testing "flat" sparql queries, with an expression tree like, for example:
     *
     * class paquetage.ProjectionExpressionNode Binding=[dir_name]
     * 	class paquetage.JoinExpressionNode 2 statement(s)
     *
     * class paquetage.ProjectionExpressionNode Binding=[dir_name, dir_caption]
     * 	class paquetage.JoinExpressionNode 3 statement(s)
     *
     * @param rows List of a map of variables to values, and these are the variables of the patterns.
     * @return Triples ready to be inserted in a repository.
     * @throws Exception
     */
    List<Statement> GenerateStatements(Solution rows) throws Exception {
        logger.warn("After this operation, the extractor is unusable. For tests only.");
        treeExtractor.SetTopLevelSolutionTestHelper(rows);
        List<Statement> statements = treeExtractor.SolutionToStatements();
        return statements;
    }

    /*
    List<Statement> GenerateStatements() throws Exception {
        List<Statement> statements = treeExtractor.SolutionToStatements();
        return statements;
    }
    */
}
