package paquetage;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

// https://www.programcreek.com/java-api-examples/?api=org.eclipse.rdf4j.query.parser.ParsedQuery

public class SparqlBGPExtractorTest {
    static void CompareVariable(ObjectPattern.PredicateObjectPair a, String predicate, String variableName) {
        Assert.assertEquals(predicate, a.Predicate);
        Assert.assertEquals(variableName, a.variableName);
        Assert.assertEquals(null, a.ObjectContent);
    }

    static void CompareKeyValue(ObjectPattern.PredicateObjectPair a, String predicate, String content) {
        Assert.assertEquals(predicate, a.Predicate);
        Assert.assertEquals(null, a.variableName);
        Assert.assertEquals(new ValueTypePair(content, ValueTypePair.ValueType.STRING_TYPE), a.ObjectContent);
    }

    @Test
    public void ParsePlainQuery() throws Exception {
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
        Assert.assertEquals(1, extractor.patternsAsArray().size());
        Assert.assertNotEquals(null, extractor.FindObjectPattern("obs"));
    }

    @Test
    /***
     * Checks patterns detection with a constant value (the object).
     */
    public void Parse_Win32_Directory_NoVariable() throws Exception {
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
        Assert.assertEquals(PresentUtils.toCIMV2("Win32_Directory"), patternWin32_Directory.ClassName);
        Assert.assertEquals(1, patternWin32_Directory.Members.size());
        CompareKeyValue(patternWin32_Directory.Members.get(0), PresentUtils.toCIMV2("Name"), "C:");
    }

    @Test
    /***
     * Checks that a pattern with a variable value (the object) is detected.
     */
    public void Parse_Win32_Directory_OneVariable() throws Exception {
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
        Assert.assertEquals(PresentUtils.toCIMV2("Win32_Directory"), patternWin32_Directory.ClassName);
        Assert.assertEquals(1, patternWin32_Directory.Members.size());
        CompareVariable(patternWin32_Directory.Members.get(0), PresentUtils.toCIMV2("Name"), "my_name");
    }

    static void FindAndCompareKeyValue(ArrayList<ObjectPattern.PredicateObjectPair> members, String predicate, boolean is_variable, String content) {
        ObjectPattern.PredicateObjectPair pairPredObj = members.stream()
                .filter(x -> x.Predicate.equals(predicate))
                .findFirst().orElse(null);
        Assert.assertNotEquals(null, pairPredObj);
        if(is_variable)
            CompareVariable(pairPredObj, predicate, content);
        else
            CompareKeyValue(pairPredObj, predicate, content);
    }

    @Test
    /***
     * Checks the BGPs extracted from a query with three types.
     */
    public void Parse_CIM_ProcessExecutable_Win32_Process_CIM_DataFile() throws Exception {
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
        Assert.assertEquals(patternCIM_ProcessExecutable.ClassName, PresentUtils.toCIMV2("CIM_ProcessExecutable"));
        Assert.assertEquals(patternCIM_ProcessExecutable.Members.size(), 2);
        FindAndCompareKeyValue(patternCIM_ProcessExecutable.Members, PresentUtils.toCIMV2("Dependent"), true, "my_process");
        FindAndCompareKeyValue(patternCIM_ProcessExecutable.Members, PresentUtils.toCIMV2("Antecedent"), true, "my_file");

        ObjectPattern patternWin32_Process = extractor.FindObjectPattern("my_process");
        Assert.assertEquals(patternWin32_Process.ClassName, PresentUtils.toCIMV2("Win32_Process"));
        Assert.assertEquals(patternWin32_Process.Members.size(), 1);
        FindAndCompareKeyValue(patternWin32_Process.Members, PresentUtils.toCIMV2("Handle"), true, "my_process_handle");

        ObjectPattern patternCIM_DataFile = extractor.FindObjectPattern("my_file");
        Assert.assertEquals(patternCIM_DataFile.ClassName, PresentUtils.toCIMV2("CIM_DataFile"));
        Assert.assertEquals(patternCIM_DataFile.Members.size(), 1);
        FindAndCompareKeyValue(patternCIM_DataFile.Members, PresentUtils.toCIMV2("Name"), false, "C:\\WINDOWS\\System32\\kernel32.dll");
    }

    /***
     * Checks the BGPs extracted from an arbitrary query with a union.
     */
    @Ignore("Not working yet")
    @Test
    public void Parse_Union_Basic() throws Exception {
        String sparqlQuery = """
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX foaf: <http://xmlns.com/foaf/0.1/>

                SELECT * WHERE
                {
                    {
                        SELECT ?page ("A" AS ?type) WHERE
                        {
                             ?s rdfs:label "Microsoft"@en;
                                foaf:page ?page
                        }
                    }
                    UNION
                    {
                        SELECT ?page ("B" AS ?type) WHERE
                        {
                             ?s rdfs:label "Apple"@en;
                                foaf:page ?page
                        }
                    }
                }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("page", "type"), extractor.bindings);
        List<ObjectPattern> patterns = extractor.patternsAsArray();
        Assert.assertEquals(1, patterns.size());
        Assert.assertNotEquals(extractor.FindObjectPattern("s"), null);
        ObjectPattern firstPattern = patterns.get(0);
        Assert.assertEquals(null, firstPattern.ClassName);
        Assert.assertEquals("s", firstPattern.VariableName);
        Assert.assertEquals(4, firstPattern.Members.size());

        CompareKeyValue(firstPattern.Members.get(0), "http://www.w3.org/2000/01/rdf-schema#label", "Microsoft");
        CompareVariable(firstPattern.Members.get(1), "http://xmlns.com/foaf/0.1/page", "page");
        CompareKeyValue(firstPattern.Members.get(2), "http://www.w3.org/2000/01/rdf-schema#label", "Apple");
        CompareVariable(firstPattern.Members.get(3), "http://xmlns.com/foaf/0.1/page", "page");
    }

    /** Another union from the same class in two different "where" blocks.
     * It must NOT return a single ObjectPattern for the same class because these are two distinct lists
     * of Win32_Process instances. Logically, it should not return a single ObjectPattern like:
     *     Class=Win32_Process
     *         Caption="Caption1"
     *         Caption="Caption2"
     * ... but something like:
     *     Class=Win32_Process
     *         Caption="Caption1"
     *     Class=Win32_Process
     *         Caption="Caption2"
     *
     * It could return two distinct ObjectPatterns in the same list, but there should NOT be a nested loop
     * on the first, then the second ObjectPattern : They should be fetched independently.
     *
     * This is different, but related to uncorrelated list of instances which should not yield nested queries,
     * but must return a cartesian product:
     *     select * where
     *     {
     *         ?process rdf:type cimv2:CIM_Process .
     *         ?datafile rdf:type cimv2:CIM_DataFile .
     *     }
     * For performance reasons, this should be executed with two independent queries,
     * followed by a cartesian product of CIM_Process instances and CIM_DataFile instances.
     * However, doing nested loops returns the same result (but much slower).
     *
     * @throws Exception
     */
    @Ignore("Not working yet")
    @Test
    public void Parse_Union_CheckNoMix() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process where 
                    {
                        {
                            select ?process1
                            where {
                                ?process1 cimv2:Win32_Process.Caption "Caption1" .
                            }
                        }
                        union
                        {
                            select ?process2
                            where {
                                ?process2 cimv2:Win32_Process.Caption "Caption2" .
                            }
                        }
                    }
                    
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);

        Assert.assertEquals(Set.of("process"), extractor.bindings);
        List<ObjectPattern> patterns = extractor.patternsAsArray();
        for(ObjectPattern objectPattern: patterns) {
            System.out.println("objectPattern=" + objectPattern);
        }
        Assert.assertEquals(2, patterns.size());

        ObjectPattern pattern1 = extractor.FindObjectPattern("process1");
        Assert.assertNotEquals(pattern1, null);
        Assert.assertEquals(null, pattern1.ClassName);
        Assert.assertEquals("process1", pattern1.VariableName);
        Assert.assertEquals(1, pattern1.Members.size());
        CompareKeyValue(pattern1.Members.get(0), "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.Caption", "Caption1");

        ObjectPattern pattern2 = extractor.FindObjectPattern("process2");
        Assert.assertNotEquals(pattern2, null);
        Assert.assertEquals("process2", pattern2.VariableName);
        Assert.assertEquals(1, pattern2.Members.size());
        CompareKeyValue(pattern2.Members.get(0), "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.Caption", "Caption2");
    }


    /***
     * Checks the BGPs extracted from an arbitrary query with a filter statement.
     */
    @Test
    public void Parse_Filter() throws Exception {
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
        Assert.assertEquals(2, patterns.size());

        Assert.assertNotEquals(extractor.FindObjectPattern("about"), null);
        ObjectPattern firstPattern = patterns.get(0);
        Assert.assertEquals(null, firstPattern.ClassName);
        Assert.assertEquals("about", firstPattern.VariableName);

        Assert.assertEquals(1, firstPattern.Members.size());
        CompareVariable(firstPattern.Members.get(0), "http://schema.org/name", "subject_name");

        Assert.assertNotEquals(extractor.FindObjectPattern("s"), null);
        ObjectPattern secondPattern = patterns.get(1);
        Assert.assertEquals(null, secondPattern.ClassName);
        Assert.assertEquals("s", secondPattern.VariableName);

        Assert.assertEquals(1, secondPattern.Members.size());
        CompareVariable(secondPattern.Members.get(0), "http://schema.org/about", "about");
    }

    @Test
    /***
     * Checks the BGPs extracted from an arbitrary query with a group statement.
     */
    public void Parse_GroupSum() throws Exception {
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
        Assert.assertEquals(null, firstPattern.ClassName);
        Assert.assertEquals("my0_dir", firstPattern.VariableName);

        Assert.assertEquals(1, firstPattern.Members.size());
        CompareKeyValue(firstPattern.Members.get(0), "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Directory.Name", "C:\\Program Files (x86)");

        Assert.assertNotEquals(extractor.FindObjectPattern("my1_assoc"), null);
        ObjectPattern secondPattern = patterns.get(1);
        Assert.assertEquals(null, secondPattern.ClassName);
        Assert.assertEquals("my1_assoc", secondPattern.VariableName);

        Assert.assertEquals(2, secondPattern.Members.size());
        CompareVariable(secondPattern.Members.get(0), "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_DirectoryContainsFile.PartComponent", "my2_file");
        CompareVariable(secondPattern.Members.get(0), "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_DirectoryContainsFile.PartComponent", "my2_file");

        Assert.assertNotEquals(extractor.FindObjectPattern("my2_file"), null);
        ObjectPattern thirdPattern = patterns.get(2);
        Assert.assertEquals(null, thirdPattern.ClassName);
        Assert.assertEquals("my2_file", thirdPattern.VariableName);

        Assert.assertEquals(1, thirdPattern.Members.size());
        CompareVariable(secondPattern.Members.get(0), "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_DirectoryContainsFile.PartComponent", "my2_file");
    }

    @Test
    /***
     * Checks the bindings extracted from an arbitrary query with a group statement.
     */
    public void Parse_GroupMinMaxSum() throws Exception {
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
    public void Parse_Optional() throws Exception {
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
        Assert.assertEquals(3, patterns.size());

        Assert.assertNotEquals(extractor.FindObjectPattern("author"), null);
        ObjectPattern firstPattern = patterns.get(0);
        Assert.assertEquals(null, firstPattern.ClassName);
        Assert.assertEquals("author", firstPattern.VariableName);
        Assert.assertEquals(1, firstPattern.Members.size());
        CompareVariable(firstPattern.Members.get(0), "http://schema.org/name", "author_name");

        Assert.assertNotEquals(extractor.FindObjectPattern("creator"), null);
        ObjectPattern secondPattern = patterns.get(1);
        Assert.assertEquals(null, secondPattern.ClassName);
        Assert.assertEquals("creator", secondPattern.VariableName);
        CompareVariable(secondPattern.Members.get(0), "http://schema.org/name", "creator_name");

        Assert.assertNotEquals(extractor.FindObjectPattern("s"), null);
        ObjectPattern thirdPattern = patterns.get(2);
        Assert.assertEquals(null, thirdPattern.ClassName);
        Assert.assertEquals("s", thirdPattern.VariableName);
        Assert.assertEquals(2, thirdPattern.Members.size());
        CompareVariable(thirdPattern.Members.get(0), "http://schema.org/creator", "creator");
    }

    @Ignore("Not working yet")
    @Test
    public void Parse_SubQuery_Basic() throws Exception {
        // Thanks to https://en.wikibooks.org/wiki/SPARQL/Subqueries and https://en.wikibooks.org/wiki/SPARQL/Prefixes
        String sparqlQuery = """
                       PREFIX wd: <http://www.wikidata.org/entity/>
                       PREFIX wds: <http://www.wikidata.org/entity/statement/>
                       PREFIX wdv: <http://www.wikidata.org/value/>
                       PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                       PREFIX wikibase: <http://wikiba.se/ontology#>
                       PREFIX p: <http://www.wikidata.org/prop/>
                       PREFIX ps: <http://www.wikidata.org/prop/statement/>
                       PREFIX pq: <http://www.wikidata.org/prop/qualifier/>
                       PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                       PREFIX bd: <http://www.bigdata.com/rdf#>
                       SELECT ?countryLabel ?population (round(?population/?worldpopulation*1000)/10 AS ?percentage)
                       WHERE {
                         ?country wdt:P31 wd:Q3624078;    # is a sovereign state
                                  wdt:P1082 ?population.

                         {
                           # subquery to determine ?worldpopulation
                           SELECT (sum(?population) AS ?worldpopulation)
                           WHERE {
                             ?country wdt:P31 wd:Q3624078;    # is a sovereign state
                                      wdt:P1082 ?population.
                           }
                         }

                         SERVICE wikibase:label {bd:serviceParam wikibase:language "[AUTO_LANGUAGE],en".}
                       }
                       ORDER BY desc(?population)
                """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        System.out.println("extractor.bindings=" + extractor.bindings);
        List<ObjectPattern> patterns = extractor.patternsAsArray();
        Assert.assertEquals(1, patterns.size());

        for(ObjectPattern pattern : patterns )
        {
            System.out.println("pattern.className"+pattern.ClassName);
            System.out.println("pattern.VariableName"+pattern.VariableName);
            for(ObjectPattern.PredicateObjectPair pop:pattern.Members) {
                System.out.println("    pattern.Predicate   :" + pop.Predicate);
                System.out.println("    pattern.Content     :" + pop.ObjectContent.toDisplayString());
                System.out.println("    pattern.variableName:" + pop.variableName);
                System.out.println("");
            }
        }

        Assert.assertNotEquals(extractor.FindObjectPattern("country"), null);
        ObjectPattern firstPattern = patterns.get(0);
        Assert.assertEquals(null, firstPattern.ClassName);
        Assert.assertEquals("country", firstPattern.VariableName);
        Assert.assertEquals(4, firstPattern.Members.size());
        CompareKeyValue(firstPattern.Members.get(0), "http://www.wikidata.org/prop/direct/P31", "http://www.wikidata.org/entity/Q3624078");
        CompareVariable(firstPattern.Members.get(1), "http://www.wikidata.org/prop/direct/P1082", "population");
        CompareKeyValue(firstPattern.Members.get(2), "http://www.wikidata.org/prop/direct/P31", "http://www.wikidata.org/entity/Q3624078");
        CompareVariable(firstPattern.Members.get(3), "http://www.wikidata.org/prop/direct/P1082", "population");
    }

    /** This checks that instances of the same class in several sub-queries are not mixed together.
     * This is similar to queries with instances in different elements of unions: They must not be mixed together
     * in the same nested WMI search, but must be loaded independently.
     * @throws Exception
     */
    @Ignore("Not working yet")
    @Test
    public void Parse_SubQuery_NoMix() throws Exception {
        /** This query does not makes sense and is just a triple cartesian product on directories.
         * It must yield three distinct ObjectPattern on the same class Win32_Directory.
         */
        String sparqlQuery = """
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?file_name ?file_caption
            where {
                ?file1 rdf:type cimv2:CIM_DataFile .
                ?file1 cimv2:Name ?file_name .
                ?file1 cimv2:Caption ?file_caption .
                ?file1 cimv2:Drive "C:" .
                {
                    select ?file_name
                    where {
                        ?file2 cimv2:CIM_DataFile.Name ?file_name .
                        ?file2 cimv2:CIM_DataFile.FileSize 123456 .
                    }
                }
                {
                    select ?file_caption
                    where {
                        ?file3 cimv2:CIM_DataFile.Caption ?file_caption .
                        ?file3 cimv2:CIM_DataFile.Extension "xyz" .
                    }
                }
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        System.out.println("extractor.bindings=" + extractor.bindings);
        List<ObjectPattern> patterns = extractor.patternsAsArray();
        Assert.assertEquals(3, patterns.size());

        for(ObjectPattern pattern : patterns )
        {
            System.out.println("pattern.className"+pattern.ClassName);
            System.out.println("pattern.VariableName"+pattern.VariableName);
            for(ObjectPattern.PredicateObjectPair pop:pattern.Members) {
                System.out.println("    pattern.Predicate   :" + pop.Predicate);
                System.out.println("    pattern.Content     :" + pop.ObjectContent.toDisplayString());
                System.out.println("    pattern.variableName:" + pop.variableName);
                System.out.println("");
            }
        }

        Assert.assertNotEquals(extractor.FindObjectPattern("country"), null);
        ObjectPattern firstPattern = patterns.get(0);
        Assert.assertEquals(null, firstPattern.ClassName);
        Assert.assertEquals("country", firstPattern.VariableName);
        Assert.assertEquals(4, firstPattern.Members.size());
        CompareKeyValue(firstPattern.Members.get(0), "http://www.wikidata.org/prop/direct/P31", "http://www.wikidata.org/entity/Q3624078");
    }

    /***
     * Checks that the BGPs of a federated query are NOT extracted.
     */
    @Test
    public void Parse_FederatedQuery() throws Exception {
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
        Assert.assertEquals(PresentUtils.toCIMV2("Win32_Directory"), patternWin32_Directory.ClassName);
        Assert.assertEquals(1, patternWin32_Directory.Members.size());
        CompareVariable(patternWin32_Directory.Members.get(0), PresentUtils.toCIMV2("Name"), "directory_name");
    }

    /** Property pathes are not handled yet.
     * This query is equivalent to:
     * select ?display_name ?dependency_type
     * where {
     *     ?service1 cimv2:Win32_Service.DisplayName "Windows Search" .
     *     ?assoc rdf:type cimv2:Win32_DependentService .
     *     ?assoc cimv2:Dependent ?service1 .
     *     ?assoc cimv2:Antecedent ?service2 .
     *     ?service2 cimv2:Win32_Service.DisplayName ?display_name .
     * }
     *
     * @throws Exception
     */
    @Test (expected = RuntimeException.class)
    public void PropertyPath_Win32_DependentService_One() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?service_name
                    where {
                        ?service1 cimv2:Win32_Service.DisplayName "Windows Search" .
                        ?service1 ^cimv2:Win32_DependentService.Dependent/cimv2:Win32_DependentService.Antecedent ?service2 .
                        ?service2 cimv2:Win32_Service.DisplayName ?service_name .
                    }
                """;
        try {
            SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
            for(ObjectPattern objectPattern : extractor.patternsAsArray()) {
                System.out.println("    " + objectPattern);
            }
        }
        catch(Exception exc)
        {
            System.out.println("exc=" + exc);
            Assert.assertEquals("Anonymous WMI subjects are not allowed yet.", exc.getMessage());
            throw exc;
        }
        Assert.fail("SparqlBGPExtractor did not throw an exception");
    }

    /** Property paths are not handled yet.
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
    public void PropertyPath_Win32_DependentService_Many() throws Exception {
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