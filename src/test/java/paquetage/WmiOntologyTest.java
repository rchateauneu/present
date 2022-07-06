package paquetage;

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Formatter;
import java.util.HashSet;

public class WmiOntologyTest {
    static WmiOntology ontology = new WmiOntology();

    /*
    @Before
    public void setUp() throws Exception {
        ontology = new WmiOntology();
    }

    @After
    public void tearDown() throws Exception {
        ontology = null;
    }
    */

    static String toSurvol(String term) {
        return WmiOntology.survol_url_prefix + term;
    }

    /** This executes a Sparql query in the repository containing the WMI ontology. */
    HashSet<String> selectColumn(String sparqlQuery, String columnName){
        HashSet<String> variablesSet = new HashSet<String>();
        try (RepositoryConnection conn = ontology.repository.getConnection()) {
            TupleQuery tupleQuery = conn.prepareTupleQuery(sparqlQuery);
            try (TupleQueryResult result = tupleQuery.evaluate()) {
                while (result.hasNext()) {  // iterate over the result
                    BindingSet bindingSet = result.next();
                    Value valueOfX = bindingSet.getValue(columnName);
                    variablesSet.add(valueOfX.toString());
                }
            }
            return variablesSet;
        }
    }

    static void assertContainsSurvolItem(HashSet<String> itemsSet, String shortItem) {
        Assert.assertTrue(itemsSet.contains(toSurvol(shortItem)));
    }


    /** This selects all triples, and detects that some classes are present. */
    @Test
    public void TestOntology_ClassesAll() {
        HashSet<String> subjectsSet = selectColumn("SELECT ?x WHERE { ?x ?p ?y }", "x");
        assertContainsSurvolItem(subjectsSet, "Win32_Process");
        assertContainsSurvolItem(subjectsSet, "CIM_DataFile");
        assertContainsSurvolItem(subjectsSet, "CIM_ProcessExecutable");
        assertContainsSurvolItem(subjectsSet, "CIM_DirectoryContainsFile");
        assertContainsSurvolItem(subjectsSet, "Win32_SystemServices");
        assertContainsSurvolItem(subjectsSet, "Win32_SubDirectory");
    }

    /** This selects all definitions of RDF types and checks the presence of some classes. */
    @Test
    public void TestOntology_ClassesFilter() {
        HashSet<String> typesSet = selectColumn("SELECT ?x WHERE { ?x rdf:type rdfs:Class }", "x");
        assertContainsSurvolItem(typesSet, "Win32_Process");
        assertContainsSurvolItem(typesSet, "CIM_ProcessExecutable");
        assertContainsSurvolItem(typesSet, "CIM_DirectoryContainsFile");
    }

    /** This selects all triples, and detects that some properties are present. */
    @Test
    public void TestOntology_PropertiesAll() {
        HashSet<String> subjectsSet = selectColumn("SELECT ?x WHERE { ?x ?p ?y }", "x");
        assertContainsSurvolItem(subjectsSet, "Handle");
        assertContainsSurvolItem(subjectsSet, "Name");
        assertContainsSurvolItem(subjectsSet, "Antecedent");
        assertContainsSurvolItem(subjectsSet, "Dependent");
    }

    /** This selects all definitions of RDF properties and checks the presence of some properties. */
    @Test
    public void TestOntology_PropertiesFilter() {
        HashSet<String> propertiesSet = selectColumn(
                "SELECT ?y WHERE { ?y rdf:type rdf:Property }", "y");
        assertContainsSurvolItem(propertiesSet, "Handle");
        assertContainsSurvolItem(propertiesSet, "Dependent");
    }

    @Test
    public void TestOntology_Win32_Process_Handle_Domain_All() {
        String querystring = new Formatter().format(
                "SELECT ?y WHERE { ?y rdfs:domain ?z }").toString( );
        HashSet<String> domainsSet = selectColumn(querystring, "y");
        assertContainsSurvolItem(domainsSet, "CIM_Process.Handle");
    }

    /** This checks the presence of class Wmi32_Process in one of the domains. */
    @Test
    public void TestOntology_Win32_Process_Handle_Domain_Filter() {
        String querystring = new Formatter().format(
                "SELECT ?x WHERE { <%s> rdfs:domain ?x }", toSurvol("Win32_Process.Handle")).toString( );
        HashSet<String> domainsSet = selectColumn(querystring, "x");
        System.out.println("domainsSet=" + domainsSet.toString());
        assertContainsSurvolItem(domainsSet, "Win32_Process");
    }

    /** This checks the presence of class string in one of the ranges. */
    @Test
    public void TestOntology_Range_Filter() {
        // Predicates: [
        // http://www.w3.org/2000/01/rdf-schema#label,
        // http://www.w3.org/2000/01/rdf-schema#domain,
        // http://www.w3.org/1999/02/22-rdf-syntax-ns#type,
        // http://www.w3.org/2000/01/rdf-schema#range]
        String querystring = new Formatter().format(
                "SELECT ?x WHERE { <%s> rdfs:range ?x }",
                toSurvol("CIM_Process.Handle")).toString( );
        HashSet<String> rangesSet = selectColumn(querystring, "x");
        Assert.assertTrue(rangesSet.contains("http://www.w3.org/2001/XMLSchema#string"));
    }

    /** This checks the presence Description for class Win32_Process. */
    @Test
    public void TestOntology_Win32_Process_Description() {
        String querystring = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_class_description
                    where {
                        cim:Win32_Process rdfs:comment ?my_class_description .
                    }
                """;
        HashSet<String> descriptionsSet = selectColumn(querystring, "my_class_description");
        Assert.assertEquals(1, descriptionsSet.size());
        String expectedDescription = "\"The Win32_Process class represents a sequence of events on a Win32 system. Any sequence consisting of the interaction of one or more processors or interpreters, some executable code, and a set of inputs, is a descendent (or member) of this class.  Example: A client application running on a Win32 system.\"";
        String firstDescription = descriptionsSet.stream().findFirst().orElse("xyz");
        System.out.println(expectedDescription);
        System.out.println(firstDescription);
        Assert.assertEquals(expectedDescription.substring(0,10), firstDescription.substring(0,10));
    }

    /** This checks the presence Description for property Handle. */
    @Test
    public void TestOntology_Handle_Description() {
        String querystring = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_property_description
                    where {
                        cim:Win32_Process.Handle rdfs:comment ?my_property_description .
                        cim:Win32_Process.Handle rdfs:domain cim:Win32_Process .
                    }
                """;
        HashSet<String> descriptionsSet = selectColumn(querystring, "my_property_description");
        System.out.println("descriptionsSet=" + descriptionsSet.toString());
        Assert.assertEquals(1, descriptionsSet.size());
        Assert.assertEquals(
                "\"A string used to identify the process. A process ID is a kind of process handle.\"",
                descriptionsSet.stream().findFirst().orElse("xyz"));
    }

    @Test
    public void TestOntology_Win32_ClassInfoAction_AppID() {
        String querystring = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_class_node
                    where {
                        cim:Win32_ClassInfoAction.AppID rdfs:domain ?my_class_node .
                    }
                """;
        HashSet<String> domainsSet = selectColumn(querystring, "my_class_node");
        Assert.assertEquals(1, domainsSet.size());
        assertContainsSurvolItem(domainsSet, "Win32_ClassInfoAction");
    }

    /** All class with the property "AppID". */
    @Test
    public void TestOntology_Classes_AppID() {
        String querystring = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_class_node
                    where {
                        ?my_property_node rdfs:domain ?my_class_node .
                        ?my_property_node rdfs:subPropertyOf cim:AppID .
                    }
                """;
        HashSet<String> domainsSet = selectColumn(querystring, "my_class_node");
        assertContainsSurvolItem(domainsSet, "Win32_ClassInfoAction");
        assertContainsSurvolItem(domainsSet, "Win32_DCOMApplication");
        assertContainsSurvolItem(domainsSet, "Win32_ClassicCOMClassSetting");
        assertContainsSurvolItem(domainsSet, "Win32_DCOMApplicationSetting");
    }

    /** Properties used by at least four tables. */
    @Test
    public void TestOntology_Classes_SharedProperty() {
        String querystring = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_property_node (COUNT(?my_property_node) AS ?total)
                    where {
                        ?my_subproperty_node rdfs:subPropertyOf ?my_property_node .
                        ?my_subproperty_node rdfs:domain ?my_class_node .
                    }
                    group by ?my_property_node
                    having (?total > 3)
                """;
        HashSet<String> propertiesSet = selectColumn(querystring, "my_property_node");
        System.out.println("propertiesSet=" + propertiesSet.toString());
        assertContainsSurvolItem(propertiesSet, "CreationClassName");
        assertContainsSurvolItem(propertiesSet, "Name");
        assertContainsSurvolItem(propertiesSet, "Caption");
        assertContainsSurvolItem(propertiesSet, "AppID");
    }

    @Test
    public void TestOntology_Associators_Antecedent() {
        String querystring = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_domain
                    where {
                        cim:CIM_ProcessExecutable.Antecedent rdfs:range ?my_domain .
                        cim:CIM_ProcessExecutable.Antecedent rdfs:domain cim:CIM_ProcessExecutable .
                    }
                """;
        // Beware, there are several cim:Antecedent properties.
        HashSet<String> domainsSet = selectColumn(querystring, "my_domain");
        System.out.println("domainsSet=" + domainsSet.toString());
        assertContainsSurvolItem(domainsSet, "CIM_DataFile");
    }

    @Test
    public void TestOntology_Associators_Dependent() {
        String querystring = """
                prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                select ?my_domain
                where {
                    cim:CIM_ProcessExecutable.Dependent rdfs:range ?my_domain .
                    cim:CIM_ProcessExecutable.Dependent rdfs:domain cim:CIM_ProcessExecutable .
                }
            """;
        // Beware, there are several cim:Dependent properties.
        HashSet<String> domainsSet = selectColumn(querystring, "my_domain");
        System.out.println("domainsSet=" + domainsSet.toString());
        assertContainsSurvolItem(domainsSet, "CIM_Process");
    }

    /** All unique properties which have the same name "Handle". */
    @Test
    public void TestOntology_Handle_Homonyms() {
        String querystring = """
                        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?my_sub_property
                        where {
                            ?my_sub_property rdfs:subPropertyOf cim:Handle .
                        }
                    """;
        HashSet<String> subPropertiesSet = selectColumn(querystring, "my_sub_property");
        System.out.println("subPropertiesSet=" + subPropertiesSet.toString());
        assertContainsSurvolItem(subPropertiesSet, "CIM_Process.Handle");
        assertContainsSurvolItem(subPropertiesSet, "CIM_Thread.Handle");
        assertContainsSurvolItem(subPropertiesSet, "Win32_Process.Handle");
        assertContainsSurvolItem(subPropertiesSet, "Win32_Thread.Handle");
    }

    /** All associators referring to a CIM_Process. */
    @Test
    public void TestOntology_Associators_To_CIM_Process() {
        String querystring = """
                        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?my_associator ?my_description
                        where {
                            ?my_subproperty rdfs:range cim:CIM_Process .
                            ?my_subproperty rdfs:domain ?my_associator .
                            ?my_associator rdfs:comment ?my_description .
                        }
                    """;
        HashSet<String> associatorsSet = selectColumn(querystring, "my_associator");
        System.out.println("associatorsSet=" + associatorsSet.toString());
        assertContainsSurvolItem(associatorsSet, "CIM_OSProcess");
        assertContainsSurvolItem(associatorsSet, "CIM_ProcessThread");
        assertContainsSurvolItem(associatorsSet, "CIM_ProcessExecutable");

        HashSet<String> descriptionsSet = selectColumn(querystring, "my_description");
        for(String oneDescription : descriptionsSet) {
            // For example: "A link between a process and the thread"
            System.out.println("    oneDescription=" + oneDescription);
            Assert.assertTrue(oneDescription.startsWith("\"A link between"));
        }
    }

    /** This selects the labels of the base properties of properties pointing to a Win32_Process in associators. */
    @Test
    public void TestOntology_Associators_To_Win32_Process() {
        String querystring = """
                        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?my_label
                        where {
                            ?my_property rdfs:label ?my_label .
                            ?my_subproperty rdfs:subPropertyOf ?my_property .
                            ?my_subproperty rdfs:range cim:Win32_Process .
                            ?my_subproperty rdfs:domain ?my_associator .
                            ?my_associator rdfs:comment ?my_description .
                        }
                    """;
        HashSet<String> labelsSet = selectColumn(querystring, "my_label");
        System.out.println("labelsSet=" + labelsSet.toString());
        Assert.assertTrue(labelsSet.contains("\"Dependent\""));
        Assert.assertTrue(labelsSet.contains("\"Member\""));
        Assert.assertTrue(labelsSet.contains("\"PartComponent\""));
    }

    /** Labels of classes linked to a CIM_DataFile with an associator. */
    @Test
    public void TestOntology_Associated_Classes_To_CIM_DataFile() {
        String querystring = """
                        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?my_label
                        where {
                            ?my_subproperty1 rdfs:range cim:CIM_DataFile .
                            ?my_subproperty1 rdfs:domain ?my_associator .
                            ?my_subproperty2 rdfs:range ?my_class .
                            ?my_subproperty2 rdfs:domain ?my_associator .
                            ?my_class rdfs:label ?my_label .
                        }
                    """;
        HashSet<String> labelsSet = selectColumn(querystring, "my_label");
        System.out.println("labelsSet=" + labelsSet.toString());
        Assert.assertTrue(labelsSet.contains("\"CIM_Directory\""));
        Assert.assertTrue(labelsSet.contains("\"Win32_PnPSignedDriver\""));
        Assert.assertTrue(labelsSet.contains("\"Win32_DCOMApplication\""));
        Assert.assertTrue(labelsSet.contains("\"CIM_DataFile\""));
        Assert.assertTrue(labelsSet.contains("\"CIM_Process\""));
        Assert.assertTrue(labelsSet.contains("\"Win32_Printer\""));
        Assert.assertTrue(labelsSet.contains("\"Win32_LogicalProgramGroupItem\""));
    }



    // ?my_property_node rdfs:domain cim:CIM_ProcessExecutable
        // my_property_node
        // propertiesSet=[http://www.primhillcomputers.com/ontology/survol#BaseAddress, http://www.primhillcomputers.com/ontology/survol#Antecedent,
        // http://www.primhillcomputers.com/ontology/survol#Dependent, http://www.primhillcomputers.com/ontology/survol#ModuleInstance,
        // http://www.primhillcomputers.com/ontology/survol#ProcessCount, http://www.primhillcomputers.com/ontology/survol#GlobalProcessCount]
        //
        // cim:Antecedent rdfs:range ?y
        // x
        // [http://www.w3.org/2001/XMLSchema#string]
        //
        // cim:Antecedent ?x ?y .
        // x
        // [http://www.w3.org/2000/01/rdf-schema#label, http://www.w3.org/2000/01/rdf-schema#domain, http://www.w3.org/1999/02/22-rdf-syntax-ns#type, http://www.w3.org/2000/01/rdf-schema#range]
        //
        // y
        // propertiesSet=[http://www.primhillcomputers.com/ontology/survol#CIM_AssociatedSupplyVoltageSensor, http://www.primhillcomputers.com/ontology/survol#CIM_SoftwareFeatureServiceImplementation,
        // http://www.primhillcomputers.com/ontology/survol#Win32_1394ControllerDevice, http://www.primhillcomputers.com/ontology/survol#Win32_DeviceBus,
        // http://www.primhillcomputers.com/ontology/survol#CIM_BootServiceAccessBySAP, http://www.primhillcomputers.com/ontology/survol#CIM_AssociatedBattery,
        // http://www.primhillcomputers.com/ontology/survol#Win32_PrinterController, http://www.primhillcomputers.com/ontology/survol#Win32_PrinterShare,
        // http://www.primhillcomputers.com/ontology/survol#Win32_DiskDriveToDiskPartition, http://www.primhillcomputers.com/ontology/survol#Win32_SCSIControllerDevice,
        // http://www.primhillcomputers.com/ontology/survol#CIM_LogicalDiskBasedOnPartition, http://www.primhillcomputers.com/ontology/survol#CIM_Mount,
        // http://www.primhillcomputers.com/ontology/survol#CIM_SlotInSlot, "Antecedent", http://www.primhillcomputers.com/ontology/survol#Win32_AssociatedProcessorMemory,
        // http://www.primhillcomputers.com/ontology/survol#CIM_Dependency, http://www.primhillcomputers.com/ontology/survol#CIM_AllocatedResource,
        // http://www.primhillcomputers.com/ontology/survol#Win32_DriverForDevice, http://www.primhillcomputers.com/ontology/survol#CIM_SoftwareFeatureSAPImplementation,
        // http://www.primhillcomputers.com/ontology/survol#CIM_ResidesOnExtent, http://www.primhillcomputers.com/ontology/survol#CIM_HostedJobDestination,
        // http://www.primhillcomputers.com/ontology/survol#Win32_ShadowBy, http://www.primhillcomputers.com/ontology/survol#Win32_PNPAllocatedResource,
        // http://www.primhillcomputers.com/ontology/survol#CIM_ServiceSAPDependency, http://www.primhillcomputers.com/ontology/survol#CIM_AssociatedSupplyCurrentSensor,
        // http://www.primhillcomputers.com/ontology/survol#CIM_ProcessExecutable, http://www.primhillcomputers.com/ontology/survol#CIM_PackageCooling,
        // http://www.primhillcomputers.com/ontology/survol#Win32_SubSession, http://www.primhillcomputers.com/ontology/survol#Win32_DependentService,
        // http://www.primhillcomputers.com/ontology/survol#CIM_ElementsLinked, http://www.primhillcomputers.com/ontology/survol#CIM_AssociatedProcessorMemory,
        // http://www.primhillcomputers.com/ontology/survol#Win32_ShadowVolumeSupport, http://www.primhillcomputers.com/ontology/survol#Win32_MemoryArrayLocation,
        // http://www.primhillcomputers.com/ontology/survol#Win32_ShadowDiffVolumeSupport, http://www.primhillcomputers.com/ontology/survol#Win32_DfsNodeTarget,
        // http://www.primhillcomputers.com/ontology/survol#CIM_MemoryWithMedia, http://www.primhillcomputers.com/ontology/survol#CIM_RealizesAggregatePExtent,
        // http://www.primhillcomputers.com/ontology/survol#Win32_ShadowFor, http://www.primhillcomputers.com/ontology/survol#CIM_CardInSlot
        // , http://www.primhillcomputers.com/ontology/survol#Win32_LogicalDiskToPartition, http://www.primhillcomputers.com/ontology/survol#Win32_PnPSignedDriverCIMDataFile
        // , http://www.primhillcomputers.com/ontology/survol#Win32_LoadOrderGroupServiceDependencies, http://www.primhillcomputers.com/ontology/survol#Win32_LogonSessionMappedDisk,
        // http://www.primhillcomputers.com/ontology/survol#Win32_AllocatedResource, http://www.primhillcomputers.com/ontology/survol#CIM_MediaPresent,
        // http://www.primhillcomputers.com/ontology/survol#CIM_SerialInterface, http://www.primhillcomputers.com/ontology/survol#Win32_LoggedOnUser,
        // http://www.primhillcomputers.com/ontology/survol#CIM_HostedBootSAP, http://www.primhillcomputers.com/ontology/survol#CIM_PackageTempSensor,
        // http://www.primhillcomputers.com/ontology/survol#Win32_LogicalProgramGroupItemDataFile, http://www.primhillcomputers.com/ontology/survol#CIM_ControlledBy,
        // http://www.primhillcomputers.com/ontology/survol#CIM_PackageInSlot, http://www.primhillcomputers.com/ontology/survol#Win32_ConnectionShare,
        // http://www.primhillcomputers.com/ontology/survol#CIM_HostedAccessPoint, http://www.primhillcomputers.com/ontology/survol#Win32_SoftwareFeatureParent,
        // http://www.primhillcomputers.com/ontology/survol#CIM_JobDestinationJobs, http://www.primhillcomputers.com/ontology/survol#CIM_DeviceAccessedByFile,
        // http://www.primhillcomputers.com/ontology/survol#Win32_IDEControllerDevice, http://www.primhillcomputers.com/ontology/survol#CIM_ConnectedTo,
        // http://www.primhillcomputers.com/ontology/survol#CIM_AssociatedCooling, http://www.primhillcomputers.com/ontology/survol#CIM_AssociatedAlarm,
        // http://www.primhillcomputers.com/ontology/survol#CIM_RealizesPExtent, http://www.primhillcomputers.com/ontology/survol#Win32_CIMLogicalDeviceCIMDataFile,
        // http://www.w3.org/1999/02/22-rdf-syntax-ns#Property, http://www.primhillcomputers.com/ontology/survol#CIM_HostedService,
        // http://www.primhillcomputers.com/ontology/survol#CIM_ComputerSystemPackage, http://www.primhillcomputers.com/ontology/survol#CIM_PackageAlarm,
        // http://www.primhillcomputers.com/ontology/survol#Win32_ShadowOn, http://www.primhillcomputers.com/ontology/survol#CIM_PSExtentBasedOnPExtent,
        // http://www.primhillcomputers.com/ontology/survol#Win32_ApplicationCommandLine, http://www.primhillcomputers.com/ontology/survol#Win32_ProtocolBinding,
        // http://www.primhillcomputers.com/ontology/survol#CIM_HostedBootService, http://www.primhillcomputers.com/ontology/survol#CIM_DeviceServiceImplementation,
        // http://www.primhillcomputers.com/ontology/survol#Win32_OperatingSystemQFE, http://www.primhillcomputers.com/ontology/survol#CIM_SAPSAPDependency,
        // http://www.primhillcomputers.com/ontology/survol#Win32_DiskDrivePhysicalMedia, http://www.primhillcomputers.com/ontology/survol#CIM_DeviceConnection,
        // http://www.primhillcomputers.com/ontology/survol#CIM_ServiceServiceDependency, http://www.primhillcomputers.com/ontology/survol#CIM_BIOSLoadedInNV,
        // http://www.primhillcomputers.com/ontology/survol#Win32_POTSModemToSerialPort, http://www.primhillcomputers.com/ontology/survol#Win32_MemoryDeviceLocation,
        // http://www.primhillcomputers.com/ontology/survol#CIM_ServiceAccessBySAP, http://www.primhillcomputers.com/ontology/survol#Win32_USBControllerDevice,
        // http://www.primhillcomputers.com/ontology/survol#Win32_SystemDriverPNPEntity, http://www.primhillcomputers.com/ontology/survol#CIM_DeviceSoftware,
        // http://www.primhillcomputers.com/ontology/survol#CIM_RunningOS, http://www.primhillcomputers.com/ontology/survol#CIM_BasedOn,
        // http://www.primhillcomputers.com/ontology/survol#Win32_SessionConnection, http://www.primhillcomputers.com/ontology/survol#CIM_ClusterServiceAccessBySAP,
        // http://www.primhillcomputers.com/ontology/survol#CIM_SCSIInterface, http://www.primhillcomputers.com/ontology/survol#Win32_LogicalProgramGroupDirectory,
        // http://www.primhillcomputers.com/ontology/survol#Win32_SessionResource, http://www.primhillcomputers.com/ontology/survol#CIM_Realizes,
        // http://www.primhillcomputers.com/ontology/survol#Win32_SessionProcess, http://www.primhillcomputers.com/ontology/survol#CIM_LogicalDiskBasedOnVolumeSet,
        // http://www.primhillcomputers.com/ontology/survol#CIM_RealizesDiskPartition, http://www.primhillcomputers.com/ontology/survol#CIM_AssociatedMemory,
        // http://www.primhillcomputers.com/ontology/survol#CIM_USBControllerHasHub, http://www.primhillcomputers.com/ontology/survol#CIM_Docked,
        // http://www.primhillcomputers.com/ontology/survol#Win32_PrinterDriverDll, http://www.primhillcomputers.com/ontology/survol#CIM_BootOSFromFS,
        // http://www.w3.org/2001/XMLSchema#string, http://www.primhillcomputers.com/ontology/survol#Win32_ControllerHasHub,
        // http://www.primhillcomputers.com/ontology/survol#CIM_AssociatedSensor, http://www.primhillcomputers.com/ontology/survol#CIM_DeviceSAPImplementation]


    /* TODO:
    Shared columns.
    Associators.
    */
}
