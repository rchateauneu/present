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
            if(!(this instanceof ProjectionExpressionNode)) {
                throw new RuntimeException("Join must not have children except Projection:" + this.getClass().getName());
            }
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
    JoinExpressionNode(BaseExpressionNode parent) {
        super(parent);
    }

    public Map<String, ObjectPattern> patternsMap;

    // These are the raw patterns extracted from the query. They may contain variables which are not defined
    // in the WMI evaluation. In this case, they are copied as is.
    // They might contain one variable defined by WMI, and another one, defined by the second Sparql evaluation.
    // In this case, they are replicated for each value found by WMI.
    private List<StatementPattern> visitorPatternsRaw = new ArrayList<>();

    void AddPattern(StatementPattern statementPattern) {
        visitorPatternsRaw.add(statementPattern);
        logger.debug("Poorly implemented yet");
    }

    // Must be called after parsing and before evaluation.
    void FinalizeStuff() {
        patternsMap = ObjectPattern.PartitionBySubject(visitorPatternsRaw);
    }
    public Solution Evaluate() {
        try {
            SparqlTranslation patternSparql = new SparqlTranslation(patternsAsArray());
            Solution solution = patternSparql.ExecuteToRows();
            logger.debug("Solution:" + solution.size());
            return solution;
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
        for(StatementPattern myPattern : visitorPatternsRaw) {
            rows.PatternToStatements(generatedStatements, myPattern);
        }

        logger.debug("Generated statements number:" + generatedStatements.size());
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
}

// TODO: Il faudra aussi ajouter LeftJoin, Intersection

// FIXME: Ca fait filtrage et join: C'est idiot.
class ProjectionExpressionNode extends BaseExpressionNode {
    Projection projectionNode;
    ProjectionExpressionNode(BaseExpressionNode parent, Projection visitedProjection) {
        super(parent);
        projectionNode = visitedProjection;
    }

    public Solution Evaluate() {
        projectionNode.getBindingNames();
        if(children.size() != 1) {
            throw new RuntimeException("Filtrer : Il ne doit y avoir qu'un seul child:" + children.size());
        }
        Solution solution = children.get(0).Evaluate();
        logger.debug("Solution:" + solution.size());
        return solution;
    }

    public String toString() {
        return " Binding=" + projectionNode.getBindingNames();
    }
};

/** Several unions together : No need to nest them.
 * On les evalue separement et on met les resultats a la file dans une Solution. */
class UnionExpressionNode extends BaseExpressionNode {
    private Union unionNode;
    UnionExpressionNode(BaseExpressionNode parent, Union visitedUnionNode) {
        super(parent);
        unionNode = visitedUnionNode;
    }
    public Solution Evaluate() {
        Solution solution = new Solution();
        for(BaseExpressionNode expressionNode: children) {
            Solution childSolution = expressionNode.Evaluate();
            solution.Append(childSolution);
        }
        logger.debug("Solution:" + solution.size());
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
            /*
            if(queryModelNode.getParentNode() == null) {
                logger.debug("Parent is null");
            } else {
                logger.debug("Parent is:" + queryModelNode.getParentNode());
            }
            */
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
            */
        logger.debug("StatementPattern=" + statementPatternNode);
        GenericReport(statementPatternNode);
        // visitedStatementPatterns.add(statementPatternNode.clone());

        /* Le parent courant est Projection ou Join.
        * Si c est une projection, il faut creer un Join. */
        if(parent instanceof ProjectionExpressionNode) {
            JoinExpressionNode joinParent = new JoinExpressionNode(parent);
            parent = joinParent;
        } else if(! (parent instanceof JoinExpressionNode)) {
            throw new RuntimeException("Invalid parent type:" + parent.getClass().getName());
        }
        JoinExpressionNode realJoin = (JoinExpressionNode)parent;
        realJoin.AddPattern(statementPatternNode);
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
        // J'imagine que l'impementation par defaut va visiter les noeuds inferieurs,
        // ce qu'on ne veut pas faire.
        logger.debug("Service=" + serviceNode);
    }

    public List<StatementPattern> patterns() {
        return visitedStatementPatterns;
    }

    void FinalizeStuffAux(BaseExpressionNode node) {
        if(node instanceof JoinExpressionNode) {
            for(BaseExpressionNode child : node.children) {
                if (!(child instanceof ProjectionExpressionNode)) {
                    throw new RuntimeException("Join node should have only Projection as children.");
                }
            }
            JoinExpressionNode joinNode = (JoinExpressionNode)node;
            joinNode.FinalizeStuff();
            return;
        }
        for(BaseExpressionNode child : node.children) {
            FinalizeStuffAux(child);
        }
    }

    void FinalizeStuff() {
        FinalizeStuffAux(parent);
    }

    private void GenerateStatementsFromTreeAux(Solution rows, List<Statement> generatedStatements, BaseExpressionNode node) throws Exception {
        logger.debug("rows:" + rows.size());
        if(node instanceof JoinExpressionNode) {
            if(!node.children.isEmpty()) {
                throw new RuntimeException("Join node should not have children");
            }
            JoinExpressionNode joinNode = (JoinExpressionNode)node;
            joinNode.GenerateStatements(generatedStatements, rows);
            joinNode.FinalizeStuff();
            return;
        }
        logger.debug("node.children:" + node.children.size());
        for(BaseExpressionNode child : node.children) {
            FinalizeStuffAux(child);
        }
    }

    List<Statement> GenerateStatementsFromTree(Solution rows) throws Exception {
        logger.debug("rows:" + rows.size());
        List<Statement> generatedStatements = new ArrayList<>();
        GenerateStatementsFromTreeAux(rows, generatedStatements, parent);
        return generatedStatements;
    }
}


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
        patternsVisitor.FinalizeStuff();
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
        //visitorPatternsRaw = patternsVisitor.patterns();
        //patternsMap = ObjectPattern.PartitionBySubject(visitorPatternsRaw);
        //logger.debug("Generated patterns: " + Long.toString(patternsMap.size()));
    }

    Solution EvaluateSolution() {
        Solution solution = patternsVisitor.parent.Evaluate();
        logger.debug("Evaluated solution:" + solution.size() + " rows.");
        return solution;
    }

    List<Statement> SolutionToStatements(Solution rows) throws Exception {
        return patternsVisitor.GenerateStatementsFromTree(rows);
    }


}
