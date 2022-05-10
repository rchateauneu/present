package paquetage;

import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.nativerdf.NativeStore;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.model.Value;

import java.io.File;

public class CreateRepository {
    public static void CreateSailRepositoryFromMemory() throws Exception {
        Repository repo = new SailRepository(new MemoryStore());
    }
    public static void CreateSailRepositoryFromFile(String the_dir) throws Exception {
        File dataDir = new File("/path/to/datadir/");
        Repository repo = new SailRepository(new NativeStore(dataDir));
    }
    public static void CreateSparqlRepository() throws Exception {
        String sparqlEndpoint = "https://query.wikidata.org/";
        Repository repo = new SPARQLRepository(sparqlEndpoint);

        try (RepositoryConnection reco = repo.getConnection()) {
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

    }
}
