package paquetage;

import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.nativerdf.NativeStore;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.model.Value;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RepositoryWrapper {
    Repository repo;
    RepositoryConnection reco;

    public void CreateSailRepositoryFromMemory() throws Exception {
        repo = new SailRepository(new MemoryStore());
        reco = repo.getConnection();
    }
    public void CreateSailRepositoryFromFile(String the_dir) throws Exception {
        File dataDir = new File("/path/to/datadir/");
        repo = new SailRepository(new NativeStore(dataDir));
        reco = repo.getConnection();
    }
    public void CreateSparqlRepository() throws Exception {
        String sparqlEndpoint = "https://query.wikidata.org/";
        repo = new SPARQLRepository(sparqlEndpoint);
        reco = repo.getConnection();

            String queryString = "SELECT ?x ?y WHERE { ?x ?p ?y } limit 10";
            TupleQuery tupleQuery = reco.prepareTupleQuery(queryString);
            try (TupleQueryResult result = tupleQuery.evaluate()) {
                while (result.hasNext()) {  // iterate over the result
                    BindingSet bindingSet = result.next();
                    Value valueOfX = bindingSet.getValue("x");
                    System.out.println(valueOfX);
                    Value valueOfY = bindingSet.getValue("y");
                    System.out.println(valueOfY);
                    // do something interesting with the values here...
                }
            }
    }

    public void InsertTriples(List<Triple> triples) {
        for (Triple triple : triples) {
            reco.add(triple.getSubject(), triple.getPredicate(), triple.getObject());
        }
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
