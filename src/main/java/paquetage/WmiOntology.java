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
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;

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

    /**
     * Difficulty when mapping WMI properties to RDF because several properties may have the same name.
     * Example: CIM_Property.Name and CIM_DataFile.Name
     *
     *         CIM_Process.Name  rdf:Type rdfs:Property
     *         CIM_DataFile.Name rdf:Type rdfs:Property
     *         Name              rdf:Type rdfs:Property
     *
     *         Name              rdfs:Caption "Name"
     *         CIM_Process.Name  rdfs:Caption "Name"
     *         CIM_DataFile.Name rdfs:Caption "Name"
     *
     *         CIM_Process.Name  rdfs:Comment "Blablah1"
     *         CIM_DataFile.Name rdfs:Comment "Blablah2""
     *
     *         CIM_Process.Name  rdfs:subPropertyOf Name
     *         CIM_DataFile.Name rdfs:subPropertyOf Name
     *
     *         # "Name" does not have a domain.
     *         CIM_Process.Name rdfs:Domain CIM_Process
     *         CIM_Process.Name rdfs:Domain CIM_DataFile
     *
     *         # No range for "Name" because it might have different types.
     *         CIM_Process.Name  rdfs:Range String
     *         CIM_DataFile.Name rdfs:Range String
     * @param repository
     */
    private void FillRepository(Repository repository){
        // We want to reuse this namespace when creating several building blocks.

        RepositoryConnection connection = repository.getConnection();

        ValueFactory factory = SimpleValueFactory.getInstance();

        /** This contains all properties without their classes : There are homonyms,
        that is, different properties with the same name, but different domains and ranges.
        "Ambiguous" properties are created because this allows to write RDF statements
        with unique property name or the ambiguous one (which is more natural).
        Example of Sparql statements:

        ?x rdf:type cim:CIM_Process
        ?x cim:Name ?name
        ... then WMI processing returns triples with the predicate cim:Name

        But if it is:
        ?x rdf:type cim:CIM_Process
        ?x cim:Process.Name ?name
        ... then WMI processing returns triples with the predicate cim:Process.Name
        */
        HashMap<String, IRI> ambiguousProperties = new HashMap<>();

        HashMap<String, IRI> classToNode = new HashMap<>();

        Function<String, IRI> lambdaClassToNode = (String className) -> {
            IRI classNode = classToNode.get(className);
            if(classNode == null) {
                classNode = iri(survol_url_prefix, className);
                classToNode.put(className, classNode);
            }
            return classNode;
        };

        WmiSelecter selecter = new WmiSelecter();
        Map<String, WmiSelecter.WmiClass> classes = selecter.Classes();
        for(Map.Entry<String, WmiSelecter.WmiClass> entry_class : classes.entrySet()) {
            String className = entry_class.getKey();

            IRI classIri = lambdaClassToNode.apply(className);

            WmiSelecter.WmiClass wmiClass = entry_class.getValue();
            connection.add(classIri, RDF.TYPE, RDFS.CLASS);
            connection.add(classIri, RDFS.LABEL, factory.createLiteral(className));
            connection.add(classIri, RDFS.COMMENT, factory.createLiteral(wmiClass.Description));

            if (wmiClass.BaseName != null) {
                IRI baseClassIri = lambdaClassToNode.apply(wmiClass.BaseName);
                if(baseClassIri != null) {
                    connection.add(classIri, RDFS.SUBCLASSOF, baseClassIri);
                }
            }

            for(Map.Entry<String, WmiSelecter.WmiProperty> entry_property : wmiClass.Properties.entrySet()) {
                String ambiguousPropertyName = entry_property.getKey();
                String uniquePropertyName = className + "." + ambiguousPropertyName;

                IRI uniquePropertyIri = iri(survol_url_prefix, uniquePropertyName);
                WmiSelecter.WmiProperty wmiProperty = entry_property.getValue();

                connection.add(uniquePropertyIri, RDF.TYPE, RDF.PROPERTY);
                connection.add(uniquePropertyIri, RDFS.LABEL, factory.createLiteral(uniquePropertyName));
                connection.add(uniquePropertyIri, RDFS.DOMAIN, classIri);
                connection.add(uniquePropertyIri, RDFS.COMMENT, factory.createLiteral(wmiProperty.Description));

                if(wmiProperty.Type.startsWith("ref:")) {
                    String domainName = wmiProperty.Type.substring(4);
                    // This should be another class.
                    IRI domainIri = iri(survol_url_prefix, domainName);
                    connection.add(uniquePropertyIri, RDFS.RANGE, domainIri);
                }
                else
                {
                    IRI iriType = wmi_type_to_xsd.get(wmiProperty.Type);
                    if(iriType == null)
                    {
                        iriType = XSD.STRING; // Default value.
                    }
                    connection.add(uniquePropertyIri, RDFS.RANGE, iriType);
                }

                // Now link this unique property with ambiguous one.
                IRI ambiguousPropertyIri = ambiguousProperties.get(ambiguousPropertyName);
                if(ambiguousPropertyIri == null) {
                    ambiguousPropertyIri = iri(survol_url_prefix, ambiguousPropertyName);
                    ambiguousProperties.put(ambiguousPropertyName, ambiguousPropertyIri);
                    connection.add(ambiguousPropertyIri, RDF.TYPE, RDF.PROPERTY);
                    connection.add(ambiguousPropertyIri, RDFS.LABEL, factory.createLiteral(ambiguousPropertyName));
                    connection.add(ambiguousPropertyIri, RDFS.COMMENT, factory.createLiteral(ambiguousPropertyName + " maps to several classes"));
                    // It does not have a domain, or rather has several domains.
                    // It does not have a range, or rather several ranges: int, string etc...
                    // or different classes if this is an associator.
                }
                connection.add(uniquePropertyIri, RDFS.SUBPROPERTYOF, ambiguousPropertyIri);
            }
        }
    }

    public WmiOntology() {
        repository = new SailRepository(new MemoryStore());
        FillRepository(repository);
    }
}
