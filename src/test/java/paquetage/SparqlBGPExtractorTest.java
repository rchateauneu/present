package paquetage;

import com.google.common.collect.Sets;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Values;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

// https://www.programcreek.com/java-api-examples/?api=org.eclipse.rdf4j.query.parser.ParsedQuery

public class SparqlBGPExtractorTest {
    ValueFactory factory = SimpleValueFactory.getInstance();

    static ObjectPattern FindObjectPattern(SparqlBGPExtractor extractor, String variable) {
        ObjectPattern pattern = extractor.patternsMap.get(variable);
        Assert.assertNotEquals(null, pattern);
        Assert.assertEquals(variable, pattern.VariableName);
        return pattern;
    }

    static String toSurvol(String term) {
        return WmiOntology.survol_url_prefix + term;
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
        Assert.assertNotEquals(null, FindObjectPattern(extractor, "obs"));
    }

    static void CompareKeyValue(ObjectPattern.PredicateObjectPair a, String predicate, boolean isVariable, String content) {
        Assert.assertEquals(predicate, a.Predicate());
        Assert.assertEquals(isVariable, a.isVariable());
        Assert.assertEquals(content, a.Content());
    }

    @Test
    /***
     * Checks patterns detection with a constant value (the object).
     */
    public void Parse_Win32_Directory_NoVariable() throws Exception {
        String sparqlQuery = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_directory
            where {
                ?my_directory rdf:type cim:Win32_Directory .
                ?my_directory cim:Name "C:" .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("my_directory"), extractor.bindings);

        Assert.assertEquals(1, extractor.patternsAsArray().size());

        ObjectPattern patternWin32_Directory = FindObjectPattern(extractor, "my_directory");
        Assert.assertEquals(toSurvol("Win32_Directory"), patternWin32_Directory.className);
        Assert.assertEquals(1, patternWin32_Directory.Members.size());
        CompareKeyValue(patternWin32_Directory.Members.get(0), toSurvol("Name"), false, "C:");
    }

    @Test
    /***
     * Checks that a pattern with a variable value (the object) is detected.
     */
    public void Parse_Win32_Directory_OneVariable() throws Exception {
        String sparqlQuery = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_name
            where {
                ?my_directory rdf:type cim:Win32_Directory .
                ?my_directory cim:Name ?my_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("my_name"), extractor.bindings);

        Assert.assertEquals(1, extractor.patternsAsArray().size());

        ObjectPattern patternWin32_Directory = FindObjectPattern(extractor, "my_directory");
        Assert.assertEquals(toSurvol("Win32_Directory"), patternWin32_Directory.className);
        Assert.assertEquals(1, patternWin32_Directory.Members.size());
        CompareKeyValue(patternWin32_Directory.Members.get(0), toSurvol("Name"), true, "my_name");
    }

    static void FindAndCompareKeyValue(ArrayList<ObjectPattern.PredicateObjectPair> members, String predicate, boolean is_variable, String content) {
        ObjectPattern.PredicateObjectPair pairPredObj = members.stream()
                .filter(x -> x.Predicate().equals(predicate))
                .findFirst().orElse(null);
        Assert.assertNotEquals(null, pairPredObj);
        CompareKeyValue(pairPredObj, predicate, is_variable, content);
    }

    @Test
    /***
     * Checks the BGPs extracted from a query with three types.
     */
    public void Parse_CIM_ProcessExecutable_Win32_Process_CIM_DataFile() throws Exception {
        String sparqlQuery = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_file_name
            where {
                ?my_assoc rdf:type cim:CIM_ProcessExecutable .
                ?my_assoc cim:Dependent ?my_process .
                ?my_assoc cim:Antecedent ?my_file .
                ?my_process rdf:type cim:Win32_Process .
                ?my_process cim:Handle ?my_process_handle .
                ?my_file rdf:type cim:CIM_DataFile .
                ?my_file cim:Name "C:\\\\WINDOWS\\\\System32\\\\kernel32.dll" .
                }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("my_file_name"), extractor.bindings);

        Assert.assertEquals(extractor.patternsAsArray().size(), 3);

        ObjectPattern patternCIM_ProcessExecutable = FindObjectPattern(extractor, "my_assoc");
        Assert.assertEquals(patternCIM_ProcessExecutable.className, toSurvol("CIM_ProcessExecutable"));
        Assert.assertEquals(patternCIM_ProcessExecutable.Members.size(), 2);
        FindAndCompareKeyValue(patternCIM_ProcessExecutable.Members, toSurvol("Dependent"), true, "my_process");
        FindAndCompareKeyValue(patternCIM_ProcessExecutable.Members, toSurvol("Antecedent"), true, "my_file");

        ObjectPattern patternWin32_Process = FindObjectPattern(extractor, "my_process");
        Assert.assertEquals(patternWin32_Process.className, toSurvol("Win32_Process"));
        Assert.assertEquals(patternWin32_Process.Members.size(), 1);
        FindAndCompareKeyValue(patternWin32_Process.Members, toSurvol("Handle"), true, "my_process_handle");

        ObjectPattern patternCIM_DataFile = FindObjectPattern(extractor, "my_file");
        Assert.assertEquals(patternCIM_DataFile.className, toSurvol("CIM_DataFile"));
        Assert.assertEquals(patternCIM_DataFile.Members.size(), 1);
        FindAndCompareKeyValue(patternCIM_DataFile.Members, toSurvol("Name"), false, "C:\\WINDOWS\\System32\\kernel32.dll");
    }

    @Test
    /***
     * Checks the BGPs extracted from an arbitrary query with a union.
     */
    public void Parse_Union() throws Exception {
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
        Assert.assertNotEquals(FindObjectPattern(extractor, "s"), null);
        ObjectPattern firstPattern = patterns.get(0);
        Assert.assertEquals(null, firstPattern.className);
        Assert.assertEquals("s", firstPattern.VariableName);
        Assert.assertEquals(4, firstPattern.Members.size());

        CompareKeyValue(firstPattern.Members.get(0), "http://www.w3.org/2000/01/rdf-schema#label", false, "Microsoft");
        CompareKeyValue(firstPattern.Members.get(1), "http://xmlns.com/foaf/0.1/page", true, "page");
        CompareKeyValue(firstPattern.Members.get(2), "http://www.w3.org/2000/01/rdf-schema#label", false, "Apple");
        CompareKeyValue(firstPattern.Members.get(3), "http://xmlns.com/foaf/0.1/page", true, "page");
    }

    @Test
    /***
     * Checks the BGPs extracted from an arbitrary query with a filter statement.
     */
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

        Assert.assertNotEquals(FindObjectPattern(extractor, "about"), null);
        ObjectPattern firstPattern = patterns.get(0);
        Assert.assertEquals(null, firstPattern.className);
        Assert.assertEquals("about", firstPattern.VariableName);

        Assert.assertEquals(1, firstPattern.Members.size());
        CompareKeyValue(firstPattern.Members.get(0), "http://schema.org/name", true, "subject_name");

        Assert.assertNotEquals(FindObjectPattern(extractor, "s"), null);
        ObjectPattern secondPattern = patterns.get(1);
        Assert.assertEquals(null, secondPattern.className);
        Assert.assertEquals("s", secondPattern.VariableName);

        Assert.assertEquals(1, secondPattern.Members.size());
        CompareKeyValue(secondPattern.Members.get(0), "http://schema.org/about", true, "about");
    }

    @Test
    /***
     * Checks the BGPs extracted from an arbitrary query with a group statement.
     */
    public void Parse_Group() throws Exception {
        String sparqlQuery = """
                prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
                prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                select (SUM(?file_size) as ?size_sum)
                where {
                    ?my2_file cim:CIM_DataFile.FileSize ?file_size .
                    ?my1_assoc cim:CIM_DirectoryContainsFile.PartComponent ?my2_file .
                    ?my1_assoc cim:GroupComponent ?my0_dir .
                    ?my0_dir cim:Win32_Directory.Name "C:\\\\Program Files (x86)" .
                } group by ?my2_file
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(Set.of("size_sum"), extractor.bindings);
        List<ObjectPattern> patterns = extractor.patternsAsArray();
        Assert.assertEquals(3, patterns.size());

        Assert.assertNotEquals(FindObjectPattern(extractor, "my0_dir"), null);
        ObjectPattern firstPattern = patterns.get(0);
        Assert.assertEquals(null, firstPattern.className);
        Assert.assertEquals("my0_dir", firstPattern.VariableName);

        Assert.assertEquals(1, firstPattern.Members.size());
        CompareKeyValue(firstPattern.Members.get(0), "http://www.primhillcomputers.com/ontology/survol#Win32_Directory.Name", false, "C:\\Program Files (x86)");

        Assert.assertNotEquals(FindObjectPattern(extractor, "my1_assoc"), null);
        ObjectPattern secondPattern = patterns.get(1);
        Assert.assertEquals(null, secondPattern.className);
        Assert.assertEquals("my1_assoc", secondPattern.VariableName);

        Assert.assertEquals(2, secondPattern.Members.size());
        CompareKeyValue(secondPattern.Members.get(0), "http://www.primhillcomputers.com/ontology/survol#CIM_DirectoryContainsFile.PartComponent", true, "my2_file");
        CompareKeyValue(secondPattern.Members.get(0), "http://www.primhillcomputers.com/ontology/survol#CIM_DirectoryContainsFile.PartComponent", true, "my2_file");

        Assert.assertNotEquals(FindObjectPattern(extractor, "my2_file"), null);
        ObjectPattern thirdPattern = patterns.get(2);
        Assert.assertEquals(null, thirdPattern.className);
        Assert.assertEquals("my2_file", thirdPattern.VariableName);

        Assert.assertEquals(1, thirdPattern.Members.size());
        CompareKeyValue(secondPattern.Members.get(0), "http://www.primhillcomputers.com/ontology/survol#CIM_DirectoryContainsFile.PartComponent", true, "my2_file");
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

        Assert.assertNotEquals(FindObjectPattern(extractor, "author"), null);
        ObjectPattern firstPattern = patterns.get(0);
        Assert.assertEquals(null, firstPattern.className);
        Assert.assertEquals("author", firstPattern.VariableName);
        Assert.assertEquals(1, firstPattern.Members.size());
        CompareKeyValue(firstPattern.Members.get(0), "http://schema.org/name", true, "author_name");

        Assert.assertNotEquals(FindObjectPattern(extractor, "creator"), null);
        ObjectPattern secondPattern = patterns.get(1);
        Assert.assertEquals(null, secondPattern.className);
        Assert.assertEquals("creator", secondPattern.VariableName);
        CompareKeyValue(secondPattern.Members.get(0), "http://schema.org/name", true, "creator_name");

        Assert.assertNotEquals(FindObjectPattern(extractor, "s"), null);
        ObjectPattern thirdPattern = patterns.get(2);
        Assert.assertEquals(null, thirdPattern.className);
        Assert.assertEquals("s", thirdPattern.VariableName);
        Assert.assertEquals(2, thirdPattern.Members.size());
        CompareKeyValue(thirdPattern.Members.get(0), "http://schema.org/creator", true, "creator");
    }

    @Test
    public void Parse_SubQuery() throws Exception {
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
            System.out.println("pattern.className"+pattern.className);
            System.out.println("pattern.VariableName"+pattern.VariableName);
            for(ObjectPattern.PredicateObjectPair pop:pattern.Members) {
                System.out.println("    pattern.Predicate :" + pop.Predicate());
                System.out.println("    pattern.Content   :" + pop.Content());
                System.out.println("    pattern.isVariable:" + pop.isVariable());
                System.out.println("");
            }
        }

        Assert.assertNotEquals(FindObjectPattern(extractor, "country"), null);
        ObjectPattern firstPattern = patterns.get(0);
        Assert.assertEquals(null, firstPattern.className);
        Assert.assertEquals("country", firstPattern.VariableName);
        Assert.assertEquals(4, firstPattern.Members.size());
        CompareKeyValue(firstPattern.Members.get(0), "http://www.wikidata.org/prop/direct/P31", false, "http://www.wikidata.org/entity/Q3624078");
        CompareKeyValue(firstPattern.Members.get(1), "http://www.wikidata.org/prop/direct/P1082", true, "population");
        CompareKeyValue(firstPattern.Members.get(2), "http://www.wikidata.org/prop/direct/P31", false, "http://www.wikidata.org/entity/Q3624078");
        CompareKeyValue(firstPattern.Members.get(3), "http://www.wikidata.org/prop/direct/P1082", true, "population");
    }


    @Test
    /***
     * Create triples from BGPs and variable-value pairs.
     */
    public void TriplesGenerationFromBGPs_1() throws Exception {
        String sparqlQuery = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name
            where {
                ?my_dir rdf:type cim:Win32_Directory .
                ?my_dir cim:Name ?dir_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("dir_name"));

        Assert.assertEquals(extractor.patternsAsArray().size(), 1);

        // Check the exact content of the BGP.
        ObjectPattern patternWin32_Directory = FindObjectPattern(extractor, "my_dir");
        Assert.assertEquals(patternWin32_Directory.className, toSurvol("Win32_Directory"));
        Assert.assertEquals(patternWin32_Directory.Members.size(), 1);
        CompareKeyValue(patternWin32_Directory.Members.get(0), toSurvol("Name"), true, "dir_name");

        // Now it generates triples from the patterns, forcing the values of the single variable.
        String dirIri = "any_iri_will_do";
        List<GenericSelecter.Row> rows = Arrays.asList(new GenericSelecter.Row(Map.of(
        "my_dir", new GenericSelecter.Row.ValueTypePair(dirIri, GenericSelecter.ValueType.NODE_TYPE),
        "dir_name", new GenericSelecter.Row.ValueTypePair("C:", GenericSelecter.ValueType.STRING_TYPE)))
        );

        List<Triple> triples = extractor.GenerateTriples(rows);

        // Now check the content of the generated triples.
        Assert.assertEquals(2, triples.size());

        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIri),
                Values.iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                Values.iri(toSurvol("Win32_Directory")))
        ));
        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIri),
                Values.iri(toSurvol("Name")),
                Values.literal("C:"))
        ));
    }

    @Test
    /***
     * Create triples from BGPs and variable-value pairs.
     */
    public void TriplesGenerationFromBGPs_2() throws Exception {
        String sparqlQuery = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name
            where {
                ?my_dir rdf:type cim:Win32_Directory .
                ?my_dir cim:Name ?dir_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("dir_name"));

        Assert.assertEquals(extractor.patternsAsArray().size(), 1);

        // Check the exact content of the BGP.
        ObjectPattern patternWin32_Directory = FindObjectPattern(extractor, "my_dir");
        Assert.assertEquals(patternWin32_Directory.className, toSurvol("Win32_Directory"));
        Assert.assertEquals(patternWin32_Directory.Members.size(), 1);
        CompareKeyValue(patternWin32_Directory.Members.get(0), toSurvol("Name"), true, "dir_name");

        // Now it generates triples from the patterns, forcing the values of the single variable.
        String dirIriC = "iriC";
        String dirIriD = "iriD";
        List<GenericSelecter.Row> rows = Arrays.asList(
                new GenericSelecter.Row(Map.of(
                        "my_dir", new GenericSelecter.Row.ValueTypePair(dirIriC, GenericSelecter.ValueType.NODE_TYPE),
                        "dir_name", new GenericSelecter.Row.ValueTypePair("C:", GenericSelecter.ValueType.STRING_TYPE))),
                new GenericSelecter.Row(Map.of(
                        "my_dir", new GenericSelecter.Row.ValueTypePair(dirIriD, GenericSelecter.ValueType.NODE_TYPE),
                        "dir_name", new GenericSelecter.Row.ValueTypePair("D:", GenericSelecter.ValueType.STRING_TYPE))));

        List<Triple> triples = extractor.GenerateTriples(rows);

        for(Triple triple: triples) {
            System.out.println("T=" + triple);
        }

        // Now check the content of the generated triples.
        Assert.assertEquals(4, triples.size());

        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIriC),
                Values.iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                Values.iri(toSurvol("Win32_Directory")))
        ));
        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIriC),
                Values.iri(toSurvol("Name")),
                Values.literal("C:"))
        ));
        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIriD),
                Values.iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                Values.iri(toSurvol("Win32_Directory")))
        ));
        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIriD),
                Values.iri(toSurvol("Name")),
                Values.literal("D:"))
        ));
    }

    @Test
    public void TriplesGenerationFromBGPs_3() throws Exception {
        String sparqlQuery = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name ?dir_caption
            where {
                ?my_dir rdf:type cim:Win32_Directory .
                ?my_dir cim:Name ?dir_name .
                ?my_dir cim:Caption ?dir_caption .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("dir_caption", "dir_name"));

        Assert.assertEquals(extractor.patternsAsArray().size(), 1);

        // Check the exact content of the BGP.
        ObjectPattern patternWin32_Directory = FindObjectPattern(extractor, "my_dir");
        Assert.assertEquals(patternWin32_Directory.className, toSurvol("Win32_Directory"));
        Assert.assertEquals(patternWin32_Directory.Members.size(), 2);
        CompareKeyValue(patternWin32_Directory.Members.get(1), toSurvol("Caption"), true, "dir_caption");
        CompareKeyValue(patternWin32_Directory.Members.get(0), toSurvol("Name"), true, "dir_name");

        // Now it generates triples from the patterns, forcing the values of the single variable.
        String dirIri = "arbitrary_iri";
        List<GenericSelecter.Row> rows = Arrays.asList(new GenericSelecter.Row(Map.of(
                "my_dir", new GenericSelecter.Row.ValueTypePair(dirIri, GenericSelecter.ValueType.NODE_TYPE),
                "dir_name", new GenericSelecter.Row.ValueTypePair("C:", GenericSelecter.ValueType.STRING_TYPE),
                "dir_caption", new GenericSelecter.Row.ValueTypePair("This is a text", GenericSelecter.ValueType.STRING_TYPE))));

        List<Triple> triples = extractor.GenerateTriples(rows);

        for(Triple triple: triples) {
            System.out.println("T=" + triple);
        }
        Assert.assertEquals(3, triples.size());

        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIri),
                Values.iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                Values.iri(toSurvol("Win32_Directory")))
        ));
        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIri),
                Values.iri(toSurvol("Name")),
                Values.literal("C:"))
        ));
        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIri),
                Values.iri(toSurvol("Caption")),
                Values.literal("This is a text"))
        ));
    }

}

/*

    query_all_classes = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        select distinct ?the_class
        where {
        ?the_class rdf:type rdfs:Class .
        }
        """

    query_CIM_ProcessExecutable = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?prop
        where {
        cim:CIM_ProcessExecutable ?prop ?obj .
        }
        """

    # Classes which are associators.
    query_associators = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?class_node
        where {
        ?class_node cim:is_association ?obj .
        }
        """

    # Properties of associator classes.
    query_associators_properties = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?class_name ?property_name
        where {
        ?class_node cim:is_association ?obj .
        ?class_node rdfs:label ?class_name .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:label ?property_name .
        }
        """

    # Range of properties of associator CIM_DirectoryContainsFile.
    query_CIM_DirectoryContainsFile_properties_range = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?property_name ?range_class_node
        where {
        ?class_node cim:is_association ?obj .
        ?class_node rdfs:label "CIM_DirectoryContainsFile" .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:range ?range_class_node .
        ?property_node rdfs:label ?property_name .
        }
        """

    # Name of class of range of properties of associator Win32_ShareToDirectory.
    query_Win32_ShareToDirectory_properties_range = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?property_name ?range_class_name
        where {
        ?class_node cim:is_association ?obj .
        ?class_node rdfs:label "Win32_ShareToDirectory" .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:range ?range_class_node .
        ?property_node rdfs:label ?property_name .
        ?range_class_node rdfs:label ?range_class_name .
        }
        """

    # Associators pointing to a CIM_DataFile.
    query_associators_to_CIM_DataFile = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?class_name ?property_name
        where {
        ?class_node cim:is_association ?obj .
        ?class_node rdfs:label ?class_name .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:range ?range_class_node .
        ?property_node rdfs:label ?property_name .
        ?range_class_node rdfs:label "CIM_DataFile" .
        }
        """

    # Name of class of range of properties of associator CIM_ProcessThread.
    query_Win32_CIM_ProcessThread_properties_range = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?property_name ?range_class_name
        where {
        ?class_node cim:is_association ?obj .
        ?class_node rdfs:label "CIM_ProcessThread" .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:range ?range_class_node .
        ?property_node rdfs:label ?property_name .
        ?range_class_node rdfs:label ?range_class_name .
        }
        """

    # Associators pointing to a CIM_Thread.
    query_associators_to_CIM_Thread = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?class_name ?property_name
        where {
        ?class_node cim:is_association ?obj .
        ?class_node rdfs:label ?class_name .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:range ?range_class_node .
        ?property_node rdfs:label ?property_name .
        ?range_class_node rdfs:label "CIM_Thread" .
        }
        """

    # Associators pointing to CIM_Process.
    query_associators_to_CIM_Process = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?class_name ?property_name
        where {
        ?class_node cim:is_association ?obj .
        ?class_node rdfs:label ?class_name .
        ?property_node rdfs:domain ?class_node .
        ?property_node rdfs:range ?range_class_node .
        ?property_node rdfs:label ?property_name .
        ?range_class_node rdfs:label "CIM_Process" .
        }
        """


class Testing_CIM_Directory(metaclass=TestBase):
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_directory
        where {
        ?my_directory rdf:type cim:Win32_Directory .
        ?my_directory cim:Name "C:" .
        }
    """

class Testing_CIM_Process(metaclass=TestBase):
    """
    This selects all process ids and their names.
    """
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_process_name ?my_process_handle
        where {
        ?my_process rdf:type cim:CIM_Process .
        ?my_process cim:Handle ?my_process_handle .
        ?my_process cim:Name ?my_process_name .
        }
    """

class Testing_CIM_Process_WithHandle(metaclass=TestBase):
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_process_caption
        where {
        ?my_process rdf:type cim:CIM_Process .
        ?my_process cim:Handle %s .
        ?my_process cim:Caption ?my_process_caption .
        }
    """ % current_pid

class Testing_CIM_Directory_WithName(metaclass=TestBase):
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_dir
        where {
        ?my_dir rdf:type cim:Win32_Directory .
        ?my_dir cim:Name 'C:' .
        }
    """

class Testing_CIM_Directory_SubDirWithName(metaclass=TestBase):
    """
    This tests that directories separators are correctly handled.
    """
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_subdir
        where {
        ?my_subdir rdf:type cim:Win32_Directory .
        ?my_subdir cim:Name 'C:\\\\Windows' .
        }
    """

class Testing_CIM_ProcessExecutable_WithDependent(metaclass=TestBase):
    """
    This selects executable file and dlls used by the current process.
    """
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_file_name
        where {
        ?my_assoc rdf:type cim:CIM_ProcessExecutable .
        ?my_assoc cim:Dependent ?my_process .
        ?my_assoc cim:Antecedent ?my_file .
        ?my_process rdf:type cim:Win32_Process .
        ?my_process cim:Handle "%d" .
        ?my_file rdf:type cim:CIM_DataFile .
        ?my_file cim:Name ?my_file_name .
        }
    """ % current_pid

class Testing_CIM_ProcessExecutable_WithAntecedent(metaclass=TestBase):
    query = r"""
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_process_handle
        where {
        ?my_assoc rdf:type cim:CIM_ProcessExecutable .
        ?my_assoc cim:Dependent ?my_process .
        ?my_assoc cim:Antecedent ?my_file .
        ?my_process rdf:type cim:Win32_Process .
        ?my_process cim:Handle ?my_process_handle .
        ?my_file rdf:type cim:CIM_DataFile .
        ?my_file cim:Name "C:\\WINDOWS\\System32\\kernel32.dll" .
        }
    """

class Testing_CIM_DirectoryContainsFile_WithFile(metaclass=TestBase):
    """
    This returns the directory of a given file.
    """
    query = r"""
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_dir_name
        where {
        ?my_assoc_dir rdf:type cim:CIM_DirectoryContainsFile .
        ?my_assoc_dir cim:GroupComponent ?my_dir .
        ?my_assoc_dir cim:PartComponent ?my_file .
        ?my_file rdf:type cim:CIM_DataFile .
        ?my_file cim:Name "C:\\WINDOWS\\System32\\kernel32.dll" .
        ?my_dir rdf:type cim:Win32_Directory .
        ?my_dir cim:Name ?my_dir_name .
        }
    """

class Testing_CIM_DirectoryContainsFile_WithDir(metaclass=TestBase):
    """
    Files under the directory "C:"
    """
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_file_name
        where {
        ?my_assoc_dir rdf:type cim:CIM_DirectoryContainsFile .
        ?my_assoc_dir cim:GroupComponent ?my_dir .
        ?my_assoc_dir cim:PartComponent ?my_file .
        ?my_file rdf:type cim:CIM_DataFile .
        ?my_file cim:Name ?my_file_name .
        ?my_dir rdf:type cim:Win32_Directory .
        ?my_dir cim:Name 'C:' .
        }
    """

class Testing_Win32_SubDirectory_WithFile(metaclass=TestBase):
    """
    This returns the directory of a given directory.
    """
    query = r"""
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_dir_name
        where {
        ?my_assoc_dir rdf:type cim:Win32_SubDirectory .
        ?my_assoc_dir cim:GroupComponent ?my_dir .
        ?my_assoc_dir cim:PartComponent ?my_subdir .
        ?my_subdir rdf:type cim:Win32_Directory .
        ?my_subdir cim:Name "C:\\WINDOWS\\System32" .
        ?my_dir rdf:type cim:Win32_Directory .
        ?my_dir cim:Name ?my_dir_name .
        }
    """

class Testing_Win32_SubDirectory_WithDir(metaclass=TestBase):
    """
    Directories under the directory "C:"
    """
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_subdir_name
        where {
        ?my_assoc_dir rdf:type cim:Win32_SubDirectory .
        ?my_assoc_dir cim:GroupComponent ?my_dir .
        ?my_assoc_dir cim:PartComponent ?my_subdir .
        ?my_subdir rdf:type cim:Win32_Directory .
        ?my_subdir cim:Name ?my_subdir_name .
        ?my_dir rdf:type cim:Win32_Directory .
        ?my_dir cim:Name 'C:' .
        }
    """

class Testing_Win32_Directory_Win32_SubDirectory_Win32_SubDirectory(metaclass=TestBase):
    """
    This displays the sub-sub-directories of C:.
    """
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_dir_name3
        where {
        ?my_dir1 rdf:type cim:Win32_Directory .
        ?my_dir1 cim:Name "C:" .
        ?my_assoc_dir1 rdf:type cim:Win32_SubDirectory .
        ?my_assoc_dir1 cim:GroupComponent ?my_dir1 .
        ?my_assoc_dir1 cim:PartComponent ?my_dir2 .
        ?my_dir2 rdf:type cim:Win32_Directory .
        ?my_assoc_dir2 rdf:type cim:Win32_SubDirectory .
        ?my_assoc_dir2 cim:GroupComponent ?my_dir2 .
        ?my_assoc_dir2 cim:PartComponent ?my_dir3 .
        ?my_dir3 rdf:type cim:Win32_Directory .
        ?my_dir3 cim:Name ?my_dir_name3 .
        }
    """

class Testing_CIM_ProcessExecutable_CIM_DirectoryContainsFile_WithHandle(metaclass=TestBase):
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_dir_name
        where {
        ?my_assoc rdf:type cim:CIM_ProcessExecutable .
        ?my_assoc cim:Dependent ?my_process .
        ?my_assoc cim:Antecedent ?my_file .
        ?my_assoc_dir rdf:type cim:CIM_DirectoryContainsFile .
        ?my_assoc_dir cim:GroupComponent ?my_dir .
        ?my_assoc_dir cim:PartComponent ?my_file .
        ?my_process rdf:type cim:Win32_Process .
        ?my_process cim:Handle "%d" .
        ?my_file rdf:type cim:CIM_DataFile .
        ?my_file cim:Name ?my_file_name .
        ?my_dir rdf:type cim:Win32_Directory .
        ?my_dir cim:Name ?my_dir_name .
        }
    """ % current_pid

class Testing_CIM_ProcessExecutable_FullScan(metaclass=TestBase):
    """
    This gets the directories of all executables and libraries of all processes.
    """
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_file_name ?my_process_handle
        where {
        ?my_assoc rdf:type cim:CIM_ProcessExecutable .
        ?my_assoc cim:Dependent ?my_process .
        ?my_assoc cim:Antecedent ?my_file .
        ?my_process rdf:type cim:Win32_Process .
        ?my_process cim:Handle ?my_process_handle .
        ?my_file rdf:type cim:CIM_DataFile .
        ?my_file cim:Name ?my_file_name .
        }
    """

class Testing_CIM_ProcessExecutable_Groups(metaclass=TestBase):
    """
    This gets the directories of all executables and libraries of all processes.
    """
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_file_name (COUNT(?my_file_name) AS ?ELEMENTCOUNT)
        where {
        ?my_assoc rdf:type cim:CIM_ProcessExecutable .
        ?my_assoc cim:Dependent ?my_process .
        ?my_assoc cim:Antecedent ?my_file .
        ?my_process rdf:type cim:Win32_Process .
        ?my_process cim:Handle ?my_process_handle .
        ?my_file rdf:type cim:CIM_DataFile .
        ?my_file cim:Name ?my_file_name .
        }
        group by ?my_file_name
    """

class Testing_CIM_ProcessExecutable_CIM_DirectoryContainsFile(metaclass=TestBase):
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_dir_name
        where {
        ?my_assoc rdf:type cim:CIM_ProcessExecutable .
        ?my_assoc cim:Dependent ?my_process .
        ?my_assoc cim:Antecedent ?my_file .
        ?my_assoc_dir rdf:type cim:CIM_DirectoryContainsFile .
        ?my_assoc_dir cim:GroupComponent ?my_dir .
        ?my_assoc_dir cim:PartComponent ?my_file .
        ?my_process rdf:type cim:Win32_Process .
        ?my_file rdf:type cim:CIM_DataFile .
        ?my_file cim:Name ?my_file_name .
        ?my_dir rdf:type cim:Win32_Directory .
        ?my_dir cim:Name ?my_dir_name .
        }
    """

class Testing_CIM_Process_CIM_DataFile_SameCaption(metaclass=TestBase):
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?same_caption
        where {
        ?my_process rdf:type cim:CIM_Process .
        ?my_process cim:Caption ?same_caption .
        ?my_file rdf:type cim:CIM_DataFile .
        ?my_file cim:Caption ?same_caption .
        }
    """
*/