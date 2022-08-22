package paquetage;

import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.eclipse.rdf4j.model.util.Values.iri;

public class WmiOntology {
    /** This is a triplestore which contains the WMI ontology. */
    //Repository repository;

    final static private Logger logger = Logger.getLogger(WmiOntology.class);

    /** Consider using a Model to store the triples of the ontology. */
    RepositoryConnection repositoryConnection;

    public static String namespaces_url_prefix = "http://www.primhillcomputers.com/ontology/";

    static private Pattern patternNamespace = Pattern.compile("^[\\\\_a-zA-Z0-9]+$", Pattern.CASE_INSENSITIVE);

    static public void CheckValidNamespace(String namespace) {
        if(!namespace.startsWith("ROOT")) {
            throw new RuntimeException("Namespace must start with 'ROOT':" + namespace);
        }
        Matcher matcher = patternNamespace.matcher(namespace);
        boolean matchFound = matcher.find();
        if (!matchFound) {
            throw new RuntimeException("Invalid namespace:" + namespace);
        }
    }

    static String NamespaceUrlPrefix(String namespace) {
        CheckValidNamespace(namespace);
        // Backslashes could be replaced with "%5C" but a slash is clearer.
        return namespaces_url_prefix + namespace.replace("\\", "/") + "#";
    }
    //public static String cimv2_url_prefix = NamespaceUrlPrefix("ROOT\\CIMV2");

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

    static Resource WbemPathToIri(String valueString) throws Exception {
        String encodedValueString = URLEncoder.encode(valueString, StandardCharsets.UTF_8.toString());
        //logger.debug("encodedValueString=" + encodedValueString);
        String iriValue = PresentUtils.toCIMV2(encodedValueString);
        //logger.debug("iriValue=" + iriValue);
        Resource resourceValue = Values.iri(iriValue);
        return resourceValue;
    }

    /**
     * This transforms the classes and properties of a WMI namespace into a RDF ontology.
     * This is a slow task so the result must be cached in a Sail repository file.
     *
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
     *
     * This difficulty is avoid by giving to a property a unique name prefixed with the class name.
     * However, the non-unique property name for an instanceis tolerated in Sparql queries,
     * if the type of the instance is also given with a rdf:type triple.
     *
     * @param connection
     */
    private void InsertOntologyInRepository(String namespace, RepositoryConnection connection){
        logger.debug("Start namespace=" + namespace);
        CheckValidNamespace(namespace);
        // We want to reuse this namespace when creating several building blocks.

        ValueFactory factory = SimpleValueFactory.getInstance();

        /** This contains all properties without their classes : There are homonyms,
        that is, different properties with the same name, but different domains and ranges.
        "Ambiguous" properties are created because this allows to write RDF statements
        with unique property name or the ambiguous one (which is more natural).
        Example of Sparql statements:

        ?x rdf:type cimv2:CIM_Process
        ?x cimv2:Name ?name
        ... then WMI processing returns triples with the predicate cimv2:Name

        But if it is:
        ?x rdf:type cimv2:CIM_Process
        ?x cimv2:Process.Name ?name
        ... then WMI processing returns triples with the predicate cimv2:Process.Name
        */
        HashMap<String, IRI> ambiguousProperties = new HashMap<>();

        HashMap<String, IRI> classToNode = new HashMap<>();

        String namespace_iri_prefix = NamespaceUrlPrefix(namespace);

        Function<String, IRI> lambdaClassToNode = (String className) -> {
            IRI classNode = classToNode.get(className);
            if(classNode == null) {
                classNode = iri(namespace_iri_prefix, className);
                classToNode.put(className, classNode);
            }
            return classNode;
        };

        WmiProvider wmiProvider = new WmiProvider();
        Map<String, WmiProvider.WmiClass> classes = wmiProvider.Classes(namespace);
        for(Map.Entry<String, WmiProvider.WmiClass> entry_class : classes.entrySet()) {
            String className = entry_class.getKey();
            //System.out.println("className=" + className);

            IRI classIri = lambdaClassToNode.apply(className);

            WmiProvider.WmiClass wmiClass = entry_class.getValue();
            connection.add(classIri, RDF.TYPE, RDFS.CLASS);
            connection.add(classIri, RDFS.LABEL, factory.createLiteral(className));
            connection.add(classIri, RDFS.COMMENT, factory.createLiteral(wmiClass.Description));

            if (wmiClass.BaseName != null) {
                IRI baseClassIri = lambdaClassToNode.apply(wmiClass.BaseName);
                if(baseClassIri != null) {
                    connection.add(classIri, RDFS.SUBCLASSOF, baseClassIri);
                }
            }

            for(Map.Entry<String, WmiProvider.WmiProperty> entry_property : wmiClass.Properties.entrySet()) {
                String ambiguousPropertyName = entry_property.getKey();
                String uniquePropertyName = className + "." + ambiguousPropertyName;

                IRI uniquePropertyIri = iri(namespace_iri_prefix, uniquePropertyName);
                WmiProvider.WmiProperty wmiProperty = entry_property.getValue();

                connection.add(uniquePropertyIri, RDF.TYPE, RDF.PROPERTY);
                connection.add(uniquePropertyIri, RDFS.LABEL, factory.createLiteral(uniquePropertyName));
                connection.add(uniquePropertyIri, RDFS.DOMAIN, classIri);
                connection.add(uniquePropertyIri, RDFS.COMMENT, factory.createLiteral(wmiProperty.Description));

                if(wmiProperty.Type.startsWith("ref:")) {
                    String domainName = wmiProperty.Type.substring(4);
                    // This should be another class.
                    IRI domainIri = iri(namespace_iri_prefix, domainName);
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
                    ambiguousPropertyIri = iri(namespace_iri_prefix, ambiguousPropertyName);
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
        logger.debug("End");
    }

    private void WriteRepository(String rdfFileName) throws Exception {
        FileOutputStream out = new FileOutputStream(rdfFileName);
        RDFWriter writer = Rio.createWriter(RDFFormat.RDFXML, out);
        writer.startRDF();
        RepositoryResult<Statement> rr = repositoryConnection.getStatements(null, null, null);

        for (Statement st: rr) {
            writer.handleStatement(st);
        }
        writer.endRDF();
    }

    public WmiOntology(String namespace, boolean isCached) {
        logger.debug("isCached=" + isCached + " namespace=" + namespace);
        CheckValidNamespace(namespace);
        if(isCached)
        {
            try {

                // Get the temporary directory and print it. Similar to:
                // TEMP=C:/Users/user/AppData/Local/Temp
                // TMP=C:/Users/user/AppData/Local/Temp
                String tempDir = System.getProperty("java.io.tmpdir");

                // To cleanup the ontology, this entire directory must be deleted, and not only its content.
                Path pathCache = Paths.get(tempDir + "\\" + "Ontologies");

                // The namespace might contain backslashes, but this is OK on Windows.
                Path pathNamespacePrefix = Paths.get(pathCache + "\\" + namespace);

                Files.createDirectories(pathCache);
                File dirSaildump = new File(pathNamespacePrefix + ".SailDir");
                logger.debug("dirSaildump=" + dirSaildump);
                if (Files.exists(dirSaildump.toPath())) {
                    logger.debug("Exists dirSaildump=" + dirSaildump);
                    MemoryStore memStore = new MemoryStore(dirSaildump);
                    memStore.setSyncDelay(1000L);
                    Repository repo = new SailRepository(memStore);
                    repositoryConnection = repo.getConnection();
                    logger.debug("Cached statements=" + repositoryConnection.size());
                    ;
                } else {
                    logger.debug("Does not exist dirSaildump=" + dirSaildump);
                    MemoryStore memStore = new MemoryStore(dirSaildump);
                    memStore.setSyncDelay(1000L);
                    Repository repo = new SailRepository(memStore);
                    repositoryConnection = repo.getConnection();
                    logger.debug("Caching new statements before=" + repositoryConnection.size());
                    InsertOntologyInRepository(namespace, repositoryConnection);
                    repositoryConnection.commit();
                    memStore.sync();
                    logger.debug("Caching new statements after=" + repositoryConnection.size());

                    WriteRepository(pathNamespacePrefix + ".rdf");
                }
            } catch(Exception exc) {
                logger.error("Caught:" + exc);
            }
            if(repositoryConnection.size() == 0) {
                throw new RuntimeException("Ontology is empty");
            }
        }
        else {
            Repository repository = new SailRepository(new MemoryStore());
            repositoryConnection = repository.getConnection();
            logger.debug("New statements before=" + repositoryConnection.size());
            InsertOntologyInRepository(namespace, repositoryConnection);
            logger.debug("New statements after=" + repositoryConnection.size());
        }
    }
}
