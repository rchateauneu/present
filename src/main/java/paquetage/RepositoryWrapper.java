package paquetage;

import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.util.ArrayList;
import java.util.List;


/** This wraps a repository connection, the logic of transforming a Sparql query in several WQL queries from WMI,
 * and the adding of the WMI ontology.
 */
public class RepositoryWrapper {
    private RepositoryConnection reco;

    private static WmiOntology ontology = new WmiOntology(true);

    /*
    RepositoryWrapper() {
        reco = null;
    }
    */

    RepositoryWrapper(RepositoryConnection repositoryConnection)
    {
        reco = repositoryConnection;
        InsertOntology();
    }

    public boolean IsValid() {
        return reco != null;
    }

    public static RepositoryWrapper CreateSailRepositoryFromMemory() throws Exception {
        Repository repo = new SailRepository(new MemoryStore());
        RepositoryConnection repositoryConnect = repo.getConnection();
        return new RepositoryWrapper(repositoryConnect);
    }

    void InsertOntology(){
        RepositoryResult<Statement> result = ontology.connection.getStatements(null, null, null, true);
        while(result.hasNext()) {
            Statement statement = result.next();
            reco.add(statement.getSubject(), statement.getPredicate(), statement.getObject());
        }
    }

    void InsertTriples(List<Triple> triples) {
        for (Triple triple : triples) {
            reco.add(triple.getSubject(), triple.getPredicate(), triple.getObject());
        }
    }

    /** This transforms a Spqrql query into a stack of WQL-like queries,
     * which are executed and their results inserted in the repository.
     * @param sparqlQuery
     * @throws Exception
     */
    public List<GenericSelecter.Row> ExecuteQuery(String sparqlQuery) throws Exception
    {
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);

        SparqlTranslation toRows = new SparqlTranslation(extractor);

        ArrayList<GenericSelecter.Row> rows = toRows.ExecuteToRows();

        List<Triple> triples = extractor.GenerateTriples(rows);

        InsertTriples(triples);

        // Now, execute the sparql query in the repository which contains the ontology
        // and the result of the WQL executions.
        List<GenericSelecter.Row> listRows = new ArrayList<>();
        TupleQuery tupleQuery = reco.prepareTupleQuery(sparqlQuery);
        try (TupleQueryResult result = tupleQuery.evaluate()) {
            while (result.hasNext()) {  // iterate over the result
                BindingSet bindingSet = result.next();
                GenericSelecter.Row newRow = new GenericSelecter.Row(bindingSet);
                listRows.add(newRow);
            }
        }
        return listRows;
    }

    public List<Triple> GetTriples() {
        ArrayList<Triple> triples = new ArrayList<Triple>();
/*
        IRI bob = Values.iri("http://example.org/people/bob");
        IRI name = Values.iri("http://example.org/ontology/name");
        IRI person = Values.iri("http://example.org/ontology/Person");
        Literal bobsName = Values.literal("Bob");
        conn.add(bob, RDF.TYPE, person);
        conn.add(bob, name, bobsName);
 */
        try (RepositoryResult<Statement> statements = reco.getStatements(null, null, null, true)) {
            ValueFactory factory = SimpleValueFactory.getInstance();
            for (Statement st: statements) {
                Triple triple = factory.createTriple(
                        st.getSubject(),
                        st.getPredicate(),
                        st.getObject());
                triples.add(triple);
            }
        }
        return triples;
    }

}
