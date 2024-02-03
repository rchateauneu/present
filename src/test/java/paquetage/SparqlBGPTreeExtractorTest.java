package paquetage;

import org.junit.*;

import java.util.*;

/** This tests the class SparqlBGPTreeExtractor with real data.
 *
 */
public class SparqlBGPTreeExtractorTest {
    static void CompareVariable(ObjectPattern.PredicateObjectPair a, String predicate, String variableName) {
        Assert.assertEquals(predicate, a.shortPredicate);
        Assert.assertEquals(variableName, a.variableName);
        Assert.assertEquals(null, a.objectContent);
    }

    static void CompareKeyValue(ObjectPattern.PredicateObjectPair a, String predicate, String content) {
        Assert.assertEquals(predicate, a.shortPredicate);
        Assert.assertEquals(null, a.variableName);
        Assert.assertEquals(ValueTypePair.FromString(content), a.objectContent);
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
        Assert.assertEquals(null, firstPattern.className);
        Assert.assertEquals("s", firstPattern.variableName);
        Assert.assertEquals(4, firstPattern.membersList.size());

        CompareKeyValue(firstPattern.membersList.get(0), "http://www.w3.org/2000/01/rdf-schema#label", "Microsoft");
        CompareVariable(firstPattern.membersList.get(1), "http://xmlns.com/foaf/0.1/page", "page");
        CompareKeyValue(firstPattern.membersList.get(2), "http://www.w3.org/2000/01/rdf-schema#label", "Apple");
        CompareVariable(firstPattern.membersList.get(3), "http://xmlns.com/foaf/0.1/page", "page");
    }

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
        Assert.assertEquals(null, pattern1.className);
        Assert.assertEquals("process1", pattern1.variableName);
        Assert.assertEquals(1, pattern1.membersList.size());
        CompareKeyValue(pattern1.membersList.get(0), "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.Caption", "Caption1");

        ObjectPattern pattern2 = extractor.FindObjectPattern("process2");
        Assert.assertNotEquals(pattern2, null);
        Assert.assertEquals("process2", pattern2.variableName);
        Assert.assertEquals(1, pattern2.membersList.size());
        CompareKeyValue(pattern2.membersList.get(0), "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.Caption", "Caption2");
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
            System.out.println("pattern.className"+pattern.className);
            System.out.println("pattern.VariableName"+pattern.variableName);
            for(ObjectPattern.PredicateObjectPair pop:pattern.membersList) {
                System.out.println("    pattern.Predicate   :" + pop.shortPredicate);
                System.out.println("    pattern.Content     :" + pop.objectContent.toDisplayString());
                System.out.println("    pattern.variableName:" + pop.variableName);
                System.out.println("");
            }
        }

        Assert.assertNotEquals(extractor.FindObjectPattern("country"), null);
        ObjectPattern firstPattern = patterns.get(0);
        Assert.assertEquals(null, firstPattern.className);
        Assert.assertEquals("country", firstPattern.variableName);
        Assert.assertEquals(4, firstPattern.membersList.size());
        CompareKeyValue(firstPattern.membersList.get(0), "http://www.wikidata.org/prop/direct/P31", "http://www.wikidata.org/entity/Q3624078");
        CompareVariable(firstPattern.membersList.get(1), "http://www.wikidata.org/prop/direct/P1082", "population");
        CompareKeyValue(firstPattern.membersList.get(2), "http://www.wikidata.org/prop/direct/P31", "http://www.wikidata.org/entity/Q3624078");
        CompareVariable(firstPattern.membersList.get(3), "http://www.wikidata.org/prop/direct/P1082", "population");
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
            System.out.println("pattern.className"+pattern.className);
            System.out.println("pattern.VariableName"+pattern.variableName);
            for(ObjectPattern.PredicateObjectPair pop:pattern.membersList) {
                System.out.println("    pattern.Predicate   :" + pop.shortPredicate);
                System.out.println("    pattern.Content     :" + pop.objectContent.toDisplayString());
                System.out.println("    pattern.variableName:" + pop.variableName);
                System.out.println("");
            }
        }

        Assert.assertNotEquals(extractor.FindObjectPattern("country"), null);
        ObjectPattern firstPattern = patterns.get(0);
        Assert.assertEquals(null, firstPattern.className);
        Assert.assertEquals("country", firstPattern.variableName);
        Assert.assertEquals(4, firstPattern.membersList.size());
        CompareKeyValue(firstPattern.membersList.get(0), "http://www.wikidata.org/prop/direct/P31", "http://www.wikidata.org/entity/Q3624078");
    }

}
