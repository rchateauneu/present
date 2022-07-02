package paquetage;

import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleStatement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.impl.StatementImpl;
import org.eclipse.rdf4j.model.impl.TreeModel;
import org.eclipse.rdf4j.model.vocabulary.FOAF;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.util.HashMap;
import java.util.Map;

import static org.eclipse.rdf4j.model.util.Values.iri;

public class WmiOntology {
    /** This is a triplestore which conyains the WMI ontology. */
    Repository repository;

    public static String survol_url_prefix = "http://www.primhillcomputers.com/ontology/survol#";

    /** This maps WMI types to standard RDF types. */
    static final Map<String , IRI> wmi_type_to_xsd = new HashMap<>() {
        {
            put("string", XSD.STRING);
            put("boolean", XSD.BOOLEAN);
            put("datetime", XSD.DATETIME);
            put("sint64", XSD.INTEGER);
            put("sint32", XSD.INTEGER);
            put("sint16", XSD.INTEGER);
            put("sint8", XSD.INTEGER);
            put("uint64", XSD.INTEGER);
            put("uint32", XSD.INTEGER);
            put("uint16", XSD.INTEGER);
            put("uint8", XSD.INTEGER);
            put("real64", XSD.DOUBLE);
            put("real32", XSD.DOUBLE);
        }};

    private void FillRepository(Repository repository){
        // We want to reuse this namespace when creating several building blocks.

        RepositoryConnection connection = repository.getConnection();

        ValueFactory factory = SimpleValueFactory.getInstance();

        WmiSelecter selecter = new WmiSelecter();
        Map<String, WmiSelecter.WmiClass> classes = selecter.Classes();
        for(Map.Entry<String, WmiSelecter.WmiClass> entry_class : classes.entrySet()) {
            String className = entry_class.getKey();
            IRI classIri = iri(survol_url_prefix, className);
            connection.add(classIri, RDF.TYPE, RDFS.CLASS);
            connection.add(classIri, RDFS.LABEL, factory.createLiteral(className));
            for(Map.Entry<String, WmiSelecter.WmiProperty> entry_property : entry_class.getValue().Properties.entrySet()) {
                String propertyName = entry_property.getKey();
                IRI propertyIri = iri(survol_url_prefix, propertyName);
                String strType = entry_property.getValue().Type;

                connection.add(propertyIri, RDF.TYPE, RDF.PROPERTY);
                connection.add(propertyIri, RDFS.DOMAIN, classIri);

                IRI iriType = wmi_type_to_xsd.get(strType);
                if(iriType == null)
                {
                    iriType = XSD.STRING; // Default value.
                }
                connection.add(propertyIri, RDFS.RANGE, iriType);
                connection.add(propertyIri, RDFS.LABEL, factory.createLiteral(propertyName));

                /*
                ?class_node cim:is_association ?obj .
                */
            }
        }

    }
    public WmiOntology() {
        repository = new SailRepository(new MemoryStore());
        FillRepository(repository);
    }
}
