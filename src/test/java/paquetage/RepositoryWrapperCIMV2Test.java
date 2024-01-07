package paquetage;

import org.apache.commons.text.CaseUtils;
import org.junit.*;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** This tests Sparql selection from a repository containing the ontology plus the result of a WQL selection.
 * The class does not extend TestCase because it is based on JUnit 4.
 * This is only for the namespace CIMV2.
 * */
public class RepositoryWrapperCIMV2Test {
    static long currentPid = ProcessHandle.current().pid();
    static String currentPidStr = String.valueOf(currentPid);
    String currentProcessUri = null;

    static String currJavaBin = PresentUtils.CurrentJavaBinary();
    static String currJavaDir = new File(currJavaBin).getParent();
    private RepositoryWrapper repositoryWrapper = null;

    @Before
    public void setUp() throws Exception {
        repositoryWrapper = new RepositoryWrapper("ROOT\\CIMV2");
        currentProcessUri = WmiOntology.CreateUriVarArgs("Win32_Process", "Handle", currentPidStr);
    }

    //@Override
    @After
    public void tearDown() throws Exception {
        repositoryWrapper = null;
    }

    /** This tests that at least some triples are there, coming from the ontology. */
    @Test
    public void testSelect_AnyTriples() throws Exception {
        String sparqlQuery = "SELECT ?x WHERE { ?x ?y ?z } limit 10";
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(10, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("x"), singleRow.KeySet());
    }

    /* Default Yasgui query. */
    @Test
    public void testSelect_Yasgui() throws Exception {
        String sparqlQuery = """
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT * WHERE {
                ?sub ?pred ?obj .
            } LIMIT 10
        """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(10, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("sub", "pred", "obj"), singleRow.KeySet());
    }

    /** This tests the presence of a table from the ontology. */
    @Test
    public void testSelect_Win32_ProcessFromOntology() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?label
                    where {
                        cimv2:Win32_Process.Handle rdfs:label ?label .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("label"), singleRow.KeySet());
    }

    /** Checks the list of classes in the ontology. */
    @Test
    public void testSelect_ClassesFromOntology() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?class_label
                    where {
                        ?class rdf:type rdfs:Class .
                        ?class rdfs:label ?class_label .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Set<String> setLabels = listRows.StringValuesSet("class_label");
        System.out.println("setLabels="+setLabels);
        // This checks that '"Win32_Process"@en' is present.
        Assert.assertTrue(setLabels.contains(PresentUtils.InternationalizeUnquoted("Win32_Process")));
        Assert.assertTrue(setLabels.contains(PresentUtils.InternationalizeUnquoted("Win32_DependentService")));
    }

    /** This selects the caption of the current process and does not use the ontology.
     * It also checks the URI of the process. */
    @Test
    public void testSelect_Win32_Process_WithClass_WithoutOntology() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?caption ?process
                    where {
                        ?process rdf:type cimv2:Win32_Process .
                        ?process cimv2:Handle "%s" .
                        ?process cimv2:Win32_Process.Caption ?caption .
                    }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("caption", "process"), singleRow.KeySet());

        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Directory.Name%3D%22C%3A%5C%5CWindows%22
        String uriProcess = WmiOntology.CreateUriVarArgs("Win32_Process", "Handle", currentPidStr);
        System.out.println("uriProcess=" + uriProcess);

        Set<String> setLabels = listRows.NodeValuesSet("process");
        System.out.println("setLabels=" + setLabels);

        Assert.assertTrue(setLabels.contains(uriProcess));
    }

    /** This selects the caption of the current process and the label of its class. */
    @Test
    public void testSelect_Win32_Process_WithClass_WithOntology() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?label ?caption ?process
                    where {
                        ?process rdf:type cimv2:Win32_Process .
                        ?process cimv2:Handle "%s" .
                        ?process cimv2:Win32_Process.Caption ?caption .
                        cimv2:Win32_Process.Handle rdfs:label ?label .
                    }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("caption", "label", "process"), singleRow.KeySet());

        String actualPath = singleRow.GetAsUri("process");
        //Assert.assertTrue(PresentUtils.CheckValidWbemPath(actualPath));
    }

    /** This selects all processes which runs the same executable as the current process.
     * It does a subquery.
     */
    @Test
    public void testSelect_Win32_Process_SameExecutable_SubQuery() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?handle ?executablepath
                    where {
                        ?process2 cimv2:Win32_Process.Handle ?handle .
                        ?process2 cimv2:Win32_Process.ExecutablePath ?executablepath .
                        {
                            select ?executablepath
                            where {
                                ?process cimv2:Handle "%s" .
                                ?process cimv2:Win32_Process.ExecutablePath ?executablepath .
                            }
                        }
                    }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        // The current process must be here.
        Set<String> setHandles = listRows.StringValuesSet("handle");
        System.out.println("currentPidStr=" + currentPidStr);
        System.out.println("setHandles=" + setHandles);
        Assert.assertTrue(setHandles.contains(currentPidStr));

        // Executables are all identical.
        Set<String> setExecutables = listRows.StringValuesSet("executablepath");
        Assert.assertEquals(1, setExecutables.size());

        // ... and have the correct value.
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("handle", "executablepath"), singleRow.KeySet());
        System.out.println("Exec=" + singleRow.GetAsLiteral("executablepath"));
        String expectedBin = "\"" + PresentUtils.CurrentJavaBinary() + "\"";
        Assert.assertEquals(expectedBin, singleRow.GetAsLiteral("executablepath"));
    }

    /** Also select the attribute ProcessId which is an integer.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Process_WithoutClass_WithoutOntology() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process ?pid
                    where {
                        ?process cimv2:Win32_Process.Handle "%s" .
                        ?process cimv2:Win32_Process.Caption ?caption .
                        ?process cimv2:Win32_Process.ProcessId ?pid .
                    }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("process", "pid"), singleRow.KeySet());
        // "18936"^^<http://www.w3.org/2001/XMLSchema#long>
        Assert.assertEquals(PresentUtils.LongToXml(currentPid), singleRow.GetAsLiteral("pid"));

        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Directory.Name%3D%22C%3A%5C%5CWindows%22
        // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Directory.Name%3D%22C%3A%5C%5CWindows%22
        String expectedProcessUri = WmiOntology.CreateUriVarArgs("Win32_Process", "Handle", currentPidStr);
        System.out.println("expectedProcessUri=" + expectedProcessUri);
        System.out.println("singleRow=" + singleRow);
        String actualProcessUri = singleRow.GetAsUri("process");
        Assert.assertEquals(expectedProcessUri, actualProcessUri);
        //Assert.assertTrue(PresentUtils.CheckValidWbemPath(actualProcessUri));
    }

    /** Selects all properties of a process.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Process_AllProperties() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?readoperationcount ?caption ?executablepath ?usermodetime ?peakworkingsetsize ?othertransfercount ?pagefileusage ?writetransfercount ?pagefaults ?readtransfercount ?maximumworkingsetsize ?quotapeakpagedpoolusage ?handlecount ?osname ?writeoperationcount ?sessionid ?otheroperationcount ?csname ?windowsversion ?description ?quotanonpagedpoolusage ?threadcount ?priority ?parentprocessid ?installdate ?commandline ?virtualsize ?quotapagedpoolusage ?creationdate ?terminationdate ?name ?peakvirtualsize ?privatepagecount ?executionstate ?peakpagefileusage ?cscreationclassname ?minimumworkingsetsize ?quotapeaknonpagedpoolusage ?status ?creationclassname ?oscreationclassname ?processid ?kernelmodetime ?workingsetsize
                    where {
                        ?process cimv2:Win32_Process.Handle "%s" .
                        ?process cimv2:Win32_Process.ReadOperationCount ?readoperationcount .
                        ?process cimv2:Win32_Process.Caption ?caption .
                        ?process cimv2:Win32_Process.ExecutablePath ?executablepath .
                        ?process cimv2:Win32_Process.UserModeTime ?usermodetime .
                        ?process cimv2:Win32_Process.PeakWorkingSetSize ?peakworkingsetsize .
                        ?process cimv2:Win32_Process.OtherTransferCount ?othertransfercount .
                        ?process cimv2:Win32_Process.PageFileUsage ?pagefileusage .
                        ?process cimv2:Win32_Process.WriteTransferCount ?writetransfercount .
                        ?process cimv2:Win32_Process.PageFaults ?pagefaults .
                        ?process cimv2:Win32_Process.ReadTransferCount ?readtransfercount .
                        ?process cimv2:Win32_Process.MaximumWorkingSetSize ?maximumworkingsetsize .
                        ?process cimv2:Win32_Process.QuotaPeakPagedPoolUsage ?quotapeakpagedpoolusage .
                        ?process cimv2:Win32_Process.HandleCount ?handlecount .
                        ?process cimv2:Win32_Process.OSName ?osname .
                        ?process cimv2:Win32_Process.WriteOperationCount ?writeoperationcount .
                        ?process cimv2:Win32_Process.SessionId ?sessionid .
                        ?process cimv2:Win32_Process.OtherOperationCount ?otheroperationcount .
                        ?process cimv2:Win32_Process.CSName ?csname .
                        ?process cimv2:Win32_Process.WindowsVersion ?windowsversion .
                        ?process cimv2:Win32_Process.Description ?description .
                        ?process cimv2:Win32_Process.QuotaNonPagedPoolUsage ?quotanonpagedpoolusage .
                        ?process cimv2:Win32_Process.ThreadCount ?threadcount .
                        ?process cimv2:Win32_Process.Priority ?priority .
                        ?process cimv2:Win32_Process.ParentProcessId ?parentprocessid .
                        ?process cimv2:Win32_Process.InstallDate ?installdate .
                        ?process cimv2:Win32_Process.CommandLine ?commandline .
                        ?process cimv2:Win32_Process.VirtualSize ?virtualsize .
                        ?process cimv2:Win32_Process.QuotaPagedPoolUsage ?quotapagedpoolusage .
                        ?process cimv2:Win32_Process.CreationDate ?creationdate .
                        ?process cimv2:Win32_Process.TerminationDate ?terminationdate .
                        ?process cimv2:Win32_Process.Name ?name .
                        ?process cimv2:Win32_Process.PeakVirtualSize ?peakvirtualsize .
                        ?process cimv2:Win32_Process.PrivatePageCount ?privatepagecount .
                        ?process cimv2:Win32_Process.ExecutionState ?executionstate .
                        ?process cimv2:Win32_Process.PeakPageFileUsage ?peakpagefileusage .
                        ?process cimv2:Win32_Process.CSCreationClassName ?cscreationclassname .
                        ?process cimv2:Win32_Process.MinimumWorkingSetSize ?minimumworkingsetsize .
                        ?process cimv2:Win32_Process.QuotaPeakNonPagedPoolUsage ?quotapeaknonpagedpoolusage .
                        ?process cimv2:Win32_Process.Status ?status .
                        ?process cimv2:Win32_Process.CreationClassName ?creationclassname .
                        ?process cimv2:Win32_Process.OSCreationClassName ?oscreationclassname .
                        ?process cimv2:Win32_Process.ProcessId ?processid .
                        ?process cimv2:Win32_Process.KernelModeTime ?kernelmodetime .
                        ?process cimv2:Win32_Process.WorkingSetSize ?workingsetsize . 
                   }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        // Only one row because pids are unique.
        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);

        // To be sure, checks the presence of all properties as extracted from the ontology.
        WmiProvider.WmiClass cl = new WmiProvider().ClassesCIMV2().get("Win32_Process");
        Set<String> allProperties = cl.Properties.keySet();
        Set<String> propertiesCamelCase = allProperties.stream()
                .map(property -> CaseUtils.toCamelCase(property, false))
                        .collect(Collectors.toSet());
        // This is not selected because used as an input.
        // Do not remove it from the original keys set !
        propertiesCamelCase.remove("handle");

        // All these values must be set.
        Assert.assertEquals(propertiesCamelCase, singleRow.KeySet());

        // Check the value of a property whose result is known.
        Assert.assertEquals(PresentUtils.LongToXml(currentPid), singleRow.GetAsLiteral("processid"));
    }

    // Transforms '"Win32_Process.VirtualSize"@en' into 'VirtualSize'
    static String extractPredicateFromLabel(String fullProperty) {
        Pattern pattern = Pattern.compile(".*\\.(.*)\"@en");
        Matcher matcher = pattern.matcher(fullProperty);
        if(matcher.find()) {
            return matcher.group(1);
        }
        throw new RuntimeException("Cannot match:" + fullProperty);
    }

    /** Get all attributes of Win32_Process.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Process_AllAttributesOntology() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?property_label
                    where {
                        ?property rdfs:domain cimv2:Win32_Process .
                        ?property rdfs:label ?property_label .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Set<String> setLabels = listRows.StringValuesSet("property_label");
        // Checks the presence of an arbitrary property.
        // setLabels=["Win32_Process.TerminationDate"@en, "Win32_Process.VirtualSize"@en, ...
        System.out.println("setLabels=" + setLabels);
        // This tests the presence of '"Win32_Process.VirtualSize"@en'
        Assert.assertTrue(setLabels.contains(PresentUtils.InternationalizeUnquoted("Win32_Process.VirtualSize")));

        // Transforms '"Win32_Process.VirtualSize"@en' into 'VirtualSize'
        Set<String> shortLabels = setLabels.stream()
                .map(RepositoryWrapperCIMV2Test::extractPredicateFromLabel)
                .collect(Collectors.toSet());

        // To be sure, checks the presence of all properties as extracted from the ontology.
        WmiProvider.WmiClass cl = new WmiProvider().ClassesCIMV2().get("Win32_Process");
        // allProperties = [CreationDate, ExecutionState, VirtualSize, ...
        Set<String> allProperties = cl.Properties.keySet();
        Assert.assertEquals(shortLabels, allProperties);
    }


    @Test
    public void testSelect_Win32_Process_WithoutClass_WithOntology() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?label ?caption
                    where {
                        ?process cimv2:Win32_Process.Handle "%s" .
                        ?process cimv2:Win32_Process.Caption ?caption .
                        cimv2:Win32_Process.Handle rdfs:label ?label .
                    }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("caption", "label"), singleRow.KeySet());
    }

    /** Get all processes with the same ParentProcessId.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Process_ParentProcessId() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?handle
                    where {
                        ?process1 cimv2:Win32_Process.Handle "%s" .
                        ?process1 cimv2:Win32_Process.ParentProcessId ?parent_process_id .
                        ?process2 cimv2:Win32_Process.Handle ?handle .
                        ?process2 cimv2:Win32_Process.ParentProcessId ?parent_process_id .
                    }
                """, currentPidStr);

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Set<String> setHandles = listRows.StringValuesSet("handle");

        System.out.println("currentPidStr=" + currentPidStr);
        System.out.println("setHandles=" + setHandles);
        // The current process must be here.
        Assert.assertTrue(setHandles.contains( currentPidStr));
    }

    /** This gets the caption of the process running the service "Windows Search".
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Service_Caption() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?caption1 ?caption2
                    where {
                        ?_1_service cimv2:Win32_Service.DisplayName "Windows Search" .
                        ?_1_service cimv2:Win32_Service.Caption ?caption1 .
                        ?_1_service cimv2:Win32_Service.ProcessId ?process_id .
                        ?_2_process cimv2:Win32_Process.ProcessId ?process_id .
                        ?_2_process cimv2:Win32_Process.Caption ?caption2 .
                    }
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        // One service only with this name.
        Assert.assertEquals(1, listRows.size());

        // Win32_Service.Caption = "Windows Search"
        // Win32_Process.Caption = "SearchIndexer.exe"
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals("\"Windows Search\"",singleRow.GetAsLiteral("caption1"));
        Assert.assertEquals("\"SearchIndexer.exe\"",singleRow.GetAsLiteral("caption2"));
    }

    /** This gets the antecedents of the process running the service "Windows Search".
     * FIXME: "TypeOfDependency" is apparently always null. See this query:
     * Get-WmiObject -Query 'select TypeOfDependency from Win32_DependentService where TypeOfDependency != null '
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Service_Antecedent() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?display_name ?dependency_type
                    where {
                        ?_1_service cimv2:Win32_Service.DisplayName "Windows Search" .
                        ?_2_assoc cimv2:Win32_DependentService.Dependent ?_1_service .
                        ?_2_assoc cimv2:Win32_DependentService.Antecedent ?_3_service .
                        ?_2_assoc cimv2:Win32_DependentService.TypeOfDependency ?dependency_type .
                        ?_3_service cimv2:Win32_Service.DisplayName ?display_name .
                    }
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        // One service only with this name.
        Assert.assertTrue(listRows.size() > 0);
        for(RdfSolution.Tuple singleRow : listRows) {
            System.out.println("Antecedent service:" + singleRow);
        }
        Set<String> setAntecedents = listRows.StringValuesSet("display_name");
        // These are the input dependencies of this service.
        String windowsVersion = System.getProperty("os.name");
        if(windowsVersion.equals("Windows 10")) {
            Assert.assertEquals(
                    Set.of("Remote Procedure Call (RPC)", "Background Tasks Infrastructure Service"),
                    setAntecedents);
        } else if(windowsVersion.equals("Windows 7")) {
            Assert.assertEquals(
                    Set.of("Remote Procedure Call (RPC)"),
                    setAntecedents);
        } else {
            throw new RuntimeException("Unidentified Windows version:" + windowsVersion);
        }
    }

    /** Logon type of the current user.
     * Logon type values:
     *    System account (0)
     *    Interactive (2)
     *    Network (3)
     *    Batch (4)
     *    Service (5)
     *    Proxy (6)
     *    Unlock (7)
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_LogonSession() throws Exception {
        String currentUser = System.getProperty("user.name");
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?logon_type ?logon_id ?logon_name
                    where {
                        ?_1_user cimv2:Win32_UserAccount.Name "%s" .
                        ?_2_assoc cimv2:Win32_LoggedOnUser.Antecedent ?_1_user .
                        ?_2_assoc cimv2:Win32_LoggedOnUser.Dependent ?_3_logon_session .
                        ?_3_logon_session cimv2:Win32_LogonSession.LogonType ?logon_type .
                        ?_3_logon_session cimv2:Win32_LogonSession.LogonId ?logon_id .
                        ?_3_logon_session cimv2:Win32_LogonSession.Name ?logon_name .
                   }
                """, currentUser);

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        System.out.println("listRows=" + listRows);

        // It might contain several sessions.
        Set<Long> setLogonTypes = listRows.LongValuesSet("logon_type");
        System.out.println("setLogonTypes=" + setLogonTypes);
        // Interactive (2)
        Assert.assertEquals(Set.of(2L), setLogonTypes);
    }

    /** Executables of the process running the service "Windows Search".
     * FIXME: This does not work because the service forbid to access its executable and libraries.
     * Same error when running from an Administrator session.
     *
     * @throws Exception
     */
    @Ignore("The service forbid to access its executable and libraries")
    @Test
    public void testSelect_Win32_Service_Executable() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?executable_name
                    where {
                        ?_1_service cimv2:Win32_Service.DisplayName "Windows Search" .
                        ?_1_service cimv2:Win32_Service.ProcessId ?process_id .
                        ?_2_process cimv2:Win32_Process.ProcessId ?process_id .
                        ?_3_assoc cimv2:CIM_ProcessExecutable.Dependent ?_2_process .
                        ?_3_assoc cimv2:CIM_ProcessExecutable.Antecedent ?_4_file .
                        ?_4_file cimv2:CIM_DataFile.Name ?executable_name .
                    }
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Set<String> listExecutables = listRows.StringValuesSet("executable_name");
        System.out.println("listExecutables=" + listExecutables);
        Assert.assertTrue(listExecutables.contains("SearchIndexer.exe"));
    }

    /** Logon type of the process running the service "Windows Search".
     * Same error when running from an Administrator session.
     *
     * @throws Exception
     */
    @Ignore("The service forbids accessing its logon session")
    @Test
    public void testSelect_Win32_Service_Win32_LogonSession() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?logon_type
                    where {
                        ?_1_service cimv2:Win32_Service.DisplayName "Windows Search" .
                        ?_1_service cimv2:Win32_Service.ProcessId ?process_id .
                        ?_2_process cimv2:Win32_Process.ProcessId ?process_id .
                        ?_3_assoc cimv2:Win32_SessionProcess.Dependent ?_2_process .
                        ?_3_assoc cimv2:Win32_SessionProcess.Antecedent ?_4_logon_session .
                        ?_4_logon_session cimv2:Win32_LogonSession.LogonType ?logon_type .
                    }
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        // Service (5)
        Assert.assertEquals(Set.of(PresentUtils.LongToXml(5)), singleRow.GetAsLiteral("logon_type"));
    }

    /** Parent of the process running the service "Windows Search".
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Service_ParentProcessId() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?parent_caption
                    where {
                        ?_1_service cimv2:Win32_Service.DisplayName "Windows Search" .
                        ?_1_service cimv2:Win32_Service.ProcessId ?process_id .
                        ?_2_process cimv2:Win32_Process.ProcessId ?process_id .
                        ?_2_process cimv2:Win32_Process.ParentProcessId ?parent_process_id .
                        ?_3_process cimv2:Win32_Process.ProcessId ?parent_process_id .
                        ?_3_process cimv2:Win32_Process.Caption ?parent_caption .
                    }
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals("\"services.exe\"", singleRow.GetAsLiteral("parent_caption"));
    }

    /** This selects the executable and libraries of the current process.
     * Patterns order, therefore the order of queries, is forced with alphabetical order and no optimisation.
     * Optimising the object patterns implies changing their order with some constraints due
     * to variable dependencies.
     * With this, the test can focus on the results of the evaluation : This is simpler to test.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_CIM_ProcessExecutable_NoFilter() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?file_name
                    where {
                        ?_1_process rdf:type cimv2:Win32_Process .
                        ?_1_process cimv2:Handle "%s" .
                        ?_1_process cimv2:Win32_Process.Caption ?caption .
                        ?_2_assoc rdf:type cimv2:CIM_ProcessExecutable .
                        ?_2_assoc cimv2:Dependent ?_1_process .
                        ?_2_assoc cimv2:Antecedent ?_3_file .
                        ?_3_file rdf:type cimv2:CIM_DataFile .
                        ?_3_file cimv2:Name ?file_name .
                   }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        //Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("file_name"), singleRow.KeySet());
        System.out.println("Exec=" + singleRow.GetAsLiteral("file_name"));
        String expectedBin = "\"" + PresentUtils.CurrentJavaBinary() + "\"";
        Assert.assertEquals(expectedBin, singleRow.GetAsLiteral("file_name"));
    }

    /** Patterns order, therefore the order of queries, is forced with alphabetical order and no optimisation.
     * Optimising the object patterns implies changing their order with some constraints due
     * to variable dependencies.
     * With this, the test can focus on the results of the evaluation : This is simpler to test.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_CIM_ProcessExecutable_Filter() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?file_name
                    where {
                        ?_1_process rdf:type cimv2:Win32_Process .
                        ?_1_process cimv2:Handle "%s" .
                        ?_1_process cimv2:Win32_Process.Caption ?caption .
                        ?_2_assoc rdf:type cimv2:CIM_ProcessExecutable .
                        ?_2_assoc cimv2:Dependent ?_1_process .
                        ?_2_assoc cimv2:Antecedent ?_3_file .
                        ?_3_file rdf:type cimv2:CIM_DataFile .
                        ?_3_file cimv2:Name ?file_name .
                        filter(regex(?file_name, "java.exe", "i" )) 
                   }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("file_name"), singleRow.KeySet());
        System.out.println("Exec=" + singleRow.GetAsLiteral("file_name"));
        String expectedBin = "\"" + PresentUtils.CurrentJavaBinary() + "\"";
        Assert.assertEquals(expectedBin, singleRow.GetAsLiteral("file_name"));
    }

    /** This fetches processes running Java, and return only the node of the associated files.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_CIM_ProcessExecutable_Assoc() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?_3_file
                    where {
                        ?_1_process cimv2:Win32_Process.Handle "%s" .
                        ?_2_assoc cimv2:CIM_ProcessExecutable.Dependent ?_1_process .
                        ?_2_assoc cimv2:CIM_ProcessExecutable.Antecedent ?_3_file .
                        ?_3_file cimv2:CIM_DataFile.Name ?file_name .
                        filter(regex(?file_name, "java.exe", "i" )) 
                   }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("_3_file"), singleRow.KeySet());
        System.out.println("Exec=" + singleRow.GetAsUri("_3_file"));
    }

    /** This selects only the associator of a given process, with a given order of patterns.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_CIM_ProcessExecutable_AssocOnlyFromProcessOrderA() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?_1_assoc
                    where {
                        ?_2_process cimv2:Win32_Process.Handle "%s" .
                        ?_1_assoc cimv2:CIM_ProcessExecutable.Dependent ?_2_process .
                   }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("_1_assoc"), singleRow.KeySet());
    }

    /** This selects only the associator of a given process, with another given order of patterns.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_CIM_ProcessExecutable_AssocOnlyFromProcessOrderB() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?_2_assoc
                    where {
                        ?_1_process cimv2:Win32_Process.Handle "%s" .
                        ?_2_assoc cimv2:CIM_ProcessExecutable.Dependent ?_1_process .
                   }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("_2_assoc"), singleRow.KeySet());
    }

    /** This select only the associator of all processes.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_CIM_ProcessExecutable_AssocOnlyFromAllProcesses() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?_1_process ?_2_assoc
                    where {
                        ?_2_assoc cimv2:CIM_ProcessExecutable.Dependent ?_1_process .
                   }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("_1_process", "_2_assoc"), singleRow.KeySet());
        System.out.println("singleRow=" + singleRow);

        // Checks that the current process is present.
        Set<String> setProcesses = listRows.NodeValuesSet("_1_process");
        System.out.println("currentProcessUri=" + currentProcessUri);
        System.out.println("setProcesses=" + setProcesses);
        Assert.assertTrue(setProcesses.contains(currentProcessUri));
    }

    /*
    This selects the directory names of the executables and libraries of all running processes.
    It must contain the directory of "java.exe" which runs the current process.
    */
    @Ignore ("SLOW ! SLOW !")
    @Test
    public void testSelect_CIM_ProcessExecutable_CIM_DirectoryContainsFile_AllProcess() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?dir_name
                    where {
                        ?_1_assoc cimv2:CIM_ProcessExecutable.Antecedent ?_3_file .
                        ?_2_dir cimv2:Win32_Directory.FileName ?dir_name .
                        ?_2_dir ^cimv2:CIM_DirectoryContainsFile.GroupComponent/cimv2:CIM_DirectoryContainsFile.PartComponent ?_3_file .
                   }
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("dir_name"), singleRow.KeySet());
        System.out.println("singleRow=" + singleRow);

        // Checks that the current process is present.
        Set<String> setDirNames = listRows.NodeValuesSet("dir_name");
        System.out.println("setDirNames=" + setDirNames);

        Assert.assertTrue(setDirNames.contains(currJavaDir));
    }

    /*
    This selects the directory names of the executables and libraries of all running processes.
    It must contain the directory of "java.exe" which runs the current process.
    */
    @Ignore("Too slow., because it loops on: 'Select FileName, __PATH from Win32_Directory'")
    @Test
    public void testSelect_CIM_ProcessExecutable_CIM_DirectoryContainsFile_CurrentProcess() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?dir_name
                    where {
                        ?_1_process cimv2:CIM_Process.Handle %s .
                        ?_1_process ^cimv2:CIM_ProcessExecutable.Dependent/cimv2:CIM_ProcessExecutable.Antecedent ?_2_file .
                        ?_3_dir cimv2:Win32_Directory.FileName ?dir_name .
                        ?_3_dir ^cimv2:CIM_DirectoryContainsFile.GroupComponent/cimv2:CIM_DirectoryContainsFile.PartComponent ?_2_file .
                   }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("dir_name"), singleRow.KeySet());
        System.out.println("singleRow=" + singleRow);

        // Checks that the current process is present.
        Set<String> setDirNames = listRows.NodeValuesSet("dir_name");
        System.out.println("setDirNames=" + setDirNames);

        Assert.assertTrue(setDirNames.contains(currJavaDir));
    }

    /*
    This selects the directory names of the executables and libraries of all running processes.
    It must contain the directory of "java.exe" whihc runs the current process.
    */
    @Ignore("Too slow. Loop on all files.")
    @Test
    public void testSelect_CIM_ProcessExecutable_CIM_DirectoryContainsFile_CurrentProcess_Concise() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?dir_name
                    where {
                        ?_1_process cimv2:CIM_Process.Handle %s .
                        ?_1_process ^cimv2:CIM_ProcessExecutable.Dependent/cimv2:CIM_ProcessExecutable.Antecedent ?_2_file .
                        ?_3_dir ^cimv2:Win32_Directory.FileName/^cimv2:CIM_DirectoryContainsFile.GroupComponent/cimv2:CIM_DirectoryContainsFile.PartComponent ?_2_file .
                   }
                """, currentPidStr);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("dir_name"), singleRow.KeySet());
        System.out.println("singleRow=" + singleRow);

        // Checks that the current process is present.
        Set<String> setDirNames = listRows.NodeValuesSet("dir_name");
        System.out.println("setDirNames=" + setDirNames);

        Assert.assertTrue(setDirNames.contains(currJavaDir));
    }

    /** This selects all Win32_UserAccount. The current account must be there.
     *
     */
    @Test
    public void testSelect_Win32_UserAccount() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?name ?domain
                    where {
                        ?user rdf:type cimv2:Win32_UserAccount .
                        ?user cimv2:Name ?name .
                        ?user cimv2:Domain ?domain .
                   }
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("name", "domain"), singleRow.KeySet());

        Set<String> setNames = listRows.StringValuesSet("name");
        String currentUser = System.getProperty("user.name");
        System.out.println("setNames=" + setNames);
        // These groups are defined on all Windows machines.
        Assert.assertTrue(setNames.contains("Administrator"));
        Assert.assertTrue(setNames.contains("Guest"));
        Assert.assertTrue(setNames.contains(currentUser));
    }

    /** This selects the groups of the current user.
     *
     */
    @Test
    public void testSelect_Win32_GroupUser () throws Exception {
        RepositoryWrapper repositoryWrapper = new RepositoryWrapper("ROOT\\CIMV2");
        String currentUser = System.getProperty("user.name");

        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?group_name
                    where {
                        ?_1_user cimv2:Win32_UserAccount.Name "%s" .
                        ?_2_assoc cimv2:Win32_GroupUser.GroupComponent ?_3_group .
                        ?_2_assoc cimv2:PartComponent ?_1_user .
                        ?_3_group cimv2:Win32_Group.Name ?group_name .
                   }
                """, currentUser);
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        // The current user is at least in one group.
        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("group_name"), singleRow.KeySet());

        Set<String> setGroups = listRows.StringValuesSet("group_name");
        // A user is always in this group.
        System.out.println("setGroups=" + setGroups);
        // Windows 7.
        // setGroups=["HomeUsers", "TelnetClients", "Administrators", "Performance Log Users", "ORA_DBA"]
        // On Windows 10, "HomeUsers" becomes "Users".
        // Assert.assertTrue(setGroups.contains("\"Users\""));
        Assert.assertTrue(setGroups.contains("Performance Log Users"));
    }

    /***
     * Volume of a given directory.
     */
    @Test
    public void testSelect_Win32_Volume() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?device_id
                    where {
                        ?my3_volume cimv2:DriveLetter ?my_drive .
                        ?my3_volume cimv2:DeviceID ?device_id .
                        ?my3_volume rdf:type cimv2:Win32_Volume .
                        ?my0_dir rdf:type cimv2:Win32_Directory .
                        ?my0_dir cimv2:Name "C:\\\\Program Files (x86)" .
                        ?my0_dir cimv2:Drive ?my_drive .
                    }
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("device_id"), singleRow.KeySet());

        Set<String> setDevices = listRows.StringValuesSet("device_id");
        System.out.println("setDevices=" + setDevices);
        Assert.assertEquals(1, setDevices.size());
        // For example: "\\?\Volume{e88d2f2b-332b-4eeb-a420-20ba76effc48}\"
        Assert.assertTrue(setDevices.stream().findFirst().orElse("xyz").startsWith("\\\\?\\Volume{"));
    }

    /***
     * Mount point of a given directory.
     * The types of each instance are implicitly given in properties. Once only is enough.
     */
    @Test
    public void testSelect_Win32_MountPoint() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_dir_name
                    where {
                        ?my3_dir cimv2:Win32_Directory.Name ?my_dir_name .
                        ?my2_assoc cimv2:Win32_MountPoint.Volume ?my1_volume .
                        ?my2_assoc cimv2:Directory ?my3_dir .
                        ?my1_volume cimv2:Win32_Volume.DriveLetter ?my_drive .
                        ?my1_volume cimv2:DeviceID ?device_id .
                        ?my0_dir cimv2:Name "C:\\\\Program Files (x86)" .
                        ?my0_dir cimv2:Win32_Directory.Drive ?my_drive .
                    }
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("my_dir_name"), singleRow.KeySet());

        Set<String> setDirs = listRows.StringValuesSet("my_dir_name");
        System.out.println("setDirs=" + setDirs);
        Assert.assertEquals(1, setDirs.size());
        // Conversion to uppercase due to different behaviour depending on the Windows version, 7 or 10.
        Assert.assertEquals("C:\\", setDirs.stream().findFirst().orElse("xyz").toUpperCase());
    }

    /** Number of files in a directory.
     * Note: This is not needed to explicitly specify cimv2:CIM_DataFile in a Sparql statement like:
     *     ?my2_file rdf:type cimv2:CIM_DataFile .
     * This is because CIM_DirectoryContainsFile.PartComponent point to CIM_DataFile only.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_CIM_DataFile_Count() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select (COUNT(?my2_file) as ?count_files)
                    where {
                        ?my1_assoc cimv2:CIM_DirectoryContainsFile.PartComponent ?my2_file .
                        ?my1_assoc cimv2:GroupComponent ?my0_dir .
                        ?my0_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                    } group by ?my0_dir
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("count_files"), singleRow.KeySet());

        // Check that no file is missing.
        File f = new File("C:\\Windows");
        Set<String> filesSetExpected = Arrays
                .stream(f.listFiles())
                .filter(subf -> subf.isFile())
                .map(subf -> subf.toPath().toString())
                .collect(Collectors.toSet());
        int countFilesExpected = filesSetExpected.size();

        // Something like '"36"^^<http://www.w3.org/2001/XMLSchema#integer>'
        String countStr = PresentUtils.IntToXml(countFilesExpected);
        System.out.println("countFilesExpected=" + Integer.toString(countFilesExpected));

        String countActual = singleRow.GetAsLiteral("count_files");
        System.out.println("count_files=" + singleRow.GetAsLiteral("count_files"));
        Assert.assertEquals(countStr, countActual);
    }

    /** Minimum, maximum and file sizes in a directory.
     * The internal results must be of long type.
     * TODO: Beware that SUM() function returns a xsd:int : Therefore it is converted to xsd:long.
     * TODO: Beware that it might not work with files bigger that 2^32, but it is because of Sparql.
     * @throws Exception
     */
    @Test
    public void testSelect_CIM_DataFile_SizeMinMaxSum() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select (MIN(?file_size) as ?size_min) (MAX(?file_size) as ?size_max) (xsd:long(SUM(?file_size)) as ?size_sum)
                    where {
                        ?my2_file cimv2:CIM_DataFile.FileSize ?file_size .
                        ?my1_assoc cimv2:CIM_DirectoryContainsFile.PartComponent ?my2_file .
                        ?my1_assoc cimv2:GroupComponent ?my0_dir .
                        ?my0_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                    } group by ?my0_dir
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        System.out.println("listRows=" + listRows);
        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);

        // Calculate "by hand" what the result should be.
        long expectedFileMin = Long.MAX_VALUE;
        long expectedFileMax = 0;
        long expectedFileSum = 0;

        File dirFile = new File("C:\\Windows");
        Set<String> filesSetExpected = Arrays
                .stream(dirFile.listFiles())
                .filter(subf -> subf.isFile())
                .map(subf -> subf.toPath().toString())
                .collect(Collectors.toSet());

        // Check that the sizes are correct.
        for(String fileName : filesSetExpected) {
            File oneFile = new File(fileName);
            long fileSize = oneFile.length();
            if(expectedFileMin > fileSize)
                expectedFileMin = fileSize;
            else if(expectedFileMax < fileSize)
                expectedFileMax = fileSize;
            expectedFileSum += fileSize;
        }

        System.out.println("expectedFileMin=" + expectedFileMin);
        System.out.println("expectedFileMax=" + expectedFileMax);
        System.out.println("expectedFileSum=" + expectedFileSum);

        Assert.assertEquals(Set.of("size_min", "size_max", "size_sum"), singleRow.KeySet());
        Assert.assertEquals(PresentUtils.LongToXml(expectedFileMin), singleRow.GetAsLiteral("size_min"));
        Assert.assertEquals(PresentUtils.LongToXml(expectedFileMax), singleRow.GetAsLiteral("size_max"));
        Assert.assertEquals(PresentUtils.LongToXml(expectedFileSum), singleRow.GetAsLiteral("size_sum"));
    }

    /** Startup time of current process.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Process_CurrentCreationDate() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?creation_date
                    where {
                        ?my_process cimv2:Win32_Process.CreationDate ?creation_date .
                        ?my_process cimv2:Win32_Process.Handle "%s" .
                    }
                """, currentPidStr);

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() == 1);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("creation_date"), singleRow.KeySet());

        String minCreationDate = singleRow.GetAsLiteral("creation_date");
        System.out.println("minCreationDate=" + minCreationDate);

        XMLGregorianCalendar xmlDate = PresentUtils.ToXMLGregorianCalendar(minCreationDate);
        ZonedDateTime zonedDateTimeActual = xmlDate.toGregorianCalendar().toZonedDateTime();
        LocalDateTime localDateTimeActual = zonedDateTimeActual.toLocalDateTime();
        Instant asInstantActual = zonedDateTimeActual.toInstant();

        Instant startExpected = ProcessHandle.current().info().startInstant().orElse(null);

        LocalDateTime dateExpected = LocalDateTime.ofInstant(startExpected, ZoneId.systemDefault());
        System.out.println("Expect:" + dateExpected);
        System.out.println("Actual:" + localDateTimeActual);
        Assert.assertEquals(dateExpected, localDateTimeActual);
        Assert.assertEquals(asInstantActual.toEpochMilli() / 10000, startExpected.toEpochMilli() / 10000);
    }

    /** Oldest running process.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Process_Oldest() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select (MIN(?creation_date) as ?min_creation_date)
                    where {
                        ?my_process cimv2:Win32_Process.CreationDate ?creation_date .
                    } #group by ?my_process
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("min_creation_date"), singleRow.KeySet());

        String minStartActualString = singleRow.GetAsLiteral("min_creation_date");
        System.out.println("minStartActualString=" + minStartActualString);

        XMLGregorianCalendar xmlDate = PresentUtils.ToXMLGregorianCalendar(minStartActualString);
        ZonedDateTime minStartActualZoned = xmlDate.toGregorianCalendar().toZonedDateTime();
        LocalDateTime minStartActualLocal = minStartActualZoned.toLocalDateTime();
        Instant minStartActualInstant = minStartActualZoned.toInstant();

        // Now calculate the same time.
        List<Instant> instantsList = ProcessHandle.allProcesses().map(ph -> ph.info().startInstant().orElse(null)).toList();
        Instant minStartExpectedInstant = null;
        System.out.println("Instants:" + instantsList.size());

        int missedProcess = 0;
        for(Instant instant: instantsList) {
            if(instant != null) {
                if (minStartExpectedInstant == null || minStartExpectedInstant.isAfter(instant)) {
                    minStartExpectedInstant = instant;
                }
            }
            else {
                ++missedProcess;
            }
        }

        LocalDateTime minStartExpectedLocal = LocalDateTime.ofInstant(minStartExpectedInstant, ZoneId.systemDefault());
        System.out.println("Expect:" + minStartExpectedLocal);
        System.out.println("Actual:" + minStartActualLocal);

        if(missedProcess > 0) {
            System.out.println("Missed " + missedProcess + " expected processes. Approximate check.");
            Assert.assertTrue(minStartActualInstant.isBefore(minStartExpectedInstant));

            // Even if some processes are missed, this should be correct.
            // FIXME: Why start date of some processes is visible with WMI, but not Java ?
            Assert.assertEquals(minStartExpectedLocal.getDayOfYear(), minStartActualLocal.getDayOfYear());
            Assert.assertEquals(minStartExpectedLocal.getMonthValue(), minStartActualLocal.getMonthValue());
        } else {
            System.out.println("All expected processes found. Exact check.");
            Assert.assertEquals(minStartExpectedInstant, minStartActualInstant);
        }
    }

    /** Creation date of a file. Here, the java executable.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_CIM_DataFile_CreationDate() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?creation_date
                    where {
                        ?my_file cimv2:CIM_DataFile.CreationDate ?creation_date .
                        ?my_file cimv2:Name "%s" .
                    }
                """, PresentUtils.CurrentJavaBinary().replace("\\", "\\\\"));

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("creation_date"), singleRow.KeySet());

        String actualCreationDate = singleRow.GetAsLiteral("creation_date");
        System.out.println("actualCreationDate=" + actualCreationDate);

        XMLGregorianCalendar xmlDate = PresentUtils.ToXMLGregorianCalendar(actualCreationDate);
        System.out.println("xmlDate=" + xmlDate);

        FileTime expectedCreationTimeXml = (FileTime) Files.getAttribute( Path.of(PresentUtils.CurrentJavaBinary()), "creationTime");
        System.out.println("expectedCreationTimeXml=" + expectedCreationTimeXml);

        // Expected :2022-02-11T00:44:44.7305199Z
        // Actual   :2022-02-11T00:44:44.730519
        // Truncate the expected date because of different format.
        Assert.assertEquals(expectedCreationTimeXml.toString().substring(0, 26), xmlDate.toString());
    }

    /** Creation date of a Win32_Directory. Here, "C:/Windows".
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Directory_CreationDate() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?creation_date
                    where {
                        ?my_file cimv2:Win32_Directory.CreationDate ?creation_date .
                        ?my_file cimv2:Win32_Directory.Name "C:\\\\Windows" .
                    }
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("creation_date"), singleRow.KeySet());

        String actualCreationDate = singleRow.GetAsLiteral("creation_date");
        System.out.println("actualCreationDate=" + actualCreationDate);

        XMLGregorianCalendar xmlDate = PresentUtils.ToXMLGregorianCalendar(actualCreationDate);
        System.out.println("xmlDate=" + xmlDate);

        FileTime expectedCreationTimeXml = (FileTime) Files.getAttribute( Path.of("C:\\Windows"), "creationTime");
        System.out.println("expectedCreationTimeXml=" + expectedCreationTimeXml);

        // Expected :2022-02-11T00:44:44.7305199Z
        // Actual   :2022-02-11T00:44:44.730519
        // Truncate the expected date because of different format.
        Assert.assertEquals(expectedCreationTimeXml.toString().substring(0, 26), xmlDate.toString());
    }

    /** Most used modules of the current process.
     * This takes the list of all modules used by the current process and find the one which is the most shared
     * by other processes.
     * This cannot work yet: https://www.techtalkz.com/threads/re-bug-with-cim_datafile.132215/
     *
     * @throws Exception
     */
    @Ignore("This does not work because InUseCount is always NULL")
    @Test
    public void testSelect_MostUsedModule() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select (MAX(?my_inusecount) as ?max_inusecount)
                    where {
                        ?my1_process cimv2:Win32_Process.Handle "%s" .
                        ?my2_assoc cimv2:CIM_ProcessExecutable.Dependent ?my1_process .
                        ?my2_assoc cimv2:CIM_ProcessExecutable.Antecedent ?my3_file .
                        ?my3_file  cimv2:CIM_DataFile.Name ?my_filename .
                        ?my3_file  cimv2:CIM_DataFile.InUseCount ?my_inusecount .
                    } group by ?my2_assoc
                """, currentPidStr);

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("max_inusecount"), singleRow.KeySet());

        // FIXME: This value is always null. Why ?
        String maxInUseCount = singleRow.GetAsLiteral("max_inusecount");
        System.out.println("maxInUseCount=" + maxInUseCount);

        Assert.assertTrue(false);
    }

    /** The intention is to test the XSD type "long".
     * "1179817" is "1200A9" in hexadecimal.
     * */
    @Test
    public void testSelect_AccessMask() throws Exception {
        String sparqlQuery = """
                prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                select ?my_name 
                where {
                    ?my1_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                    ?my2_assoc cimv2:GroupComponent ?my1_dir .
                    ?my2_assoc cimv2:CIM_DirectoryContainsFile.PartComponent ?my3_file .
                    ?my3_file  cimv2:CIM_DataFile.Name ?my_name .
                    ?my3_file  cimv2:CIM_DataFile.AccessMask "1179817"^^<http://www.w3.org/2001/XMLSchema#long> .
                }
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        for(RdfSolution.Tuple tuple: listRows) {
            System.out.println("tuple=" + tuple);
        }

        Assert.assertTrue(listRows.size() > 0);
    }

    /** Average number of threads in the current process.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_CountThreadsCurrentProcess() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select (COUNT(?my2_thread) as ?count_threads)
                    where {
                        ?my1_process cimv2:Win32_Process.Handle "%s" .
                        ?my2_thread cimv2:Win32_Thread.ProcessHandle "%s" .
                    } group by ?process_handle
                """, currentPidStr, currentPidStr);

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("count_threads"), singleRow.KeySet());

        // For example: count_threads = ""41"^^<http://www.w3.org/2001/XMLSchema#integer>"
        Long countThreads = PresentUtils.XmlToLong(singleRow.GetAsLiteral("count_threads"));
        System.out.println("countThreads=" + countThreads);

        // At least one thread in this process.
        Assert.assertTrue(countThreads >= 1);
    }


    /** Average number of threads in the current process.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_AverageThreadsPerProcess() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select (AVG(?count_threads) as ?average_count_threads)
                    where {
                        select (COUNT(?_1_thread) as ?count_threads)
                        where {
                            ?_1_thread cimv2:Win32_Thread.ProcessHandle ?process_handle .
                        } group by ?process_handle
                    }
                """, currentPidStr);

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple singleRow = listRows.get(0);
        // Typical value: "14.870860927152317880794702"^^<http://www.w3.org/2001/XMLSchema#decimal>
        Assert.assertEquals(Set.of("average_count_threads"), singleRow.KeySet());

        double average_count_threads = PresentUtils.XmlToDouble(singleRow.GetAsLiteral("average_count_threads"));
        System.out.println("average_count_threads=" + average_count_threads);

        // At least one thread per process on the average.
        Assert.assertTrue(average_count_threads >= 1.0);
    }

    /** Services dependent of the service "Windows Search", at first level only.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_PropertyPath_Win32_DependentService_One() throws Exception {
        /*
        FIXME: The order of the WQL queries is not optimised yet, and there are executed in the alphabetical order
        of the variable of the subject.
        Therefore, the first subject to be calculated is prefixed to be executed first,
        especially before the anonymous variables created by the Sparql query parser.
        This trick will be necessary until the optimizer is added.
        Anonymous variable names are like "_anon_ba92a16d_be71_4c7c_bb3b_3b7c9917466c.
        Here, it forces the anonymous variable to be executed AFTER _1_service1 and BEFORE zzzzz_2_service2.
        */
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?service_name
                    where {
                        ?_1_service1 cimv2:Win32_Service.DisplayName "Windows Search" .
                        ?_1_service1 ^cimv2:Win32_DependentService.Dependent/cimv2:Win32_DependentService.Antecedent ?zzzzz_2_service2 .
                        ?zzzzz_2_service2 cimv2:Win32_Service.DisplayName ?service_name .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Set<String> setNames = listRows.StringValuesSet("service_name");
        System.out.println("setNames=" + setNames);

        // These might depend on Windows version.
        Assert.assertTrue(setNames.contains("Remote Procedure Call (RPC)"));
        Assert.assertTrue(setNames.contains("Background Tasks Infrastructure Service"));
    }

    @Ignore("Property paths not implemented yet")
    @Test
    public void testSelect_PropertyPath_Win32_DependentService_Many() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?service_name
                    where {
                        ?service1 cimv2:Win32_Service.DisplayName "Windows Search" .
                        ?service1 (^cimv2:Win32_DependentService.Dependent/cimv2:Win32_DependentService.Antecedent)+ ?service2 .
                        ?service2 cimv2:Win32_Service.DisplayName ?service_name .
                    }
                """;
        Assert.fail("Not implemented yet");
    }

    /** Files in a directory.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_PropertyPath_Win32_Directory_One() throws Exception {
        /*
        FIXME: The order of the WQL queries is not optimised yet, and there are executed in the alphabetical order
        of the variable of the subject.
        Therefore, the first subject to be calculated is prefixed to be executed first,
        especially before the anonymous variables created by the Sparql query parser.
        Anonymous variable names are like "_anon_ba92a16d_be71_4c7c_bb3b_3b7c9917466c.
        This trick will be necessary until the optimizer is added.
        */
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?file_name
                    where {
                        ?_1_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                        ?_1_dir ^cimv2:CIM_DirectoryContainsFile.GroupComponent/cimv2:CIM_DirectoryContainsFile.PartComponent ?file .
                        ?file cimv2:CIM_DataFile.Name ?file_name .
                    }
                """;
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Set<String> setNames = listRows.StringValuesSet("file_name");
        System.out.println("setNames=" + setNames);

        // These files are always present.
        Assert.assertTrue(setNames.contains("C:\\Windows\\notepad.exe"));
        Assert.assertTrue(setNames.contains("C:\\Windows\\explorer.exe"));
        Assert.assertTrue(setNames.contains("C:\\Windows\\win.ini"));
        Assert.assertTrue(setNames.contains("C:\\Windows\\system.ini"));
    }

    /** Files in a directory at several levels with a property path.
     *
     * @throws Exception
     */
    @Ignore("Property paths not implemented yet")
    @Test
    public void testSelect_PropertyPath_Win32_Directory_Many() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?file_name
                    where {
                        ?dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                        ?dir (^cimv2:CIM_DirectoryContainsFile.GroupComponent/cimv2:CIM_DirectoryContainsFile.PartComponent)+ ?file .
                        ?file cimv2:CIM_DataFile.Name ?file_name .
                    }
                """;
        Assert.fail("Not implemented yet");
    }

    /** Pids of the subprocesses of the parent of the current process.
     * This could be greatly simplified because Handle and ProcessId are the same (with different types).
     * The point of this test is to check that the logic works.
     *
     * FIXME: Maybe a slash should not come before a caret ?
     * @throws Exception
     */
    @Test
    public void testSelect_PropertyPath_Win32_Process_One() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process_sub_pid
                    where {
                        ?process_top cimv2:Win32_Process.Handle "%s" .
                        ?process_sub cimv2:Win32_Process.ParentProcessId/^cimv2:Win32_Process.ProcessId ?process_top .
                        ?process_sub cimv2:Win32_Process.Handle ?process_sub_pid
                    }
                """, PresentUtils.ParentProcessId());

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Set<String> setPids = listRows.StringValuesSet("process_sub_pid");
        System.out.println("setPids=" + setPids);

        Assert.assertTrue(setPids.contains(currentPidStr));
    }

    /** Name of the parent process.
     * The property is chosen to work when running From Intellij and also from Maven on a command line.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_PropertyPath_Win32_Process_ParentName() throws Exception {
        /*
        The order of the WQL queries is not optimised yet, and there are executed in the alphabetical order
        of the variable of the subject.
        Therefore, the first subject to be calculated is prefixed to be executed first,
        especially before the anonymous variables created by the Sparql query parser.
        Anonymous variable names are like "_anon_ba92a16d_be71_4c7c_bb3b_3b7c9917466c.
        This trick will be necessary until the optimizer is added.
        */
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?parent_process_command
                    where {
                        ?_1_process_sub cimv2:Win32_Process.Handle "%s" .
                        ?parent_process_command ^cimv2:Win32_Process.Name/cimv2:Win32_Process.ProcessId/^cimv2:Win32_Process.ParentProcessId  ?_1_process_sub .
                    }
                """, currentPidStr);

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Set<String> setCommands = listRows.StringValuesSet("parent_process_command");
        System.out.println("setCommands=" + setCommands);

        String commandParentProcess = PresentUtils.ParentProcessName();
        System.out.println("Expected command=" + commandParentProcess);

        Assert.assertEquals(Set.of(commandParentProcess), setCommands);
    }

    /** Subprocesses of a process at any level.
     *
     * @throws Exception
     */
    @Ignore("Property paths not implemented yet")
    @Test
    public void testSelect_PropertyPath_Win32_Process_Many() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?service_name
                    where {
                        ?process_top cimv2:Win32_Process.Name "dir_name" .
                        ?process_top (^cimv2:CIM_DirectoryContainsFile.Dependent/cimv2:CIM_DirectoryContainsFile.Antecedent)+ ?process_sub .
                        ?process_sub cimv2:Win32_Process.Name ?file_name .
                    }
                """;
        Assert.fail("Not implemented yet");
    }

    /** Union of processes with pid multiple of 2 and pids multiple of 3.
     * This is an arbitrary test but the result is easy to check.
     * The intention is to check that union works.
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Process_Filter_Union() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?handle where 
                    {
                        {
                            select ?handle
                            where {
                                ?process1 cimv2:Win32_Process.ProcessId ?handle .
                                filter( (?handle / 2) * 2 = ?handle) 
                            }
                        }
                        union
                        {
                            select ?handle
                            where {
                                ?process2 cimv2:Win32_Process.ProcessId ?handle .
                                filter( (?handle / 3) * 3 = ?handle) 
                            }
                        }
                    }
                    
        """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Set<Long> setPids = listRows.LongValuesSet("handle");
        System.out.println("setPids=" + setPids);
        // Pids 0 is always present, divisible by 2 ad 3.
        Assert.assertTrue(setPids.contains(0L));
        for(Long onePid: setPids) {
            Assert.assertTrue(onePid % 2 == 0 || onePid % 3 == 0);
        }
    }


    /** Read boolean members of class Win32_Directory. */
    @Test
    public void testSelect_Win32_Directory_Boolean() throws Exception {
        String sparqlQuery = """
                prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                select ?dir_name ?dir_archive ?dir_compressed ?dir_encrypted ?dir_hidden ?dir_readable ?dir_system ?dir_writeable
                where
                {
                    ?_1_dir cimv2:Win32_Directory.Name "C:\\\\WINDOWS" .
                    ?_2_assoc_dir cimv2:Win32_SubDirectory.GroupComponent ?_1_dir .
                    ?_2_assoc_dir cimv2:Win32_SubDirectory.PartComponent ?_3_subdir .
                    ?_3_subdir cimv2:Win32_Directory.FileName ?dir_name .
                    ?_3_subdir cimv2:Win32_Directory.Archive ?dir_archive .
                    ?_3_subdir cimv2:Win32_Directory.Compressed ?dir_compressed .
                    ?_3_subdir cimv2:Win32_Directory.Encrypted ?dir_encrypted .
                    ?_3_subdir cimv2:Win32_Directory.Hidden ?dir_hidden .
                    ?_3_subdir cimv2:Win32_Directory.Readable ?dir_readable .
                    ?_3_subdir cimv2:Win32_Directory.System ?dir_system .
                    ?_3_subdir cimv2:Win32_Directory.Writeable ?dir_writeable .
                }
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        for(RdfSolution.Tuple tuple : listRows) {
            System.out.println(tuple);
        }

        Function<String, Map<String, Boolean>> ToMapToBool = (String booleanAttribute) ->
                listRows.stream()
                        .collect(
                                Collectors
                                        .toMap(
                                                tp -> PresentUtils.trimQuotes(tp.GetAsLiteral("dir_name")),
                                                tp -> PresentUtils.XmlToBoolean(tp.GetAsLiteral(booleanAttribute))));

        Map<String, Boolean> mapNameToSystem = ToMapToBool.apply("dir_system");
        System.out.println("mapNameToSystem=" + mapNameToSystem);

        //Assert.assertFalse(mapNameToSystem.get("SystemTemp"));
        Assert.assertFalse(mapNameToSystem.get("logs"));
        Assert.assertFalse(mapNameToSystem.get("syswow64"));
        Assert.assertFalse(mapNameToSystem.get("system32"));

        //Assert.assertTrue(mapNameToSystem.get("downloaded program files"));
        Assert.assertTrue(mapNameToSystem.get("fonts"));
        Assert.assertTrue(mapNameToSystem.get("media"));
        //Assert.assertTrue(mapNameToSystem.get("installer"));

        Map<String, Boolean> mapNameToWritable = ToMapToBool.apply("dir_writeable");
        System.out.println("mapNameToWritable=" + mapNameToWritable);

        Assert.assertFalse(mapNameToWritable.get("media"));
        Assert.assertFalse(mapNameToWritable.get("printdialog"));
        Assert.assertFalse(mapNameToWritable.get("fonts"));
        Assert.assertFalse(mapNameToWritable.get("microsoft.net"));

        Assert.assertTrue(mapNameToWritable.get("syswow64"));
        Assert.assertTrue(mapNameToWritable.get("system"));
        Assert.assertTrue(mapNameToWritable.get("winsxs"));
        Assert.assertTrue(mapNameToWritable.get("system32"));
    }

    /** Select system directories, and checks that boolean constant values can be used in a query.
     * */
    @Test
    public void testSelect_Win32_Directory_SelectSystem() throws Exception {
        String sparqlQuery = """
                prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                select ?dir_name
                where
                {
                    ?_1_dir cimv2:Win32_Directory.Name "C:\\\\WINDOWS" .
                    ?_2_assoc_dir cimv2:Win32_SubDirectory.GroupComponent ?_1_dir .
                    ?_2_assoc_dir cimv2:Win32_SubDirectory.PartComponent ?_3_subdir .
                    ?_3_subdir cimv2:Win32_Directory.FileName ?dir_name .
                    ?_3_subdir cimv2:Win32_Directory.System "1"^^xsd:boolean .
                }
                """;

        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        for(RdfSolution.Tuple tuple : listRows) {
            System.out.println(tuple);
        }

        Set<String> setDirs = listRows.StringValuesSet("dir_name");
        System.out.println("setDirs=" + setDirs);

        Assert.assertFalse(setDirs.contains("SystemTemp"));
        Assert.assertFalse(setDirs.contains("logs"));
        Assert.assertFalse(setDirs.contains("syswow64"));
        Assert.assertFalse(setDirs.contains("system32"));

        //Assert.assertTrue(setDirs.contains("downloaded program files"));
        Assert.assertTrue(setDirs.contains("fonts"));
        Assert.assertTrue(setDirs.contains("media"));
        //Assert.assertTrue(setDirs.contains("installer"));
    }

    /** Union of directories in C:\Windows, system dirs and non-system ones.
     * The union must be the whole list of directories there.
     * This is an arbitrary test but the result is easy to check.
     * The intention is to check that union works.
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Directory_Filter_Union() throws Exception {
        String sparqlQuery = """
                prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                select ?dir_name where
                {
                    {
                        select ?dir_name where
                        {
                            ?_1_dir cimv2:Win32_Directory.Name "C:\\\\WINDOWS" .
                            ?_2_assoc_dir cimv2:Win32_SubDirectory.GroupComponent ?_1_dir .
                            ?_2_assoc_dir cimv2:Win32_SubDirectory.PartComponent ?_3_subdir .
                            ?_3_subdir cimv2:Win32_Directory.FileName ?dir_name .
                            ?_3_subdir cimv2:Win32_Directory.System "0"^^xsd:boolean .
                        }
                    }
                    union
                    {
                        select ?dir_name where
                        {
                            ?_1_dir cimv2:Win32_Directory.Name "C:\\\\WINDOWS" .
                            ?_2_assoc_dir cimv2:Win32_SubDirectory.GroupComponent ?_1_dir .
                            ?_2_assoc_dir cimv2:Win32_SubDirectory.PartComponent ?_3_subdir .
                            ?_3_subdir cimv2:Win32_Directory.FileName ?dir_name .
                            ?_3_subdir cimv2:Win32_Directory.System "1"^^xsd:boolean .
                        }
                    }
                }
                """;

            RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

            Set<String> actualDirNames = listRows.StringValuesSet("dir_name").stream().map(s -> s.toUpperCase()).collect(Collectors.toSet());
            ArrayList<String> actualDirNamesArray = new ArrayList<>(actualDirNames);
            Collections.sort(actualDirNamesArray);
            System.out.println("actualDirNamesArray=" + actualDirNamesArray);

            Path directory = Paths.get("C:\\WINDOWS");

            Set<String> expectedDirNames = Files.walk(directory, 1).filter(entry -> !entry.equals(directory))
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString().toUpperCase())
                    .collect(Collectors.toSet());
            ArrayList<String> expectedDirNamesArray = new ArrayList<>(expectedDirNames);
            Collections.sort(expectedDirNamesArray);
            System.out.println("expectedDirNamesArray=" + expectedDirNamesArray);

            Assert.assertTrue(actualDirNamesArray.contains("SYSTEM"));
            Assert.assertEquals(expectedDirNamesArray, actualDirNamesArray);
        }

        /** This selects the current process with an integer constant in ProcessId. */
        @Test
        public void testSelect_Win32_Process_Constant_ProcessId() throws Exception {
            String sparqlQuery = String.format("""
                        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                        select ?handle
                        where {
                            ?process cimv2:Win32_Process.ProcessId "%s"^^xsd:integer .
                            ?process cimv2:Win32_Process.Handle ?handle .
                        }
                    """, currentPidStr);
            RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

            Set<String> setHandles = listRows.StringValuesSet("handle");
            System.out.println("setHandles=" + setHandles);
            Assert.assertEquals(Set.of(currentPidStr), setHandles);
        }

        /** This selects the current process with an integer constant in ParentProcessId. */
        @Test
        public void testSelect_Win32_Process_Constant_ParentProcessId() throws Exception {
            String parentPid = PresentUtils.ParentProcessId();
            String sparqlQuery = String.format("""
                            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                            select ?handle
                            where {
                                ?process cimv2:Win32_Process.ParentProcessId "%s"^^xsd:integer .
                                ?process cimv2:Win32_Process.Handle ?handle .
                            }
                        """, parentPid);
            RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

            Set<String> setHandles = listRows.StringValuesSet("handle");
            System.out.println("setHandles=" + setHandles);
            Assert.assertTrue(setHandles.contains(currentPidStr));
        }




        /*
        TODO: Recursive search of files and sub-directories.
        TODO: Size of the dlls of the current process.
        TODO: Sum of the CPU of processes running a specific program.
        TODO: Sum of the CPU of processes using a specific DLL.
         */
    }