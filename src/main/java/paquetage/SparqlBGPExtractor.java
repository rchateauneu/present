package paquetage;

// Notamment "query explain"
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
    public String sparql_query;
    public Set<String> bindings;
    public Map<String, ObjectPattern> patternsMap;

    public SparqlBGPExtractor(String input_query) throws Exception {
        sparql_query = input_query;
        Parse();
    }

    List<ObjectPattern> patternsAsArray() {
        ArrayList<ObjectPattern> patternsArray = new ArrayList<ObjectPattern>();
        for(ObjectPattern pattern: patternsMap.values()) {
            patternsArray.add(pattern);
        }
        return patternsArray;
    }

    void Parse() throws Exception {
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
            String predicateName = predicate.getName();
            Var object = myPattern.getObjectVar();
            String objectName = object.getName();
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
                refPattern.className = objectName;
            }
        }
    }

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
