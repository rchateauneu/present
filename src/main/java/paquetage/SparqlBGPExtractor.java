package paquetage;

// Notamment "query explain"
// https://rdf4j.org/documentation/programming/repository/

/*
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.model.Value;
import java.io.File;
*/
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.algebra.*;
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor;

// https://www.programcreek.com/java-api-examples/?api=org.eclipse.rdf4j.query.parser.ParsedQuery

// https://github.com/apache/rya/blob/master/sail/src/main/java/org/apache/rya/rdftriplestore/inference/IntersectionOfVisitor.java#L54

/**
 * This extracts the BGP (basig graph patterns) from a Sparql query.
 */
public class SparqlBGPExtractor {
    static public Map<String, ObjectPattern> Parse(String sparql_query) throws Exception {
        SPARQLParser parser = new SPARQLParser();
        ParsedQuery pq = parser.parseQuery(sparql_query, null);
        TupleExpr tupleExpr = pq.getTupleExpr();
        PatternsVisitor myVisitor = new PatternsVisitor();
        tupleExpr.visit(myVisitor);

        Map<String, ObjectPattern> patternsMap = new HashMap<String, ObjectPattern>();
        for(StatementPattern myPattern : myVisitor.patterns())
        {
            String patternAsStr = myPattern.toString();
            System.out.println("Pattern=" + patternAsStr);
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
            System.out.println("subjectName=" + subjectName);
            System.out.println("predicateName=" + predicateName);
            System.out.println("objectName=" + objectName);
            // TODO: Try comparing the nodes instead of the strings, if this is possible.
            if(!predicate.getValue().stringValue().equals(RDF.TYPE.stringValue())) {
                if(object.isConstant()) {
                    if( !object.isAnonymous()) {
                        throw new Exception("isConstant and not isAnonymous");
                    }
                    refPattern.AddKeyValue(predicate.getValue().stringValue(), false, object.getValue().stringValue());
                }
                else {
                    if( object.isAnonymous()) {
                        throw new Exception("not isConstant and isAnonymous");
                    }
                    refPattern.AddKeyValue(predicate.getValue().stringValue(), true, object.getName());
                }
            }
        }

        return patternsMap;
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
