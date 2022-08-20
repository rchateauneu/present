package paquetage;

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.junit.Assert;
import org.junit.Test;

import java.util.Formatter;
import java.util.HashSet;
import java.util.Set;

public class WmiOntologyTest {
    static WmiOntology ontologyCIMV2 = new WmiOntology("ROOT\\CIMV2", false);
    static WmiOntology ontologyMicrosoft = new WmiOntology("ROOT\\Microsoft", false);

    private static HashSet<String> selectColumnFromOntology(WmiOntology ontologyRef, String sparqlQuery, String columnName){
        HashSet<String> variablesSet = new HashSet<String>();
        TupleQuery tupleQuery = ontologyRef.repositoryConnection.prepareTupleQuery(sparqlQuery);
        try (TupleQueryResult result = tupleQuery.evaluate()) {
            while (result.hasNext()) {  // iterate over the result
                BindingSet bindingSet = result.next();
                Value valueOfX = bindingSet.getValue(columnName);
                variablesSet.add(valueOfX.toString());
            }
        }
        return variablesSet;
    }

    /** This executes a Sparql query in the repository containing the WMI ontology. */
    private HashSet<String> selectColumnCIMV2(String sparqlQuery, String columnName){
        return selectColumnFromOntology(ontologyCIMV2, sparqlQuery, columnName);
    }

    static void assertContainsItemMicrosoft(HashSet<String> itemsSet, String shortItem) {
        Assert.assertTrue(itemsSet.contains(PresentUtils.NamespaceTermToIRI("ROOT\\Microsoft", shortItem)));
    }

    /*
    @Test
    public void TestOntologyMicrosoft_ClassesAll() {
        HashSet<String> subjectsSet = selectColumnFromOntology(ontologyMicrosoft, "SELECT ?x WHERE { ?x ?p ?y }", "x");
        System.out.println("subjectsSet=" + subjectsSet);
        assertContainsItemMicrosoft(subjectsSet, "Win32_PhysicalMemoryLocation");
        assertContainsItemMicrosoft(subjectsSet, "Win32_NamedJobObject");
        assertContainsItemMicrosoft(subjectsSet, "Win32_OperatingSystem");
    }
    */

    static void assertContainsItemCIMV2(HashSet<String> itemsSet, String shortItem) {
        Assert.assertTrue(itemsSet.contains(PresentUtils.toCIMV2(shortItem)));
    }

    /** The content of the cached ontology and the fresh one should be the same.
     * This compares the number of triples in both repositories, and this gives a good estimate.
     * TODO: Why does the ontology changes so often ?
     */
    @Test
    public void TestOntologyCIMV2_Cached() {
        WmiOntology ontologyCachedCIMV2 = new WmiOntology("ROOT\\CIMV2", true);
        String sparqlQuery = "select (count(*) as ?count) where { ?s ?p ?o }";
        HashSet<String> countFresh = selectColumnFromOntology(ontologyCIMV2, sparqlQuery, "count");
        HashSet<String> countCache = selectColumnFromOntology(ontologyCachedCIMV2, sparqlQuery, "count");
        Assert.assertEquals(countFresh, countCache);
    }

    /** This instantiates all ontologies of all namespaces.
     *
     */
    @Test
    public void TestCreateAllOntologies() {
        WmiProvider wmiProvider = new WmiProvider();
        Set<String> setNamespaces = wmiProvider.Namespaces();
        for (String oneNamespace : setNamespaces) {
            WmiOntology ontologyNamespace = new WmiOntology(oneNamespace, true);
            String sparqlQuery = "select (count(*) as ?count) where { ?s ?p ?o }";
            HashSet<String> countTriples = selectColumnFromOntology(ontologyNamespace, sparqlQuery, "count");
            Assert.assertEquals(1, countTriples.size());
            String firstString = countTriples.iterator().next();
            long sizeLong = PresentUtils.XmlToLong(firstString);
            Assert.assertTrue(sizeLong > 0);
        }
    }

    /** This selects all triples, and detects that some classes are present. */
    @Test
    public void TestOntologyCIMV2_ClassesAll() {
        HashSet<String> subjectsSet = selectColumnCIMV2("SELECT ?x WHERE { ?x ?p ?y }", "x");
        assertContainsItemCIMV2(subjectsSet, "Win32_Process");
        assertContainsItemCIMV2(subjectsSet, "CIM_DataFile");
        assertContainsItemCIMV2(subjectsSet, "CIM_ProcessExecutable");
        assertContainsItemCIMV2(subjectsSet, "CIM_DirectoryContainsFile");
        assertContainsItemCIMV2(subjectsSet, "Win32_SystemServices");
        assertContainsItemCIMV2(subjectsSet, "Win32_SubDirectory");
    }

    /** This selects all definitions of RDF types and checks the presence of some classes. */
    @Test
    public void TestOntology_ClassesFilter() {
        HashSet<String> typesSet = selectColumnCIMV2("SELECT ?x WHERE { ?x rdf:type rdfs:Class }", "x");
        assertContainsItemCIMV2(typesSet, "Win32_Process");
        assertContainsItemCIMV2(typesSet, "CIM_ProcessExecutable");
        assertContainsItemCIMV2(typesSet, "CIM_DirectoryContainsFile");
    }

    /** This selects all triples, and detects that some properties are present. */
    @Test
    public void TestOntology_PropertiesAll() {
        HashSet<String> subjectsSet = selectColumnCIMV2("SELECT ?x WHERE { ?x ?p ?y }", "x");
        assertContainsItemCIMV2(subjectsSet, "Handle");
        assertContainsItemCIMV2(subjectsSet, "Name");
        assertContainsItemCIMV2(subjectsSet, "Antecedent");
        assertContainsItemCIMV2(subjectsSet, "Dependent");
    }

    /** This selects all definitions of RDF properties and checks the presence of some properties. */
    @Test
    public void TestOntology_PropertiesFilter() {
        HashSet<String> propertiesSet = selectColumnCIMV2(
                "SELECT ?y WHERE { ?y rdf:type rdf:Property }", "y");
        assertContainsItemCIMV2(propertiesSet, "Handle");
        assertContainsItemCIMV2(propertiesSet, "Dependent");
    }

    /** This checks that the domains of some properties are loaded in the ontology.
     *
     */
    @Test
    public void TestOntology_Handle_Domain_All() {
        String queryString = new Formatter().format(
                "SELECT ?y WHERE { ?y rdfs:domain ?z }").toString();
        HashSet<String> domainsSet = selectColumnCIMV2(queryString, "y");
        assertContainsItemCIMV2(domainsSet, "CIM_Process.Handle");
        assertContainsItemCIMV2(domainsSet, "Win32_UserAccount.Name");
        assertContainsItemCIMV2(domainsSet, "Win32_Process.Handle");
    }

    /** This checks the presence of class Wmi32_Process in the domain of Win32_Process.Handle.
     * The node of Win32_Process.Handle is explicitly given.
     */
    @Test
    public void TestOntology_Win32_Process_Handle_Domain_Filter() {
        String queryString = new Formatter().format(
                "SELECT ?x WHERE { <%s> rdfs:domain ?x }", PresentUtils.toCIMV2("Win32_Process.Handle")).toString();
        HashSet<String> domainsSet = selectColumnCIMV2(queryString, "x");
        System.out.println("domainsSet=" + domainsSet.toString());
        assertContainsItemCIMV2(domainsSet, "Win32_Process");
    }

    /** This checks the presence of class string in one of the ranges. */
    @Test
    public void TestOntology_Range_Filter() {
        // Predicates: [
        // http://www.w3.org/2000/01/rdf-schema#label,
        // http://www.w3.org/2000/01/rdf-schema#domain,
        // http://www.w3.org/1999/02/22-rdf-syntax-ns#type,
        // http://www.w3.org/2000/01/rdf-schema#range]
        String queryString = new Formatter().format(
                "SELECT ?x WHERE { <%s> rdfs:range ?x }",
                PresentUtils.toCIMV2("CIM_Process.Handle")).toString( );
        HashSet<String> rangesSet = selectColumnCIMV2(queryString, "x");
        Assert.assertTrue(rangesSet.contains("http://www.w3.org/2001/XMLSchema#string"));
    }

    /** This checks the presence Description for class Win32_Process. */
    @Test
    public void TestOntology_Win32_Process_Description() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_class_description
                    where {
                        cimv2:Win32_Process rdfs:comment ?my_class_description .
                    }
                """;
        HashSet<String> descriptionsSet = selectColumnCIMV2(queryString, "my_class_description");
        Assert.assertEquals(1, descriptionsSet.size());
        String expectedDescription = "\"The Win32_Process class represents a sequence of events on a Win32 system. Any sequence consisting of the interaction of one or more processors or interpreters, some executable code, and a set of inputs, is a descendent (or member) of this class.  Example: A client application running on a Win32 system.\"";
        String firstDescription = descriptionsSet.stream().findFirst().orElse("xyz");
        System.out.println(expectedDescription);
        System.out.println(firstDescription);
        Assert.assertEquals(expectedDescription.substring(0,10), firstDescription.substring(0,10));
    }

    /** This selects the base class of Win32_Process */
    @Test
    public void TestOntology_Win32_Process_BaseClass() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_base_class
                    where {
                        cimv2:Win32_Process rdfs:subClassOf ?my_base_class .
                    }
                """;
        HashSet<String> baseClassesSet = selectColumnCIMV2(queryString, "my_base_class");
        Assert.assertEquals(1, baseClassesSet.size());
        System.out.println(baseClassesSet);
        assertContainsItemCIMV2(baseClassesSet, "CIM_Process");
    }

    /** This select the labels of all CIM classes starting from the top. */
    @Test
    public void TestOntology_DerivedClasses() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_caption
                    where {
                        ?my_derived_class rdfs:subClassOf+ cimv2:CIM_LogicalElement .
                        ?my_derived_class rdfs:label ?my_caption .
                    }
                """;
        HashSet<String> derivedClassNamesSet = selectColumnCIMV2(queryString, "my_caption");
        System.out.println(derivedClassNamesSet);
        // Test the presence of some classes which derive of this one.
        Assert.assertTrue(derivedClassNamesSet.contains("\"Win32_Directory\""));
        Assert.assertTrue(derivedClassNamesSet.contains("\"Win32_BIOS\""));
    }

    /** This checks the presence of Description for property Win32_Process.Handle. */
    @Test
    public void TestOntology_Win32_Process_Handle_Description() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_property_description
                    where {
                        cimv2:Win32_Process.Handle rdfs:comment ?my_property_description .
                        cimv2:Win32_Process.Handle rdfs:domain cimv2:Win32_Process .
                    }
                """;
        HashSet<String> descriptionsSet = selectColumnCIMV2(queryString, "my_property_description");
        System.out.println("descriptionsSet=" + descriptionsSet.toString());
        Assert.assertEquals(1, descriptionsSet.size());
        Assert.assertEquals(
                "\"A string used to identify the process. A process ID is a kind of process handle.\"",
                descriptionsSet.stream().findFirst().orElse("xyz"));
    }

    /** This checks the presence of Description for property Win32_Process.Handle. */
    @Test
    public void TestOntology_Win32_UserAccount_Name_Description() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_property_description
                    where {
                        cimv2:Win32_UserAccount.Name rdfs:comment ?my_property_description .
                        cimv2:Win32_UserAccount.Name rdfs:domain cimv2:Win32_UserAccount .
                    }
                """;
        HashSet<String> descriptionsSet = selectColumnCIMV2(queryString, "my_property_description");
        System.out.println("descriptionsSet=" + descriptionsSet.toString());
        Assert.assertEquals(1, descriptionsSet.size());
        Assert.assertTrue(
                descriptionsSet.stream().findFirst().orElse("xyz").startsWith("\"The Name property"));
    }

    @Test
    public void TestOntology_Win32_ClassInfoAction_AppID() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_class_node
                    where {
                        cimv2:Win32_ClassInfoAction.AppID rdfs:domain ?my_class_node .
                    }
                """;
        HashSet<String> domainsSet = selectColumnCIMV2(queryString, "my_class_node");
        Assert.assertEquals(1, domainsSet.size());
        assertContainsItemCIMV2(domainsSet, "Win32_ClassInfoAction");
    }

    /** All class with the property "AppID". */
    @Test
    public void TestOntology_Classes_AppID() {
        String queryString = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_class_node
                    where {
                        ?my_property_node rdfs:domain ?my_class_node .
                        ?my_property_node rdfs:subPropertyOf cimv2:AppID .
                    }
                """;
        HashSet<String> domainsSet = selectColumnCIMV2(queryString, "my_class_node");
        assertContainsItemCIMV2(domainsSet, "Win32_ClassInfoAction");
        assertContainsItemCIMV2(domainsSet, "Win32_DCOMApplication");
        assertContainsItemCIMV2(domainsSet, "Win32_ClassicCOMClassSetting");
        assertContainsItemCIMV2(domainsSet, "Win32_DCOMApplicationSetting");
    }

    /** Properties used by at least four tables. */
    @Test
    public void TestOntology_Classes_SharedProperty() {
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
        HashSet<String> propertiesSet = selectColumnCIMV2(queryString, "my_property_node");
        System.out.println("propertiesSet=" + propertiesSet.toString());
        assertContainsItemCIMV2(propertiesSet, "CreationClassName");
        assertContainsItemCIMV2(propertiesSet, "Name");
        assertContainsItemCIMV2(propertiesSet, "Caption");
        assertContainsItemCIMV2(propertiesSet, "AppID");
    }

    @Test
    public void TestOntology_Associators_Antecedent() {
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
        HashSet<String> domainsSet = selectColumnCIMV2(queryString, "my_domain");
        System.out.println("domainsSet=" + domainsSet.toString());
        assertContainsItemCIMV2(domainsSet, "CIM_DataFile");
    }

    @Test
    public void TestOntology_Associators_Dependent() {
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
        HashSet<String> domainsSet = selectColumnCIMV2(queryString, "my_domain");
        System.out.println("domainsSet=" + domainsSet.toString());
        assertContainsItemCIMV2(domainsSet, "CIM_Process");
    }

    /** All unique properties which have the same name "Handle". */
    @Test
    public void TestOntology_Handle_Homonyms() {
        String queryString = """
                        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?my_sub_property
                        where {
                            ?my_sub_property rdfs:subPropertyOf cimv2:Handle .
                        }
                    """;
        HashSet<String> subPropertiesSet = selectColumnCIMV2(queryString, "my_sub_property");
        System.out.println("subPropertiesSet=" + subPropertiesSet.toString());
        assertContainsItemCIMV2(subPropertiesSet, "CIM_Process.Handle");
        assertContainsItemCIMV2(subPropertiesSet, "CIM_Thread.Handle");
        assertContainsItemCIMV2(subPropertiesSet, "Win32_Thread.Handle");
        assertContainsItemCIMV2(subPropertiesSet, "Win32_Process.Handle");
    }

    /** All associators referring to a CIM_Process. */
    @Test
    public void TestOntology_Associators_To_CIM_Process() {
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
        HashSet<String> associatorsSet = selectColumnCIMV2(queryString, "my_associator");
        System.out.println("associatorsSet=" + associatorsSet.toString());
        assertContainsItemCIMV2(associatorsSet, "CIM_OSProcess");
        assertContainsItemCIMV2(associatorsSet, "CIM_ProcessThread");
        assertContainsItemCIMV2(associatorsSet, "CIM_ProcessExecutable");

        HashSet<String> descriptionsSet = selectColumnCIMV2(queryString, "my_description");
        for(String oneDescription : descriptionsSet) {
            // For example: "A link between a process and the thread"
            System.out.println("    oneDescription=" + oneDescription);
            Assert.assertTrue(oneDescription.startsWith("\"A link between"));
        }
    }

    /** All associators referring to a Win32_Process. */
    @Test
    public void TestOntology_Associators_To_Win32_Process() {
        String queryString = """
                        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?my_associator
                        where {
                            ?my_subproperty rdfs:range cimv2:Win32_Process .
                            ?my_subproperty rdfs:domain ?my_associator .
                        }
                    """;
        HashSet<String> associatorsSet = selectColumnCIMV2(queryString, "my_associator");
        System.out.println("associatorsSet=" + associatorsSet.toString());
        assertContainsItemCIMV2(associatorsSet, "Win32_SessionProcess");
        assertContainsItemCIMV2(associatorsSet, "Win32_SystemProcesses");
        assertContainsItemCIMV2(associatorsSet, "Win32_NamedJobObjectProcess");
    }

    /** This selects the labels of the base properties of properties pointing to a Win32_Process in associators. */
    @Test
    public void TestOntology_Associators_Labels_To_Win32_Process() {
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
        HashSet<String> labelsSet = selectColumnCIMV2(queryString, "my_label");
        System.out.println("labelsSet=" + labelsSet.toString());
        Assert.assertTrue(labelsSet.contains("\"Dependent\""));
        Assert.assertTrue(labelsSet.contains("\"Member\""));
        Assert.assertTrue(labelsSet.contains("\"PartComponent\""));
    }

    /** Labels of classes linked to a CIM_DataFile with an associator. */
    @Test
    public void TestOntology_Associated_Classes_To_CIM_DataFile() {
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
        HashSet<String> labelsSet = selectColumnCIMV2(queryString, "my_label");
        System.out.println("labelsSet=" + labelsSet.toString());
        Assert.assertTrue(labelsSet.contains("\"CIM_Directory\""));
        Assert.assertTrue(labelsSet.contains("\"Win32_PnPSignedDriver\""));
        Assert.assertTrue(labelsSet.contains("\"Win32_DCOMApplication\""));
        Assert.assertTrue(labelsSet.contains("\"CIM_DataFile\""));
        Assert.assertTrue(labelsSet.contains("\"CIM_Process\""));
        Assert.assertTrue(labelsSet.contains("\"Win32_Printer\""));
        Assert.assertTrue(labelsSet.contains("\"Win32_LogicalProgramGroupItem\""));
    }

    /** Labels of classes linked to a Win32_Process with an associator. */
    @Test
    public void TestOntology_Associated_Classes_To_Win32_Process() {
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
        HashSet<String> labelsSet = selectColumnCIMV2(queryString, "my_label");
        System.out.println("labelsSet=" + labelsSet.toString());
        Assert.assertTrue(labelsSet.contains("\"Win32_LogonSession\""));
        Assert.assertTrue(labelsSet.contains("\"Win32_ComputerSystem\""));
        Assert.assertTrue(labelsSet.contains("\"Win32_NamedJobObject\""));
        Assert.assertTrue(labelsSet.contains("\"Win32_Process\""));
    }



    // ?my_property_node rdfs:domain cimv2:CIM_ProcessExecutable
        // my_property_node
        // propertiesSet=[http://www.primhillcomputers.com/ontology/ROOT/CIMV2#BaseAddress, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Antecedent,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Dependent, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#ModuleInstance,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#ProcessCount, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#GlobalProcessCount]
        //
        // cimv2:Antecedent rdfs:range ?y
        // x
        // [http://www.w3.org/2001/XMLSchema#string]
        //
        // cimv2:Antecedent ?x ?y .
        // x
        // [http://www.w3.org/2000/01/rdf-schema#label, http://www.w3.org/2000/01/rdf-schema#domain, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, http://www.w3.org/2000/01/rdf-schema#range]
        //
        // y
        // propertiesSet=[http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_AssociatedSupplyVoltageSensor, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_SoftwareFeatureServiceImplementation,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_1394ControllerDevice, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_DeviceBus,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_BootServiceAccessBySAP, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_AssociatedBattery,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_PrinterController, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_PrinterShare,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_DiskDriveToDiskPartition, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_SCSIControllerDevice,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_LogicalDiskBasedOnPartition, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_Mount,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_SlotInSlot, "Antecedent", http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_AssociatedProcessorMemory,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_Dependency, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_AllocatedResource,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_DriverForDevice, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_SoftwareFeatureSAPImplementation,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_ResidesOnExtent, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_HostedJobDestination,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_ShadowBy, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_PNPAllocatedResource,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_ServiceSAPDependency, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_AssociatedSupplyCurrentSensor,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_ProcessExecutable, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_PackageCooling,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_SubSession, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_DependentService,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_ElementsLinked, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_AssociatedProcessorMemory,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_ShadowVolumeSupport, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_MemoryArrayLocation,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_ShadowDiffVolumeSupport, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_DfsNodeTarget,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_MemoryWithMedia, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_RealizesAggregatePExtent,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_ShadowFor, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_CardInSlot
        // , http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_LogicalDiskToPartition, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_PnPSignedDriverCIMDataFile
        // , http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_LoadOrderGroupServiceDependencies, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_LogonSessionMappedDisk,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_AllocatedResource, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_MediaPresent,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_SerialInterface, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_LoggedOnUser,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_HostedBootSAP, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_PackageTempSensor,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_LogicalProgramGroupItemDataFile, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_ControlledBy,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_PackageInSlot, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_ConnectionShare,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_HostedAccessPoint, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_SoftwareFeatureParent,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_JobDestinationJobs, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_DeviceAccessedByFile,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_IDEControllerDevice, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_ConnectedTo,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_AssociatedCooling, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_AssociatedAlarm,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_RealizesPExtent, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_CIMLogicalDeviceCIMDataFile,
        // http://www.w3.org/1999/02/22-rdf-syntax-ns#Property, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_HostedService,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_ComputerSystemPackage, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_PackageAlarm,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_ShadowOn, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_PSExtentBasedOnPExtent,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_ApplicationCommandLine, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_ProtocolBinding,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_HostedBootService, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_DeviceServiceImplementation,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_OperatingSystemQFE, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_SAPSAPDependency,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_DiskDrivePhysicalMedia, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_DeviceConnection,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_ServiceServiceDependency, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_BIOSLoadedInNV,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_POTSModemToSerialPort, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_MemoryDeviceLocation,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_ServiceAccessBySAP, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_USBControllerDevice,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_SystemDriverPNPEntity, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_DeviceSoftware,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_RunningOS, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_BasedOn,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_SessionConnection, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_ClusterServiceAccessBySAP,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_SCSIInterface, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_LogicalProgramGroupDirectory,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_SessionResource, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_Realizes,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_SessionProcess, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_LogicalDiskBasedOnVolumeSet,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_RealizesDiskPartition, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_AssociatedMemory,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_USBControllerHasHub, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_Docked,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_PrinterDriverDll, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_BootOSFromFS,
        // http://www.w3.org/2001/XMLSchema#string, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_ControllerHasHub,
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_AssociatedSensor, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_DeviceSAPImplementation]


    /* TODO:
    Shared columns.
    Associators.
    */
}
