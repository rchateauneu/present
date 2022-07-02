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
    static WmiOntology ontology = null;

    @Before
    public void setUp() throws Exception {
        ontology = new WmiOntology();
    }

    @After
    public void tearDown() throws Exception {
        ontology = null;
    }

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
        HashSet<String> propertiesSet = selectColumn("SELECT ?y WHERE { ?y rdf:type rdf:Property }", "y");
        assertContainsSurvolItem(propertiesSet, "Handle");
        assertContainsSurvolItem(propertiesSet, "Dependent");
    }

    @Test
    public void TestOntology_Win32_Process_Handle_Domain_All() {
        String querystring = new Formatter().format(
                "SELECT ?y WHERE { ?y rdfs:domain ?z }").toString( );
        HashSet<String> domainsSet = selectColumn(querystring, "y");
        assertContainsSurvolItem(domainsSet, "Handle");
    }

    @Test
    public void TestOntology_Win32_Process_Handle_Domain_Filter() {
        String querystring = new Formatter().format(
                "SELECT ?x WHERE { <%s> rdfs:domain ?x }", toSurvol("Handle")).toString( );
        HashSet<String> domainsSet = selectColumn(querystring, "x");
        assertContainsSurvolItem(domainsSet, "Win32_Process");
    }

    @Test
    public void TestOntology_Win32_Process_Handle_Range_Filter() {
        // Predicates: [
        // http://www.w3.org/2000/01/rdf-schema#label,
        // http://www.w3.org/2000/01/rdf-schema#domain,
        // http://www.w3.org/1999/02/22-rdf-syntax-ns#type,
        // http://www.w3.org/2000/01/rdf-schema#range]
        String querystring = new Formatter().format(
                "SELECT ?x WHERE { <%s> rdfs:range ?x }", WmiOntology.survol_url_prefix + "Handle").toString( );
        HashSet<String> rangesSet = selectColumn(querystring, "x");
        Assert.assertTrue(rangesSet.contains("http://www.w3.org/2001/XMLSchema#string"));
    }

    /** All class with the property "AppID". */
    @Test
    public void TestOntology_Classes_AppID() {
        String querystring = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_class_node
                    where {
                        cim:AppID rdfs:domain ?my_class_node .
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
                        ?my_property_node rdfs:domain ?my_class_node .
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

    // @Test
    public void TestOntology_Associators() {
        String querystring = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?y
                    where {
                        cim:Antecedent rdfs:range ?y .
                    }

                """;

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
        HashSet<String> propertiesSet = selectColumn(querystring, "y");
        System.out.println("propertiesSet=" + propertiesSet.toString());
        assertContainsSurvolItem(propertiesSet, "CreationClassName");
    }


    /* TODO:
    Shared columns.
    Associators.
    */
}
