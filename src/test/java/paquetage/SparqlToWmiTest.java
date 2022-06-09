package paquetage;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;


public class SparqlToWmiTest {
    String pidString = String.valueOf(ProcessHandle.current().pid());

    static Set<String> RowColumnAsSet(ArrayList<MetaSelecter.Row> rowsList, String columnName) {
        return rowsList
                .stream()
                .map(entry -> entry.Elements.get(columnName)).collect(Collectors.toSet());
    }

    static Set<String> RowColumnAsSetUppercase(ArrayList<MetaSelecter.Row> rowsList, String columnName) {
        return rowsList
                .stream()
                .map(entry -> entry.Elements.get(columnName).toUpperCase()).collect(Collectors.toSet());
    }

    @Test
    public void Execution_Win32_Process_1() throws Exception {
        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_process_handle
                    where {
                        ?my_process rdf:type cim:Win32_Process .
                        ?my_process cim:Handle ?my_process_handle .
                    }
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_handle"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();
        boolean foundCurrentPid = false;
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);
        for (MetaSelecter.Row row : the_rows) {
            if (row.Elements.get("my_process_handle").equals(pidString)) {
                foundCurrentPid = true;
                break;
            }
        }
        Assert.assertTrue(foundCurrentPid);
    }

    @Test
    public void Execution_Win32_Process_2() throws Exception {
        String sparql_query = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_process_caption
                    where {
                        ?my_process rdf:type cim:Win32_Process .
                        ?my_process cim:Caption ?my_process_caption .
                        ?my_process cim:Handle "%s" .
                    }
                """, pidString);
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_caption"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();
        Assert.assertEquals(1, the_rows.size());
        Assert.assertEquals("java.exe", the_rows.get(0).Elements.get("my_process_caption"));
    }

    @Test
    public void Execution_Forced_CIM_ProcessExecutable_1() throws Exception {
        String sparql_query = String.format("""
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file_name
                    where {
                        ?my1_assoc rdf:type cim:CIM_ProcessExecutable .
                        ?my1_assoc cim:Dependent ?my0_process .
                        ?my1_assoc cim:Antecedent ?my2_file .
                        ?my0_process rdf:type cim:Win32_Process .
                        ?my0_process cim:Handle "%s" .
                        ?my2_file rdf:type cim:CIM_DataFile .
                        ?my2_file cim:Name ?my_file_name .
                    }
                """, pidString);

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();
        Set<String> libsSet = RowColumnAsSet(the_rows, "my_file_name");
        // This tests the presence of some libraries which are used by the current Java process.
        Assert.assertTrue(libsSet.contains("C:\\Program Files\\Java\\jdk-17.0.2\\bin\\java.exe"));
        Assert.assertTrue(libsSet.contains("C:\\WINDOWS\\SYSTEM32\\ntdll.dll"));
        Assert.assertTrue(libsSet.contains("C:\\WINDOWS\\System32\\USER32.dll"));
    }

    @Test
    /**
     * The current pid must be found in the processes using a specific library.
     * The order of evaluation, i.e. the order of object patterns, is forced with the alphabetical order
     * of main variables.
     *
     * TODO: This test might fail is a process unexpectedly leaves in the middle of the query.
     */
    public void Execution_Forced_CIM_ProcessExecutable_2() throws Exception {
        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_handle
                    where {
                        ?my1_assoc rdf:type cim:CIM_ProcessExecutable .
                        ?my1_assoc cim:Dependent ?my2_process .
                        ?my1_assoc cim:Antecedent ?my0_file .
                        ?my2_process rdf:type cim:Win32_Process .
                        ?my2_process cim:Handle ?my_handle .
                        ?my0_file rdf:type cim:CIM_DataFile .
                        ?my0_file cim:Name "C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_handle"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();

        // The current pid must be there because it uses this library.
        Set<String> libsSet = RowColumnAsSet(the_rows, "my_handle");
        Assert.assertTrue(libsSet.contains(pidString));
    }

    @Test
    /**
     * This gets the handle and caption of all processes using a specific library.
     * The current pid must be found in the processes using a specific library.
     * This checks that two columns are properly returned.
     * The order of evaluation, i.e. the order of object patterns, is forced with the alphabetical order
     * of main variables.
     */
    public void Execution_Forced_CIM_ProcessExecutable_3() throws Exception {
        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_handle ?my_caption
                    where {
                        ?my1_assoc rdf:type cim:CIM_ProcessExecutable .
                        ?my1_assoc cim:Dependent ?my2_process .
                        ?my1_assoc cim:Antecedent ?my0_file .
                        ?my2_process rdf:type cim:Win32_Process .
                        ?my2_process cim:Handle ?my_handle .
                        ?my2_process cim:Caption ?my_caption .
                        ?my0_file rdf:type cim:CIM_DataFile .
                        ?my0_file cim:Name "C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_caption", "my_handle"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();

        // The current caption must be there because it uses this library.
        Set<String> captionsSet = RowColumnAsSet(the_rows, "my_caption");
        Assert.assertTrue(captionsSet.contains("java.exe"));

        // The current pid must be there because it uses this library.
        Set<String> libsSet = RowColumnAsSet(the_rows, "my_handle");
        Assert.assertTrue(libsSet.contains(pidString));
    }

    @Test
    /***
     * This gets the parent-directory of a file.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_1() throws Exception {
        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_dir_name
                    where {
                        ?my1_assoc rdf:type cim:CIM_DirectoryContainsFile .
                        ?my1_assoc cim:GroupComponent ?my2_dir .
                        ?my1_assoc cim:PartComponent ?my0_file .
                        ?my2_dir rdf:type cim:Win32_Directory .
                        ?my2_dir cim:Name ?my_dir_name .
                        ?my0_file rdf:type cim:CIM_DataFile .
                        ?my0_file cim:Name "C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_dir_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();
        Assert.assertEquals(the_rows.size(), 1);
        // Filename cases are not stable wrt Windows version.
        Assert.assertEquals(the_rows.get(0).Elements.get("my_dir_name").toUpperCase(), "C:\\WINDOWS\\SYSTEM32");
    }

    @Test
    /**
     * This gets the list of files in a directory.
     * Possibly because accessing CIM_DataFile is slow anyway.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_2() throws Exception {
        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file_name
                    where {
                        ?my1_assoc rdf:type cim:CIM_DirectoryContainsFile .
                        ?my1_assoc cim:GroupComponent ?my0_dir .
                        ?my1_assoc cim:PartComponent ?my2_file .
                        ?my0_dir rdf:type cim:Win32_Directory .
                        ?my0_dir cim:Name "C:\\\\WINDOWS\\\\SYSTEM32" .
                        ?my2_file rdf:type cim:CIM_DataFile .
                        ?my2_file cim:Name ?my_file_name .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();

        // These files must be in this directory.
        // Filename cases in system directories are not stable, therefore uppercase.
        Set<String> filesSet = RowColumnAsSetUppercase(the_rows, "my_file_name");
        Assert.assertTrue(filesSet.contains("C:\\WINDOWS\\SYSTEM32\\NTDLL.DLL"));
        Assert.assertTrue(filesSet.contains("C:\\WINDOWS\\SYSTEM32\\USER32.DLL"));
    }

    @Test
    /***
     * This gets the parent-parent-directory of a file.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_Win32_SubDirectory_1() throws Exception {
        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_dir_name
                    where {
                        ?my0_file rdf:type cim:CIM_DataFile .
                        ?my0_file cim:Name "C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll" .
                        ?my1_assoc rdf:type cim:CIM_DirectoryContainsFile .
                        ?my1_assoc cim:PartComponent ?my0_file .
                        ?my1_assoc cim:GroupComponent ?my2_dir .
                        ?my2_dir rdf:type cim:Win32_Directory .
                        ?my3_assoc rdf:type cim:Win32_SubDirectory .
                        ?my3_assoc cim:PartComponent ?my2_dir .
                        ?my3_assoc cim:GroupComponent ?my4_dir .
                        ?my4_dir rdf:type cim:Win32_Directory .
                        ?my4_dir cim:Name ?my_dir_name .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_dir_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();
        Assert.assertEquals(the_rows.size(), 1);
        // Case of filenames are not stable wrt Windows version.
        Assert.assertEquals(the_rows.get(0).Elements.get("my_dir_name").toUpperCase(), "C:\\WINDOWS");
    }

    @Test
    /**
     * This gets sub-sub-files of a directory.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_Win32_SubDirectory_2() throws Exception {
        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file_name
                    where {
                        ?my4_file rdf:type cim:CIM_DataFile .
                        ?my4_file cim:Name ?my_file_name .
                        ?my3_assoc rdf:type cim:CIM_DirectoryContainsFile .
                        ?my3_assoc cim:PartComponent ?my4_file .
                        ?my3_assoc cim:GroupComponent ?my2_dir .
                        ?my2_dir rdf:type cim:Win32_Directory .
                        ?my1_assoc rdf:type cim:Win32_SubDirectory .
                        ?my1_assoc cim:PartComponent ?my2_dir .
                        ?my1_assoc cim:GroupComponent ?my0_dir .
                        ?my0_dir rdf:type cim:Win32_Directory .
                        ?my0_dir cim:Name "C:\\\\Program Files\\\\Internet Explorer" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();

        // These files must be in this directory.
        Set<String> filesSet = RowColumnAsSet(the_rows, "my_file_name");
        Assert.assertTrue(filesSet.contains("C:\\Program Files\\Internet Explorer\\en-US\\hmmapi.dll.mui"));
        Assert.assertTrue(filesSet.contains("C:\\Program Files\\Internet Explorer\\images\\bing.ico"));
    }

    @Test
    /**
     * This gets the grandparent of the sub-sub-files of a directory. It must be the same.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_Win32_SubDirectory_3() throws Exception {
        String sparql_query = """
                    prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_dir_name
                    where {
                        ?my8_dir rdf:type cim:Win32_Directory .
                        ?my8_dir cim:Name ?my_dir_name .
                        ?my7_assoc rdf:type cim:Win32_SubDirectory .
                        ?my7_assoc cim:PartComponent ?my6_dir .
                        ?my7_assoc cim:GroupComponent ?my8_dir .
                        ?my6_dir rdf:type cim:Win32_Directory .
                        ?my5_assoc rdf:type cim:CIM_DirectoryContainsFile .
                        ?my5_assoc cim:PartComponent ?my4_file .
                        ?my5_assoc cim:GroupComponent ?my6_dir .
                        ?my4_file rdf:type cim:CIM_DataFile .
                        ?my3_assoc rdf:type cim:CIM_DirectoryContainsFile .
                        ?my3_assoc cim:PartComponent ?my4_file .
                        ?my3_assoc cim:GroupComponent ?my2_dir .
                        ?my2_dir rdf:type cim:Win32_Directory .
                        ?my1_assoc rdf:type cim:Win32_SubDirectory .
                        ?my1_assoc cim:PartComponent ?my2_dir .
                        ?my1_assoc cim:GroupComponent ?my0_dir .
                        ?my0_dir rdf:type cim:Win32_Directory .
                        ?my0_dir cim:Name "C:\\\\Program Files\\\\Internet Explorer" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_dir_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();

        // It must fall back to the initial directory.
        Set<String> dirsSet = RowColumnAsSet(the_rows, "my_dir_name");
        Assert.assertEquals(1, dirsSet.size());
        Assert.assertTrue(dirsSet.contains("C:\\Program Files\\Internet Explorer"));
    }

    @Test
    /**
     * This gets the directories of all executables and libraries used by running processes.
     * The order of evaluation is forced with the alphabetical order of main variables.
     */
    public void Execution_Forced_CIM_ProcessExecutable_CIM_DirectoryContainsFile_1() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_dir_name
            where {
                ?my0_process rdf:type cim:Win32_Process .
                ?my1_assoc rdf:type cim:CIM_ProcessExecutable .
                ?my1_assoc cim:Dependent ?my0_process .
                ?my1_assoc cim:Antecedent ?my2_file .
                ?my2_file rdf:type cim:CIM_DataFile .
                ?my3_assoc rdf:type cim:CIM_DirectoryContainsFile .
                ?my3_assoc cim:PartComponent ?my2_file .
                ?my3_assoc cim:GroupComponent ?my4_dir .
                ?my4_dir rdf:type cim:Win32_Directory .
                ?my4_dir cim:Name ?my_dir_name .
            }
        """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_dir_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();
        System.out.println("Rows number=" + the_rows.size());

        /*
        Beware that filename cases are not stable. WMI returns for example:
        "C:\WINDOWS\System32", "c:\windows\system32", "C:\WINDOWS\system32"
         */
        Set<String> dirsSet = RowColumnAsSetUppercase(the_rows, "my_dir_name");
        Assert.assertTrue(dirsSet.contains("C:\\WINDOWS"));
        Assert.assertTrue(dirsSet.contains("C:\\WINDOWS\\SYSTEM32"));
    }

    @Test
    /**
     * Names of processes which have an executable or a library in a given directory.
     */
    public void Execution_Forced_CIM_ProcessExecutable_CIM_DirectoryContainsFile_2() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_process_name
            where {
                ?my4_process rdf:type cim:Win32_Process .
                ?my4_process cim:Name ?my_process_name .
                ?my3_assoc rdf:type cim:CIM_ProcessExecutable .
                ?my3_assoc cim:Dependent ?my4_process .
                ?my3_assoc cim:Antecedent ?my2_file .
                ?my2_file rdf:type cim:CIM_DataFile .
                ?my1_assoc rdf:type cim:CIM_DirectoryContainsFile .
                ?my1_assoc cim:PartComponent ?my2_file .
                ?my1_assoc cim:GroupComponent ?my0_dir .
                ?my0_dir rdf:type cim:Win32_Directory .
                ?my0_dir cim:Name "C:\\\\Program Files\\\\Java\\\\jdk-17.0.2\\\\bin" .
            }
        """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();
        System.out.println("Rows number=" + the_rows.size());

        Set<String> namesSet = RowColumnAsSet(the_rows, "my_process_name");
        for(String oneName: namesSet) {
            System.out.println("Name=" + oneName);
        }
        Assert.assertTrue(namesSet.contains("java.exe"));
    }


    /**
     Look for the volume that disk "C:" is mapped to.
     __PATH           : \\LAPTOP-R89KG6V1\root\cimv2:Win32_MountPoint.Directory="Win32_Directory.Name=\"C:\\\\\"",Volume="Win32_Volume.DeviceID=\"\\\\\\\\?\\\\Volume{e88d2f2b-332b-4eeb-a420-20ba76effc48}\\\\\""
     Directory        : Win32_Directory.Name="C:\\"
     Volume           : Win32_Volume.DeviceID="\\\\?\\Volume{e88d2f2b-332b-4eeb-a420-20ba76effc48}\\"

     Get-WmiObject -Query 'Select * from Win32_MountPoint where Directory = "Win32_Directory.Name=\"C:\\\\\""'
     */
    @Test
    public void Execution_Forced_Win32_MountPoint_1() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?device_id
            where {
                ?my2_volume cim:DeviceID ?device_id .
                ?my2_volume rdf:type cim:Win32_Volume .
                ?my1_assoc rdf:type cim:Win32_MountPoint .
                ?my1_assoc cim:Volume ?my2_volume .
                ?my1_assoc cim:Directory ?my0_dir .
                ?my0_dir rdf:type cim:Win32_Directory .
                ?my0_dir cim:Name "C:\\\\" .
            }
        """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("device_id"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();

        // Disk "C:" must be found.
        Set<String> volumesSet = RowColumnAsSet(the_rows, "device_id");
        for(String deviceId: volumesSet) {
            System.out.println("DeviceId=" + deviceId);
        }
        Assert.assertTrue(volumesSet.size() > 0);
    }

    /***
     * Look for mounted volumes.
     */
    @Test
    public void Execution_Forced_Win32_MountPoint_2() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name
            where {
                ?my0_assoc rdf:type cim:Win32_MountPoint .
                ?my0_assoc cim:Directory ?my1_dir .
                ?my1_dir rdf:type cim:Win32_Directory .
                ?my1_dir cim:Name ?dir_name .
            }
        """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("dir_name"));

        SparqlToWmi patternSparql = new SparqlToWmi(extractor);
        ArrayList<MetaSelecter.Row> the_rows = patternSparql.Execute();

        // Disk "C:" must be found.
        Set<String> dirsSet = RowColumnAsSet(the_rows, "dir_name");
        for(String oneName: dirsSet) {
            System.out.println("Dir=" + dirsSet);
        }
        Assert.assertTrue(dirsSet.size() > 0);
        Assert.assertTrue(dirsSet.contains("C:\\"));
    }



    /*
    TODO: Specify the path of an object.
     */
}
