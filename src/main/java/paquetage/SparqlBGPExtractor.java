package paquetage;

// See "query explain"
// https://rdf4j.org/documentation/programming/repository/

import java.util.*;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Values;
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
        patternsMap = new HashMap<>();
        for(StatementPattern myPattern : visitorPatternsRaw)
        {
            Var subject = myPattern.getSubjectVar();
            String subjectName = subject.getName();
            logger.debug("subjectName=" + subjectName);
            if(subject.isConstant()) {
                logger.warn("Constant subject:" + subjectName);
                continue;
            }

            Var predicate = myPattern.getPredicateVar();
            Var object = myPattern.getObjectVar();
            // TODO: Try comparing the nodes instead of the strings, if this is possible. It could be faster.
            Value predicateValue = predicate.getValue();
            // If the predicate is not usable, continue without creating a pattern.
            if(predicateValue == null) {
                logger.warn("Predicate is null");
                continue;
            }
            String predicateStr = predicateValue.stringValue();

            if(subject.isAnonymous()) {
                logger.warn("Anonymous subject:" + subjectName);
                /* Anonymous nodes due to fixed-length paths should be processed by creating an anonymous variable.
                but this is not implemented yet for data later loaded from WMI.
                This occurs with triples like:
                "^cimv2:Win32_DependentService.Dependent/cimv2:Win32_DependentService.Antecedent"
                or, on top of an ArbitraryLengthPath:
                "?service1 (^cimv2:Win32_DependentService.Dependent/cimv2:Win32_DependentService.Antecedent)+ ?service2"

                However, with triples whose instance are unrelated to WMI, this is OK. Like:
                "cimv2:Win32_Process.Handle rdfs:label ?label"
                */
                if(predicateStr.startsWith(WmiOntology.namespaces_url_prefix)) {
                    throw new RuntimeException("Anonymous WMI subjects are not allowed yet.");
                }
            }


            ObjectPattern refPattern;
            if(! patternsMap.containsKey(subjectName))
            {
                refPattern = new ObjectPattern(subjectName);
                patternsMap.put(subjectName, refPattern);
            }
            else
            {
                refPattern = patternsMap.get(subjectName);
            }
            if(!predicateStr.equals(RDF.TYPE.stringValue())) {
                if(object.isConstant()) {
                    if( !object.isAnonymous()) {
                        throw new Exception("isConstant and not isAnonymous");
                    }
                    refPattern.AddKeyValue(predicateStr, false, object.getValue().stringValue());
                }
                else {
                    // If it is a variable.
                    if( object.isAnonymous()) {
                        throw new Exception("not isConstant and isAnonymous");
                    }
                    refPattern.AddKeyValue(predicateStr, true, object.getName());
                }
            }
            else {
                refPattern.className = object.getValue().stringValue();
            }
        }
        logger.debug("Generated patterns: " + Long.toString(patternsMap.size()));
    }

    private static ValueFactory factory = SimpleValueFactory.getInstance();

    private void PatternToTriples(List<Triple> generatedTriples, StatementPattern myPattern, Solution rows) throws Exception {
        Var subject = myPattern.getSubjectVar();
        Var predicate = myPattern.getPredicateVar();
        if(!predicate.isConstant()) {
            logger.debug("Predicate is not constant:" + predicate);
            return;
        }
        IRI predicateIri = Values.iri(predicate.getValue().stringValue());
        Var object = myPattern.getObjectVar();
        if (subject.isConstant()) {
            String subjectString = subject.getValue().stringValue();
            Resource resourceSubject = Values.iri(subjectString);

            if (object.isConstant()) {
                // One insertion only. Variables are not needed.
                String objectString = object.getValue().stringValue();
                Resource resourceObject = Values.iri(objectString);

                generatedTriples.add(factory.createTriple(
                        resourceSubject,
                        predicateIri,
                        resourceObject));
            } else {
                // Only the object changes for each row.
                Iterator<Solution.Row> rowIterator = rows.iterator();
                while(rowIterator.hasNext()) {
                    Solution.Row row = rowIterator.next();
                    Solution.Row.ValueTypePair pairValueType = row.TryValueType(object.getName());
                    if(pairValueType == null) {
                        // TODO: If this triple contains a variable calculated by WMI, maybe replicate it ?
                        logger.debug("Variable " + object.getName() + " not defined. Continuing to next pattern.");
                        continue;
                    }
                    String objectString = pairValueType.Value();
                    Value resourceObject = patternsMap.containsKey(object.getName())
                            ? Values.iri(objectString)
                            : pairValueType.ValueTypeToLiteral();
                    generatedTriples.add(factory.createTriple(
                            resourceSubject,
                            predicateIri,
                            resourceObject));
                }
            }
        } else {
            if (object.isConstant()) {
                // Only the subject changes for each row.
                String objectString = object.getValue().stringValue();
                logger.debug("objectString=" + objectString + " isIRI=" + object.getValue().isIRI());
                // TODO: Maybe this is already an IRI ? So, should not transform it again !
                Value resourceObject = object.getValue().isIRI()
                        ? Values.iri(objectString)
                        : object.getValue(); // Keep the original type of the constant.

                Iterator<Solution.Row> rowIterator = rows.iterator();
                while (rowIterator.hasNext()) {
                    Solution.Row row = rowIterator.next();
                    // Consistency check.
                    // TODO: Maybe this is an IRI ? So, do not transform it again !
                    Resource resourceSubject = row.AsIRI(subject);

                    generatedTriples.add(factory.createTriple(
                            resourceSubject,
                            predicateIri,
                            resourceObject));
                }
            } else {
                // The subject and the object change for each row.
                Iterator<Solution.Row> rowIterator = rows.iterator();
                while (rowIterator.hasNext()) {
                    Solution.Row row = rowIterator.next();

                    Resource resourceSubject = row.AsIRI(subject);

                    Solution.Row.ValueTypePair objectValue = row.GetVarValue(object);
                    Value resourceObject;
                    if (patternsMap.containsKey(object.getName())) {
                        resourceObject = row.AsIRI(object);
                    } else {
                        if (objectValue == null) {
                            throw new RuntimeException("Null value for " + object.getName());
                        }
                        resourceObject = objectValue.ValueTypeToLiteral();
                    }

                    generatedTriples.add(factory.createTriple(
                            resourceSubject,
                            predicateIri,
                            resourceObject));
                }
            }
        }
    }

    /** This generates the triples from substituting the variables of the patterns by their values.
     *
     * @param rows List of a map of variables to values, and these are the variables of the patterns.
     * @return Triples ready to be inserted in a repository.
     * @throws Exception
     */
    List<Triple> GenerateTriples(Solution rows) throws Exception {
        List<Triple> generatedTriples = new ArrayList<>();

        logger.debug("Visitor patterns number:" + visitorPatternsRaw.size());
        logger.debug("Rows number:" + rows.size());
        for(StatementPattern myPattern : visitorPatternsRaw) {
            PatternToTriples(generatedTriples, myPattern, rows);
        }

        logger.debug("Generated triples number:" + generatedTriples.size());
        return generatedTriples;
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
