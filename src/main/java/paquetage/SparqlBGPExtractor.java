package paquetage;

// See "query explain"
// https://rdf4j.org/documentation/programming/repository/

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.algebra.*;
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

// https://www.programcreek.com/java-api-examples/?api=org.eclipse.rdf4j.query.parser.ParsedQuery

// https://github.com/apache/rya/blob/master/sail/src/main/java/org/apache/rya/rdftriplestore/inference/IntersectionOfVisitor.java#L54

/**
 * This extracts the BGP (basic graph patterns) from a Sparql query.
 */
public class SparqlBGPExtractor {
    final static private Logger logger = Logger.getLogger(SparqlBGPExtractor.class);

    public Set<String> bindings;

    // This should be private except for tests.
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
    void ParseQuery(String sparql_query) throws Exception {
        SPARQLParser parser = new SPARQLParser();
        ParsedQuery pq = parser.parseQuery(sparql_query, null);
        TupleExpr tupleExpr = pq.getTupleExpr();
        bindings = tupleExpr.getBindingNames();
        PatternsVisitor myVisitor = new PatternsVisitor();
        tupleExpr.visit(myVisitor);
        visitorPatternsRaw = myVisitor.patterns();
        patternsMap = new HashMap<>();
        for(StatementPattern myPattern : visitorPatternsRaw)
        {
            Var subject = myPattern.getSubjectVar();
            String subjectName = subject.getName();
            //logger.debug("subjectName=" + subjectName);
            if(subject.isConstant() || subject.isAnonymous()) {
                logger.warn("Anonymous or constant subject:" + subjectName);
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
            if(!predicateValue.stringValue().equals(RDF.TYPE.stringValue())) {
                if(object.isConstant()) {
                    if( !object.isAnonymous()) {
                        throw new Exception("isConstant and not isAnonymous");
                    }
                    refPattern.AddKeyValue(predicateValue.stringValue(), false, object.getValue().stringValue());
                }
                else {
                    // If it is a variable.
                    if( object.isAnonymous()) {
                        throw new Exception("not isConstant and isAnonymous");
                    }
                    refPattern.AddKeyValue(predicateValue.stringValue(), true, object.getName());
                }
            }
            else {
                refPattern.className = object.getValue().stringValue();
            }
        }
        logger.debug("Generated patterns: " + Long.toString(patternsMap.size()));
    }

    private static String GetVarString(Var var, GenericProvider.Row row) throws Exception {
        GenericProvider.Row.ValueTypePair pairValueType = row.GetValueType(var.getName());
        String value = pairValueType.Value();
        //if(pairValueType.Type() == GenericSelecter.ValueType.NODE_TYPE) {
        //    logger.debug("This is a NODE:" + var.getName() + "=" + value);
        //}
        return value;
    }

    private static GenericProvider.Row.ValueTypePair GetVarValue(Var var, GenericProvider.Row row) throws Exception {
        GenericProvider.Row.ValueTypePair pairValueType = row.GetValueType(var.getName());
        return pairValueType;
    }

    /**
     * IRIS must look like this:
     * objectString=http://www.primhillcomputers.com/ontology/CIMV2#Win32_Process isIRI=true
     *
     * But Wbem path are like that:
     * subjectString=\\LAPTOP-R89KG6V1\ROOT\CIMV2:Win32_Process.Handle="31640"
     *
     * So, Wbem path must be URL-encoded and prefixed.
     *
     * @param var
     * @param row
     * @return
     * @throws Exception
     */
    private static Resource AsIRI(Var var, GenericProvider.Row row) throws Exception {
        GenericProvider.Row.ValueTypePair pairValueType = row.GetValueType(var.getName());
        if(pairValueType.Type() != GenericProvider.ValueType.NODE_TYPE) {
            throw new Exception("This should be a NODE:" + var.getName() + "=" + pairValueType);
        }
        String valueString = pairValueType.Value();
        //logger.debug("valueTypePair=" + valueTypePair);
        if(pairValueType.Type() != GenericProvider.ValueType.NODE_TYPE) {
            throw new Exception("Value " + var.getName() + "=" + valueString + " is not a node");
        }
        // Consistency check, for debugging.
        if(valueString.startsWith(WmiOntology.cimv2_url_prefix)) {
            throw new Exception("Double transformation in IRI:" + valueString);
        }
        Resource resourceValue = WmiOntology.WbemPathToIri(valueString);
        return resourceValue;
    }

    private static ValueFactory factory = SimpleValueFactory.getInstance();
    private static final DatatypeFactory datatypeFactory ;

    static {
        try {
            datatypeFactory = DatatypeFactory.newInstance();
        }
        catch (Exception e) {
            throw new RuntimeException("Cannot initialize DatatypeFactory:", e);
        }
    }

    private static Value ValueTypeToLiteral(GenericProvider.Row.ValueTypePair pairValueType) {
        GenericProvider.ValueType valueType = pairValueType.Type();
        if(valueType == null) {
            logger.warn("Invalid null type of literal value.");
            Object nullObject = new Object();
            return Values.literal(nullObject);
        }
        String strValue = pairValueType.Value();
        if(strValue == null) {
            logger.warn("Invalid null literal value.");
            return Values.literal("Unexpected null value. Type=\" + valueType");
        }
        switch(valueType) {
            case INT_TYPE:
                return Values.literal(Long.parseLong(strValue));
            case FLOAT_TYPE:
                return Values.literal(Double.parseDouble(strValue));
            case DATE_TYPE:
                if (strValue == null) {
                    return Values.literal("NULL_DATE");
                } else {
                    ZoneId zone = ZoneId.systemDefault();
                    /**
                     * See SWbemDateTime
                     * https://docs.microsoft.com/en-us/windows/win32/wmisdk/swbemdatetime
                     * https://docs.microsoft.com/en-us/windows/win32/wmisdk/cim-datetime
                     *
                     * strValue = '20220720095636.399854+060' for example.
                     * The time zone offset is in minutes.
                     *
                     * https://stackoverflow.com/questions/37308672/parse-cim-datetime-with-milliseconds-to-java-date                     *
                     */
                    //logger.debug("strValue=" + strValue);

                    String offsetInMinutesAsString = strValue.substring ( 22 );
                    long offsetInMinutes = Long.parseLong ( offsetInMinutesAsString );
                    LocalTime offsetAsLocalTime = LocalTime.MIN.plusMinutes ( offsetInMinutes );
                    String offsetAsString = offsetAsLocalTime.format ( DateTimeFormatter.ISO_LOCAL_TIME );
                    String inputModified = strValue.substring ( 0 , 22 ) + offsetAsString;

                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss.SSSSSSZZZZZ");
                    LocalDateTime dateFromGmtString = formatter.parse(inputModified, Instant::from).atZone(zone).toLocalDateTime();

                    String strDate = dateFromGmtString.toString();
                    //logger.debug("strDate=" + strDate);
                    XMLGregorianCalendar dateGregorian = datatypeFactory.newXMLGregorianCalendar(strDate);

                    return Values.literal(factory, dateGregorian, true);
                }
            case STRING_TYPE:
                return Values.literal(strValue);
            case NODE_TYPE:
                return Values.literal(strValue);
        }
        throw new RuntimeException("Data type not handled:" + valueType);
    }

    /** This generates the triples from substituting the variables of the patterns by their values.
     *
     * @param rows List of a map of variables to values, and these are the variables of the patterns.
     * @return Triples ready to be inserted in a repository.
     * @throws Exception
     */
    List<Triple> GenerateTriples(List<GenericProvider.Row> rows) throws Exception {
        List<Triple> generatedTriples = new ArrayList<>();

        logger.debug("Visitor patterns number:" + Long.toString(visitorPatternsRaw.size()));
        logger.debug("Rows number:" + Long.toString(rows.size()));
        for(StatementPattern myPattern : visitorPatternsRaw) {
            Var subject = myPattern.getSubjectVar();
            Var predicate = myPattern.getPredicateVar();
            if(!predicate.isConstant()) {
                logger.debug("Predicate is not constant:" + predicate.toString());
                continue;
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
                    for (GenericProvider.Row row : rows) {
                        GenericProvider.Row.ValueTypePair pairValueType = row.TryValueType(object.getName());
                        if(pairValueType == null) {
                            // TODO: If this triple contains a variable calculated by WMI, maybe replicate it ?
                            logger.debug("Variable " + object.getName() + " not defined. Continuing to next pattern.");
                            continue;
                        }
                        String objectString = pairValueType.Value();
                        Value resourceObject = patternsMap.containsKey(object.getName())
                                ? Values.iri(objectString)
                                : ValueTypeToLiteral(pairValueType);
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

                    for (GenericProvider.Row row : rows) {
                        // Consistency check.
                        // TODO: Maybe this is an IRI ? So, do not transform it again !
                        Resource resourceSubject = AsIRI(subject, row);

                        //logger.debug("resourceSubject=" + resourceSubject);

                        generatedTriples.add(factory.createTriple(
                                resourceSubject,
                                predicateIri,
                                resourceObject));
                    }
                } else {
                    // The subject and the object change for each row.
                    for (GenericProvider.Row row : rows) {
                        //logger.debug("subject=" + subject + ".");

                        Resource resourceSubject = AsIRI(subject, row);

                        GenericProvider.Row.ValueTypePair objectValue = GetVarValue(object, row);
                        //String objectString = objectValue.Value();
                        Value resourceObject;
                        if(patternsMap.containsKey(object.getName())) {
                            resourceObject = AsIRI(object, row); // Values.iri(objectString);
                        } else {
                            if(objectValue == null) {
                                throw new RuntimeException("Null value for " + object.getName());
                            }
                            resourceObject = ValueTypeToLiteral(objectValue);
                        }

                        generatedTriples.add(factory.createTriple(
                                resourceSubject,
                                predicateIri,
                                resourceObject));
                    }
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
