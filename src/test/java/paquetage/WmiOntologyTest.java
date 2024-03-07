package paquetage;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.Assert;
import org.junit.Test;

import java.util.Formatter;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WmiOntologyTest {
    static private RepositoryConnection ontologyCIMV2 = WmiOntology.cloneToMemoryConnection("ROOT\\CIMV2");
    static private RepositoryConnection ontologyInterop = WmiOntology.cloneToMemoryConnection("ROOT\\Interop");
    static private RepositoryConnection ontologyStandardCimv2 = WmiOntology.cloneToMemoryConnection("ROOT\\StandardCimv2");

    static private RepositoryConnection ontologyCIMV2_StandardCimv2 = WmiOntology.cloneToMemoryConnection("ROOT\\CIMV2", "ROOT\\StandardCimv2");

    static WmiProvider wmiProvider = new WmiProvider();

    /** The Sparql query is executed in the repository of the ontology.
     * This is why this repository must NOT be cached because it is polluted with the output of the tests.
     * @param repositoryConnection
     * @param sparqlQuery
     * @param columnName
     * @return
     */
    private static Set<String> selectColumnFromOntology(RepositoryConnection repositoryConnection, String sparqlQuery, String columnName){
        System.out.println("Repository size before=" + repositoryConnection.size());
        TupleQuery tupleQuery = repositoryConnection.prepareTupleQuery(sparqlQuery);
        TupleQueryResult queryResult = tupleQuery.evaluate();
        Set<String> variablesSet;
        if(false) {
            // For debugging.
            List<BindingSet> ll = queryResult.stream().collect(Collectors.toList());
            System.out.println("ll.size()=" + ll.size());
            for (BindingSet bs : ll) {
                System.out.println("bs=" + bs);
            }
            variablesSet = ll.stream().map(result -> result.getValue(columnName).toString()).collect(Collectors.toSet());
        } else {
            variablesSet = queryResult.stream().map(result -> result.getValue(columnName).toString()).collect(Collectors.toSet());
        }
        System.out.println("Repository size after=" + repositoryConnection.size());
        return variablesSet;
    }

    /** This executes a Sparql query in the repository containing the CIMV2 ontology. */
    private static Set<String> selectColumnCIMV2(String sparqlQuery, String columnName){
        return selectColumnFromOntology(ontologyCIMV2, sparqlQuery, columnName);
    }

    /** This executes a Sparql query in the repository containing the StandardCIMV2 ontology. */
    private static Set<String> selectColumnStandardCimv2(String sparqlQuery, String columnName){
        return selectColumnFromOntology(ontologyStandardCimv2, sparqlQuery, columnName);
    }

    /** This executes a Sparql query in the repository containing the CIMV2 and StandardCIMV2 ontology. */
    private static Set<String> selectColumnCIMV2_StandardCimv2(String sparqlQuery, String columnName){
        return selectColumnFromOntology(ontologyCIMV2_StandardCimv2, sparqlQuery, columnName);
    }

    private static void assertContainsItemCIMV2(Set<String> itemsSet, String shortItem) {
        Assert.assertTrue(itemsSet.contains(WmiProvider.toCIMV2(shortItem)));
    }

    private static void assertContainsItemInterop(Set<String> itemsSet, String shortItem) {
        Assert.assertTrue(itemsSet.contains(WmiOntology.namespaceTermToIRI("ROOT\\Interop", shortItem)));
    }

    @Test
    public void testExtractClassnameFromRef_test() {
        String wbemPath = "\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:Win32_Process.Handle=\"12456\"";
        String className = WmiProvider.extractClassnameFromRef(wbemPath);
        Assert.assertEquals("Win32_Process", className);
    }

    @Test
    public void testSplitIRI_testA() {
        String token = "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process";
        WmiOntology.NamespaceTokenPair ntp = WmiOntology.splitIRI(token);
        Assert.assertEquals("ROOT\\CIMV2", ntp.pairNamespace);
        Assert.assertEquals("Win32_Process", ntp.pairToken);
        Assert.assertEquals(WmiOntology.NamespaceTokenPair.TokenTypeEnum.CLASS_IRI, ntp.pairTokenType);
    }

    @Test
    public void testSplitIRI_testB() {
        String iri = "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.Handle";
        WmiOntology.NamespaceTokenPair ntp = WmiOntology.splitIRI(iri);
        Assert.assertEquals("ROOT\\CIMV2", ntp.pairNamespace);
        Assert.assertEquals("Win32_Process.Handle", ntp.pairToken);
        Assert.assertEquals(WmiOntology.NamespaceTokenPair.TokenTypeEnum.PREDICATE_IRI, ntp.pairTokenType);
    }
    @Test
    public void testSplitIRI_testC() {
        String iri = "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Process.Handle%3D%223160%22";
        WmiOntology.NamespaceTokenPair ntp = WmiOntology.splitIRI(iri);
        Assert.assertEquals("ROOT\\CIMV2", ntp.pairNamespace);
        Assert.assertEquals("Win32_Process", ntp.pairToken);
        Assert.assertEquals(WmiOntology.NamespaceTokenPair.TokenTypeEnum.INSTANCE_IRI, ntp.pairTokenType);
    }

    @Test
    public void testSplitIRI_testD() {
        String iri = "http://www.w3.org/2000/01/rdf-schema#label" ;
        WmiOntology.NamespaceTokenPair ntp = WmiOntology.splitIRI(iri);
        Assert.assertEquals(null, ntp);
    }

    @Test
    public void testIriToWbemPath_testA() {
        String iri = "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Process.Handle%3D%222424%22";
        String wbemPath = WmiOntology.iriToWbemPath("ROOT\\CIMV2", iri);
        Assert.assertEquals("\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:Win32_Process.Handle=\"2424\"", wbemPath);
    }

    @Test
    public void testIriToWbemPath_testB() {
        String iri = "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.Handle";
        String wbemPath = WmiOntology.iriToWbemPath("ROOT\\CIMV2", iri);
        Assert.assertEquals("Win32_Process.Handle", wbemPath);
    }

    @Test
    public void testInterop_ClassesAll() {
        Set<String> subjectsSet = selectColumnFromOntology(
                ontologyInterop,
                "SELECT ?x WHERE { ?x ?p ?y }",
                "x");
        System.out.println("subjectsSet=" + subjectsSet);
        assertContainsItemInterop(subjectsSet, "CIM_ReferencedProfile");
        assertContainsItemInterop(subjectsSet, "CIM_ElementConformsToProfile");
        assertContainsItemInterop(subjectsSet, "Win32_PowerMeterConformsToProfile");
    }

    /** The content of the cached ontology and the fresh one should be the same.
     * This compares the number of triples in both repositories, and this gives a good estimate.
     * TODO: Why does the ontology changes so often ?
     */
    @Test
    public void testCompare_Ontology_Cached() {
        String namespace = "ROOT\\Cli";
        RepositoryConnection ontologyNonCached = WmiOntology.readOnlyOntologyConnectionNoCacheInMemory(namespace);
        RepositoryConnection cachedOntology = WmiOntology.cloneToMemoryConnection(namespace);

        String sparqlQuery = "select (count(*) as ?count) where { ?s ?p ?o }";
        Set<String> countFresh = selectColumnFromOntology(cachedOntology, sparqlQuery, "count");
        Set<String> countCache = selectColumnFromOntology(ontologyNonCached, sparqlQuery, "count");
        Assert.assertEquals(countFresh, countCache);
    }

    /** This instantiates all ontologies of all namespaces.
     *
     */
    @Test
    public void testLoadAllOntologies() throws Exception {
        Set<String> setNamespaces = wmiProvider.namespacesList();
        for (String oneNamespace : setNamespaces) {
            RepositoryConnection ontologyNamespace = WmiOntology.cloneToMemoryConnection(oneNamespace);
            String sparqlQuery = "select (count(*) as ?count) where { ?s ?p ?o }";
            Set<String> countTriples = selectColumnFromOntology(ontologyNamespace, sparqlQuery, "count");
            Assert.assertEquals(1, countTriples.size());
            String firstString = countTriples.iterator().next();
            long sizeLong = PresentUtils.xmlToLong(firstString);
            Assert.assertTrue(sizeLong > 0);
        }
    }

    /** This selects all triples, and detects that some classes are present. */
    @Test
    public void testCIMV2_ClassesAll() {
        Set<String> subjectsSet = selectColumnCIMV2("SELECT ?x WHERE { ?x ?p ?y }", "x");
        assertContainsItemCIMV2(subjectsSet, "Win32_Process");
        assertContainsItemCIMV2(subjectsSet, "CIM_DataFile");
        assertContainsItemCIMV2(subjectsSet, "CIM_ProcessExecutable");
        assertContainsItemCIMV2(subjectsSet, "CIM_DirectoryContainsFile");
        assertContainsItemCIMV2(subjectsSet, "Win32_SystemServices");
        assertContainsItemCIMV2(subjectsSet, "Win32_SubDirectory");
    }

    /** This selects all definitions of RDF types and checks the presence of some classes. */
    @Test
    public void testCIMV2_ClassesFilter() {
        Set<String> typesSet = selectColumnCIMV2("SELECT ?x WHERE { ?x rdf:type rdfs:Class }", "x");
        assertContainsItemCIMV2(typesSet, "Win32_Process");
        assertContainsItemCIMV2(typesSet, "CIM_ProcessExecutable");
        assertContainsItemCIMV2(typesSet, "CIM_DirectoryContainsFile");
    }

    /** This selects all triples, and detects that some properties are present. */
    @Test
    public void testCIMV2_PropertiesAll() {
        Set<String> subjectsSet = selectColumnCIMV2("SELECT ?x WHERE { ?x ?p ?y }", "x");
        assertContainsItemCIMV2(subjectsSet, "Handle");
        assertContainsItemCIMV2(subjectsSet, "Name");
        assertContainsItemCIMV2(subjectsSet, "Antecedent");
        assertContainsItemCIMV2(subjectsSet, "Dependent");
    }

    /** This selects all definitions of RDF properties and checks the presence of some properties. */
    @Test
    public void testCIMV2_PropertiesFilter() {
        Set<String> propertiesSet = selectColumnCIMV2(
                "SELECT ?y WHERE { ?y rdf:type rdf:Property }", "y");
        assertContainsItemCIMV2(propertiesSet, "Handle");
        assertContainsItemCIMV2(propertiesSet, "Dependent");
    }

    /** This checks that the domains of some properties are loaded in the ontology.
     *
     */
    @Test
    public void testCIMV2_Domain_All() {
        String queryString = "SELECT ?y WHERE { ?y rdfs:domain ?z }";
        Set<String> domainsSet = selectColumnCIMV2(queryString, "y");
        assertContainsItemCIMV2(domainsSet, "CIM_Process.Handle");
        assertContainsItemCIMV2(domainsSet, "Win32_UserAccount.Name");
        assertContainsItemCIMV2(domainsSet, "Win32_Process.Handle");
    }

    /* This data is required by Wikidata Sparql GUI. */
    @Test
    public void testCIMV2_DirectClaim() {
        String queryString = "SELECT ?property WHERE { ?wdt_predicate <http://wikiba.se/ontology#directClaim> ?property }";
        Set<String> propertysSet = selectColumnCIMV2(queryString, "property");
        assertContainsItemCIMV2(propertysSet, "Win32_Process.Handle");
    }

    /** This checks the presence of class Wmi32_Process in the domain of Win32_Process.Handle.
     * The node of Win32_Process.Handle is explicitly given.
     */
    @Test
    public void testCIMV2_Win32_Process_Handle_Domain_Filter() {
        String queryString = new Formatter().format(
                "SELECT ?x WHERE { <%s> rdfs:domain ?x }", WmiProvider.toCIMV2("Win32_Process.Handle")).toString();
        Set<String> domainsSet = selectColumnCIMV2(queryString, "x");
        System.out.println("domainsSet=" + domainsSet.toString());
        assertContainsItemCIMV2(domainsSet, "Win32_Process");
    }

    /** This checks the presence of the class "string" in one of the ranges. */
    @Test
    public void testCIMV2_Range_Filter() {
        // Predicates: [
        // http://www.w3.org/2000/01/rdf-schema#label,
        // http://www.w3.org/2000/01/rdf-schema#domain,
        // http://www.w3.org/1999/02/22-rdf-syntax-ns#type,
        // http://www.w3.org/2000/01/rdf-schema#range]
        String queryString = new Formatter().format(
                "SELECT ?x WHERE { <%s> rdfs:range ?x }",
                WmiProvider.toCIMV2("CIM_Process.Handle")).toString( );
        Set<String> rangesSet = selectColumnCIMV2(queryString, "x");
        Assert.assertTrue(rangesSet.contains("http://www.w3.org/2001/XMLSchema#string"));
    }

    /** This checks the presence Description for class Win32_Process. */
    @Test
    public void testCIMV2_Win32_Process_Description() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_class_description
                    where {
                        cimv2:Win32_Process rdfs:comment ?my_class_description .
                    }
                """;
        Set<String> descriptionsSet = selectColumnCIMV2(queryString, "my_class_description");
        Assert.assertEquals(1, descriptionsSet.size());
        String expectedDescription = "\"\"The Win32_Process class represents a sequence of events on a Win32 system. Any sequence consisting of the interaction of one or more processors or interpreters, some executable code, and a set of inputs, is a descendent (or member) of this class.  Example: A client application running on a Win32 system.\"";
        String actualDescription = descriptionsSet.stream().findFirst().orElse("xyz");
        System.out.println("expectedDescription=" + expectedDescription);
        System.out.println("actualDescription=" + actualDescription);
        Assert.assertEquals(expectedDescription.substring(0,10), actualDescription.substring(0,10));
        Assert.assertTrue(actualDescription.endsWith("\"@en\""));
    }

    /** This selects the base class of Win32_Process */
    @Test
    public void testCIMV2_Win32_Process_BaseClass() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_base_class
                    where {
                        cimv2:Win32_Process rdfs:subClassOf ?my_base_class .
                    }
                """;
        Set<String> baseClassesSet = selectColumnCIMV2(queryString, "my_base_class");
        Assert.assertEquals(1, baseClassesSet.size());
        System.out.println(baseClassesSet);
        assertContainsItemCIMV2(baseClassesSet, "CIM_Process");
    }

    /** This select the labels of all CIM classes starting from the top. */
    @Test
    public void testCIMV2_DerivedClasses() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_caption
                    where {
                        ?my_derived_class rdfs:subClassOf+ cimv2:CIM_LogicalElement .
                        ?my_derived_class rdfs:label ?my_caption .
                    }
                """;
        Set<String> derivedClassNamesSet = selectColumnCIMV2(queryString, "my_caption");
        System.out.println(derivedClassNamesSet);
        // Test the presence of some classes which derive of this one.
        Assert.assertTrue(derivedClassNamesSet.contains(PresentUtils.internationalizeQuoted("Win32_Directory")));
        Assert.assertTrue(derivedClassNamesSet.contains(PresentUtils.internationalizeQuoted("Win32_BIOS")));
    }

    /** This checks the presence of Description for property Win32_Process.Handle. */
    @Test
    public void testCIMV2_Win32_Process_Handle_Description() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_property_description
                    where {
                        cimv2:Win32_Process.Handle rdfs:comment ?my_property_description .
                        cimv2:Win32_Process.Handle rdfs:domain cimv2:Win32_Process .
                    }
                """;
        Set<String> descriptionsSet = selectColumnCIMV2(queryString, "my_property_description");
        System.out.println("descriptionsSet=" + descriptionsSet.toString());
        Assert.assertEquals(1, descriptionsSet.size());
        String firstDescription = descriptionsSet.stream().findFirst().orElse("xyz");
        /*
JSON:
"propertyDescription":"A string used to identify the process. A process ID is a kind of process handle."
RDF:
<comment xmlns="http://www.w3.org/2000/01/rdf-schema#">"A string used to identify the process.
A process ID is a kind of process handle."@en</comment>

descriptionsSet=[""A string used to identify the process. A process ID is a kind of process handle."@en"]

On dirait vraiment que c'est la lecture de Sparql qui ajoute des quotes.
Mais pourquoi aux labels et pas aux autres chaines ?
         */
        System.out.println("firstDescription=" + firstDescription);
        Assert.assertEquals(
                PresentUtils.internationalizeQuoted("\"A string used to identify the process. A process ID is a kind of process handle.\""),
                firstDescription);
    }

    @Test
    public void testCIMV2_Win32_Process_Handle_Label() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_property_label
                    where {
                        cimv2:Win32_Process.Handle rdfs:label ?my_property_label .
                        cimv2:Win32_Process.Handle rdfs:domain cimv2:Win32_Process .
                    }
                """;
        Set<String> labelSet = selectColumnCIMV2(queryString, "my_property_label");
        System.out.println("labelSet=" + labelSet.toString());
        Assert.assertEquals(1, labelSet.size());
        String firstLabel = labelSet.stream().findFirst().orElse("xyz");
        /*
JSON:
"propertyDescription":"A string used to identify the process. A process ID is a kind of process handle."
RDF:
<comment xmlns="http://www.w3.org/2000/01/rdf-schema#">"A string used to identify the process.
A process ID is a kind of process handle."@en</comment>

labelSet=[""A string used to identify the process. A process ID is a kind of process handle."@en"]

On dirait vraiment que c'est la lecture de Sparql qui ajoute des quotes.
Mais pourquoi aux labels et pas aux autres chaines ?

bs=[my_property_label=""Win32_Process.Handle"@en"]
Repository size after=171779
labelSet=[""Win32_Process.Handle"@en"]
firstLabel=""Win32_Process.Handle"@en"
expectedLabel=""Win32_Process.Handle"@enEFGH"

org.junit.ComparisonFailure:
Expected :""Win32_Process.Handle"@enEFGH"
Actual   :""Win32_Process.Handle"@en"

Soit le contenu est foireux, soit les quotes sont ajoutees a la selection.
En effet, le code de Present n'est quasiment pas utilise.
         */

        //Verifier ceci avec le select d'un label calcule et pas charge de la base statique.
        // Oui, aussi : testSPARQL_ENTITES_INCOMING_NoFilter

        System.out.println("firstLabel=" + firstLabel);
        String expectedLabel = PresentUtils.internationalizeQuoted("Win32_Process.Handle");
        System.out.println("expectedLabel=" + expectedLabel);
        Assert.assertEquals(
                expectedLabel,
                firstLabel);
    }

    /** This checks the presence of Description for property Win32_Process.Handle. */
    @Test
    public void testCIMV2_Win32_UserAccount_Name_Description() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_property_description
                    where {
                        cimv2:Win32_UserAccount.Name rdfs:comment ?my_property_description .
                        cimv2:Win32_UserAccount.Name rdfs:domain cimv2:Win32_UserAccount .
                    }
                """;
        Set<String> descriptionsSet = selectColumnCIMV2(queryString, "my_property_description");
        System.out.println("descriptionsSet=" + descriptionsSet.toString());
        Assert.assertEquals(1, descriptionsSet.size());
        String firstDescription = descriptionsSet.stream().findFirst().orElse("xyz");
        Assert.assertTrue(firstDescription.startsWith("\"\"The Name property"));
        Assert.assertTrue(firstDescription.endsWith("\"@en\""));
    }

    @Test
    public void testCIMV2_Win32_ClassInfoAction_AppID() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_class_node
                    where {
                        cimv2:Win32_ClassInfoAction.AppID rdfs:domain ?my_class_node .
                    }
                """;
        Set<String> domainsSet = selectColumnCIMV2(queryString, "my_class_node");
        Assert.assertEquals(1, domainsSet.size());
        assertContainsItemCIMV2(domainsSet, "Win32_ClassInfoAction");
    }

    /** All class with the property "AppID". */
    @Test
    public void testCIMV2_Classes_AppID() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_class_node
                    where {
                        ?my_property_node rdfs:domain ?my_class_node .
                        ?my_property_node rdfs:subPropertyOf cimv2:AppID .
                    }
                """;
        Set<String> domainsSet = selectColumnCIMV2(queryString, "my_class_node");
        assertContainsItemCIMV2(domainsSet, "Win32_ClassInfoAction");
        assertContainsItemCIMV2(domainsSet, "Win32_DCOMApplication");
        assertContainsItemCIMV2(domainsSet, "Win32_ClassicCOMClassSetting");
        assertContainsItemCIMV2(domainsSet, "Win32_DCOMApplicationSetting");
    }

    /** Properties used by at least four tables. */
    @Test
    public void testCIMV2_Classes_SharedProperty() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_property_node (COUNT(?my_property_node) AS ?total)
                    where {
                        ?my_subproperty_node rdfs:subPropertyOf ?my_property_node .
                        ?my_subproperty_node rdfs:domain ?my_class_node .
                    }
                    group by ?my_property_node
                    having (?total > 3)
                """;
        Set<String> propertiesSet = selectColumnCIMV2(queryString, "my_property_node");
        System.out.println("propertiesSet=" + propertiesSet.toString());
        assertContainsItemCIMV2(propertiesSet, "CreationClassName");
        assertContainsItemCIMV2(propertiesSet, "Name");
        assertContainsItemCIMV2(propertiesSet, "Caption");
        assertContainsItemCIMV2(propertiesSet, "AppID");
    }

    @Test
    public void testCIMV2_Associators_Antecedent() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_domain
                    where {
                        cimv2:CIM_ProcessExecutable.Antecedent rdfs:range ?my_domain .
                        cimv2:CIM_ProcessExecutable.Antecedent rdfs:domain cimv2:CIM_ProcessExecutable .
                    }
                """;
        // Beware, there are several cimv2:Antecedent properties.
        Set<String> domainsSet = selectColumnCIMV2(queryString, "my_domain");
        System.out.println("domainsSet=" + domainsSet.toString());
        assertContainsItemCIMV2(domainsSet, "CIM_DataFile");
    }

    @Test
    public void testCIMV2_Associators_Dependent() {
        String queryString = """
                prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                select ?my_domain
                where {
                    cimv2:CIM_ProcessExecutable.Dependent rdfs:range ?my_domain .
                    cimv2:CIM_ProcessExecutable.Dependent rdfs:domain cimv2:CIM_ProcessExecutable .
                }
            """;
        // Beware, there are several cimv2:Dependent properties.
        Set<String> domainsSet = selectColumnCIMV2(queryString, "my_domain");
        System.out.println("domainsSet=" + domainsSet.toString());
        assertContainsItemCIMV2(domainsSet, "CIM_Process");
    }

    /** All unique properties which have the same name "Handle". */
    @Test
    public void CIMV2_Handle_Homonyms() {
        String queryString = """
                        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?my_sub_property
                        where {
                            ?my_sub_property rdfs:subPropertyOf cimv2:Handle .
                        }
                    """;
        Set<String> subPropertiesSet = selectColumnCIMV2(queryString, "my_sub_property");
        System.out.println("subPropertiesSet=" + subPropertiesSet.toString());
        assertContainsItemCIMV2(subPropertiesSet, "CIM_Process.Handle");
        assertContainsItemCIMV2(subPropertiesSet, "CIM_Thread.Handle");
        assertContainsItemCIMV2(subPropertiesSet, "Win32_Thread.Handle");
        assertContainsItemCIMV2(subPropertiesSet, "Win32_Process.Handle");
    }

    /** All associators referring to a CIM_Process. */
    @Test
    public void testCIMV2_Associators_To_CIM_Process() {
        String queryString = """
                        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?my_associator ?my_description
                        where {
                            ?my_subproperty rdfs:range cimv2:CIM_Process .
                            ?my_subproperty rdfs:domain ?my_associator .
                            ?my_associator rdfs:comment ?my_description .
                        }
                    """;
        Set<String> associatorsSet = selectColumnCIMV2(queryString, "my_associator");
        System.out.println("associatorsSet=" + associatorsSet.toString());
        assertContainsItemCIMV2(associatorsSet, "CIM_OSProcess");
        assertContainsItemCIMV2(associatorsSet, "CIM_ProcessThread");
        assertContainsItemCIMV2(associatorsSet, "CIM_ProcessExecutable");

        Set<String> descriptionsSet = selectColumnCIMV2(queryString, "my_description");
        for(String oneDescription : descriptionsSet) {
            // For example: "A link between a process and the thread"
            System.out.println("    oneDescription=" + oneDescription);
            Assert.assertTrue(oneDescription.startsWith("\"\"A link between"));
        }
    }

    /** All associators referring to a Win32_Process. */
    @Test
    public void testCIMV2_Associators_To_Win32_Process() {
        String queryString = """
                        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?my_associator
                        where {
                            ?my_subproperty rdfs:range cimv2:Win32_Process .
                            ?my_subproperty rdfs:domain ?my_associator .
                        }
                    """;
        Set<String> associatorsSet = selectColumnCIMV2(queryString, "my_associator");
        System.out.println("associatorsSet=" + associatorsSet.toString());
        assertContainsItemCIMV2(associatorsSet, "Win32_SessionProcess");
        assertContainsItemCIMV2(associatorsSet, "Win32_SystemProcesses");
        assertContainsItemCIMV2(associatorsSet, "Win32_NamedJobObjectProcess");
    }

    /** This selects the labels of the base properties of properties pointing to a Win32_Process in associators. */
    @Test
    public void testCIMV2_Associators_Labels_To_Win32_Process() {
        String queryString = """
                        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?my_label
                        where {
                            ?my_property rdfs:label ?my_label .
                            ?my_subproperty rdfs:subPropertyOf ?my_property .
                            ?my_subproperty rdfs:range cimv2:Win32_Process .
                            ?my_subproperty rdfs:domain ?my_associator .
                            ?my_associator rdfs:comment ?my_description .
                        }
                    """;
        Set<String> labelsSet = selectColumnCIMV2(queryString, "my_label");
        System.out.println("labelsSet=" + labelsSet.toString());
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("Dependent")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("Member")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("PartComponent")));
    }

    /** Labels of classes linked to a CIM_DataFile with an associator. */
    @Test
    public void testCIMV2_Associated_Classes_To_CIM_DataFile() {
        String queryString = """
                        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?my_label
                        where {
                            ?my_subproperty1 rdfs:range cimv2:CIM_DataFile .
                            ?my_subproperty1 rdfs:domain ?my_associator .
                            ?my_subproperty2 rdfs:range ?my_class .
                            ?my_subproperty2 rdfs:domain ?my_associator .
                            ?my_class rdfs:label ?my_label .
                        }
                    """;
        Set<String> labelsSet = selectColumnCIMV2(queryString, "my_label");
        System.out.println("labelsSet=" + labelsSet.toString());
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("CIM_Directory")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("Win32_PnPSignedDriver")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("Win32_DCOMApplication")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("CIM_DataFile")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("CIM_Process")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("Win32_Printer")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("Win32_LogicalProgramGroupItem")));
    }

    /** Labels of classes linked to a Win32_Process with an associator. */
    @Test
    public void testCIMV2_Associated_Classes_To_Win32_Process() {
        String queryString = """
                        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?my_label
                        where {
                            ?my_subproperty1 rdfs:range cimv2:Win32_Process .
                            ?my_subproperty1 rdfs:domain ?my_associator .
                            ?my_subproperty2 rdfs:range ?my_class .
                            ?my_subproperty2 rdfs:domain ?my_associator .
                            ?my_class rdfs:label ?my_label .
                        }
                    """;
        Set<String> labelsSet = selectColumnCIMV2(queryString, "my_label");
        System.out.println("labelsSet=" + labelsSet);
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("Win32_LogonSession")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("Win32_ComputerSystem")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("Win32_NamedJobObject")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("Win32_Process")));
    }

    /**
     * Properties of StandardCimv2 class "MSFT_NetUDPEndpoint".
     */
    @Test
    public void testStandardCimv2_MSFT_NetUDPEndpoint_Properties() {
        String queryString = """
                        prefix standard_cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/StandardCimv2#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?my_label
                        where {
                            ?my_subproperty rdfs:domain standard_cimv2:MSFT_NetUDPEndpoint .
                            ?my_subproperty rdfs:label ?my_label .
                        }
                    """;
        Set<String> labelsSet = selectColumnStandardCimv2(queryString, "my_label");
        System.out.println("labelsSet=" + labelsSet);
        // Checks the presence of arbitrary properties.
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("MSFT_NetUDPEndpoint.AggregationBehavior")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("MSFT_NetUDPEndpoint.CommunicationStatus")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("MSFT_NetUDPEndpoint.LocalAddress")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("MSFT_NetUDPEndpoint.LocalPort")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("MSFT_NetUDPEndpoint.OwningProcess")));
    }

    /**
     * Classes in the namespace StandardCimv2, whose a property name contains the string "Process".
     */
    @Test
    public void testStandardCimv2_ClassesWithFilteredProperties() {
        String queryString = """
                        prefix standard_cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/StandardCimv2#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?property_label
                        where {
                            ?my_property rdf:type rdf:Property .
                            ?my_subproperty rdfs:subPropertyOf ?my_property .
                            ?my_subproperty rdfs:domain ?my_class .
                            ?my_subproperty rdfs:label ?property_label .
                            ?my_class rdf:type rdfs:Class .
                            FILTER regex(STR(?property_label), "Process").
                        }
                    """;
        Set<String> labelsSet = selectColumnStandardCimv2(queryString, "property_label");
        System.out.println("labelsSet=" + labelsSet);
        Pattern patternNamespace = Pattern.compile("^.*Process.*$", Pattern.CASE_INSENSITIVE);
        for(String label: labelsSet ) {
            System.out.println("    label=" + label);
            Matcher matcher = patternNamespace.matcher(label);
            boolean matchFound = matcher.find();
            Assert.assertTrue(matchFound);
        }
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("MSFT_NetTransportConnection.OwningProcess")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("MSFT_NetUDPEndpoint.OwningProcess")));
    }

    /**
     * Classes with the same name in the namespaces StandardCimv2 and CIMV2.
     */
    @Test
    public void testStandardCimv2_CIMV2_HomonymClasses() {
        String queryString = """
                    prefix standard_cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/StandardCimv2#>
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix wmi:  <http://www.primhillcomputers.com/ontology/>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?class_label
                    where {
                        ?my_class_cimv2 rdf:type rdfs:Class .
                        ?my_class_cimv2 rdfs:label ?class_label .
                        ?my_class_cimv2 wmi:NamespaceDefinition "ROOT\\\\CIMV2" .
                        ?my_class_standard_cimv2 rdf:type rdfs:Class .
                        ?my_class_standard_cimv2 rdfs:label ?class_label .
                        ?my_class_standard_cimv2 wmi:NamespaceDefinition "ROOT\\\\StandardCimv2" .
                    }
                """;
        // ontologyCIMV2_StandardCimv2
        Set<String> labelsSet = selectColumnCIMV2_StandardCimv2(queryString, "class_label");
        System.out.println("labelsSet=" + labelsSet);

        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("__Win32Provider")));
        Assert.assertTrue(labelsSet.contains(PresentUtils.internationalizeQuoted("__EventFilter")));
        Assert.assertFalse(labelsSet.contains(PresentUtils.internationalizeQuoted("Win32_Process")));
        Assert.assertFalse(labelsSet.contains(PresentUtils.internationalizeQuoted("CIM_DataFile")));
        Assert.assertFalse(labelsSet.contains(PresentUtils.internationalizeQuoted("MSFT_NetUDPEndpoint")));
        Assert.assertFalse(labelsSet.contains(PresentUtils.internationalizeQuoted("MSFT_NetTCPConnection")));
    }


    /* TODO:
    Shared columns.
    Associators.
    */
}
