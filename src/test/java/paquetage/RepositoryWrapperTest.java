package paquetage;

import junit.framework.TestCase;
import org.apache.commons.text.CaseUtils;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.File;
import java.time.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** This tests Sparql selection from a repository containing the ontology plus the result of a WQL selection. */
public class RepositoryWrapperTest extends TestCase {
    static long currentPid = ProcessHandle.current().pid();
    static String currentPidStr = String.valueOf(currentPid);

    private RepositoryWrapper repositoryWrapper = null;

    protected void setUp() throws Exception {
        repositoryWrapper = RepositoryWrapper.CreateSailRepositoryFromMemory();
        Assert.assertTrue(repositoryWrapper.IsValid());
    }


    @Override
    protected void tearDown() throws Exception {
        repositoryWrapper = null;
    }

    /** This tests that at least some triples are there, coming from the ontology. */
    @Test
    public void testSelectAnyTriples() throws Exception {
        String sparqlQuery = "SELECT ?x WHERE { ?x ?y ?z } limit 10";
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(10, listRows.size());
        GenericProvider.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("x"), singleRow.KeySet());
    }

    /** This tests the presence of element from the ontology. */
    @Test
    public void testSelectFromOntology() throws Exception {
        String sparqlQuery = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?label
                    where {
                        cim:Win32_Process.Handle rdfs:label ?label .
                    }
                """;
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericProvider.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("label"), singleRow.KeySet());
    }

    @Test
    public void testSelect_Win32_Process_WithClass_WithoutOntology() throws Exception {
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericProvider.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("caption", "process"), singleRow.KeySet());
    }

    @Test
    public void testSelect_Win32_Process_WithClass_WithOntology() throws Exception {
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericProvider.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("caption", "label"), singleRow.KeySet());
    }

    /** Also select the attribute ProcessId which is an integer.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Process_WithoutClass_WithoutOntology() throws Exception {
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericProvider.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("process", "pid"), singleRow.KeySet());
        // "18936"^^<http://www.w3.org/2001/XMLSchema#long>
        Assert.assertEquals(PresentUtils.ToXml(currentPid), singleRow.GetStringValue("pid"));
    }

    /** Selects all properties of a process.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Process_AllProperties() throws Exception {
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        // Only one row because pids are unique.
        Assert.assertEquals(1, listRows.size());
        GenericProvider.Row singleRow = listRows.get(0);

        // To be sure, checks the presence of all properties as extracted from the ontology.
        WmiProvider.WmiClass cl = new WmiProvider().Classes().get("Win32_Process");
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
        Assert.assertEquals(PresentUtils.ToXml(currentPid), singleRow.GetStringValue("processid"));
    }

    /** Get all attributes of Win32_Process.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Process_AllAttributesOntology() throws Exception {
        String sparqlQuery = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?property_label
                    where {
                        ?property rdfs:domain cim:Win32_Process .
                        ?property rdfs:label ?property_label .
                    }
                """;
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        Set<String> setLabels = listRows.stream().map(row->row.GetStringValue("property_label")).collect(Collectors.toSet());
        // Checks the presence of an arbitrary property.
        System.out.println("setLabels=" + setLabels);
        Assert.assertTrue(setLabels.contains("\"Win32_Process.VirtualSize\""));

        // Transforms '"Win32_Process.VirtualSize"' into 'VirtualSize'
        Set<String> shortLabels = setLabels.stream()
                .map(fullProperty -> fullProperty.substring(1,fullProperty.length()-1).split("\\.")[1])
                .collect(Collectors.toSet());

        // To be sure, checks the presence of all properties as extracted from the ontology.
        WmiProvider.WmiClass cl = new WmiProvider().Classes().get("Win32_Process");
        Set<String> allProperties = cl.Properties.keySet();
        Assert.assertEquals(shortLabels, allProperties);
    }

    @Test
    public void testSelect_Win32_Process_WithoutClass_WithOntology() throws Exception {
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericProvider.Row singleRow = listRows.get(0);
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
    public void testSelect_CIM_ProcessExecutable_NoFilter() throws Exception {
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        //Assert.assertEquals(1, listRows.size());
        GenericProvider.Row singleRow = listRows.get(0);
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
    public void testSelect_CIM_ProcessExecutable_Filter() throws Exception {
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        GenericProvider.Row singleRow = listRows.get(0);
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
    public void testSelect_CIM_ProcessExecutable_Assoc() throws Exception {
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        //Assert.assertEquals(1, listRows.size());
        Assert.assertTrue(listRows.size() > 0);
        GenericProvider.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("_3_file"), singleRow.KeySet());
        System.out.println("Exec=" + singleRow.GetStringValue("_3_file"));
    }

    /** This selects all Win32_UserAccount. The current account must be there.
     *
     */
    @Test
    public void testSelect_Win32_UserAccount() throws Exception {
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        GenericProvider.Row singleRow = listRows.get(0);
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
        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        // The current user is at least in one group.
        Assert.assertTrue(listRows.size() > 0);
        GenericProvider.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("group_name"), singleRow.KeySet());

        Set<String> setGroups = listRows.stream().map(row -> row.GetStringValue("group_name")).collect(Collectors.toSet());
        // A user is always in this group.
        Assert.assertTrue(setGroups.contains("\"Users\""));
    }

    /***
     * Volume of a given directory.
     */
    @Test
    public void testSelect_Win32_Volume() throws Exception {
        String sparqlQuery = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?device_id
                    where {
                        ?my3_volume cim:DriveLetter ?my_drive .
                        ?my3_volume cim:DeviceID ?device_id .
                        ?my3_volume rdf:type cim:Win32_Volume .
                        ?my0_dir rdf:type cim:Win32_Directory .
                        ?my0_dir cim:Name "C:\\\\Program Files (x86)" .
                        ?my0_dir cim:Drive ?my_drive .
                    }
                """;

        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        GenericProvider.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("device_id"), singleRow.KeySet());

        Set<String> setDevices = listRows.stream().map(row -> row.GetStringValue("device_id")).collect(Collectors.toSet());
        System.out.println("setDevices=" + setDevices);
        Assert.assertEquals(1, setDevices.size());
        // For example: "\\?\Volume{e88d2f2b-332b-4eeb-a420-20ba76effc48}\"
        Assert.assertTrue(setDevices.stream().findFirst().orElse("xyz").startsWith("\"\\\\?\\Volume{"));
    }

    /***
     * Mount point of a given directory.
     * The types of each instance are implicitly given in properties. Once only is enough.
     */
    @Test
    public void testSelect_Win32_MountPoint() throws Exception {
        String sparqlQuery = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_dir_name
                    where {
                        ?my3_dir cim:Win32_Directory.Name ?my_dir_name .
                        ?my2_assoc cim:Win32_MountPoint.Volume ?my1_volume .
                        ?my2_assoc cim:Directory ?my3_dir .
                        ?my1_volume cim:Win32_Volume.DriveLetter ?my_drive .
                        ?my1_volume cim:DeviceID ?device_id .
                        ?my0_dir cim:Name "C:\\\\Program Files (x86)" .
                        ?my0_dir cim:Win32_Directory.Drive ?my_drive .
                    }
                """;

        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        GenericProvider.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("my_dir_name"), singleRow.KeySet());

        Set<String> setDirs = listRows.stream().map(row -> row.GetStringValue("my_dir_name")).collect(Collectors.toSet());
        System.out.println("setDirs=" + setDirs);
        Assert.assertEquals(1, setDirs.size());
        Assert.assertEquals("\"C:\\\"", setDirs.stream().findFirst().orElse("xyz"));
    }

    /** Number of files in a directory.
     * Note: This is not needed to explicitly specify cim:CIM_DataFile in a Sparql statement like:
     *     ?my2_file rdf:type cim:CIM_DataFile .
     * This is because CIM_DirectoryContainsFile.PartComponent point to CIM_DataFile only.
     *
     * @throws Exception
    ?my2_file rdf:type cim:CIM_DataFile .
     */
    @Test
    public void testSelect_CIM_DataFile_Count() throws Exception {
        String sparqlQuery = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select (COUNT(?my2_file) as ?count_files)
                    where {
                        ?my1_assoc cim:CIM_DirectoryContainsFile.PartComponent ?my2_file .
                        ?my1_assoc cim:GroupComponent ?my0_dir .
                        ?my0_dir cim:Win32_Directory.Name "C:\\\\Windows" .
                    } group by ?my0_dir
                """;

        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        GenericProvider.Row singleRow = listRows.get(0);
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
        String countStr = "\"" + countFilesExpected + "\"^^<http://www.w3.org/2001/XMLSchema#integer>";
        System.out.println("countFilesExpected=" + Integer.toString(countFilesExpected));

        String countActual = singleRow.GetStringValue("count_files");
        System.out.println("count_files=" + singleRow.GetStringValue("count_files"));
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
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select (MIN(?file_size) as ?size_min) (MAX(?file_size) as ?size_max) (xsd:long(SUM(?file_size)) as ?size_sum)
                    where {
                        ?my2_file cim:CIM_DataFile.FileSize ?file_size .
                        ?my1_assoc cim:CIM_DirectoryContainsFile.PartComponent ?my2_file .
                        ?my1_assoc cim:GroupComponent ?my0_dir .
                        ?my0_dir cim:Win32_Directory.Name "C:\\\\Windows" .
                    } group by ?my0_dir
                """;

        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        System.out.println("listRows=" + listRows);
        Assert.assertTrue(listRows.size() > 0);
        GenericProvider.Row singleRow = listRows.get(0);

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
        Assert.assertEquals(PresentUtils.ToXml(expectedFileMin), singleRow.GetStringValue("size_min"));
        Assert.assertEquals(PresentUtils.ToXml(expectedFileMax), singleRow.GetStringValue("size_max"));
        Assert.assertEquals(PresentUtils.ToXml(expectedFileSum), singleRow.GetStringValue("size_sum"));
    }

    XMLGregorianCalendar ToXMLGregorianCalendar(String theDate) throws Exception {
        // https://docs.microsoft.com/en-us/windows/win32/wmisdk/cim-datetime
        // yyyymmddHHMMSS.mmmmmmsUUU
        // "20220720101048.502446+060"

        // '"2022-07-20"^^<http://www.w3.org/2001/XMLSchema#date>'
        DatatypeFactory dataTypeFactory = DatatypeFactory.newInstance();

        String regex = "\"(.*)\".*";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(theDate);
        matcher.find();
        String dateOnly = matcher.group(1);

        //System.out.println("dateOnly=" + dateOnly);

        XMLGregorianCalendar xmlDate = dataTypeFactory.newXMLGregorianCalendar(dateOnly);
        System.out.println("xmlDate=" + xmlDate);
        return xmlDate;
    }

    /** Startup time of current process.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_Win32_Process_CurrentCreationDate() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?creation_date
                    where {
                        ?my_process cim:Win32_Process.CreationDate ?creation_date .
                        ?my_process cim:Win32_Process.Handle "%s" .
                    }
                """, currentPidStr);

        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() == 1);
        GenericProvider.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("creation_date"), singleRow.KeySet());

        String minCreationDate = singleRow.GetStringValue("creation_date");
        System.out.println("minCreationDate=" + minCreationDate);

        XMLGregorianCalendar xmlDate = ToXMLGregorianCalendar(minCreationDate);
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
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select (MIN(?creation_date) as ?min_creation_date)
                    where {
                        ?my_process cim:Win32_Process.CreationDate ?creation_date .
                    } group by ?my_process
                """;

        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        GenericProvider.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("min_creation_date"), singleRow.KeySet());

        String minCreationDate = singleRow.GetStringValue("min_creation_date");
        System.out.println("minCreationDate=" + minCreationDate);

        XMLGregorianCalendar xmlDate = ToXMLGregorianCalendar(minCreationDate);
        ZonedDateTime zonedDateTimeActual = xmlDate.toGregorianCalendar().toZonedDateTime();
        LocalDateTime localDateTimeActual = zonedDateTimeActual.toLocalDateTime();
        Instant asInstantActual = zonedDateTimeActual.toInstant();

        // Now calculate the same time.
        List<Instant> instantsList = ProcessHandle.allProcesses().map(ph -> ph.info().startInstant().orElse(null)).toList();
        Instant minStartExpected = null;
        System.out.println("Instants:" + instantsList.size());
        for(Instant instant: instantsList) {
            if(instant != null) {
                if (minStartExpected == null || minStartExpected.isAfter(instant)) {
                    minStartExpected = instant;
                }
            }
        }

        LocalDateTime minStartDate = LocalDateTime.ofInstant(minStartExpected, ZoneId.systemDefault());
        System.out.println("Expect:" + minStartDate);
        System.out.println("Actuel:" + localDateTimeActual);

        /** Apparently, the hand-made loop misses some processes, so we do a rounding to 10 seconds.
         * Expected :2022-07-20T08:56:40.199Z
         * Actual   :2022-07-20T08:56:41.672Z
         */
        Assert.assertEquals(asInstantActual.toEpochMilli() / 10000, minStartExpected.toEpochMilli() / 10000);

        // Assert.assertEquals(asInstantActual, minStartExpected);
        Assert.assertTrue(asInstantActual.isBefore(minStartExpected));
    }


    /** Most used modules of the current process.
     * This takes the list of all modules used by the current process and find the one which is the most shared
     * by other processes.
     *
     * @throws Exception
     */
    @Test
    public void testSelect_MostUsedModule() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select (MAX(?my_inusecount) as ?max_inusecount)
                    where {
                        ?my1_process cim:Win32_Process.Handle "%s" .
                        ?my2_assoc cim:CIM_ProcessExecutable.Dependent ?my1_process .
                        ?my2_assoc cim:CIM_ProcessExecutable.Antecedent ?my3_file .
                        ?my3_file  cim:CIM_DataFile.Name ?my_filename .
                        ?my3_file  cim:CIM_DataFile.InUseCount ?my_inusecount .
                    } group by ?my2_assoc
                """, currentPidStr);

        List<GenericProvider.Row> listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);

        Assert.assertTrue(listRows.size() > 0);
        GenericProvider.Row singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("max_inusecount"), singleRow.KeySet());

        String maxInUseCount = singleRow.GetStringValue("max_inusecount");
        System.out.println("maxInUseCount=" + maxInUseCount);

        Assert.assertTrue(false);
    }



    // InUseCount



    /*
    TODO: Recursive search of files and sub-directories.
    TODO: Size of the dlls of the current process.
    TODO: Sum of the CPU of processes running a specific program.
    TODO: Sum of the CPU of processes using a specific DLL.
    TODO: Replace "cim:" with "wmi:"
     */
}