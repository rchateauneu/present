package paquetage;

import org.junit.Assert;
import org.junit.Test;

import java.util.*;

// https://www.programcreek.com/java-api-examples/?api=org.eclipse.rdf4j.query.parser.ParsedQuery

public class SparqlBGPExtractorTest {
    static void compareVariable(ObjectPattern.PredicateObjectPair a, String predicate, String variableName) {
        Assert.assertEquals(predicate, a.shortPredicate);
        Assert.assertEquals(variableName, a.variableName);
        Assert.assertEquals(null, a.objectContent);
    }

    static void compareKeyValue(ObjectPattern.PredicateObjectPair a, String predicate, String content) {
        Assert.assertEquals(predicate, a.shortPredicate);
        Assert.assertEquals(null, a.variableName);
        Assert.assertEquals(ValueTypePair.FromString(content), a.objectContent);
    }

    @Test
    public void testParsePlainQuery() throws Exception {
        String sparqlQuery = """
            prefix function: <http://org.apache.rya/function#>
            prefix time: <http://www.w3.org/2006/time#>
            prefix fn: <http://www.w3.org/2006/fn#>
            select ?obs ?time ?lat
            where {
                Filter(function:periodic(?time, 12.0, 6.0,time:hours))
                Filter(fn:test(?lat, 25))
                ?obs <uri:hasTime> ?time.
                ?obs <uri:hasLatitude> ?lat
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("obs", "time", "lat"), extractor.bindings);
        Assert.assertTrue(extractor.patternsAsArray().isEmpty());
    }

    @Test
    /***
     * Checks patterns detection with a constant value (the object).
     */
    public void testParseWin32_Directory_NoVariable() throws Exception {
        String sparqlQuery = """
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_directory
            where {
                ?my_directory rdf:type cimv2:Win32_Directory .
                ?my_directory cimv2:Name "C:" .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("my_directory"), extractor.bindings);

        Assert.assertEquals(1, extractor.patternsAsArray().size());

        ObjectPattern patternWin32_Directory = extractor.FindObjectPattern("my_directory");
        Assert.assertEquals("Win32_Directory", patternWin32_Directory.className);
        Assert.assertEquals(1, patternWin32_Directory.membersList.size());
        compareKeyValue(patternWin32_Directory.membersList.get(0), "Name", "C:");
    }

    @Test
    /***
     * Checks that a pattern with a variable value (the object) is detected.
     */
    public void testParseWin32_Directory_OneVariable() throws Exception {
        String sparqlQuery = """
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_name
            where {
                ?my_directory rdf:type cimv2:Win32_Directory .
                ?my_directory cimv2:Name ?my_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("my_name"), extractor.bindings);

        Assert.assertEquals(1, extractor.patternsAsArray().size());

        ObjectPattern patternWin32_Directory = extractor.FindObjectPattern("my_directory");
        Assert.assertEquals("Win32_Directory", patternWin32_Directory.className);
        Assert.assertEquals(1, patternWin32_Directory.membersList.size());
        compareVariable(patternWin32_Directory.membersList.get(0), "Name", "my_name");
    }

    static void findAndCompareKeyValue(ArrayList<ObjectPattern.PredicateObjectPair> members, String predicate, boolean is_variable, String content) {
        ObjectPattern.PredicateObjectPair pairPredObj = members.stream()
                .filter(x -> x.shortPredicate.equals(predicate))
                .findFirst().orElse(null);
        Assert.assertNotEquals(null, pairPredObj);
        if(is_variable)
            compareVariable(pairPredObj, predicate, content);
        else
            compareKeyValue(pairPredObj, predicate, content);
    }

    @Test
    /***
     * Checks the BGPs extracted from a query with three types.
     */
    public void testParseCIM_ProcessExecutable_Win32_Process_CIM_DataFile() throws Exception {
        String sparqlQuery = """
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_file_name
            where {
                ?my_assoc rdf:type cimv2:CIM_ProcessExecutable .
                ?my_assoc cimv2:Dependent ?my_process .
                ?my_assoc cimv2:Antecedent ?my_file .
                ?my_process rdf:type cimv2:Win32_Process .
                ?my_process cimv2:Handle ?my_process_handle .
                ?my_file rdf:type cimv2:CIM_DataFile .
                ?my_file cimv2:Name "C:\\\\WINDOWS\\\\System32\\\\kernel32.dll" .
                }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("my_file_name"), extractor.bindings);

        Assert.assertEquals(extractor.patternsAsArray().size(), 3);

        ObjectPattern patternCIM_ProcessExecutable = extractor.FindObjectPattern("my_assoc");
        Assert.assertEquals("CIM_ProcessExecutable", patternCIM_ProcessExecutable.className);
        Assert.assertEquals(patternCIM_ProcessExecutable.membersList.size(), 2);
        findAndCompareKeyValue(patternCIM_ProcessExecutable.membersList, "Dependent", true, "my_process");
        findAndCompareKeyValue(patternCIM_ProcessExecutable.membersList, "Antecedent", true, "my_file");

        ObjectPattern patternWin32_Process = extractor.FindObjectPattern("my_process");
        Assert.assertEquals("Win32_Process", patternWin32_Process.className);
        Assert.assertEquals(patternWin32_Process.membersList.size(), 1);
        findAndCompareKeyValue(patternWin32_Process.membersList, "Handle", true, "my_process_handle");

        ObjectPattern patternCIM_DataFile = extractor.FindObjectPattern("my_file");
        Assert.assertEquals("CIM_DataFile", patternCIM_DataFile.className);
        Assert.assertEquals(patternCIM_DataFile.membersList.size(), 1);
        findAndCompareKeyValue(patternCIM_DataFile.membersList, "Name", false, "C:\\WINDOWS\\System32\\kernel32.dll");
    }


    /***
     * Checks the BGPs extracted from an arbitrary query with a filter statement.
     */
    @Test
    public void testParseFilter() throws Exception {
        String sparqlQuery = """
                PREFIX schema: <http://schema.org/>
                SELECT ?subject_name
                FROM <http://www.worldcat.org/oclc/660967222>
                WHERE {
                ?s schema:about ?about .
                ?about schema:name ?subject_name
                FILTER regex(STR(?about), "fast").
                }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("subject_name"), extractor.bindings);
        List<ObjectPattern> patterns = extractor.patternsAsArray();
        Assert.assertTrue(patterns.isEmpty());
    }

    @Test
    /***
     * Checks the BGPs extracted from an arbitrary query with a group statement.
     */
    public void testParseGroupSum() throws Exception {
        String sparqlQuery = """
                prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                select (SUM(?file_size) as ?size_sum)
                where {
                    ?my2_file cimv2:CIM_DataFile.FileSize ?file_size .
                    ?my1_assoc cimv2:CIM_DirectoryContainsFile.PartComponent ?my2_file .
                    ?my1_assoc cimv2:GroupComponent ?my0_dir .
                    ?my0_dir cimv2:Win32_Directory.Name "C:\\\\Program Files (x86)" .
                } group by ?my2_file
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("size_sum"), extractor.bindings);
        List<ObjectPattern> patterns = extractor.patternsAsArray();
        Assert.assertEquals(3, patterns.size());

        Assert.assertNotEquals(extractor.FindObjectPattern("my0_dir"), null);
        ObjectPattern firstPattern = patterns.get(0);
        Assert.assertEquals("Win32_Directory", firstPattern.className);
        Assert.assertEquals("my0_dir", firstPattern.variableName);

        Assert.assertEquals(1, firstPattern.membersList.size());
        compareKeyValue(firstPattern.membersList.get(0), "Name", "C:\\Program Files (x86)");

        Assert.assertNotEquals(extractor.FindObjectPattern("my1_assoc"), null);
        ObjectPattern secondPattern = patterns.get(1);
        Assert.assertEquals("CIM_DirectoryContainsFile", secondPattern.className);
        Assert.assertEquals("my1_assoc", secondPattern.variableName);

        Assert.assertEquals(2, secondPattern.membersList.size());
        compareVariable(secondPattern.membersList.get(0), "PartComponent", "my2_file");
        compareVariable(secondPattern.membersList.get(0), "PartComponent", "my2_file");

        Assert.assertNotEquals(extractor.FindObjectPattern("my2_file"), null);
        ObjectPattern thirdPattern = patterns.get(2);
        Assert.assertEquals("CIM_DataFile", thirdPattern.className);
        Assert.assertEquals("my2_file", thirdPattern.variableName);

        Assert.assertEquals(1, thirdPattern.membersList.size());
        compareVariable(secondPattern.membersList.get(0), "PartComponent", "my2_file");
    }

    @Test
    /***
     * Checks the bindings extracted from an arbitrary query with a group statement.
     */
    public void testParseGroupMinMaxSum() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select (MIN(?file_size) as ?size_min) (MAX(?file_size) as ?size_max) (SUM(?file_size) as ?size_sum)
                    where {
                        ?my2_file cimv2:CIM_DataFile.FileSize ?file_size .
                        ?my1_assoc cimv2:CIM_DirectoryContainsFile.PartComponent ?my2_file .
                        ?my1_assoc cimv2:GroupComponent ?my0_dir .
                        ?my0_dir cimv2:Win32_Directory.Name "C:\\\\Program Files (x86)" .
                    } group by ?my2_file
            """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("size_min", "size_max", "size_sum"), extractor.bindings);
    }

    @Test
    /***
     * Checks the BGPs extracted from an arbitrary query with an optional statement.
     */
    public void testParseOptional() throws Exception {
        String sparqlQuery = """
                    PREFIX schema: <http://schema.org/>
                    SELECT ?creator_name ?author_name
                    FROM <http://www.worldcat.org/oclc/660967222>
                    WHERE {
                    ?s schema:creator ?creator .
                    ?creator schema:name ?creator_name
                    OPTIONAL {?s schema:author ?author .
                    ?author schema:name ?author_name} .
                    }
            """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("creator_name", "author_name"), extractor.bindings);
        List<ObjectPattern> patterns = extractor.patternsAsArray();
        Assert.assertTrue(patterns.isEmpty());
    }

    /***
     * Checks that the BGPs of a federated query are NOT extracted.
     */
    @Test
    public void testParseFederatedQuery() throws Exception {
        String sparqlQuery = """
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?directory_name
            where {
                ?my_directory rdf:type cimv2:Win32_Directory .
                ?my_directory cimv2:Name ?directory_name .
                SERVICE <http://any.machine/sparql> {
                    ?my_process rdf:type cimv2:Win32_Process .
                    ?my_process cimv2:Caption ?my_caption .
                }
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("directory_name"), extractor.bindings);

        for(ObjectPattern objPatt: extractor.patternsAsArray()) {
            System.out.println("    " + objPatt);
        }

        Assert.assertEquals(1, extractor.patternsAsArray().size());

        ObjectPattern patternWin32_Directory = extractor.FindObjectPattern("my_directory");
        Assert.assertEquals("Win32_Directory", patternWin32_Directory.className);
        Assert.assertEquals(1, patternWin32_Directory.membersList.size());
        compareVariable(patternWin32_Directory.membersList.get(0), "Name", "directory_name");
    }

    @Test
    public void testPropertyPath_CIM_Win32_SubDirectory() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?dir_name
                    where {
                        ?_1_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                        ?_1_dir ^cimv2:Win32_SubDirectory.GroupComponent/cimv2:Win32_SubDirectory.PartComponent ?dir .
                        ?dir cimv2:Win32_Directory.Name ?dir_name .
                    }
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("dir_name"), extractor.bindings);

        for(ObjectPattern objPatt: extractor.patternsAsArray()) {
            System.out.println("    " + objPatt);
        }

        Assert.assertEquals(3, extractor.patternsAsArray().size());

        ObjectPattern patternWin32_Directory = extractor.FindObjectPattern("_1_dir");
        Assert.assertEquals("Win32_Directory", patternWin32_Directory.className);
        Assert.assertEquals(1, patternWin32_Directory.membersList.size());
        compareKeyValue(patternWin32_Directory.membersList.get(0), "Name", "C:\\Windows");
    }

    @Test
    public void testPropertyPath_CIM_Win32_SubDirectory_DirectoryContainsFile() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?file_name
                    where {
                        ?_1_dir cimv2:Win32_Directory.Name "C:\\\\Windows" .
                        ?_1_dir ^cimv2:Win32_SubDirectory.GroupComponent/cimv2:Win32_SubDirectory.PartComponent/^cimv2:CIM_DirectoryContainsFile.GroupComponent/cimv2:CIM_DirectoryContainsFile.PartComponent ?file .
                        ?file cimv2:CIM_DataFile.Name ?file_name .
                    }
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("file_name"), extractor.bindings);

        for(ObjectPattern objPatt: extractor.patternsAsArray()) {
            System.out.println("    " + objPatt);
        }

        Assert.assertEquals(4, extractor.patternsAsArray().size());

        ObjectPattern patternWin32_Directory = extractor.FindObjectPattern("_1_dir");
        Assert.assertEquals("Win32_Directory", patternWin32_Directory.className);
        Assert.assertEquals(1, patternWin32_Directory.membersList.size());
        compareKeyValue(patternWin32_Directory.membersList.get(0), "Name", "C:\\Windows");
    }

    /** Arbitrary length property paths are not handled yet.
     * This query is equivalent to:
     * select ?display_name ?dependency_type
     * where {
     *     ?service1 cimv2:Win32_Service.DisplayName "Windows Search" .
     *     ?assoc1 rdf:type cimv2:Win32_DependentService .
     *     ?assoc1 cimv2:Dependent ?service1_1 .
     *     ?assoc1 cimv2:Antecedent ?service1_2 .
     *     ...
     *     ?assocn rdf:type cimv2:Win32_DependentService .
     *     ?assocn cimv2:Dependent ?servicen_1 .
     *     ?assocn cimv2:Antecedent ?service2 .
     *     ?service2 cimv2:Win32_Service.DisplayName ?display_name .
     * }
     *
     * @throws Exception
     */
    @Test (expected = RuntimeException.class)
    public void testPropertyPath_Win32_DependentService_Many() throws Exception {
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
        try {
            SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        }
        catch(Exception exc)
        {
            System.out.println("exc=" + exc);
            Assert.assertEquals("ArbitraryLengthPath are not allowed yet.", exc.getMessage());
            throw exc;
        }
        Assert.fail("SparqlBGPExtractor did not throw an exception");
    }

}

/*

    query_all_classes = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        select distinct ?the_class
        where {
        ?the_class rdf:type rdfs:Class .
        }
        """

    query_CIM_ProcessExecutable = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?prop
        where {
        cimv2:CIM_ProcessExecutable ?prop ?obj .
        }
        """

    # Classes which are associators.
    query_associators = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?class_node
        where {
        ?class_node cimv2:is_association ?obj .
        }
        """

    # Properties of associator classes.
    query_associators_properties = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?class_name ?property_name
        where {
        ?class_node cimv2:is_association ?obj .
        ?class_node rdfs:label ?class_name .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:label ?property_name .
        }
        """

    # Range of properties of associator CIM_DirectoryContainsFile.
    query_CIM_DirectoryContainsFile_properties_range = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?property_name ?range_class_node
        where {
        ?class_node cimv2:is_association ?obj .
        ?class_node rdfs:label "CIM_DirectoryContainsFile" .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:range ?range_class_node .
        ?property_node rdfs:label ?property_name .
        }
        """

    # Name of class of range of properties of associator Win32_ShareToDirectory.
    query_Win32_ShareToDirectory_properties_range = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?property_name ?range_class_name
        where {
        ?class_node cimv2:is_association ?obj .
        ?class_node rdfs:label "Win32_ShareToDirectory" .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:range ?range_class_node .
        ?property_node rdfs:label ?property_name .
        ?range_class_node rdfs:label ?range_class_name .
        }
        """

    # Associators pointing to a CIM_DataFile.
    query_associators_to_CIM_DataFile = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?class_name ?property_name
        where {
        ?class_node cimv2:is_association ?obj .
        ?class_node rdfs:label ?class_name .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:range ?range_class_node .
        ?property_node rdfs:label ?property_name .
        ?range_class_node rdfs:label "CIM_DataFile" .
        }
        """

    # Name of class of range of properties of associator CIM_ProcessThread.
    query_Win32_CIM_ProcessThread_properties_range = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?property_name ?range_class_name
        where {
        ?class_node cimv2:is_association ?obj .
        ?class_node rdfs:label "CIM_ProcessThread" .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:range ?range_class_node .
        ?property_node rdfs:label ?property_name .
        ?range_class_node rdfs:label ?range_class_name .
        }
        """

    # Associators pointing to a CIM_Thread.
    query_associators_to_CIM_Thread = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?class_name ?property_name
        where {
        ?class_node cimv2:is_association ?obj .
        ?class_node rdfs:label ?class_name .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:range ?range_class_node .
        ?property_node rdfs:label ?property_name .
        ?range_class_node rdfs:label "CIM_Thread" .
        }
        """

    # Associators pointing to CIM_Process.
    query_associators_to_CIM_Process = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?class_name ?property_name
        where {
        ?class_node cimv2:is_association ?obj .
        ?class_node rdfs:label ?class_name .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:range ?range_class_node .
        ?property_node rdfs:label ?property_name .
        ?range_class_node rdfs:label "CIM_Process" .
        }
        """


class Testing_CIM_Directory(metaclass=TestBase):
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_directory
        where {
        ?my_directory rdf:type cimv2:Win32_Directory .
        ?my_directory cimv2:Name "C:" .
        }
    """

class Testing_CIM_Process(metaclass=TestBase):
    """
    This selects all process ids and their names.
    """
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_process_name ?my_process_handle
        where {
        ?my_process rdf:type cimv2:CIM_Process .
        ?my_process cimv2:Handle ?my_process_handle .
        ?my_process cimv2:Name ?my_process_name .
        }
    """

class Testing_CIM_Process_WithHandle(metaclass=TestBase):
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_process_caption
        where {
        ?my_process rdf:type cimv2:CIM_Process .
        ?my_process cimv2:Handle %s .
        ?my_process cimv2:Caption ?my_process_caption .
        }
    """ % current_pid

class Testing_CIM_Directory_WithName(metaclass=TestBase):
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_dir
        where {
        ?my_dir rdf:type cimv2:Win32_Directory .
        ?my_dir cimv2:Name 'C:' .
        }
    """

class Testing_CIM_Directory_SubDirWithName(metaclass=TestBase):
    """
    This tests that directories separators are correctly handled.
    """
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_subdir
        where {
        ?my_subdir rdf:type cimv2:Win32_Directory .
        ?my_subdir cimv2:Name 'C:\\\\Windows' .
        }
    """

class Testing_CIM_ProcessExecutable_WithDependent(metaclass=TestBase):
    """
    This selects executable file and dlls used by the current process.
    """
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_file_name
        where {
        ?my_assoc rdf:type cimv2:CIM_ProcessExecutable .
        ?my_assoc cimv2:Dependent ?my_process .
        ?my_assoc cimv2:Antecedent ?my_file .
        ?my_process rdf:type cimv2:Win32_Process .
        ?my_process cimv2:Handle "%d" .
        ?my_file rdf:type cimv2:CIM_DataFile .
        ?my_file cimv2:Name ?my_file_name .
        }
    """ % current_pid

class Testing_CIM_ProcessExecutable_WithAntecedent(metaclass=TestBase):
    query = r"""
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_process_handle
        where {
        ?my_assoc rdf:type cimv2:CIM_ProcessExecutable .
        ?my_assoc cimv2:Dependent ?my_process .
        ?my_assoc cimv2:Antecedent ?my_file .
        ?my_process rdf:type cimv2:Win32_Process .
        ?my_process cimv2:Handle ?my_process_handle .
        ?my_file rdf:type cimv2:CIM_DataFile .
        ?my_file cimv2:Name "C:\\WINDOWS\\System32\\kernel32.dll" .
        }
    """

class Testing_CIM_DirectoryContainsFile_WithFile(metaclass=TestBase):
    """
    This returns the directory of a given file.
    """
    query = r"""
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_dir_name
        where {
        ?my_assoc_dir rdf:type cimv2:CIM_DirectoryContainsFile .
        ?my_assoc_dir cimv2:GroupComponent ?my_dir .
        ?my_assoc_dir cimv2:PartComponent ?my_file .
        ?my_file rdf:type cimv2:CIM_DataFile .
        ?my_file cimv2:Name "C:\\WINDOWS\\System32\\kernel32.dll" .
        ?my_dir rdf:type cimv2:Win32_Directory .
        ?my_dir cimv2:Name ?my_dir_name .
        }
    """

class Testing_CIM_DirectoryContainsFile_WithDir(metaclass=TestBase):
    """
    Files under the directory "C:"
    """
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_file_name
        where {
        ?my_assoc_dir rdf:type cimv2:CIM_DirectoryContainsFile .
        ?my_assoc_dir cimv2:GroupComponent ?my_dir .
        ?my_assoc_dir cimv2:PartComponent ?my_file .
        ?my_file rdf:type cimv2:CIM_DataFile .
        ?my_file cimv2:Name ?my_file_name .
        ?my_dir rdf:type cimv2:Win32_Directory .
        ?my_dir cimv2:Name 'C:' .
        }
    """

class Testing_Win32_SubDirectory_WithFile(metaclass=TestBase):
    """
    This returns the directory of a given directory.
    """
    query = r"""
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_dir_name
        where {
        ?my_assoc_dir rdf:type cimv2:Win32_SubDirectory .
        ?my_assoc_dir cimv2:GroupComponent ?my_dir .
        ?my_assoc_dir cimv2:PartComponent ?my_subdir .
        ?my_subdir rdf:type cimv2:Win32_Directory .
        ?my_subdir cimv2:Name "C:\\WINDOWS\\System32" .
        ?my_dir rdf:type cimv2:Win32_Directory .
        ?my_dir cimv2:Name ?my_dir_name .
        }
    """

class Testing_Win32_SubDirectory_WithDir(metaclass=TestBase):
    """
    Directories under the directory "C:"
    """
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_subdir_name
        where {
        ?my_assoc_dir rdf:type cimv2:Win32_SubDirectory .
        ?my_assoc_dir cimv2:GroupComponent ?my_dir .
        ?my_assoc_dir cimv2:PartComponent ?my_subdir .
        ?my_subdir rdf:type cimv2:Win32_Directory .
        ?my_subdir cimv2:Name ?my_subdir_name .
        ?my_dir rdf:type cimv2:Win32_Directory .
        ?my_dir cimv2:Name 'C:' .
        }
    """

class Testing_Win32_Directory_Win32_SubDirectory_Win32_SubDirectory(metaclass=TestBase):
    """
    This displays the sub-sub-directories of C:.
    """
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_dir_name3
        where {
        ?my_dir1 rdf:type cimv2:Win32_Directory .
        ?my_dir1 cimv2:Name "C:" .
        ?my_assoc_dir1 rdf:type cimv2:Win32_SubDirectory .
        ?my_assoc_dir1 cimv2:GroupComponent ?my_dir1 .
        ?my_assoc_dir1 cimv2:PartComponent ?my_dir2 .
        ?my_dir2 rdf:type cimv2:Win32_Directory .
        ?my_assoc_dir2 rdf:type cimv2:Win32_SubDirectory .
        ?my_assoc_dir2 cimv2:GroupComponent ?my_dir2 .
        ?my_assoc_dir2 cimv2:PartComponent ?my_dir3 .
        ?my_dir3 rdf:type cimv2:Win32_Directory .
        ?my_dir3 cimv2:Name ?my_dir_name3 .
        }
    """

class Testing_CIM_ProcessExecutable_CIM_DirectoryContainsFile_WithHandle(metaclass=TestBase):
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_dir_name
        where {
        ?my_assoc rdf:type cimv2:CIM_ProcessExecutable .
        ?my_assoc cimv2:Dependent ?my_process .
        ?my_assoc cimv2:Antecedent ?my_file .
        ?my_assoc_dir rdf:type cimv2:CIM_DirectoryContainsFile .
        ?my_assoc_dir cimv2:GroupComponent ?my_dir .
        ?my_assoc_dir cimv2:PartComponent ?my_file .
        ?my_process rdf:type cimv2:Win32_Process .
        ?my_process cimv2:Handle "%d" .
        ?my_file rdf:type cimv2:CIM_DataFile .
        ?my_file cimv2:Name ?my_file_name .
        ?my_dir rdf:type cimv2:Win32_Directory .
        ?my_dir cimv2:Name ?my_dir_name .
        }
    """ % current_pid

class Testing_CIM_ProcessExecutable_FullScan(metaclass=TestBase):
    """
    This gets the directories of all executables and libraries of all processes.
    """
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_file_name ?my_process_handle
        where {
        ?my_assoc rdf:type cimv2:CIM_ProcessExecutable .
        ?my_assoc cimv2:Dependent ?my_process .
        ?my_assoc cimv2:Antecedent ?my_file .
        ?my_process rdf:type cimv2:Win32_Process .
        ?my_process cimv2:Handle ?my_process_handle .
        ?my_file rdf:type cimv2:CIM_DataFile .
        ?my_file cimv2:Name ?my_file_name .
        }
    """

class Testing_CIM_ProcessExecutable_Groups(metaclass=TestBase):
    """
    This gets the directories of all executables and libraries of all processes.
    """
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_file_name (COUNT(?my_file_name) AS ?ELEMENTCOUNT)
        where {
        ?my_assoc rdf:type cimv2:CIM_ProcessExecutable .
        ?my_assoc cimv2:Dependent ?my_process .
        ?my_assoc cimv2:Antecedent ?my_file .
        ?my_process rdf:type cimv2:Win32_Process .
        ?my_process cimv2:Handle ?my_process_handle .
        ?my_file rdf:type cimv2:CIM_DataFile .
        ?my_file cimv2:Name ?my_file_name .
        }
        group by ?my_file_name
    """

class Testing_CIM_ProcessExecutable_CIM_DirectoryContainsFile(metaclass=TestBase):
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_dir_name
        where {
        ?my_assoc rdf:type cimv2:CIM_ProcessExecutable .
        ?my_assoc cimv2:Dependent ?my_process .
        ?my_assoc cimv2:Antecedent ?my_file .
        ?my_assoc_dir rdf:type cimv2:CIM_DirectoryContainsFile .
        ?my_assoc_dir cimv2:GroupComponent ?my_dir .
        ?my_assoc_dir cimv2:PartComponent ?my_file .
        ?my_process rdf:type cimv2:Win32_Process .
        ?my_file rdf:type cimv2:CIM_DataFile .
        ?my_file cimv2:Name ?my_file_name .
        ?my_dir rdf:type cimv2:Win32_Directory .
        ?my_dir cimv2:Name ?my_dir_name .
        }
    """

class Testing_CIM_Process_CIM_DataFile_SameCaption(metaclass=TestBase):
    query = """
        prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?same_caption
        where {
        ?my_process rdf:type cimv2:CIM_Process .
        ?my_process cimv2:Caption ?same_caption .
        ?my_file rdf:type cimv2:CIM_DataFile .
        ?my_file cimv2:Caption ?same_caption .
        }
    """
*/