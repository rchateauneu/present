package paquetage;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;


public class SparqlToWmiTest {
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
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        boolean foundCurrentPid = false;
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);
        for (WmiSelecter.Row row : the_rows) {
            if (row.Elements.get("my_process_handle").equals(pidString)) {
                foundCurrentPid = true;
                break;
            }
        }
        Assert.assertTrue(foundCurrentPid);
    }

    @Test
    public void Execution_Win32_Process_2() throws Exception {
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);

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
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        Assert.assertEquals(1, the_rows.size());
        Assert.assertEquals("java.exe", the_rows.get(0).Elements.get("my_process_caption"));
    }

    @Test
    public void Execution_Forced_CIM_ProcessExecutable_1() throws Exception {
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);

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
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        Set<String> libsSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_file_name")).collect(Collectors.toSet());
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
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);

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
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();

        Set<String> libsSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_handle")).collect(Collectors.toSet());
        // The current pid must be there because it uses this library.
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
        long pid = ProcessHandle.current().pid();
        String pidString = String.valueOf(pid);

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
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();

        Set<String> captionsSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_caption")).collect(Collectors.toSet());
        // The current caption must be there because it uses this library.
        Assert.assertTrue(captionsSet.contains("java.exe"));
        Set<String> libsSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_handle")).collect(Collectors.toSet());
        // The current pid must be there because it uses this library.
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
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
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
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        Set<String> filesSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_file_name").toUpperCase()).collect(Collectors.toSet());
        // These files must be in this directory.
        // Filename cases in system directories are not stable, therefore uppercase.
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
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
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
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        Set<String> filesSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_file_name")).collect(Collectors.toSet());
        // These files must be in this directory.
        Assert.assertTrue(filesSet.contains("C:\\Program Files\\Internet Explorer\\en-US\\hmmapi.dll.mui"));
        Assert.assertTrue(filesSet.contains("C:\\Program Files\\Internet Explorer\\images\\bing.ico"));
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
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        System.out.println("Rows number=" + the_rows.size());

        // Filenames are converted to uppercase because cases are not stable depending on Windows version.
        Set<String> dirsSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_dir_name").toUpperCase()).collect(Collectors.toSet());
        //for(String oneLib: dirsSet) {
        //    System.out.println("Lib=" + oneLib);
        //}
        /*
        Beware that filename cases are not stable. WMI returns for example:
        "C:\WINDOWS\System32", "c:\windows\system32", "C:\WINDOWS\system32"
         */
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
        ArrayList<WmiSelecter.Row> the_rows = patternSparql.Execute();
        System.out.println("Rows number=" + the_rows.size());

        Set<String> namesSet = the_rows
                .stream()
                .map(entry -> entry.Elements.get("my_process_name")).collect(Collectors.toSet());
        for(String oneName: namesSet) {
            System.out.println("Name=" + oneName);
        }
        Assert.assertTrue(namesSet.contains("java.exe"));
    }


    /**
     *         self.assertEqual(map_attributes["Win32_MountPoint.Directory"],
     *             {"predicate_type": "ref:Win32_Directory", "predicate_domain": ["Win32_Volume"]})
     *         self.assertEqual(map_attributes["Win32_MountPoint.Volume"],
     *             {"predicate_type": "ref:Win32_Volume", "predicate_domain": ["Win32_Directory"]})
     */



    /*
    TODO: Specify the path of an object.
     */
}
