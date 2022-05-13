package paquetage;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static org.eclipse.rdf4j.model.util.Values.literal;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.TreeModel;
import org.eclipse.rdf4j.model.vocabulary.FOAF;
import org.eclipse.rdf4j.model.vocabulary.RDF;

import static org.eclipse.rdf4j.model.util.Values.bnode;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

import java.util.ArrayList;

/**
 * RDF Tutorial example 01: Building a simple RDF Model using Eclipse RDF4J
 *
 * @author Jeen Broekstra
 */

/*
All inspired from this: https://rdf4j.org/documentation/tutorials/getting-started/

Charger un fichier.
Faire tourner un requete Sparql.
Extraire les BGP.
 */

public class CreateTreeModel {
    public Model model;

    public CreateTreeModel(String file_name) throws Exception{
        String ex = "http://example.org/";

        IRI picasso = iri(ex, "Picasso");
        IRI artist = iri(ex, "Artist");

        // Create a new, empty Model object.
        model = new TreeModel();

        model.add(picasso, RDF.TYPE, artist);

        model.add(picasso, FOAF.FIRST_NAME, literal("Pablo"));

        // to see what's in our model, let's just print it to the screen
        for (Statement st : model) {
            System.out.println(st);
        }

        // It does the same but this is shorter.
        model.forEach(System.out::println);

        // https://github.com/eclipse/rdf4j/blob/main/examples/src/main/java/org/eclipse/rdf4j/examples/model/Example06WriteRdfXml.java
        // Instead of simply printing the statements to the screen, we use a Rio writer to
        // write the model in RDF/XML syntax:
        Rio.write(model, System.out, RDFFormat.RDFXML);

        java.io.FileWriter fw = new java.io.FileWriter(file_name);
        Rio.write(model, fw, RDFFormat.RDFXML);
    }

}

