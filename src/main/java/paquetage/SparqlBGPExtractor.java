package paquetage;

// See "query explain"
// https://rdf4j.org/documentation/programming/repository/

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.OleAuto;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleStatement;
import org.eclipse.rdf4j.model.impl.SimpleTriple;
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
    public Set<String> bindings;
    Map<String, ObjectPattern> patternsMap;

    List<StatementPattern> visitorPatterns;

    List<ObjectPattern> patternsArray;

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
        visitorPatterns = myVisitor.patterns();
        patternsMap = new HashMap<String, ObjectPattern>();
        for(StatementPattern myPattern : visitorPatterns)
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

    List<Triple> GenerateTriples(List<MetaSelecter.Row> rows) throws Exception {
        // Reorganize statements by input variable.
        HashMap<String, List<StatementPattern>> triplesWithVariable = new HashMap<>();

        List<Triple> generatedTriples = new ArrayList<>();

        ValueFactory factory = SimpleValueFactory.getInstance();


        // This lambda adds an IRI.
        BiConsumer<Var, StatementPattern> storeIri = (Var iri, StatementPattern myPattern) -> {
            if(!iri.isConstant()) {
                //if (iri.isAnonymous()) {
                //    throw new Exception("Iri isConstant and not isAnonymous");
                //}
                if(triplesWithVariable.containsKey(iri.getName())) {
                    triplesWithVariable.get(iri.getName()).add(myPattern);
                } else {
                    List<StatementPattern> patternsList = new ArrayList<>();
                    patternsList.add(myPattern);
                    triplesWithVariable.put(iri.getName(), patternsList);
                }
            }
        } ;

        for(StatementPattern myPattern : visitorPatterns)
        {
            Var subject = myPattern.getSubjectVar();
            storeIri.accept(subject, myPattern);
            /*
            if(!subject.isConstant()) {
                if (subject.isAnonymous()) {
                    throw new Exception("Subject isConstant and not isAnonymous");
                }
                if(triplesWithVariable.containsKey(subject.getName())) {
                    triplesWithVariable.get(subject.getName()).add(myPattern);
                } else {
                    triplesWithVariable.put(subject.getName(), Arrays.asList(myPattern));
                }
            }

             */

            Var object = myPattern.getObjectVar();
            storeIri.accept(object, myPattern);
            /*
            if(!object.isConstant()) {
                if (object.isAnonymous()) {
                    throw new Exception("Object isConstant and not isAnonymous");
                }
                if(triplesWithVariable.containsKey(object.getName())) {
                    triplesWithVariable.get(object.getName()).add(myPattern);
                } else {
                    triplesWithVariable.put(object.getName(), Arrays.asList(myPattern));
                }
            }

             */

            /*
            POURQUOI INSERER LES PATTERNS CONSTANTS ???
            Si on entre l'IRI d'un objet constant. Mais il faudrait verifier qu'il est effectivement present.
            Seul cas ou ca peut etre necessaire: Si
            */
            if(false) {
                if (subject.isConstant() && object.isConstant()) {
                    generatedTriples.add(factory.createTriple(
                            (Resource) subject,
                            (IRI) myPattern.getPredicateVar(),
                            Values.literal(object))); // (Value) object));
                }
            }
        }

        for(MetaSelecter.Row row: rows) {
            for(Map.Entry<String, String> variable_value_pair : row.Elements.entrySet()) {
                String variableName = variable_value_pair.getKey();
                String variableValue = variable_value_pair.getValue();
                List<StatementPattern> patternsList = triplesWithVariable.get(variableName);
                if (patternsList == null) {
                    throw new Exception("Unexpected unknown variable=" + variableName);
                }

                for(StatementPattern myPattern : patternsList) {
                    Var subject = myPattern.getSubjectVar();
                    String subjectString = subject.isConstant()
                            ? subject.getValue().stringValue()
                            : variableValue;
                    Resource resourceSubject = Values.iri(subjectString); // factory.createIRI(subjectString);
                    Var object = myPattern.getObjectVar();
                    String objectString = object.isConstant()
                            ? object.getValue().stringValue()
                            : variableValue;
                    Value resourceObject = patternsMap.containsKey(variableName)
                            ? Values.iri(objectString) // factory.createIRI(objectString)
                            : Values.literal(objectString); // factory.createLiteral(objectString);
                    generatedTriples.add(factory.createTriple(
                            resourceSubject,
                            Values.iri(myPattern.getPredicateVar().getValue().stringValue()),
                            resourceObject));
                }
            }
        }

        return generatedTriples;
    }

    /** This is used to extract the BGPs of a Sparql query.
     *
     */
    static class PatternsVisitor extends AbstractQueryModelVisitor {
        public void meet(TripleRef node) {
        }

        private List<StatementPattern> visitedStatementPatterns = new ArrayList<StatementPattern>();

        @Override
        public void meet(StatementPattern sp) {
            visitedStatementPatterns.add(sp.clone());
        }

        public List<StatementPattern> patterns() {
            return visitedStatementPatterns;
        }
    }
}
