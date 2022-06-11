package paquetage;

// See "query explain"
// https://rdf4j.org/documentation/programming/repository/

import java.util.*;

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
    public Set<String> bindings;
    Map<String, ObjectPattern> patternsMap;

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

    void ParseQuery(String sparql_query) throws Exception {
        SPARQLParser parser = new SPARQLParser();
        ParsedQuery pq = parser.parseQuery(sparql_query, null);
        TupleExpr tupleExpr = pq.getTupleExpr();
        bindings = tupleExpr.getBindingNames();
        PatternsVisitor myVisitor = new PatternsVisitor();
        tupleExpr.visit(myVisitor);

        patternsMap = new HashMap<String, ObjectPattern>();
        for(StatementPattern myPattern : myVisitor.patterns())
        {
            Var subject = myPattern.getSubjectVar();
            String subjectName = subject.getName();

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
            Var predicate = myPattern.getPredicateVar();
            Var object = myPattern.getObjectVar();
            // TODO: Try comparing the nodes instead of the strings, if this is possible. It could be faster.
            if(!predicate.getValue().stringValue().equals(RDF.TYPE.stringValue())) {
                if(object.isConstant()) {
                    if( !object.isAnonymous()) {
                        throw new Exception("isConstant and not isAnonymous");
                    }
                    refPattern.AddKeyValue(predicate.getValue().stringValue(), false, object.getValue().stringValue());
                }
                else {
                    // If it is a variable.
                    if( object.isAnonymous()) {
                        throw new Exception("not isConstant and isAnonymous");
                    }
                    refPattern.AddKeyValue(predicate.getValue().stringValue(), true, object.getName());
                }
            }
            else {
                refPattern.className = object.getValue().stringValue();
            }
        }
    }

    /** This is used to extract the BGPs of a Sparql query.
     *
     */
    static class PatternsVisitor extends AbstractQueryModelVisitor {
        public void meet(TripleRef node) {
        }

        private List<StatementPattern> antecedentStatementPatterns = new ArrayList<StatementPattern>();

        @Override
        public void meet(StatementPattern sp) {
            antecedentStatementPatterns.add(sp.clone());
        }

        public List<StatementPattern> patterns() {
            return antecedentStatementPatterns;
        }
    }
}
