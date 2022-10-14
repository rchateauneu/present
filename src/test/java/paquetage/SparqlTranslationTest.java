package paquetage;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;


public class SparqlTranslationTest {
    static private String currentPidStr = String.valueOf(ProcessHandle.current().pid());

    static private Set<String> RowColumnAsSet(Solution rowsList, String columnName) {
        return rowsList
                .stream()
                .map(entry -> entry.GetStringValue(columnName)).collect(Collectors.toSet());
    }

    static private Set<String> RowColumnAsSetUppercase(Solution rowsList, String columnName) {
        return rowsList
                .stream()
                .map(entry -> entry.GetStringValue(columnName).toUpperCase()).collect(Collectors.toSet());
    }

    /** Loads a file with its name and checks the correctness of the attributes.

     string   Caption;
     string   Description;
     datetime InstallDate;
     string   Status;
     uint32   AccessMask;
     boolean  Archive;
     boolean  Compressed;
     string   CompressionMethod;
     string   CreationClassName;
     datetime CreationDate;
     string   CSCreationClassName;
     string   CSName;
     string   Drive;
     string   EightDotThreeFileName;
     boolean  Encrypted;
     string   EncryptionMethod;
     string   Name;
     string   Extension;
     string   FileName;
     uint64   FileSize;
     string   FileType;
     string   FSCreationClassName;
     string   FSName;
     boolean  Hidden;
     uint64   InUseCount;
     datetime LastAccessed;
     datetime LastModified;
     string   Path;
     boolean  Readable;
     boolean  System;
     boolean  Writeable;
     string   Manufacturer;
     string   Version;
     };

     *
     * @throws Exception
     */
    @Test
    public void Execution_Forced_CIM_DataFile() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?file_Caption ?file_Drive ?file_FileSize ?file_Path
                    where {
                        ?my0_file rdf:type cimv2:CIM_DataFile .
                        ?my0_file cimv2:Caption ?file_Caption .
                        ?my0_file cimv2:Drive ?file_Drive .
                        ?my0_file cimv2:FileSize ?file_FileSize .
                        ?my0_file cimv2:Name "C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll" .
                        ?my0_file cimv2:Path ?file_Path .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet(
                "file_Caption", "file_FileSize", "file_Drive", "file_Path"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        Assert.assertEquals(1, listRows.size());

        Solution.Row firstRow = listRows.get(0);

        // The current pid must be there because it uses this library.
        String fileName = "C:\\WINDOWS\\SYSTEM32\\ntdll.dll";
        Assert.assertEquals(fileName, firstRow.GetStringValue("file_Caption"));
        Assert.assertEquals("c:", firstRow.GetStringValue("file_Drive"));
        File f = new File(fileName);
        long fileSize = f.length();
        String fileSizeStr = Long.toString(fileSize);
        Assert.assertEquals(fileSizeStr, firstRow.GetStringValue("file_FileSize"));
        Assert.assertEquals("\\windows\\system32\\", firstRow.GetStringValue("file_Path"));
    }

    /** This runs a SPARQL query which returns pids of all processes and checks that the current pid is found.
     *
     * @throws Exception
     */
    @Test
    public void Execution_Forced_Win32_Process_1() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_process_handle
                    where {
                        ?my_process rdf:type cimv2:Win32_Process .
                        ?my_process cimv2:Handle ?my_process_handle .
                    }
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_handle"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();
        boolean foundCurrentPid = false;

        for(Solution.Row row: listRows) {
            if (row.GetStringValue("my_process_handle").equals(currentPidStr)) {
                foundCurrentPid = true;
                break;
            }
        }
        Assert.assertTrue(foundCurrentPid);
    }

    /** Checks the caption of the current process selected with the current pid.
     *
     * @throws Exception
     */
    @Test
    public void Execution_Forced_Win32_Process_2() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_process_caption
                    where {
                        ?my_process rdf:type cimv2:Win32_Process .
                        ?my_process cimv2:Caption ?my_process_caption .
                        ?my_process cimv2:Handle "%s" .
                    }
                """, currentPidStr);
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_caption"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();
        Assert.assertEquals(1, listRows.size());
        Assert.assertEquals("java.exe", listRows.get(0).GetStringValue("my_process_caption"));
    }

    /** Checks the caption of the current process selected with the current pid.
     * This gets only the node.
     * @throws Exception
     */
    @Test
    public void Execution_Forced_Win32_Process_3() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_process
                    where {
                        ?my_process rdf:type cimv2:Win32_Process .
                        ?my_process cimv2:Handle "%s" .
                    }
                """, currentPidStr);
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();
        Assert.assertEquals(1, listRows.size());
        Assert.assertEquals(Set.of("my_process"), listRows.get(0).KeySet());
    }

    /** This deduces the type of the object because of the prefix predicate.
     *
     * @throws Exception
     */
    @Test
    public void Execution_Forced_Win32_Process_4() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_process_caption
                    where {
                        ?my_process cimv2:Win32_Process.Caption ?my_process_caption .
                        ?my_process cimv2:Handle "%s" .
                    }
                """, currentPidStr);
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_caption"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();
        Assert.assertEquals(1, listRows.size());
        Assert.assertEquals("java.exe", listRows.get(0).GetStringValue("my_process_caption"));
    }

    @Test
    public void Execution_Forced_CIM_ProcessExecutable_1() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file_name
                    where {
                        ?my1_assoc rdf:type cimv2:CIM_ProcessExecutable .
                        ?my1_assoc cimv2:Dependent ?my0_process .
                        ?my1_assoc cimv2:Antecedent ?my2_file .
                        ?my0_process rdf:type cimv2:Win32_Process .
                        ?my0_process cimv2:Handle "%s" .
                        ?my2_file rdf:type cimv2:CIM_DataFile .
                        ?my2_file cimv2:Name ?my_file_name .
                    }
                """, currentPidStr);

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();
        Set<String> libsSet = RowColumnAsSet(listRows, "my_file_name");
        // This tests the presence of some libraries which are used by the current Java process.
        String javabin = PresentUtils.CurrentJavaBinary();
        Assert.assertTrue(libsSet.contains(javabin));

        System.out.println("libsSet=" + libsSet);
        // Different behaviour on Windows 7 with string cases, hence this conversion.
        libsSet = libsSet.stream().map(str -> str.toUpperCase()).collect(Collectors.toSet());
        Assert.assertTrue(libsSet.contains("C:\\WINDOWS\\SYSTEM32\\ntdll.dll".toUpperCase()));
        Assert.assertTrue(libsSet.contains("C:\\WINDOWS\\System32\\USER32.dll".toUpperCase()));
    }

    /**
     * The current pid must be found in the processes using a specific library.
     * The order of evaluation, i.e. the order of object patterns, is forced with the alphabetical order
     * of main variables.
     *
     * TODO: This test might fail is a process unexpectedly leaves in the middle of the query.
     */
    @Test
    public void Execution_Forced_CIM_ProcessExecutable_2() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_handle
                    where {
                        ?my1_assoc rdf:type cimv2:CIM_ProcessExecutable .
                        ?my1_assoc cimv2:Dependent ?my2_process .
                        ?my1_assoc cimv2:Antecedent ?my0_file .
                        ?my2_process rdf:type cimv2:Win32_Process .
                        ?my2_process cimv2:Handle ?my_handle .
                        ?my0_file rdf:type cimv2:CIM_DataFile .
                        ?my0_file cimv2:Name "C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_handle"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        // The current pid must be there because it uses this library.
        Set<String> pidsSet = RowColumnAsSet(listRows, "my_handle");
        System.out.println("pidsSet=" + pidsSet);
        Assert.assertTrue(pidsSet.contains(currentPidStr));
    }

    /**
     * This gets the handle and caption of all processes using a specific library.
     * The current pid must be found in the processes using a specific library.
     * This checks that two columns are properly returned.
     * The order of evaluation, i.e. the order of object patterns, is forced with the alphabetical order
     * of main variables.
     */
    @Test
    public void Execution_Forced_CIM_ProcessExecutable_3() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_handle ?my_caption
                    where {
                        ?my1_assoc rdf:type cimv2:CIM_ProcessExecutable .
                        ?my1_assoc cimv2:Dependent ?my2_process .
                        ?my1_assoc cimv2:Antecedent ?my0_file .
                        ?my2_process rdf:type cimv2:Win32_Process .
                        ?my2_process cimv2:Handle ?my_handle .
                        ?my2_process cimv2:Caption ?my_caption .
                        ?my0_file rdf:type cimv2:CIM_DataFile .
                        ?my0_file cimv2:Name "C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_caption", "my_handle"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        // The current caption must be there because it uses this library.
        Set<String> captionsSet = RowColumnAsSet(listRows, "my_caption");
        System.out.println("captionsSet=" + captionsSet);
        Assert.assertTrue(captionsSet.contains("java.exe"));

        // The current pid must be there because it uses this library.
        Set<String> libsSet = RowColumnAsSet(listRows, "my_handle");
        Assert.assertTrue(libsSet.contains(currentPidStr));
    }

    @Test
    /***
     * This gets the parent-directory of a file.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_1() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_dir_name
                    where {
                        ?my1_assoc rdf:type cimv2:CIM_DirectoryContainsFile .
                        ?my1_assoc cimv2:GroupComponent ?my2_dir .
                        ?my1_assoc cimv2:PartComponent ?my0_file .
                        ?my2_dir rdf:type cimv2:Win32_Directory .
                        ?my2_dir cimv2:Name ?my_dir_name .
                        ?my0_file rdf:type cimv2:CIM_DataFile .
                        ?my0_file cimv2:Name "C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_dir_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();
        Assert.assertEquals(listRows.size(), 1);
        // Filename cases are not stable wrt Windows version.
        Assert.assertEquals(listRows.get(0).GetStringValue("my_dir_name").toUpperCase(), "C:\\WINDOWS\\SYSTEM32");
    }

    @Test
    /**
     * This gets the list of files in a directory.
     * Possibly because accessing CIM_DataFile is slow anyway.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_2() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file_name
                    where {
                        ?my1_assoc rdf:type cimv2:CIM_DirectoryContainsFile .
                        ?my1_assoc cimv2:GroupComponent ?my0_dir .
                        ?my1_assoc cimv2:PartComponent ?my2_file .
                        ?my0_dir rdf:type cimv2:Win32_Directory .
                        ?my0_dir cimv2:Name "C:\\\\WINDOWS\\\\SYSTEM32" .
                        ?my2_file rdf:type cimv2:CIM_DataFile .
                        ?my2_file cimv2:Name ?my_file_name .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        // These files must be in this directory.
        // Filename cases in system directories are not stable, therefore uppercase.
        Set<String> filesSet = RowColumnAsSetUppercase(listRows, "my_file_name");
        Assert.assertTrue(filesSet.contains("C:\\WINDOWS\\SYSTEM32\\NTDLL.DLL"));
        Assert.assertTrue(filesSet.contains("C:\\WINDOWS\\SYSTEM32\\USER32.DLL"));
    }

    @Test
    /***
     * This gets the parent-parent-directory of a file.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_Win32_SubDirectory_1() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_dir_name
                    where {
                        ?my0_file rdf:type cimv2:CIM_DataFile .
                        ?my0_file cimv2:Name "C:\\\\WINDOWS\\\\SYSTEM32\\\\ntdll.dll" .
                        ?my1_assoc rdf:type cimv2:CIM_DirectoryContainsFile .
                        ?my1_assoc cimv2:PartComponent ?my0_file .
                        ?my1_assoc cimv2:GroupComponent ?my2_dir .
                        ?my2_dir rdf:type cimv2:Win32_Directory .
                        ?my3_assoc rdf:type cimv2:Win32_SubDirectory .
                        ?my3_assoc cimv2:PartComponent ?my2_dir .
                        ?my3_assoc cimv2:GroupComponent ?my4_dir .
                        ?my4_dir rdf:type cimv2:Win32_Directory .
                        ?my4_dir cimv2:Name ?my_dir_name .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_dir_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();
        Assert.assertEquals(listRows.size(), 1);
        // Case of filenames are not stable wrt Windows version.
        Assert.assertEquals(listRows.get(0).GetStringValue("my_dir_name").toUpperCase(), "C:\\WINDOWS");
    }

    @Test
    /**
     * This gets sub-sub-files of a directory.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_Win32_SubDirectory_2() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_file_name
                    where {
                        ?my4_file rdf:type cimv2:CIM_DataFile .
                        ?my4_file cimv2:Name ?my_file_name .
                        ?my3_assoc rdf:type cimv2:CIM_DirectoryContainsFile .
                        ?my3_assoc cimv2:PartComponent ?my4_file .
                        ?my3_assoc cimv2:GroupComponent ?my2_dir .
                        ?my2_dir rdf:type cimv2:Win32_Directory .
                        ?my1_assoc rdf:type cimv2:Win32_SubDirectory .
                        ?my1_assoc cimv2:PartComponent ?my2_dir .
                        ?my1_assoc cimv2:GroupComponent ?my0_dir .
                        ?my0_dir rdf:type cimv2:Win32_Directory .
                        ?my0_dir cimv2:Name "C:\\\\Program Files\\\\Internet Explorer" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        // These files must be in this directory.
        Set<String> filesSet = RowColumnAsSet(listRows, "my_file_name");
        //Assert.assertTrue(filesSet.contains("C:\\Program Files\\Internet Explorer\\en-US\\hmmapi.dll.mui"));
        System.out.println(" filesSet=" + filesSet);
        // Different behaviour on Windows 7 with string cases, therefore this conversion.
        filesSet = filesSet.stream().map(str -> str.toUpperCase()).collect(Collectors.toSet());
        Assert.assertTrue(filesSet.contains("C:\\Program Files\\Internet Explorer\\images\\bing.ico".toUpperCase()));
    }

    @Test
    /**
     * This gets the grandparent of the sub-sub-files of a directory. It must be the same.
     */
    public void Execution_Forced_CIM_DirectoryContainsFile_Win32_SubDirectory_3() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_dir_name
                    where {
                        ?my8_dir rdf:type cimv2:Win32_Directory .
                        ?my8_dir cimv2:Name ?my_dir_name .
                        ?my7_assoc rdf:type cimv2:Win32_SubDirectory .
                        ?my7_assoc cimv2:PartComponent ?my6_dir .
                        ?my7_assoc cimv2:GroupComponent ?my8_dir .
                        ?my6_dir rdf:type cimv2:Win32_Directory .
                        ?my5_assoc rdf:type cimv2:CIM_DirectoryContainsFile .
                        ?my5_assoc cimv2:PartComponent ?my4_file .
                        ?my5_assoc cimv2:GroupComponent ?my6_dir .
                        ?my4_file rdf:type cimv2:CIM_DataFile .
                        ?my3_assoc rdf:type cimv2:CIM_DirectoryContainsFile .
                        ?my3_assoc cimv2:PartComponent ?my4_file .
                        ?my3_assoc cimv2:GroupComponent ?my2_dir .
                        ?my2_dir rdf:type cimv2:Win32_Directory .
                        ?my1_assoc rdf:type cimv2:Win32_SubDirectory .
                        ?my1_assoc cimv2:PartComponent ?my2_dir .
                        ?my1_assoc cimv2:GroupComponent ?my0_dir .
                        ?my0_dir rdf:type cimv2:Win32_Directory .
                        ?my0_dir cimv2:Name "C:\\\\Program Files\\\\Internet Explorer" .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_dir_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        // It must fall back to the initial directory.
        Set<String> dirsSet = RowColumnAsSet(listRows, "my_dir_name");
        Assert.assertEquals(1, dirsSet.size());
        System.out.println("dirsSet=" + dirsSet);
        // Conversion to uppercase for Windows7.
        dirsSet = dirsSet.stream().map(str -> str.toUpperCase()).collect(Collectors.toSet());;
        Assert.assertTrue(dirsSet.contains("C:\\Program Files\\Internet Explorer".toUpperCase()));
    }

    @Test
    /**
     * This gets the directories of all executables and libraries used by running processes.
     * The order of evaluation is forced with the alphabetical order of main variables.
     */
    public void Execution_Forced_CIM_ProcessExecutable_CIM_DirectoryContainsFile_1() throws Exception {
        String sparqlQuery = """
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_dir_name
            where {
                ?my0_process rdf:type cimv2:Win32_Process .
                ?my1_assoc rdf:type cimv2:CIM_ProcessExecutable .
                ?my1_assoc cimv2:Dependent ?my0_process .
                ?my1_assoc cimv2:Antecedent ?my2_file .
                ?my2_file rdf:type cimv2:CIM_DataFile .
                ?my3_assoc rdf:type cimv2:CIM_DirectoryContainsFile .
                ?my3_assoc cimv2:PartComponent ?my2_file .
                ?my3_assoc cimv2:GroupComponent ?my4_dir .
                ?my4_dir rdf:type cimv2:Win32_Directory .
                ?my4_dir cimv2:Name ?my_dir_name .
            }
        """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_dir_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();
        System.out.println("Rows number=" + listRows.size());

        /*
        Beware that filename cases are not stable. WMI returns for example:
        "C:\WINDOWS\System32", "c:\windows\system32", "C:\WINDOWS\system32"
         */
        Set<String> dirsSet = RowColumnAsSetUppercase(listRows, "my_dir_name");
        Assert.assertTrue(dirsSet.contains("C:\\WINDOWS"));
        Assert.assertTrue(dirsSet.contains("C:\\WINDOWS\\SYSTEM32"));
    }

    @Test
    /**
     * Names of processes which have an executable or a library in a given directory.
     */
    public void Execution_Forced_CIM_ProcessExecutable_CIM_DirectoryContainsFile_2() throws Exception {
        File file = new File(PresentUtils.CurrentJavaBinary());
        String parent = file.getAbsoluteFile().getParent();
        // Typically "C:\\Program Files\\Java\\jdk-17.0.2\\bin"
        String dirExecutable = parent.replace("\\", "\\\\");
        String sparqlQuery = String.format("""
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_process_name
            where {
                ?my4_process rdf:type cimv2:Win32_Process .
                ?my4_process cimv2:Name ?my_process_name .
                ?my3_assoc rdf:type cimv2:CIM_ProcessExecutable .
                ?my3_assoc cimv2:Dependent ?my4_process .
                ?my3_assoc cimv2:Antecedent ?my2_file .
                ?my2_file rdf:type cimv2:CIM_DataFile .
                ?my1_assoc rdf:type cimv2:CIM_DirectoryContainsFile .
                ?my1_assoc cimv2:PartComponent ?my2_file .
                ?my1_assoc cimv2:GroupComponent ?my0_dir .
                ?my0_dir rdf:type cimv2:Win32_Directory .
                ?my0_dir cimv2:Name "%s" .
            }
        """, dirExecutable);

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_process_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();
        System.out.println("Rows number=" + listRows.size());

        Set<String> namesSet = RowColumnAsSet(listRows, "my_process_name");
        for(String oneName: namesSet) {
            System.out.println("Name=" + oneName);
        }
        System.out.println("namesSet=" + namesSet.toString());
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
        String sparqlQuery = """
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?device_id
            where {
                ?my2_volume cimv2:DeviceID ?device_id .
                ?my2_volume rdf:type cimv2:Win32_Volume .
                ?my1_assoc rdf:type cimv2:Win32_MountPoint .
                ?my1_assoc cimv2:Volume ?my2_volume .
                ?my1_assoc cimv2:Directory ?my0_dir .
                ?my0_dir rdf:type cimv2:Win32_Directory .
                ?my0_dir cimv2:Name "C:\\\\" .
            }
        """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("device_id"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        // Disk "C:" must be found.
        Set<String> volumesSet = RowColumnAsSet(listRows, "device_id");
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
        String sparqlQuery = """
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name
            where {
                ?my0_assoc rdf:type cimv2:Win32_MountPoint .
                ?my0_assoc cimv2:Directory ?my1_dir .
                ?my1_dir rdf:type cimv2:Win32_Directory .
                ?my1_dir cimv2:Name ?dir_name .
            }
        """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("dir_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        // Disk "C:" must be found.
        Set<String> dirsSet = RowColumnAsSet(listRows, "dir_name");
        for(String oneName: dirsSet) {
            System.out.println("Dir=" + dirsSet);
        }
        // Conversion to uppercase because of different behaviour on Windows 7.
        dirsSet = dirsSet.stream().map(str -> str.toUpperCase()).collect(Collectors.toSet());
        Assert.assertTrue(dirsSet.size() > 0);
        Assert.assertTrue(dirsSet.contains("C:\\"));
    }


    /***
     * Drive of a given directory.
     */
    @Test
    public void Execution_Forced_Win32_Directory_Drive_1() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_drive ?my1_dir
                    where {
                        ?my1_dir rdf:type cimv2:Win32_Directory .
                        ?my1_dir cimv2:Name ?my_drive .
                        ?my0_dir rdf:type cimv2:Win32_Directory .
                        ?my0_dir cimv2:Name "C:\\\\Program Files (x86)" .
                        ?my0_dir cimv2:Drive ?my_drive .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_drive", "my1_dir"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        // Disk "C:" must be found.
        Set<String> drivesSet = RowColumnAsSet(listRows, "my_drive");
        for(String drive: drivesSet) {
            System.out.println("Drive=" + drive);
        }
        Assert.assertTrue(drivesSet.size() == 1);
        Assert.assertTrue(drivesSet.contains("c:"));

        // Display the dir path.
        Set<String> dirsSet = RowColumnAsSet(listRows, "my1_dir");
        for(String dir: dirsSet) {
            System.out.println("Dir=" + dir);
        }
    }

    @Test
    public void Execution_Forced_Win32_Account() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_account_name
                    where {
                        ?my_account rdf:type cimv2:Win32_Account .
                        ?my_account cimv2:Name ?my_account_name .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_account_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        Set<String> accountsSet = RowColumnAsSet(listRows, "my_account_name");
        Assert.assertTrue(accountsSet.contains("Users"));
    }

    @Test
    public void Execution_Forced_Win32_COMClass() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_class_name
                    where {
                        ?my_com_class rdf:type cimv2:Win32_COMClass .
                        ?my_com_class cimv2:Name ?my_class_name .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_class_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        Set<String> classesSet = RowColumnAsSet(listRows, "my_class_name");
        System.out.println("classesSet=" + classesSet);
        // Some randomly-chosen classes.
        Assert.assertTrue(classesSet.contains("Memory Allocator"));
        Assert.assertTrue(classesSet.contains("System.Exception"));
    }

    @Test
    public void Execution_Forced_Win32_Thread() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_thread_name
                    where {
                        ?my_thread rdf:type cimv2:Win32_Thread .
                        ?my_thread cimv2:Name ?my_thread_name .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_thread_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        Set<String> threadsSet = RowColumnAsSet(listRows, "my_thread_name");
        for(String threadName: threadsSet) {
            if((threadName != null) && ! threadName.equals(""))
                System.out.println("Thread=" + threadName);
        }
        // Most threads have no name.
        Assert.assertTrue(threadsSet.contains(null));
    }

    @Test
    public void Execution_Forced_Win32_Thread_Optional_Priority() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_thread_name ?my_thread_priority
                    where {
                        ?my_thread rdf:type cimv2:Win32_Thread .
                        ?my_thread cimv2:Name ?my_thread_name .
                        optional { ?my_thread cimv2:Priority ?my_thread_priority . } .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_thread_name", "my_thread_priority"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        Set<String> threadsSet = RowColumnAsSet(listRows, "my_thread_name");
        for(String threadName: threadsSet) {
            if((threadName != null) && ! threadName.equals(""))
                System.out.println("Thread=" + threadName);
        }
        // Most threads have no name.
        Assert.assertTrue(threadsSet.contains(null));
    }

    /** The type of the instances are deduced from the property names */
    @Test
    public void Execution_Forced_Win32_Thread_NoType() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_thread_name
                    where {
                        ?my_thread cimv2:Win32_Thread.Name ?my_thread_name .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_thread_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        Set<String> threadsSet = RowColumnAsSet(listRows, "my_thread_name");
        for(String threadName: threadsSet) {
            if((threadName != null) && ! threadName.equals(""))
                System.out.println("Thread=" + threadName);
        }
        // Most threads have no name.
        Assert.assertTrue(threadsSet.contains(null));
    }

    /** Fetching Wmi32_Product is slow:
     * https://stackoverflow.com/questions/25083520/wmi-select-from-win32-product-takes-a-long-time
     *
     * @throws Exception
     */
    @Test
    public void Execution_Forced_Win32_Product() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_product_number ?my_product_name ?my_product_vendor ?my_product_caption ?my_product_version
                    where {
                        ?my_product rdf:type cimv2:Win32_Product .
                        ?my_product cimv2:IdentifyingNumber ?my_product_number .
                        ?my_product cimv2:Name ?my_product_name .
                        ?my_product cimv2:Vendor ?my_product_vendor .
                        ?my_product cimv2:Caption ?my_product_caption .
                        ?my_product cimv2:Version ?my_product_version .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet(
                "my_product_number", "my_product_name", "my_product_vendor", "my_product_caption", "my_product_version"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        Set<String> productNamesSet = RowColumnAsSet(listRows, "my_product_name");
        Assert.assertTrue(productNamesSet.contains("Windows SDK Signing Tools"));
        //Assert.assertTrue(productNamesSet.contains("Microsoft Update Health Tools"));

        Set<String> productVendorsSet = RowColumnAsSet(listRows, "my_product_vendor");
        Assert.assertTrue(productVendorsSet.contains("Microsoft Corporation"));
    }

    @Test
    public void Execution_Forced_Win32_DCOMApplication() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_application_name
                    where {
                        ?my_application rdf:type cimv2:Win32_DCOMApplication .
                        ?my_application cimv2:Name ?my_application_name .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_application_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        Set<String> applicationsSet = RowColumnAsSet(listRows, "my_application_name");
        Assert.assertTrue(applicationsSet.contains("User Notification"));
        Assert.assertTrue(applicationsSet.contains("IMAPI2"));
    }

    /** Join between two lists of objects with Win32_DCOMApplication.AppID == Win32_DCOMApplicationSetting.AppID
     *
     * @throws Exception
     */
    @Test
    public void Execution_Forced_Win32_DCOMApplication_Win32_DCOMApplicationSetting() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?my_application_name ?my_local_service
                    where {
                        ?my_application rdf:type cimv2:Win32_DCOMApplication .
                        ?my_application cimv2:Name ?my_application_name .
                        ?my_application cimv2:AppID ?my_app_id .
                        ?my_setting rdf:type cimv2:Win32_DCOMApplicationSetting .
                        ?my_setting cimv2:AppID ?my_app_id .
                        ?my_setting cimv2:LocalService ?my_local_service .
                    }
                """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_application_name", "my_local_service"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        Set<String> applicationsSet = RowColumnAsSet(listRows, "my_application_name");
        //Assert.assertTrue(applicationsSet.contains("User Notification"));
        Assert.assertTrue(applicationsSet.contains("IMAPI2"));

        Set<String> servicesSet = RowColumnAsSet(listRows, "my_local_service");
        System.out.println("servicesSet=" + servicesSet);
        // Windows 7: servicesSet=[null, vds, PDFescape Desktop Update Service, sdrsvc, lltdsvc, GoogleChromeElevationService, TrustedInstaller, cphs, BITS, EapHost, HFGService, MSIServer, IPBusEnum, PDFescape Desktop Creator, wercplsupport, napagent, wbengine, TlntSvr, hMailServer, VSS, edgeupdatem, wuauserv, hpqwmiex, VsEtwService120, MsDtsServer110, WSearch, hpqcaslwmiex, NisSrv, WMIApSrv, defragsvc, gupdate, TermService, hkmsvc, ehSched, EventSystem, BsHelpCS, swprv, WatAdminSvc, WcsPlugInService, HomeGroupProvider, netprofm, gupdatem, WbemConsumer, CscService, edgeupdate, netman, SharedAccess, profsvc, stisvc, ehRecvr, IEEtwCollectorService, AxInstSv, winmgmt, ShellHWDetection, SkypeUpdate, fdPHost, LMS, VSStandardCollectorService150, PDFescape Desktop, upnphost, BlueSoleilCS, ALG]
        //Assert.assertTrue(servicesSet.contains("wlansvc")); // Windows 10
        //Assert.assertTrue(servicesSet.contains("upnphost")); // Windows 10
        Assert.assertTrue(servicesSet.contains("netman")); // Windows 7
    }

    /** The order of execution is forced to be sure of the result at this stage.
     *
     * @throws Exception
     */
    @Test
    public void Execution_Forced_Win32_Process_CIM_ProcessExecutable_CIM_DataFile() throws Exception {
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

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("file_name"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        Set<String> applicationsSet = RowColumnAsSet(listRows, "file_name");
        System.out.println("applicationsSet=" + applicationsSet);
        Assert.assertTrue(applicationsSet.contains(PresentUtils.CurrentJavaBinary()));
    }

    /** This gets the list of files in a directory, and their sizes.
     *
     * @throws Exception
     */
    @Test
    public void Execution_Forced_Win32_Process_CIM_DataFile_Name_FileSize() throws Exception {
        String sparqlQuery = String.format("""
                prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                select ?file_name ?file_size
                where {
                    ?my2_file cimv2:CIM_DataFile.Name ?file_name .
                    ?my2_file cimv2:CIM_DataFile.FileSize ?file_size .
                    ?my1_assoc cimv2:CIM_DirectoryContainsFile.PartComponent ?my2_file .
                    ?my1_assoc cimv2:GroupComponent ?my0_dir .
                    ?my0_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                }
                """, currentPidStr);

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("file_name", "file_size"));

        SparqlTranslation patternSparql = new SparqlTranslation(extractor);
        Solution listRows = patternSparql.ExecuteToRows();

        Set<String> filesSetActual = new HashSet<>();

        // Check that the sizes are correct.
        for(Solution.Row row : listRows) {
            String fileName = row.GetStringValue("file_name");
            filesSetActual.add(fileName);
            // This is not transformed into a XML value such as ""28400"^^<http://www.w3.org/2001/XMLSchema#long>"
            // because it is not transformed by the Sparql engine.
            long fileSizeActual = row.GetLongValue("file_size");
            File f = new File(fileName);
            long fileSizeExpected = f.length();
            System.out.println("fileName=" + fileName);
            Assert.assertEquals(fileSizeExpected, fileSizeActual);
        }

        // Check that no file is missing.
        File f = new File("C:\\Windows");
        // Set<String> filesSetExpected = Set.copyOf(Arrays.asList(f.list()));
        Set<String> filesSetExpected = Arrays
                .stream(f.listFiles())
                .filter(subf -> subf.isFile())
                .map(subf -> subf.toPath().toString())
                .collect(Collectors.toSet());
        // Conversion to uppercase because of different behaviour in Windows 7.
        filesSetExpected = filesSetExpected.stream().map(str -> str.toUpperCase()).collect(Collectors.toSet());
        filesSetActual = filesSetActual.stream().map(str -> str.toUpperCase()).collect(Collectors.toSet());
        Assert.assertEquals(filesSetExpected, filesSetActual);
    }

    /*
    Execution of Sparql commands in Powershell:
    PS C:\Users\me> Get-WmiObject -Query 'Select * from Win32_DCOMApplicationSetting'
    PS C:\Users\me> Get-WmiObject -Query 'Select * from CIM_ElementSetting'
    */


    /*
    TODO: Specify the path of an object.
     */
}
