package paquetage;

// See "query explain"
// https://rdf4j.org/documentation/programming/repository/

import java.util.*;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.query.algebra.*;
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor;

// https://www.programcreek.com/java-api-examples/?api=org.eclipse.rdf4j.query.parser.ParsedQuery

// https://github.com/apache/rya/blob/master/sail/src/main/java/org/apache/rya/rdftriplestore/inference/IntersectionOfVisitor.java#L54

interface InterfaceExpressionNode {
    Solution Evaluate() ;
}

abstract class BaseExpressionNode implements InterfaceExpressionNode {
    final static protected Logger logger = Logger.getLogger(BaseExpressionNode.class);
    BaseExpressionNode parent = null;
    BaseExpressionNode(BaseExpressionNode parent) {
        if((parent != null) && (parent instanceof JoinExpressionNode)) {
        }
        this.parent = parent;
        if(parent != null)
            parent.children.add(this);
    }

    protected List<BaseExpressionNode> children = new ArrayList<>();

    public String toString() {
        return "";
    }

    String toStringRecursive(String margin) {
        String result = margin + this.getClass().toString() + toString() + "\n";
        String newMargin = margin + "\t";
        for(BaseExpressionNode child: children) {
            result += child.toStringRecursive(newMargin);
        }
        return result;
    }
}


/** For the nodes which might contain one or several StatementPattern. */
class JoinExpressionNode extends BaseExpressionNode {
    final static protected Logger logger = Logger.getLogger(JoinExpressionNode.class);
    JoinExpressionNode(BaseExpressionNode parent) {
        super(parent);
        logger.debug("JoinExpressionNode. Parent type=" + parent.getClass().getName());
    }

    public Map<String, ObjectPattern> patternsMap = null;

    // These are the raw patterns extracted from the query. They may contain variables which are not defined
    // in the WMI evaluation. In this case, they are copied as is.
    // They might contain one variable defined by WMI, and another one, defined by the second Sparql evaluation.
    // In this case, they are replicated for each value found by WMI.
    private List<StatementPattern> visitorPatternsRaw = new ArrayList<>();

    void AddPattern(StatementPattern statementPattern) {
        logger.debug("Add one pattern to " + visitorPatternsRaw.size());
        visitorPatternsRaw.add(statementPattern);
    }

    // Must be called after parsing and before evaluation.
    void JoinBGPPartition() {
        if(patternsMap != null) {
            throw new RuntimeException("patternsMap should not be set twice.");
        }
        patternsMap = ObjectPattern.PartitionBySubject(visitorPatternsRaw);
        logger.debug("Pattern keys:" + patternsMap.keySet());
    }

    Solution localSolution = null;

    public Solution Evaluate() {
        logger.debug("children.size()=" + children.size() + " visitorPatternsRaw.size()=" + visitorPatternsRaw.size());
        try {
            SparqlTranslation patternSparql = new SparqlTranslation(patternsAsArray());
            /*
            TODO: This solution is returned only for testing.
            TODO: It is possible to get rid of it and keep only the solutions in nodes which have a BGP.
             */
            localSolution = patternSparql.ExecuteToRows();
            logger.debug("Solution:" + localSolution.size() + " Header=" + localSolution.header());

            // TODO: Avoid this cartesian product by merging BGPs in lower nodes.
            for(BaseExpressionNode child : children){
                Solution subSolution = child.Evaluate();
                Solution cartesianProduct = localSolution.CartesianProduct(subSolution);
                localSolution = cartesianProduct;
            }
            return localSolution;
        }
        catch(Exception exc) {
            throw new RuntimeException(exc);
        }
    }

    public String toString() {
        return " " + visitorPatternsRaw.size() + " statement(s)";
    }

    /** This generates the triples from substituting the variables of the patterns by their values.
     *
     * @param rows List of a map of variables to values, and these are the variables of the patterns.
     * @return Triples ready to be inserted in a repository.
     * @throws Exception
     */
    void GenerateStatements(List<Statement> generatedStatements, Solution rows) throws Exception {
        logger.debug("Visitor patterns number:" + visitorPatternsRaw.size());
        logger.debug("Rows number:" + rows.size());
        logger.debug("Generated statements number before:" + generatedStatements.size());
        for(StatementPattern statementPattern : visitorPatternsRaw) {
            // rows.PatternToStatements(generatedStatements, statementPattern);
            localSolution.PatternToStatements(generatedStatements, statementPattern);
            logger.debug("Generated statements number after:" + generatedStatements.size());
        }

        logger.debug("Generated statements number:" + generatedStatements.size());
    }

    /**
     * Returns the BGPs as a list whose order is guaranteed.
     * @return
     */
    List<ObjectPattern> patternsAsArray() {
        if(patternsMap == null) {
            throw new RuntimeException("patternsMap is null");
        }
        ArrayList<ObjectPattern> patternsArray = new ArrayList<ObjectPattern>(patternsMap.values());
        patternsArray.sort(Comparator.comparing(s -> s.VariableName));
        return patternsArray;
    }
} // JoinExpressionNode

// TODO: Also add LeftJoin, Intersection

// TODO: Binding: Rename some columns in Solution ?

class ProjectionExpressionNode extends BaseExpressionNode {
    final static protected Logger logger = Logger.getLogger(ProjectionExpressionNode.class);
    Projection projectionNode;
    ProjectionExpressionNode(BaseExpressionNode parent, Projection visitedProjection) {
        super(parent);
        //logger.debug("ProjectionExpressionNode. Parent type=" + parent.getClass().getName());
        projectionNode = visitedProjection;
    }

    public Solution Evaluate() {
        projectionNode.getBindingNames();
        logger.debug("projectionNode.getBindingNames():" + projectionNode.getBindingNames());
        if(children.size() != 1) {
            throw new RuntimeException("There must be one child only:" + children.size());
        }
        logger.debug("children.size():" + children.size());
        Solution solution = children.get(0).Evaluate();
        logger.debug("Solution:" + solution.size() + " Header=" + solution.header());
        /*
        TODO: Keep only some columns.
         */
        return solution;
    }

    public String toString() {
        return " Binding=" + projectionNode.getBindingNames();
    }
};

class UnionExpressionNode extends BaseExpressionNode {
    final static protected Logger logger = Logger.getLogger(UnionExpressionNode.class);
    private Union unionNode;
    UnionExpressionNode(BaseExpressionNode parent, Union visitedUnionNode) {
        super(parent);
        unionNode = visitedUnionNode;
    }
    public Solution Evaluate() {
        logger.debug("Children:" + children.size());
        Solution solution = new Solution();
        for(BaseExpressionNode expressionNode: children) {
            Solution childSolution = expressionNode.Evaluate();
            logger.debug("childSolution:" + childSolution.size() + " Header=" + childSolution.header());
            /* TODO: Instead of creating a new solution, why not pass a visitor to immediately generate triples ?
            TODO: On the other hand, it is more difficult to test.
            */

            // FIXME: What about bindings, i.e. renaming of columns ?

            solution.Append(childSolution);
        }
        logger.debug("Solution:" + solution.size() + " Header=" + solution.header());
        return solution;
    }
};

/** This is used to extract the BGPs of a Sparql query.
 *
 */
class PatternsVisitor extends AbstractQueryModelVisitor {
    final static private Logger logger = Logger.getLogger(PatternsVisitor.class);

    private List<StatementPattern> visitedStatementPatterns = new ArrayList<StatementPattern>();

    public String toString() {
        return parent.toStringRecursive("");
    }

    void GenericReport(QueryModelNode queryModelNode) {
        System.out.println("-----------------------------------------------------------------------------");
    }

    BaseExpressionNode parent = null;

    @Override
    public void meet(Union unionNode) throws Exception {
        logger.debug("Union=\n" + unionNode);
        GenericReport(unionNode);
        BaseExpressionNode previousParent = parent;
        // currentStatementsGatherer = new UnionExpressionNode(parent, unionNode);
        UnionExpressionNode currentUnion = new UnionExpressionNode(parent, unionNode);
        parent = currentUnion;
        super.meet(unionNode);
        if(previousParent != null)
            parent = previousParent;
    }

    /*** This flattens the joins. */
    @Override
    public void meet(Join joinNode) throws Exception {
        logger.debug("Join=\n" + joinNode);
        GenericReport(joinNode);

        BaseExpressionNode previousParent = parent;
        boolean isParentJoin = parent instanceof JoinExpressionNode;
        JoinExpressionNode nextParent;
        // If the ancestor is also a join, reuse it instead of creating a new one.
        if(isParentJoin) {
            nextParent = (JoinExpressionNode)parent;
        } else {
            nextParent = new JoinExpressionNode(parent);
        }
        parent = nextParent;
        super.meet(joinNode);
        if(previousParent != null)
            parent = previousParent;
    }

    @Override
    public void meet(Projection projectionNode) throws Exception {
        logger.debug("Projection=\n" + projectionNode);
        GenericReport(projectionNode);
        BaseExpressionNode previousParent = parent;
        ProjectionExpressionNode currentProjection = new ProjectionExpressionNode(parent, projectionNode);
        parent = currentProjection;
        super.meet(projectionNode);
        if(previousParent != null)
            parent = previousParent;
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
        logger.debug("StatementPattern=" + statementPatternNode);
        GenericReport(statementPatternNode);

        // If the parent is not a join, create a join child.
        if(parent instanceof ProjectionExpressionNode) {
            JoinExpressionNode joinParent = new JoinExpressionNode(parent);
            // TODO: Is it really necessary to change the parent ?
            parent = joinParent;
            JoinExpressionNode realJoin = (JoinExpressionNode)parent;
            realJoin.AddPattern(statementPatternNode);
        } else if(parent instanceof UnionExpressionNode) {
            // Do not change the parent. This creates a single "Join" node for a single pattern.
            JoinExpressionNode joinParent = new JoinExpressionNode(parent);
            joinParent.AddPattern(statementPatternNode);
        } else if(! (parent instanceof JoinExpressionNode)) {
            throw new RuntimeException("Invalid parent type:" + parent.getClass().getName());
        } else {
            JoinExpressionNode realJoin = (JoinExpressionNode)parent;
            realJoin.AddPattern(statementPatternNode);
        }
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

    public List<StatementPattern> patterns() {
        return visitedStatementPatterns;
    }

    private void PartitionBGPAux(BaseExpressionNode node) {
        logger.debug("Class:" + node.getClass().getName() + " Node:" + node.children.size() + " children");
        if(node instanceof JoinExpressionNode) {
            JoinExpressionNode joinNode = (JoinExpressionNode)node;
            if(!node.children.isEmpty()) {
                logger.warn("Join has children - if projection, it can be optimised.");
            }
            for(BaseExpressionNode child : node.children) {
                if (!(child instanceof ProjectionExpressionNode)) {
                    // throw new RuntimeException("Join node should have only Projection as children, not:" + child.getClass().getName());
                    logger.warn("Join node should have only Projection as children, not:" + child.getClass().getName());
                }
            }
            joinNode.JoinBGPPartition();
        }
        for(BaseExpressionNode child : node.children) {
            PartitionBGPAux(child);
        }
    }

    void PartitionBGP() {
        logger.debug("PartitionBGP Class:" + getClass().getName());
        PartitionBGPAux(parent);
    }

    private void GenerateStatementsFromTreeAux(Solution rows, List<Statement> generatedStatements, BaseExpressionNode node) throws Exception {
        logger.debug("Node:" + node.getClass().getName() + " Solution:" + rows.size() + " rows. generatedStatements:" + generatedStatements.size() + " statements.");
        if(node instanceof JoinExpressionNode) {
            if(!node.children.isEmpty()) {
                logger.warn("Join has children - if projection, it can be optimised.");
            }
            JoinExpressionNode joinNode = (JoinExpressionNode)node;
            joinNode.GenerateStatements(generatedStatements, rows);
        }
        logger.debug("node.children:" + node.children.size());
        for(BaseExpressionNode child : node.children) {
            GenerateStatementsFromTreeAux(rows, generatedStatements,child);
        }
    }

    List<Statement> GenerateStatementsFromTree(Solution rows) throws Exception {
        logger.debug("Solution:" + rows.size() + " rows.");
        List<Statement> generatedStatements = new ArrayList<>();
        GenerateStatementsFromTreeAux(rows, generatedStatements, parent);
        return generatedStatements;
    }
} // PatternsVisitor


/**
 * This extracts the BGP (basic graph patterns) from a Sparql query.
 */
public class SparqlBGPTreeExtractor {
    final static private Logger logger = Logger.getLogger(SparqlBGPTreeExtractor.class);

    public Set<String> bindings;

    // These are the raw patterns extracted from the query. They may contain variables which are not defined
    // in the WMI evaluation. In this case, they are copied as is.
    // They might contain one variable defined by WMI, and another one, defined by the second Sparql evaluation.
    // In this case, they are replicated for each value found by WMI.
    // private List<StatementPattern> visitorPatternsRaw;

    public SparqlBGPTreeExtractor(String input_query) throws Exception {
        ParseQuery(input_query);
        String extractorString = patternsVisitor.toString();
        System.out.println("Extractor:\n" + extractorString);
        patternsVisitor.PartitionBGP();
    }

    private PatternsVisitor patternsVisitor = new PatternsVisitor();

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
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        tupleExpr.visit(patternsVisitor);
        System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        System.out.println(tupleExpr);
        System.out.println("=============================================================================");
    }

    Solution EvaluateSolution() {
        Solution solution = patternsVisitor.parent.Evaluate();
        logger.debug("Evaluated solution:" + solution.size() + " rows.");
        return solution;
    }

    List<Statement> SolutionToStatements(Solution rows) throws Exception {
        logger.debug("Solution:" + rows.size() + " rows.");
        return patternsVisitor.GenerateStatementsFromTree(rows);
    }


}
