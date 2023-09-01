package paquetage;

import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

// Is it used ???
public class SparqlFrontEnd {
    /**
     * - Receives a Sparql query.
     * - Run the Sparql query.
     * - Get the triples
     * - Return the triples.
     */

    private RepositoryWrapper repositoryWrapper;
}
