package paquetage;

import junit.framework.TestCase;
import org.apache.commons.text.CaseUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** This tests Sparql selection from a repository containing the ontology plus the result of a WQL selection. */
public class RepositoryWrapperTest extends TestCase {
    static String currentPidStr = String.valueOf(ProcessHandle.current().pid());
    @Test
    public static void testFromMemory() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());
    }

    /** This tests that at least some triples are there, coming from the ontology. */
    @Test
    public static void testSelectAnyTriples() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = "SELECT ?x WHERE { ?x ?y ?z } limit 10";
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(10, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("x"), singleRow.KeySet());
    }

    /** This tests the presence of element from the ontology. */
    @Test
    public static void testSelectFromOntology() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?label
                    where {
                        cim:Win32_Process.Handle rdfs:label ?label .
                    }
                """;
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("label"), singleRow.KeySet());
    }

    @Test
    public static void testSelect_Win32_Process_WithClass_WithoutOntology() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?caption ?process
                    where {
                        ?process rdf:type cim:Win32_Process .
                        ?process cim:Handle "%s" .
                        ?process cim:Win32_Process.Caption ?caption .
                    }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("caption", "process"), singleRow.KeySet());
    }

    @Test
    public static void testSelect_Win32_Process_WithClass_WithOntology() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?label ?caption
                    where {
                        ?process rdf:type cim:Win32_Process .
                        ?process cim:Handle "%s" .
                        ?process cim:Win32_Process.Caption ?caption .
                        cim:Win32_Process.Handle rdfs:label ?label .
                    }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("caption", "label"), singleRow.KeySet());
    }

    /** Also select the attribute ProcessId which is an integer.
     *
     * @throws Exception
     */
    @Test
    public static void testSelect_Win32_Process_WithoutClass_WithoutOntology() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process ?pid
                    where {
                        ?process cim:Win32_Process.Handle "%s" .
                        ?process cim:Win32_Process.Caption ?caption .
                        ?process cim:Win32_Process.ProcessId ?pid .
                    }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("process", "pid"), singleRow.KeySet());
        Assert.assertEquals("\"" + currentPidStr + "\"", singleRow.GetStringValue("pid"));
    }

    /** Selects all properties of a process.
     *
     * @throws Exception
     */
    @Test
    public static void testSelect_Win32_Process_AllProperties() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?readoperationcount ?caption ?executablepath ?usermodetime ?peakworkingsetsize ?othertransfercount ?pagefileusage ?writetransfercount ?pagefaults ?readtransfercount ?maximumworkingsetsize ?quotapeakpagedpoolusage ?handlecount ?osname ?writeoperationcount ?sessionid ?otheroperationcount ?csname ?windowsversion ?description ?quotanonpagedpoolusage ?threadcount ?priority ?parentprocessid ?installdate ?commandline ?virtualsize ?quotapagedpoolusage ?creationdate ?terminationdate ?name ?peakvirtualsize ?privatepagecount ?executionstate ?peakpagefileusage ?cscreationclassname ?minimumworkingsetsize ?quotapeaknonpagedpoolusage ?status ?creationclassname ?oscreationclassname ?processid ?kernelmodetime ?workingsetsize
                    where {
                        ?process cim:Win32_Process.Handle "%s" .
                        ?process cim:Win32_Process.ReadOperationCount ?readoperationcount .
                        ?process cim:Win32_Process.Caption ?caption .
                        ?process cim:Win32_Process.ExecutablePath ?executablepath .
                        ?process cim:Win32_Process.UserModeTime ?usermodetime .
                        ?process cim:Win32_Process.PeakWorkingSetSize ?peakworkingsetsize .
                        ?process cim:Win32_Process.OtherTransferCount ?othertransfercount .
                        ?process cim:Win32_Process.PageFileUsage ?pagefileusage .
                        ?process cim:Win32_Process.WriteTransferCount ?writetransfercount .
                        ?process cim:Win32_Process.PageFaults ?pagefaults .
                        ?process cim:Win32_Process.ReadTransferCount ?readtransfercount .
                        ?process cim:Win32_Process.MaximumWorkingSetSize ?maximumworkingsetsize .
                        ?process cim:Win32_Process.QuotaPeakPagedPoolUsage ?quotapeakpagedpoolusage .
                        ?process cim:Win32_Process.HandleCount ?handlecount .
                        ?process cim:Win32_Process.OSName ?osname .
                        ?process cim:Win32_Process.WriteOperationCount ?writeoperationcount .
                        ?process cim:Win32_Process.SessionId ?sessionid .
                        ?process cim:Win32_Process.OtherOperationCount ?otheroperationcount .
                        ?process cim:Win32_Process.CSName ?csname .
                        ?process cim:Win32_Process.WindowsVersion ?windowsversion .
                        ?process cim:Win32_Process.Description ?description .
                        ?process cim:Win32_Process.QuotaNonPagedPoolUsage ?quotanonpagedpoolusage .
                        ?process cim:Win32_Process.ThreadCount ?threadcount .
                        ?process cim:Win32_Process.Priority ?priority .
                        ?process cim:Win32_Process.ParentProcessId ?parentprocessid .
                        ?process cim:Win32_Process.InstallDate ?installdate .
                        ?process cim:Win32_Process.CommandLine ?commandline .
                        ?process cim:Win32_Process.VirtualSize ?virtualsize .
                        ?process cim:Win32_Process.QuotaPagedPoolUsage ?quotapagedpoolusage .
                        ?process cim:Win32_Process.CreationDate ?creationdate .
                        ?process cim:Win32_Process.TerminationDate ?terminationdate .
                        ?process cim:Win32_Process.Name ?name .
                        ?process cim:Win32_Process.PeakVirtualSize ?peakvirtualsize .
                        ?process cim:Win32_Process.PrivatePageCount ?privatepagecount .
                        ?process cim:Win32_Process.ExecutionState ?executionstate .
                        ?process cim:Win32_Process.PeakPageFileUsage ?peakpagefileusage .
                        ?process cim:Win32_Process.CSCreationClassName ?cscreationclassname .
                        ?process cim:Win32_Process.MinimumWorkingSetSize ?minimumworkingsetsize .
                        ?process cim:Win32_Process.QuotaPeakNonPagedPoolUsage ?quotapeaknonpagedpoolusage .
                        ?process cim:Win32_Process.Status ?status .
                        ?process cim:Win32_Process.CreationClassName ?creationclassname .
                        ?process cim:Win32_Process.OSCreationClassName ?oscreationclassname .
                        ?process cim:Win32_Process.ProcessId ?processid .
                        ?process cim:Win32_Process.KernelModeTime ?kernelmodetime .
                        ?process cim:Win32_Process.WorkingSetSize ?workingsetsize . 
                   }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        // Only one row because pids are unique.
        Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);

        // To be sure, checks the presence of all properties as extracted from the ontology.
        WmiSelecter.WmiClass cl = new WmiSelecter().Classes().get("Win32_Process");
        Set<String> allProperties = cl.Properties.keySet();
        Set<String> propertiesCamelCase = allProperties.stream()
                .map(property -> CaseUtils.toCamelCase(property, false))
                        .collect(Collectors.toSet());
        // This is not selected because used as an input.
        // Do not remove it from the oroginal keys set !
        propertiesCamelCase.remove("handle");

        // All these values must be set.
        Assert.assertEquals(propertiesCamelCase, singleRow.KeySet());

        // Check the value of a property whose result is known.
        Assert.assertEquals("\"" + currentPidStr + "\"", singleRow.GetStringValue("processid"));
    }

    /** Get all attributes of Win32_Process.
     *
     * @throws Exception
     */
    @Test
    public static void testSelect_Win32_Process_AllAttributesOntology() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?property_label
                    where {
                        ?property rdfs:domain cim:Win32_Process .
                        ?property rdfs:label ?property_label .
                    }
                """;
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Set<String> setLabels = listRows.stream().map(row->row.GetStringValue("property_label")).collect(Collectors.toSet());
        // Checks the presence of an arbitrary property.
        System.out.println("setLabels=" + setLabels);
        Assert.assertTrue(setLabels.contains("\"Win32_Process.VirtualSize\""));

        // Transforms '"Win32_Process.VirtualSize"' into 'VirtualSize'
        Set<String> shortLabels = setLabels.stream()
                .map(fullProperty -> fullProperty.substring(1,fullProperty.length()-1).split("\\.")[1])
                .collect(Collectors.toSet());

        // To be sure, checks the presence of all properties as extracted from the ontology.
        WmiSelecter.WmiClass cl = new WmiSelecter().Classes().get("Win32_Process");
        Set<String> allProperties = cl.Properties.keySet();
        Assert.assertEquals(shortLabels, allProperties);
    }

    @Test
    public static void testSelect_Win32_Process_WithoutClass_WithOntology() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?label ?caption
                    where {
                        ?process cim:Win32_Process.Handle "%s" .
                        ?process cim:Win32_Process.Caption ?caption .
                        cim:Win32_Process.Handle rdfs:label ?label .
                    }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("caption", "label"), singleRow.KeySet());
    }

    /** Patterns order, therefore the order of queries, is forced with alphabetical order and no optimisation.
     * Optimising the object patterns implies changing their order with some constraints due
     * to variable dependencies.
     * With this, the test can focus on the results of the evaluation : This is simpler to test.
     *
     * @throws Exception
     */
    @Test
    public static void testSelect_CIM_ProcessExecutable_NoFilter() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?file_name
                    where {
                        ?_1_process rdf:type cim:Win32_Process .
                        ?_1_process cim:Handle "%s" .
                        ?_1_process cim:Win32_Process.Caption ?caption .
                        ?_2_assoc rdf:type cim:CIM_ProcessExecutable .
                        ?_2_assoc cim:Dependent ?_1_process .
                        ?_2_assoc cim:Antecedent ?_3_file .
                        ?_3_file rdf:type cim:CIM_DataFile .
                        ?_3_file cim:Name ?file_name .
                   }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        //Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("file_name"), singleRow.KeySet());
        System.out.println("Exec=" + singleRow.GetStringValue("file_name"));
        String expectedBin = "\"" + PresentUtils.CurrentJavaBinary() + "\"";
        Assert.assertEquals(expectedBin, singleRow.GetStringValue("file_name"));
    }

    /** Patterns order, therefore the order of queries, is forced with alphabetical order and no optimisation.
     * Optimising the object patterns implies changing their order with some constraints due
     * to variable dependencies.
     * With this, the test can focus on the results of the evaluation : This is simpler to test.
     *
     * @throws Exception
     */
    @Test
    public static void testSelect_CIM_ProcessExecutable_Filter() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?file_name
                    where {
                        ?_1_process rdf:type cim:Win32_Process .
                        ?_1_process cim:Handle "%s" .
                        ?_1_process cim:Win32_Process.Caption ?caption .
                        ?_2_assoc rdf:type cim:CIM_ProcessExecutable .
                        ?_2_assoc cim:Dependent ?_1_process .
                        ?_2_assoc cim:Antecedent ?_3_file .
                        ?_3_file rdf:type cim:CIM_DataFile .
                        ?_3_file cim:Name ?file_name .
                        filter(regex(?file_name, "java.exe", "i" )) 
                   }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("file_name"), singleRow.KeySet());
        System.out.println("Exec=" + singleRow.GetStringValue("file_name"));
        String expectedBin = "\"" + PresentUtils.CurrentJavaBinary() + "\"";
        Assert.assertEquals(expectedBin, singleRow.GetStringValue("file_name"));
    }

    /** Only gets the node of the associated instances.
     *
     * @throws Exception
     */
    @Test
    public static void testSelect_CIM_ProcessExecutable_Assoc() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?_3_file
                    where {
                        ?_1_process cim:Handle "%s" .
                        ?_1_process cim:Win32_Process.Caption ?caption .
                        ?_2_assoc cim:CIM_ProcessExecutable.Dependent ?_1_process .
                        ?_2_assoc cim:CIM_ProcessExecutable.Antecedent ?_3_file .
                        ?_3_file cim:CIM_DataFile.Name ?file_name .
                        filter(regex(?file_name, "java.exe", "i" )) 
                   }
                """, currentPidStr);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        //Assert.assertEquals(1, listRows.size());
        Assert.assertTrue(listRows.size() > 0);
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("_3_file"), singleRow.KeySet());
        System.out.println("Exec=" + singleRow.GetStringValue("_3_file"));
    }

    /** This selects all Win32_UserAccount. The current account must be there.
     *
     */
    @Test
    public static void testSelect_Win32_UserAccount() throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());

        String sparqlQuery = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?name ?domain
                    where {
                        ?user rdf:type cim:Win32_UserAccount .
                        ?user cim:Name ?name .
                        ?user cim:Domain ?domain .
                   }
                """;
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("name", "domain"), singleRow.KeySet());

        Set<String> setNames = listRows.stream().map(row -> row.GetStringValue("name")).collect(Collectors.toSet());
        String currentUser = System.getProperty("user.name");
        System.out.println("setNames=" + setNames);
        Assert.assertTrue(setNames.contains("\"Administrator\""));
        Assert.assertTrue(setNames.contains("\"Guest\""));
        Assert.assertTrue(setNames.contains("\"" + currentUser + "\""));
    }

    /** This selects the groups of the current user.
     *
     */
    @Test
    public static void testSelect_Win32_GroupUser () throws Exception {
        RepositoryWrapper repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());
        String currentUser = System.getProperty("user.name");

        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?group_name
                    where {
                        ?_1_user cim:Win32_UserAccount.Name "%s" .
                        ?_2_assoc cim:Win32_GroupUser.GroupComponent ?_3_group .
                        ?_2_assoc cim:PartComponent ?_1_user .
                        ?_3_group cim:Win32_Group.Name ?group_name .
                   }
                """, currentUser);
        List<GenericSelecter.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        // The current user is at least in one group.
        Assert.assertTrue(listRows.size() > 0);
        GenericSelecter.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("group_name"), singleRow.KeySet());

        Set<String> setGroups = listRows.stream().map(row -> row.GetStringValue("group_name")).collect(Collectors.toSet());
        // A user is always in this group.
        Assert.assertTrue(setGroups.contains("\"Users\""));
    }

}