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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static org.eclipse.rdf4j.model.util.Values.iri;

public class WmiOntology {
    /** This is a triplestore which contains the WMI ontology. */
    //Repository repository;

    final static private Logger logger = Logger.getLogger(WmiOntology.class);

    /** Consider using a Model to store the triples of the ontology. */

    static private WmiProvider wmiProvider = new WmiProvider();

    public static String namespaces_url_prefix = "http://www.primhillcomputers.com/ontology/";

    /** Each WMI class and property has its namespace with this predicate.
     TODO: Consider a hierarchical definition of namespaces ?
     */
    private static IRI iriNamespaceProperty = iri(namespaces_url_prefix, "NamespaceDefinition");

    static String NamespaceUrlPrefix(String namespace) {
        WmiProvider.CheckValidNamespace(namespace);
        // Backslashes in namespaces could be replaced with "%5C" but a slash is clearer.
        // All characters in a namespace are otherwise admissible in a URL, so no encoding is needed.

        return namespaces_url_prefix + namespace.replace("\\", "/") + "#" ; // + namespace;
    }

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

    static String NamespaceTermToIRI(String namespace, String term) {
        return NamespaceUrlPrefix(namespace) + term;
    }

    static Resource wbemPathToIri(String namespace, String valueString) {
        WmiProvider.CheckValidNamespace(namespace);
        // Convention: Namespaces in uppercase.
        // Beware thet Associators return their references with lowercase namespaces, for no reason.
        // This is not an issue in WMI, but a problem when creating an IRI which are case-sensitive.
        String upperNamespace = namespace.toUpperCase();
        String valueStringUpNS = valueString.replace(namespace, upperNamespace);
        try {
            String encodedValueString = URLEncoder.encode(valueStringUpNS, StandardCharsets.UTF_8.toString());
            String iriValue = NamespaceTermToIRI(upperNamespace, encodedValueString);
            Resource resourceValue = Values.iri(iriValue);
            return resourceValue;
        } catch(Exception exc) {
            throw new RuntimeException(exc);
        }
    }

    /*
    Input can be: "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.Handle"

    FIXME: The namespace should not be a parameter, but parsed from the IRI and compared with the WBEM path.
    */
    static public String IriToWbemPath(String namespace, String iri) {
        WmiProvider.CheckValidNamespace(namespace);
        int lenPrefix = NamespaceUrlPrefix(namespace).length();
        String encodedValueString = iri.substring(lenPrefix);
        try {
            String decodedValueString = URLDecoder.decode(encodedValueString, StandardCharsets.UTF_8.toString());
            return decodedValueString;
        }
        catch(Exception exc) {
            logger.error("Invalid iri=" + iri);
            throw new RuntimeException(exc);
        }
    }

    static private ValueFactory factory = SimpleValueFactory.getInstance();

    static private Literal literalInternational(String inputString) {
        return factory.createLiteral(PresentUtils.internationalizeUnquoted(inputString));
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
    static private void CreateOntologyInRepository(String namespace, RepositoryConnection connection){
        logger.debug("CreateOntologyInRepository namespace=" + namespace);
        // We want to reuse this namespace when creating several building blocks.

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

        Map<String, WmiProvider.WmiClass> classes = wmiProvider.classesMap(namespace);
        Literal literalNamespace = factory.createLiteral(namespace);

        for(Map.Entry<String, WmiProvider.WmiClass> entry_class : classes.entrySet()) {
            String className = entry_class.getKey();

            IRI classIri = lambdaClassToNode.apply(className);

            WmiProvider.WmiClass wmiClass = entry_class.getValue();
            connection.add(classIri, RDF.TYPE, RDFS.CLASS);
            connection.add(classIri, RDFS.LABEL, literalInternational(className));
            connection.add(classIri, RDFS.COMMENT, literalInternational(wmiClass.classDescription));
            connection.add(classIri, iriNamespaceProperty, literalNamespace);

            if (wmiClass.classBaseName != null) {
                IRI baseClassIri = lambdaClassToNode.apply(wmiClass.classBaseName);
                if(baseClassIri != null) {
                    connection.add(classIri, RDFS.SUBCLASSOF, baseClassIri);
                }
            }

            for(Map.Entry<String, WmiProvider.WmiProperty> entry_property : wmiClass.classProperties.entrySet()) {
                String ambiguousPropertyName = entry_property.getKey();
                String uniquePropertyName = className + "." + ambiguousPropertyName;

                IRI uniquePropertyIri = iri(namespace_iri_prefix, uniquePropertyName);
                WmiProvider.WmiProperty wmiProperty = entry_property.getValue();

                connection.add(uniquePropertyIri, RDF.TYPE, RDF.PROPERTY);
                connection.add(uniquePropertyIri, RDFS.LABEL, literalInternational(uniquePropertyName));
                connection.add(uniquePropertyIri, RDFS.DOMAIN, classIri);
                connection.add(uniquePropertyIri, RDFS.COMMENT, literalInternational(wmiProperty.propertyDescription));
                connection.add(uniquePropertyIri, iriNamespaceProperty, literalNamespace);

                if(wmiProperty.isWbemPathRef()) {
                    String domainName = wmiProperty.propertyType.substring(4);
                    // This should be another class.
                    IRI domainIri = iri(namespace_iri_prefix, domainName);
                    connection.add(uniquePropertyIri, RDFS.RANGE, domainIri);
                }
                else
                {
                    IRI iriType = wmi_type_to_xsd.get(wmiProperty.propertyType);
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
                    connection.add(ambiguousPropertyIri, RDFS.LABEL, literalInternational(ambiguousPropertyName));
                    connection.add(ambiguousPropertyIri, RDFS.COMMENT, literalInternational(ambiguousPropertyName + " maps to several classes"));
                    // It does not have a domain, or rather has several domains.
                    // It does not have a range, or rather several ranges: int, string etc...
                    // or different classes if this is an associator.
                }
                connection.add(uniquePropertyIri, RDFS.SUBPROPERTYOF, ambiguousPropertyIri);
            }
        }
        logger.debug("End");
    }

    public static boolean isWbemPath(String namespace, String className, String predicateName) {
        /* Special case for testing purpose only. */
        if(namespace.equals("ROOT\\CIMV2") && className.equals("DummyClass") && predicateName.equals("DummyKey")) {
            return false;
        }

        Map<String, WmiProvider.WmiClass> classes = wmiProvider.classesMap(namespace);
        if(className == null) {
            throw new RuntimeException("Class name is null, namespace=" + namespace);
        }
        WmiProvider.WmiClass oneClass = classes.get(className);
        if(oneClass == null) {
            throw new RuntimeException("Cannot find namespace=" + namespace + " className=" + className);
        }

        // This is a special WMI case.
        if(predicateName.equals("PSComputerName")) {
            // https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.management/get-wmiobject?view=powershell-5.1
            // Beginning in Windows PowerShell 3.0,
            // the __Server property of the object that Get-WmiObject returns has a PSComputerName alias.
            logger.debug("Special case namespace=" + namespace + " className=" + className + " predicateName=" + predicateName);
            return false;
        }
        WmiProvider.WmiProperty oneProperty = oneClass.classProperties.get(predicateName);
        if(oneProperty == null) {
            throw new RuntimeException("Cannot find namespace=" + namespace + " className=" + className + " predicateName=" + predicateName);
        }
        return oneProperty.isWbemPathRef();
    }

    static private void WriteRepository(RepositoryConnection repositoryConnection, String rdfFileName) throws Exception {
        logger.debug("Storing RDF statements to:" + rdfFileName);
        FileOutputStream out = new FileOutputStream(rdfFileName);
        RDFWriter writer = Rio.createWriter(RDFFormat.RDFXML, out);
        writer.startRDF();
        RepositoryResult<Statement> rr = repositoryConnection.getStatements(null, null, null);

        for (Statement st: rr) {
            writer.handleStatement(st);
        }
        writer.endRDF();
    }

    /** This stores the repository connection containing the ontology for each namespace,
     * so it is not necessary to read them several times from file.
     * They should never be modified but instead copied to a new repository.
     */
    static private HashMap<String, RepositoryConnection> mapNamespaceToConnects = new HashMap<>();

    /** Used to display information about the repository. */
    /*
    static public Set<String> Namespaces() {
        return mapNamespaceToConnects.keySet();
    }
    */

    static public RepositoryConnection readOnlyOntologyConnection(String namespace) {
        WmiProvider.CheckValidNamespace(namespace);
        RepositoryConnection repositoryConnection = mapNamespaceToConnects.get(namespace);
        if (repositoryConnection != null) {
            logger.debug("Ontology connection in cache for namespace=" + namespace);
            return repositoryConnection;
        }
        repositoryConnection = readOnlyOntologyConnectionNoCache(namespace);
        mapNamespaceToConnects.put(namespace, repositoryConnection);
        return repositoryConnection;
    }

    /** This is only for tests. */
    static public RepositoryConnection readOnlyOntologyConnectionNoCacheInMemory(String namespace) {
        logger.debug("Memory-only ontology for namespace=" + namespace);
        MemoryStore memStore = new MemoryStore();
        Repository repo = new SailRepository(memStore);
        RepositoryConnection repositoryConnection = repo.getConnection();
        CreateOntologyInRepository(namespace, repositoryConnection);
        logger.debug("Number of created statements=" + repositoryConnection.size());
        return repositoryConnection;
    }

    static private RepositoryConnection readOnlyOntologyConnectionNoCache(String namespace) {
        WmiProvider.CheckValidNamespace(namespace);
        RepositoryConnection repositoryConnection;

        try {
            File dirSaildump = CacheManager.DirSailDump(namespace);
            logger.debug("namespace=" + namespace + " dirSaildump=" + dirSaildump);
            boolean fileExists = Files.exists(dirSaildump.toPath());

            // TODO: Find a way to IMPORT this content into a new MemoryStore, later unconnected to the file.
            // TODO: This will avoid a useless copy.
            // TODO: See MemoryStore.setPersist
            // FIXME : Possibly a bug when the cache is obsolete. In this case, just delete the cache directory.

            if (fileExists) {
                // Load the existing ontology from the file and sets the repository connection to it.
                logger.debug("Directory exists: dirSaildump=" + dirSaildump);
                MemoryStore memStore = new MemoryStore(dirSaildump);
                memStore.setSyncDelay(3600000L); // Practically no synchronization.
                logger.debug("MemoryStore created and loaded");
                Repository repo = new SailRepository(memStore);
                repositoryConnection = repo.getConnection();
                logger.debug("Cached statements=" + repositoryConnection.size());
            } else {
                // Creates a file repository and creates the ontology into it,
                // or reuses an existing file (This case for testing only).
                logger.debug("File does not exist: dirSaildump=" + dirSaildump);
                MemoryStore memStore = new MemoryStore(dirSaildump);
                memStore.setSyncDelay(3600000L); // Practically no synchronization.
                Repository repo = new SailRepository(memStore);
                repositoryConnection = repo.getConnection();
                logger.debug("Caching new statements before=" + repositoryConnection.size() + " isActive=" + repositoryConnection.isActive());
                repositoryConnection.begin();
                CreateOntologyInRepository(namespace, repositoryConnection);
                repositoryConnection.commit();
                memStore.sync();
                logger.debug("Cached " + repositoryConnection.size() + " statements");
                // Also saves the new ontology to a RDF file.
                Path pathNamespacePrefix = CacheManager.PathNamespacePrefix(namespace);
                WriteRepository(repositoryConnection, pathNamespacePrefix + ".rdf");
            }
        }
        catch(Exception exc)
        {
            logger.error("Namespace=" + namespace + " Caught:" + exc);
            throw new RuntimeException(exc);
        }
        if(repositoryConnection.isEmpty()) {
            throw new RuntimeException("Ontology in " + CacheManager.ontologiesPathCache + " is empty for namespace=" + namespace);
        }
        return repositoryConnection;
    }

    private static void insertOntologyToConnection(String namespace, RepositoryConnection repositoryConnection) {
        long countInit = repositoryConnection.size();
        logger.debug("Inserting " + namespace + " ontology statements:" + countInit);
        logger.debug("isActive " + repositoryConnection.isActive());
        RepositoryConnection sourceConnection = readOnlyOntologyConnection(namespace);
        // TODO: Avoid this useless copy.
        RepositoryResult<Statement> result = sourceConnection.getStatements(null, null, null, true);
        repositoryConnection.add(result);
        long countEnd = repositoryConnection.size();
        logger.debug("Inserted " + (countEnd - countInit) + " statements from " + countInit);
    }

    /** This loads all triples of the ontology and inserts them in the repository.
     * This is rather slow because an ontology contains thousands of triples.
     * TODO: Automatically load the ontology associated to the namespace when parsing the Sparql query.
     */
    public static RepositoryConnection cloneToMemoryConnection(String ... namespaces) {
        // This is not persisted to a file.
        Repository repo = new SailRepository(new MemoryStore());
        RepositoryConnection repositoryConnection = repo.getConnection();
        for(String namespace:namespaces) {
            WmiProvider.CheckValidNamespace(namespace);
            insertOntologyToConnection(namespace, repositoryConnection);
        }
        return repositoryConnection;
    }

    /** This returns a repository with ontologies of all namespaces. */
    public static RepositoryConnection cloneToMemoryConnection() throws Exception {
        Repository repo = new SailRepository(new MemoryStore());
        RepositoryConnection repositoryConnection = repo.getConnection();

        for(String namespace: wmiProvider.namespacesList()) {
            insertOntologyToConnection(namespace, repositoryConnection);
        }

        return repositoryConnection;
    }

    /* This IRI can be a RDF/RDFS one, a WBEM class or predicate, or a WBEM instance.
    Examples:
         "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process"
    or:  "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.Handle"
    or: "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Process.Handle%3D%223160%22"
    or: "http://www.w3.org/2000/01/rdf-schema#label"
    */
    static NamespaceTokenPair splitIRI(String token) {
        logger.debug("token=" + token);
        if(! token.contains("#")) {
            logger.debug("Cannot split token:" + token);
            return null;
        }
        String[] splitToken = token.split("#");

        String prefixUrl = splitToken[0];

        if(prefixUrl.startsWith(namespaces_url_prefix)) {
            String wmiNamespaceSlashes = prefixUrl.substring(namespaces_url_prefix.length());
            // In the URL, the backslash separator of namespaces is replaced with a slash.
            String wmiNamespace = wmiNamespaceSlashes.replace("/", "\\");
            WmiProvider.CheckValidNamespace(wmiNamespace);
            String className = splitToken[1];
            NamespaceTokenPair.TokenTypeEnum tokenType;
            if(className.startsWith("%5C%5C")) {
                /* Typical values:
                token = "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Process.Handle%3D%223160%22"
                className = "%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Process.Handle%3D%223160%22"
                This is a constant instance IRI built with a WMI path. Machine name and the namespace must be removed.
                */
                String wbemPath = IriToWbemPath("ROOT\\CIMV2", token);
                className = WmiProvider.ExtractClassnameFromRef(wbemPath);
                if(className == null) {
                    throw new RuntimeException("Classname should not be null. wbemPath=" + wbemPath);
                }
                tokenType = NamespaceTokenPair.TokenTypeEnum.INSTANCE_IRI;
            } else {
                if (className.indexOf('.') > 0) {
                    tokenType = NamespaceTokenPair.TokenTypeEnum.PREDICATE_IRI;
                    WmiProvider.CheckValidPredicate(className);
                } else {
                    tokenType = NamespaceTokenPair.TokenTypeEnum.CLASS_IRI;
                    WmiProvider.CheckValidClassname(className);
                }
            }
            return new NamespaceTokenPair(wmiNamespace, className, tokenType);
        } else {
            // the input string might be "http://www.w3.org/2000/01/rdf-schema#label"
            return null;
        }
    }

    // It returns something like "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Directory.Name%3D%22C%3A%5C%5CWindows%22"
    static public String createUriVarArgs(String className, String propertyName, String propertyValue) throws Exception {
        String iri = "\\\\" + PresentUtils.computerName + "\\ROOT\\CIMV2:" + className + "." + propertyName + "=" + "\"" + propertyValue + "\"";
        return wbemPathToIri("ROOT\\CIMV2", iri).stringValue();
    }


    /** This contains a WMI namespace, and a class name or a property name. */
    public static class NamespaceTokenPair {
        public String nameSpace;
        public String Token;

        public enum TokenTypeEnum {
            INSTANCE_IRI,
            CLASS_IRI,
            PREDICATE_IRI
        }

        TokenTypeEnum TokenType;

        NamespaceTokenPair(String namespace, String token, TokenTypeEnum tokenType) {
            nameSpace = namespace;
            Token = token;
            TokenType = tokenType;
        }
    }
}
