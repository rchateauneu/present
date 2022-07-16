package paquetage;

import com.google.common.collect.Sets;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Values;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        String sparql_query = """
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
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(Sets.newHashSet("obs", "time", "lat"), extractor.bindings);
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
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_directory
            where {
                ?my_directory rdf:type cim:Win32_Directory .
                ?my_directory cim:Name "C:" .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_directory"));

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
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?my_name
            where {
                ?my_directory rdf:type cim:Win32_Directory .
                ?my_directory cim:Name ?my_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_name"));

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
        String sparql_query = """
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
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("my_file_name"));

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
        String sparql_query = """
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
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("page", "type"));
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
        String sparql_query = """
                PREFIX schema: <http://schema.org/>
                SELECT ?subject_name
                FROM <http://www.worldcat.org/oclc/660967222>
                WHERE {
                ?s schema:about ?about .
                ?about schema:name ?subject_name
                FILTER regex(STR(?about), "fast").
                }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("subject_name"));
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
     * Checks the BGPs extracted from an arbitrary query with an optional statement.
     */
    public void Parse_Optional() throws Exception {
        String sparql_query = """
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
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
        Assert.assertEquals(Sets.newHashSet("creator_name", "author_name"), extractor.bindings);
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
    /***
     * Create triples from BGPs and variable-value pairs.
     */
    public void TriplesGenerationFromBGPs_1() throws Exception {
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name
            where {
                ?my_dir rdf:type cim:Win32_Directory .
                ?my_dir cim:Name ?dir_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
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
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name
            where {
                ?my_dir rdf:type cim:Win32_Directory .
                ?my_dir cim:Name ?dir_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
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
        String sparql_query = """
            prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name ?dir_caption
            where {
                ?my_dir rdf:type cim:Win32_Directory .
                ?my_dir cim:Name ?dir_name .
                ?my_dir cim:Caption ?dir_caption .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparql_query);
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

                def test_content_ontology(ontology_filename):
                    """
    This test loads in a graph the RDF/XML file containing the ontology of WMI classes and properties.
    After that, it checks the presence of specific triples, to ensure they are correctly loaded.

    This validates the creation of this ontology.
    :param ontology_filename: The filename containing the ontology in RDF/XML format.
    :return: Nothing
    """

    rdf_graph = rdflib.Graph()
    rdf_graph.parse(ontology_filename)
    assert len(list(rdf_graph.triples((rdflib.URIRef(SURVOLNS["CIM_ProcessExecutable"]), None, None)))) == 4
    assert len(list(rdf_graph.triples((rdflib.URIRef(SURVOLNS["CIM_ProcessExecutable"]), wmi_sparql.property_association_node, None)))) == 1

    query_all_classes = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        select distinct ?the_class
        where {
        ?the_class rdf:type rdfs:Class .
        }
        """
    results_all_classes = set(rdf_graph.query(query_all_classes))
    assert (rdflib.URIRef(SURVOLNS["CIM_ProcessExecutable"]),) in results_all_classes

    if False:
        print("Plain loop on all classes")
        for s, p, o in rdf_graph.triples((rdflib.URIRef(SURVOLNS["CIM_ProcessExecutable"]), None, None)):
            print(s, p, o)

    query_CIM_ProcessExecutable = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?prop
        where {
        cim:CIM_ProcessExecutable ?prop ?obj .
        }
        """
    print("CIM_ProcessExecutable")
    results_CIM_ProcessExecutable = set(rdf_graph.query(query_CIM_ProcessExecutable))
    for x in results_CIM_ProcessExecutable:
        print(x)
    assert (rdflib.URIRef(SURVOLNS["is_association"]),) in results_CIM_ProcessExecutable
    assert (rdflib.namespace.RDF.type,) in results_CIM_ProcessExecutable
    assert (rdflib.namespace.RDFS.label,) in results_CIM_ProcessExecutable
    assert (rdflib.namespace.RDFS.comment,) in results_CIM_ProcessExecutable
    assert len(results_CIM_ProcessExecutable) == 4

    # Classes which are associators.
    query_associators = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?class_node
        where {
        ?class_node cim:is_association ?obj .
        }
        """
    print("Associators")
    results_associators = set(rdf_graph.query(query_associators))
    print("Number of associators:", len(results_associators))
    assert (rdflib.URIRef(SURVOLNS["CIM_ProcessExecutable"]),) in results_associators
    assert (rdflib.URIRef(SURVOLNS["CIM_DirectoryContainsFile"]),) in results_associators
    assert (rdflib.URIRef(SURVOLNS["Win32_COMApplicationClasses"]),) in results_associators
    assert (rdflib.URIRef(SURVOLNS["Win32_ApplicationCommandLine"]),) in results_associators
    assert (rdflib.URIRef(SURVOLNS["CIM_ProcessThread"]),) in results_associators
    assert (rdflib.URIRef(SURVOLNS["Win32_DependentService"]),) in results_associators
    assert (rdflib.URIRef(SURVOLNS["Win32_SystemServices"]),) in results_associators
    assert (rdflib.URIRef(SURVOLNS["Win32_UserInDomain"]),) in results_associators
    assert (rdflib.URIRef(SURVOLNS["Win32_SystemUsers"]),) in results_associators
    assert (rdflib.URIRef(SURVOLNS["Win32_SessionProcess"]),) in results_associators
    assert (rdflib.URIRef(SURVOLNS["CIM_ProductSoftwareFeatures"]),) in results_associators
    assert (rdflib.URIRef(SURVOLNS["Win32_SystemProcesses"]),) in results_associators
    assert (rdflib.URIRef(SURVOLNS["Win32_SubDirectory"]),) in results_associators

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
    print("Properties of associators")
    results_associators_properties = set(rdf_graph.query(query_associators_properties))
    results_associators_properties = to_literals(results_associators_properties)
    assert ("CIM_DirectoryContainsFile", "CIM_DirectoryContainsFile.GroupComponent",) in results_associators_properties
    assert ("CIM_DirectoryContainsFile", "CIM_DirectoryContainsFile.PartComponent",) in results_associators_properties

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
    print("Range of properties of associator CIM_DirectoryContainsFile")
    results_CIM_DirectoryContainsFile_properties_range = rdf_graph.query(query_CIM_DirectoryContainsFile_properties_range)
    for x in results_CIM_DirectoryContainsFile_properties_range:
        print(x)
    assert (
               rdflib.term.Literal("CIM_DirectoryContainsFile.GroupComponent"),
               rdflib.URIRef(SURVOLNS["CIM_Directory"]),) in results_CIM_DirectoryContainsFile_properties_range
    assert (
               rdflib.term.Literal("CIM_DirectoryContainsFile.PartComponent"),
               rdflib.URIRef(SURVOLNS["CIM_DataFile"]),) in results_CIM_DirectoryContainsFile_properties_range

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
    print("Range of properties of associator Win32_ShareToDirectory")
    results_Win32_ShareToDirectory_properties_range = rdf_graph.query(query_Win32_ShareToDirectory_properties_range)
    results_Win32_ShareToDirectory_properties_range = to_literals(results_Win32_ShareToDirectory_properties_range)
    for x in results_Win32_ShareToDirectory_properties_range:
        print(x)
    assert ("Win32_ShareToDirectory.Share", "Win32_Share",) in results_Win32_ShareToDirectory_properties_range
    assert ("Win32_ShareToDirectory.SharedElement", "CIM_Directory",) in results_Win32_ShareToDirectory_properties_range

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
    print("Associators to CIM_DataFile")
    results_associators_to_CIM_DataFile = rdf_graph.query(query_associators_to_CIM_DataFile)
    results_associators_to_CIM_DataFile = to_literals(results_associators_to_CIM_DataFile)
    assert ('CIM_DirectoryContainsFile', 'CIM_DirectoryContainsFile.PartComponent', ) in results_associators_to_CIM_DataFile
    assert ('Win32_PnPSignedDriverCIMDataFile', 'Win32_PnPSignedDriverCIMDataFile.Dependent', ) in results_associators_to_CIM_DataFile
    assert ('Win32_LogicalProgramGroupItemDataFile', 'Win32_LogicalProgramGroupItemDataFile.Dependent', ) in results_associators_to_CIM_DataFile
    assert ('CIM_ProcessExecutable', 'CIM_ProcessExecutable.Antecedent', ) in results_associators_to_CIM_DataFile
    assert ('Win32_PrinterDriverDll', 'Win32_PrinterDriverDll.Antecedent', ) in results_associators_to_CIM_DataFile
    assert ('Win32_ClientApplicationSetting', 'Win32_ClientApplicationSetting.Client', ) in results_associators_to_CIM_DataFile
    assert ('Win32_CIMLogicalDeviceCIMDataFile', 'Win32_CIMLogicalDeviceCIMDataFile.Dependent', ) in results_associators_to_CIM_DataFile

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
    print("Range of properties of associator CIM_ProcessThread")
    results_CIM_ProcessThread_properties_range = rdf_graph.query(query_Win32_CIM_ProcessThread_properties_range)
    results_CIM_ProcessThread_properties_range = to_literals(results_CIM_ProcessThread_properties_range)
    assert ("CIM_ProcessThread.GroupComponent", "CIM_Process",) in results_CIM_ProcessThread_properties_range
    assert ("CIM_ProcessThread.PartComponent", "CIM_Thread",) in results_CIM_ProcessThread_properties_range

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
    print("Associators to CIM_Thread")
    results_associators_to_CIM_Thread = rdf_graph.query(query_associators_to_CIM_Thread)
    results_associators_to_CIM_Thread = to_literals(results_associators_to_CIM_Thread)
    assert ('CIM_ProcessThread', 'CIM_ProcessThread.PartComponent', ) in results_associators_to_CIM_Thread

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
    print("Associators to CIM_Process")
    results_associators_to_CIM_Process = rdf_graph.query(query_associators_to_CIM_Process)
    results_associators_to_CIM_Process = to_literals(results_associators_to_CIM_Process)
    assert ('CIM_OSProcess', 'CIM_OSProcess.PartComponent') in results_associators_to_CIM_Process
    assert ('CIM_ProcessExecutable', 'CIM_ProcessExecutable.Dependent') in results_associators_to_CIM_Process
    assert ('CIM_ProcessThread', 'CIM_ProcessThread.GroupComponent') in results_associators_to_CIM_Process


def test_keys_lists():
    assert _contains_all_keys("CIM_Directory", {"Name": None})
    assert _contains_all_keys("CIM_Directory", {"Name": None, "Anything": None})
    assert not _contains_all_keys("CIM_Directory", {"Nothing": None})
    assert _contains_all_keys("CIM_DirectoryContainsFile", {"GroupComponent": None, "PartComponent": None})
    assert _contains_all_keys("CIM_DirectoryContainsFile", {"GroupComponent": None, "PartComponent": None, "xyz": None})
    assert not _contains_all_keys("CIM_DirectoryContainsFile", {"PartComponent": None, "xyz": None})

    assert _contains_one_key("CIM_Directory", {"Name": None})
    assert _contains_one_key("CIM_Directory", {"Name": None, "Anything": None})
    assert not _contains_one_key("CIM_Directory", {"SomethingElse": None})
    assert _contains_one_key("CIM_DirectoryContainsFile", {"GroupComponent": None, "PartComponent": None})
    assert _contains_one_key("CIM_DirectoryContainsFile", {"GroupComponent": None, "PartComponent": None, "xyz": None})
    assert _contains_one_key("CIM_DirectoryContainsFile", {"GroupComponent": None, "xyz": None})
    assert not _contains_one_key("CIM_DirectoryContainsFile", {"xyz": None})


machine_root_cimv2 = r'\\LAPTOP-R89KG6V1\root\cimv2:'

def test_moniker():
    """
    This checks the creation of a WMI moniker using a class name and key-value pairs.
    :return:
    """
    assert _create_wmi_moniker("CIM_Directory", Name="C:") == \
           machine_root_cimv2 + r'CIM_Directory.Name="C:"'
    # "C:\Windows\System32\kernel32.dll"
    assert _create_wmi_moniker("CIM_DataFile", Name=r"C:\WINDOWS\System32\kernel32.dll") == \
           machine_root_cimv2 + r'CIM_DataFile.Name="C:\\WINDOWS\\System32\\kernel32.dll"'

    moniker_partcomponent = _create_wmi_moniker("CIM_Directory", Name=r"C:\Windows")
    assert moniker_partcomponent == \
           machine_root_cimv2 + r'CIM_Directory.Name="C:\\Windows"'
    moniker_groupcomponent = _create_wmi_moniker("CIM_Directory", Name="C:")
    assert moniker_groupcomponent == \
           machine_root_cimv2 + r'CIM_Directory.Name="C:"'
    moniker_containsfile = _create_wmi_moniker(
        "CIM_DirectoryContainsFile",
        PartComponent=moniker_partcomponent,
        GroupComponent=moniker_groupcomponent)
    print("moniker_containsfile=", moniker_containsfile)
    assert moniker_containsfile == \
           r'\\LAPTOP-R89KG6V1\root\cimv2:CIM_DirectoryContainsFile.GroupComponent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_Directory.Name="C:"",PartComponent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_Directory.Name="C:\\\\Windows""'


def test_moniker_to_rdf():
    # http://www.primhillcomputers.com/ontology/survol#%5C%5C%5C%5CLAPTOP-R89KG6V1%5C%5Croot%5C%5Ccimv2%3ACIM_Directory.Name%3D%22C%3A%22
    assert str(wmi_attributes_to_rdf_node("CIM_Directory", Name="C:")) == \
        "http://www.primhillcomputers.com/ontology/survol#%5C%5CLAPTOP-R89KG6V1%5Croot%5Ccimv2%3ACIM_Directory.Name%3D%22C%3A%22"
    actual_node= str(wmi_attributes_to_rdf_node("CIM_DataFile", Name=r"C:\WINDOWS\System32\kernel32.dll"))
    print("actual_node=", actual_node)
    assert actual_node == \
        "http://www.primhillcomputers.com/ontology/survol#%5C%5CLAPTOP-R89KG6V1%5Croot%5Ccimv2%3ACIM_DataFile.Name%3D%22C%3A%5C%5CWINDOWS%5C%5CSystem32%5C%5Ckernel32.dll%22"

    moniker_partcomponent = _create_wmi_moniker("CIM_Directory", Name=r"C:\Windows")
    print("moniker_partcomponent=", moniker_partcomponent)
    assert moniker_partcomponent == \
           machine_root_cimv2 + r'CIM_Directory.Name="C:\\Windows"'
    moniker_groupcomponent = _create_wmi_moniker("CIM_Directory", Name="C:")
    assert moniker_groupcomponent == \
           machine_root_cimv2 + r'CIM_Directory.Name="C:"'
    rdfnode_containsfile = wmi_attributes_to_rdf_node(
        "CIM_DirectoryContainsFile",
        PartComponent=moniker_partcomponent,
        GroupComponent=moniker_groupcomponent)
    print("rdfnode_containsfile=", rdfnode_containsfile)
    assert str(rdfnode_containsfile) == \
        'http://www.primhillcomputers.com/ontology/survol#%5C%5CLAPTOP-R89KG6V1%5Croot%5Ccimv2%3ACIM_DirectoryContainsFile.GroupComponent%3D%22%5C%5C%5C%5CLAPTOP-R89KG6V1%5C%5Croot%5C%5Ccimv2%3ACIM_Directory.Name%3D%22C%3A%22%22%2CPartComponent%3D%22%5C%5C%5C%5CLAPTOP-R89KG6V1%5C%5Croot%5C%5Ccimv2%3ACIM_Directory.Name%3D%22C%3A%5C%5C%5C%5CWindows%22%22'


def test_node_insertion():
    """
    This checks that WMI nodes are inserted in a rdflib without modification.
    :return: Nothing
    """

    moniker_partcomponent = _create_wmi_moniker("CIM_Directory", Name=r"C:\Windows")
    print("moniker_partcomponent=", moniker_partcomponent)
    assert moniker_partcomponent == \
           machine_root_cimv2 + r'CIM_Directory.Name="C:\\Windows"'
    node_partcomponent = _wmi_moniker_to_rdf_node(moniker_partcomponent)

    moniker_groupcomponent = _create_wmi_moniker("CIM_Directory", Name="C:")
    assert moniker_groupcomponent == \
           machine_root_cimv2 + r'CIM_Directory.Name="C:"'
    node_groupcomponent = _wmi_moniker_to_rdf_node(moniker_groupcomponent)

    rdfnode_containsfile = wmi_attributes_to_rdf_node(
        "CIM_DirectoryContainsFile",
        PartComponent=moniker_partcomponent,
        GroupComponent=moniker_groupcomponent)
    print("rdfnode_containsfile=", rdfnode_containsfile)
    assert str(rdfnode_containsfile) == \
        'http://www.primhillcomputers.com/ontology/survol#%5C%5CLAPTOP-R89KG6V1%5Croot%5Ccimv2%3ACIM_DirectoryContainsFile.GroupComponent%3D%22%5C%5C%5C%5CLAPTOP-R89KG6V1%5C%5Croot%5C%5Ccimv2%3ACIM_Directory.Name%3D%22C%3A%22%22%2CPartComponent%3D%22%5C%5C%5C%5CLAPTOP-R89KG6V1%5C%5Croot%5C%5Ccimv2%3ACIM_Directory.Name%3D%22C%3A%5C%5C%5C%5CWindows%22%22'

    the_graph = rdflib.Graph()
    # This triple does not need to be consistent but it must be read as it was inserted.
    # The intention is to detect if strings are escaped.
    triple_to_insert = (node_partcomponent, node_groupcomponent, rdfnode_containsfile)
    the_graph.add(triple_to_insert)
    inserted_triple = list(the_graph.triples((None, None, None)))[0]
    #print("triple_to_insert=", triple_to_insert)
    #print("inserted_triple=", inserted_triple)
    assert str(triple_to_insert[0]) == str(inserted_triple[0])
    assert str(triple_to_insert[1]) == str(inserted_triple[1])
    assert str(triple_to_insert[2]) == str(inserted_triple[2])


#################################################################################################

if False:
    samples_list["Metadata1"] = (
        """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?same_caption
        where {
        ?my_process rdf:type cim:CIM_Process .
        ?my_process ?cim_property ?same_caption .
        ?cim_property rdf:name ?cim_property_name .
        }
        """,
        [
            _CimPattern(VARI('my_process'), 'CIM_Process', {'Caption': VARI('same_caption')}),
            _CimPattern(VARI('my_file'), 'CIM_DataFile', {'Caption': VARI('same_caption')}),
        ],
        no_check
    )

#################################################################################################

class TestBase(type):
    subclasses = dict()

    def __init__(cls, name, bases, dct):
        print("Init", name, type(cls))
        cls.subclasses[cls.label] = cls

class Testing_CIM_Directory(metaclass=TestBase):
    label = "CIM_Directory"
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_directory
        where {
        ?my_directory rdf:type cim:Win32_Directory .
        ?my_directory cim:Name "C:" .
        }
    """
    expected_patterns = [
        _CimPattern(VARI('my_directory'), 'Win32_Directory', {'Name': LITT('C:')}),
    ]

    def check_graph(rdflib_graph):
        assert len(rdflib_graph) > 0

    def check_query_results(query_results):
        assert len(query_results) == 1

        print("query_results=", query_results)
        assert query_results == [(
                wmi_attributes_to_rdf_node("Win32_Directory", Name="C:"),
        )]


class Testing_CIM_Process(metaclass=TestBase):
    """
    This selects all process ids and their names.
    """
    label = "CIM_Process"
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
    expected_patterns = [
        _CimPattern(VARI('my_process'), 'CIM_Process', {'Handle': VARI('my_process_handle'), 'Name': VARI('my_process_name')}),
    ]

    def check_graph(rdflib_graph):
        assert len(rdflib_graph) > 0

    def check_query_results(query_results):
        """
        It must contain at least the current process.
        :return:
        """
        # This should be something like "python.exe"
        process_name = os.path.basename(sys.executable)
        assert (rdflib.term.Literal(process_name), rdflib.term.Literal(str(current_pid))) in query_results


class Testing_CIM_Process_WithHandle(metaclass=TestBase):
    label = "CIM_Process with Handle=current process"
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
    expected_patterns = [
        _CimPattern(VARI('my_process'), 'CIM_Process', {'Handle': LITT(current_pid), 'Caption': VARI('my_process_caption')}),
    ]

    def check_graph(rdflib_graph):
        assert len(rdflib_graph) > 0

    def check_query_results(query_results):
        process_name = os.path.basename(sys.executable)
        assert query_results == [(
                rdflib.term.Literal(process_name),
        )]


class Testing_CIM_Directory_WithName(metaclass=TestBase):
    # TODO: This should check that the file really exists.
    label = "CIM_Directory with Name=C:"
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_dir
        where {
        ?my_dir rdf:type cim:Win32_Directory .
        ?my_dir cim:Name 'C:' .
        }
    """
    expected_patterns = [
        _CimPattern(VARI('my_dir'), 'Win32_Directory', {'Name': LITT('C:'), }),
    ]

    def check_graph(rdflib_graph):
        assert len(rdflib_graph) > 0

    def check_query_results(query_results):
        assert query_results == [(
            wmi_attributes_to_rdf_node("Win32_Directory", Name='C:'),
        )]


class Testing_CIM_Directory_SubDirWithName(metaclass=TestBase):
    """
    This tests that directories separators are correctly handled.
    """
    label = r"CIM_Directory subdirectory with Name=C:\\Windows"
    query = """
        prefix cim:  <http://www.primhillcomputers.com/ontology/survol#>
        prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
        select ?my_subdir
        where {
        ?my_subdir rdf:type cim:Win32_Directory .
        ?my_subdir cim:Name 'C:\\\\Windows' .
        }
    """
    expected_patterns = [
        _CimPattern(VARI('my_subdir'), 'Win32_Directory', {'Name': LITT(r'C:\Windows'),}),
    ]

    def check_graph(rdflib_graph):
        """
        This checks some important triples in the graph.
        This intermediary checks helps to understand the result of the query.
        """
        assert len(rdflib_graph) > 0

    def check_query_results(query_results):
        print("         query_results=", query_results)
        print("expected query_results=", wmi_attributes_to_rdf_node("Win32_Directory", Name=r'C:\Windows'))
        assert query_results == [(
            wmi_attributes_to_rdf_node("Win32_Directory", Name=r'C:\Windows'),
        )]


class Testing_CIM_ProcessExecutable_WithDependent(metaclass=TestBase):
    """
    This selects executable file and dlls used by the current process.
    """
    label = "CIM_ProcessExecutable with Dependent=current process"
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
    expected_patterns = [
        _CimPattern(VARI('my_assoc'), 'CIM_ProcessExecutable', {'Dependent': VARI('my_process'), 'Antecedent': VARI('my_file')}),
        _CimPattern(VARI('my_process'), 'Win32_Process', {'Handle': LITT('%d' % current_pid)}),
        _CimPattern(VARI('my_file'), 'CIM_DataFile', {'Name': VARI('my_file_name')}),
    ]

    def check_graph(rdflib_graph):
        moniker_dependent = _create_wmi_moniker("Win32_Process", Handle=current_pid)
        #print("moniker_dependent=", moniker_dependent)
        assert moniker_dependent == (machine_root_cimv2 + r'Win32_Process.Handle="%s"' % current_pid).upper()
        node_dependent = _wmi_moniker_to_rdf_node(moniker_dependent)

        print("len(rdflib_graph)=", len(rdflib_graph))
        types_set = set([o for s, p, o in rdflib_graph.triples((None, rdflib.namespace.RDF.type, None))])
        print("types_set=", types_set)

        print("node_dependent=", node_dependent)
        assert (node_dependent, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["Win32_Process"]),) \
               in rdflib_graph.triples((None, None, None))

        moniker_antecedent = _create_wmi_moniker("CIM_DataFile", Name=r"C:\WINDOWS\System32\CRYPT32.dll")
        #print("moniker_antecedent=", moniker_antecedent)
        assert moniker_antecedent == (machine_root_cimv2 + r'CIM_DataFile.Name="C:\\WINDOWS\\System32\\CRYPT32.dll"').upper()
        node_antecedent = _wmi_moniker_to_rdf_node(moniker_antecedent)
        #print("node_antecedent    =", node_antecedent)

        assert (node_antecedent, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["CIM_DataFile"]),) \
               in rdflib_graph.triples((None, None, None))

        # It is NOT possible to build the node of an association class from the nodes of the referenced objects.
        # Only monikers are accepted.
        node_processexecutable = wmi_attributes_to_rdf_node(
            "CIM_ProcessExecutable",
            Dependent=moniker_dependent,
            Antecedent=moniker_antecedent)
        print("node_processexecutable=", node_processexecutable)

        assert (node_processexecutable, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["CIM_ProcessExecutable"]),) \
               in rdflib_graph.triples((None, None, None))

    def check_query_results(query_results):
        # Should not be empty.
        assert query_results
        print("query_results=", query_results)

        assert (LITT(sys.executable), ) in query_results
        assert (LITT(r'C:\WINDOWS\System32\KERNEL32.DLL'), ) in query_results
        assert (LITT(r'C:\WINDOWS\System32\USER32.dll'), ) in query_results


class Testing_CIM_ProcessExecutable_WithAntecedent(metaclass=TestBase):
    label = "CIM_ProcessExecutable with Antecedent=kernel32.dll"
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
    expected_patterns = [
        _CimPattern(VARI('my_assoc'), 'CIM_ProcessExecutable', {'Dependent': VARI('my_process'), 'Antecedent': VARI('my_file')}),
        _CimPattern(VARI('my_process'), 'Win32_Process', {'Handle': VARI('my_process_handle')}),
        _CimPattern(VARI('my_file'), 'CIM_DataFile', {'Name': LITT(r'C:\WINDOWS\System32\kernel32.dll')}),
    ]

    def check_graph(rdflib_graph):
        # The current process must be in the graph.
        moniker_current_process = _create_wmi_moniker("Win32_Process", Handle=current_pid)
        #print("moniker_current_process=", moniker_current_process)
        assert moniker_current_process == (machine_root_cimv2 + r'Win32_Process.Handle="%s"' % current_pid).upper()
        node_current_process = _wmi_moniker_to_rdf_node(moniker_current_process)

        assert (node_current_process, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["Win32_Process"]),) in rdflib_graph.triples((None, None, None))
        assert (node_current_process, rdflib.URIRef(SURVOLNS["Handle"]), LITT(str(current_pid)),) in rdflib_graph.triples((None, None, None))

        # The dll Kernel32 must be in the graph.
        moniker_kernel32_dll = _create_wmi_moniker("CIM_DataFile", Name=r"C:\WINDOWS\System32\kernel32.dll")
        #print("moniker_kernel32_dll=", moniker_kernel32_dll)
        assert moniker_kernel32_dll == (machine_root_cimv2 + r'CIM_DataFile.Name="C:\\WINDOWS\\System32\\kernel32.dll"').upper()
        node_kernel32_dll = _wmi_moniker_to_rdf_node(moniker_kernel32_dll)

        assert (node_kernel32_dll, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["CIM_DataFile"]),) \
               in rdflib_graph.triples((None, None, None))
        assert (node_kernel32_dll, rdflib.URIRef(SURVOLNS["Name"]), LITT(r"C:\WINDOWS\System32\kernel32.dll"),) \
               in rdflib_graph.triples((None, None, None))

        # The associator also, must be in the graph.
        node_processexecutable = wmi_attributes_to_rdf_node(
            "CIM_ProcessExecutable",
            Dependent=moniker_current_process,
            Antecedent=moniker_kernel32_dll)
        print("node_processexecutable=", node_processexecutable)

        print("Recherche")
        for s, p, o in rdflib_graph.triples((None, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["CIM_ProcessExecutable"]))):
            if str(s).find("CIM_ProcessExecutable") > 0 and str(s).upper().find("KERN") > 0 :
                print("AAA", s)
                print("BBB", node_processexecutable)

        assert (node_processexecutable, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["CIM_ProcessExecutable"]),) \
               in rdflib_graph.triples((None, None, None))

    def check_query_results(query_results):
        # The current process uses "KERNEL32" and must be in the result.
        assert (LITT(str(current_pid)), ) in query_results


class Testing_CIM_DirectoryContainsFile_WithFile(metaclass=TestBase):
    """
    This returns the directory of a given file.
    """
    label = "CIM_DirectoryContainsFile with File=kernel32.dll"
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
    expected_patterns = [
        _CimPattern(VARI('my_assoc_dir'), 'CIM_DirectoryContainsFile', {'GroupComponent': VARI('my_dir'), 'PartComponent': VARI('my_file')}),
        _CimPattern(VARI('my_dir'), 'Win32_Directory', {'Name': VARI('my_dir_name')}),
        _CimPattern(VARI('my_file'), 'CIM_DataFile', {'Name': LITT(r"C:\WINDOWS\System32\kernel32.dll")}),
    ]

    def check_graph(rdflib_graph):
        print("len(rdflib_graph)=", len(rdflib_graph))

        moniker_partcomponent = _create_wmi_moniker("CIM_DataFile", Name=r"C:\WINDOWS\System32\kernel32.dll")
        print("moniker_partcomponent=", moniker_partcomponent)
        assert moniker_partcomponent == (machine_root_cimv2 + r'CIM_DataFile.Name="C:\\WINDOWS\\System32\\kernel32.dll"').upper()
        node_partcomponent = _wmi_moniker_to_rdf_node(moniker_partcomponent)

        print("node_partcomponent=", node_partcomponent)
        assert (node_partcomponent, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["CIM_DataFile"])) \
               in rdflib_graph.triples((None, None, None))

        moniker_groupcomponent = _create_wmi_moniker("Win32_Directory", Name=r"C:\WINDOWS\System32")
        print("moniker_groupcomponent=", moniker_groupcomponent)
        assert moniker_groupcomponent == (machine_root_cimv2 + r'Win32_Directory.Name="C:\\WINDOWS\\System32"').upper()
        node_groupcomponent = _wmi_moniker_to_rdf_node(moniker_groupcomponent)
        print("node_groupcomponent=", node_groupcomponent)

        assert (node_groupcomponent, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["Win32_Directory"])) \
               in rdflib_graph.triples((None, None, None))

        # It is NOT possible to build the node of an association class from the nodes of the referenced objects.
        # Only monikers are accepted.
        print("moniker_partcomponent=", moniker_partcomponent, len(moniker_partcomponent), type(moniker_partcomponent))
        print("moniker_groupcomponent=", moniker_groupcomponent, len(moniker_groupcomponent), type(moniker_groupcomponent))
        node_containsfile_from_monikers = wmi_attributes_to_rdf_node(
            "CIM_DirectoryContainsFile",
            PartComponent=moniker_partcomponent,
            GroupComponent=moniker_groupcomponent)
        print("node_containsfile_from_monikers=", node_containsfile_from_monikers)

        # These are genuine WQL queries:
        # PS C:\Users\rchat> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Antecedent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\DriverStore\\\\FileRepository\\\\iigd_dch.inf_amd64_ea63d1eddd5853b5\\\\igdinfo64.dll\""'
        # PS C:\Users\rchat> Get-WmiObject -Query 'select * from CIM_ProcessExecutable where Dependent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"32308\""'
        #
        # Typical result from WQL:
        # __PATH             : \\LAPTOP-R89KG6V1\root\cimv2:CIM_ProcessExecutable.Antecedent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\SYSTEM32\\\\winbrand.dll\"",Dependent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:Win32_Process.Handle=\"4044\""
        #
        # Or:
        # PS C:\Users\rchat> Get-WmiObject -Query 'select * from CIM_DirectoryContainsFile where PartComponent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\DriverStore\\\\FileRepository\\\\iigd_dch.inf_amd64_ea63d1eddd5853b5\\\\igdinfo64.dll\""'
        #
        # Result:
        # __PATH           : \\LAPTOP-R89KG6V1\root\cimv2:CIM_DirectoryContainsFile.GroupComponent="\\\\LAPTOP-R89KG6V1\\root\\ci
        #                    mv2:Win32_Directory.Name=\"C:\\\\WINDOWS\\\\System32\\\\DriverStore\\\\FileRepository\\\\iigd_dch.in
        #                    f_amd64_ea63d1eddd5853b5\"",PartComponent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\
        #                    \\WINDOWS\\\\System32\\\\DriverStore\\\\FileRepository\\\\iigd_dch.inf_amd64_ea63d1eddd5853b5\\\\igd
        #                    info64.dll\""
        assert str(node_containsfile_from_monikers).upper() == \
            'http://www.primhillcomputers.com/ontology/survol#%5C%5CLAPTOP-R89KG6V1%5Croot%5Ccimv2%3ACIM_DirectoryContainsFile.GroupComponent%3D%22%5C%5C%5C%5CLAPTOP-R89KG6V1%5C%5Croot%5C%5Ccimv2%3AWin32_Directory.Name%3D%5C%22C%3A%5C%5C%5C%5CWINDOWS%5C%5C%5C%5CSystem32%5C%22%22%2CPartComponent%3D%22%5C%5C%5C%5CLAPTOP-R89KG6V1%5C%5Croot%5C%5Ccimv2%3ACIM_DataFile.Name%3D%5C%22C%3A%5C%5C%5C%5CWINDOWS%5C%5C%5C%5CSystem32%5C%5C%5C%5Ckernel32.dll%5C%22%22'.upper()

        assert (node_containsfile_from_monikers, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["CIM_DirectoryContainsFile"])) \
               in rdflib_graph.triples((None, None, None))

    def check_query_results(query_results):
        assert [(LITT('C:\\WINDOWS\\System32'),)] == query_results


class Testing_CIM_DirectoryContainsFile_WithDir(metaclass=TestBase):
    """
    Files under the directory "C:"
    """
    label = "CIM_DirectoryContainsFile with Directory=C:"
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
    expected_patterns = [
        _CimPattern(VARI('my_assoc_dir'), 'CIM_DirectoryContainsFile', {'GroupComponent': VARI('my_dir'), 'PartComponent': VARI('my_file')}),
        _CimPattern(VARI('my_file'), 'CIM_DataFile', {'Name': VARI('my_file_name')}),
        _CimPattern(VARI('my_dir'), 'Win32_Directory', {'Name': LITT('C:')}),
    ]

    def top_level_files():
        for root_dir, c_dirs, c_files in os.walk("C:\\"):
            break

        # On Windows 10, this should be something like:
        # ['C:\\DumpStack.log', 'C:\\swapfile.sys', 'C:\\hiberfil.sys', 'C:\\DumpStack.log.tmp' 'C:\\pagefile.sys'
        expected_c_paths = [os.path.join(root_dir, one_file) for one_file in c_files]
        return expected_c_paths

    def check_graph(rdflib_graph):
        expected_c_paths = Testing_CIM_DirectoryContainsFile_WithDir.top_level_files()

        for one_c_path in expected_c_paths:
            moniker_partcomponent = _create_wmi_moniker("CIM_DataFile", Name=one_c_path)
            node_partcomponent = _wmi_moniker_to_rdf_node(moniker_partcomponent)
            assert (node_partcomponent, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["CIM_DataFile"])) \
                   in rdflib_graph.triples((None, None, None))

    def check_query_results(query_results):
        expected_c_paths = Testing_CIM_DirectoryContainsFile_WithDir.top_level_files()
        c_paths_nodes = sorted([(LITT(one_c_path),) for one_c_path in expected_c_paths])

        assert c_paths_nodes == sorted(query_results)


class Testing_Win32_SubDirectory_WithFile(metaclass=TestBase):
    """
    This returns the directory of a given directory.
    """
    label = "Win32_SubDirectory_WithFile with File=Windows/System32"

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
    expected_patterns = [
        _CimPattern(VARI('my_assoc_dir'), 'Win32_SubDirectory', {'GroupComponent': VARI('my_dir'), 'PartComponent': VARI('my_subdir')}),
        _CimPattern(VARI('my_dir'), 'Win32_Directory', {'Name': VARI('my_dir_name')}),
        _CimPattern(VARI('my_subdir'), 'Win32_Directory', {'Name': LITT(r"C:\WINDOWS\System32")}),
    ]

    def check_graph(rdflib_graph):
        print("len(rdflib_graph)=", len(rdflib_graph))

        moniker_partcomponent = _create_wmi_moniker("Win32_Directory", Name=r"C:\WINDOWS\System32")
        print("moniker_partcomponent=", moniker_partcomponent)
        assert moniker_partcomponent == (machine_root_cimv2 + r'Win32_Directory.Name="C:\\WINDOWS\\System32"').upper()
        node_partcomponent = _wmi_moniker_to_rdf_node(moniker_partcomponent)

        print("node_partcomponent=", node_partcomponent)
        assert (node_partcomponent, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["Win32_Directory"])) \
               in rdflib_graph.triples((None, None, None))

        moniker_groupcomponent = _create_wmi_moniker("Win32_Directory", Name=r"C:\WINDOWS")
        print("moniker_groupcomponent=", moniker_groupcomponent)
        assert moniker_groupcomponent == (machine_root_cimv2 + r'Win32_Directory.Name="C:\\WINDOWS"').upper()
        node_groupcomponent = _wmi_moniker_to_rdf_node(moniker_groupcomponent)
        print("node_groupcomponent=", node_groupcomponent)

        assert (node_groupcomponent, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["Win32_Directory"])) \
               in rdflib_graph.triples((None, None, None))

        # It is NOT possible to build the node of an association class from the nodes of the referenced objects.
        # Only monikers are accepted.
        node_containsfile_from_monikers = wmi_attributes_to_rdf_node(
            "Win32_SubDirectory",
            PartComponent=moniker_partcomponent,
            GroupComponent=moniker_groupcomponent)
        print("node_containsfile_from_monikers=", node_containsfile_from_monikers)

        assert str(node_containsfile_from_monikers) == \
            'http://www.primhillcomputers.com/ontology/survol#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWIN32_SUBDIRECTORY.GROUPCOMPONENT%3D%22%5C%5C%5C%5CLAPTOP-R89KG6V1%5C%5CROOT%5C%5CCIMV2%3AWIN32_DIRECTORY.NAME%3D%5C%22C%3A%5C%5C%5C%5CWINDOWS%5C%22%22%2CPARTCOMPONENT%3D%22%5C%5C%5C%5CLAPTOP-R89KG6V1%5C%5CROOT%5C%5CCIMV2%3AWIN32_DIRECTORY.NAME%3D%5C%22C%3A%5C%5C%5C%5CWINDOWS%5C%5C%5C%5CSYSTEM32%5C%22%22'
        assert (node_containsfile_from_monikers, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["Win32_SubDirectory"])) \
               in rdflib_graph.triples((None, None, None))

    def check_query_results(query_results):
        assert [(LITT('C:\\WINDOWS'),)] == query_results


class Testing_Win32_SubDirectory_WithDir(metaclass=TestBase):
    """
    Directories under the directory "C:"
    """
    label = "Win32_SubDirectory with GroupComponent = Directory=C:"
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
    expected_patterns = [
        _CimPattern(VARI('my_assoc_dir'), 'Win32_SubDirectory', {'GroupComponent': VARI('my_dir'), 'PartComponent': VARI('my_subdir')}),
        _CimPattern(VARI('my_subdir'), 'Win32_Directory', {'Name': VARI('my_subdir_name')}),
        _CimPattern(VARI('my_dir'), 'Win32_Directory', {'Name': LITT('C:')}),
    ]

    def top_level_dirs():
        for root_dir, c_dirs, c_files in os.walk("C:\\"):
            break

        expected_c_paths = [os.path.join(root_dir, one_dir) for one_dir in c_dirs]
        return expected_c_paths

    def check_graph(rdflib_graph):
        subjects_only = set([sub for sub, prop, obj in rdflib_graph.triples((None, None, None))])
        for one_subject in subjects_only:
            if one_subject.find("Recycle") >= 0:
                print("one_subject=", one_subject)
        # print("subjects_only=", subjects_only)

        expected_c_paths = Testing_Win32_SubDirectory_WithDir.top_level_dirs()

        for one_c_path in expected_c_paths:
            moniker_partcomponent = _create_wmi_moniker("Win32_Directory", Name=one_c_path)
            node_partcomponent = _wmi_moniker_to_rdf_node(moniker_partcomponent)

            print("node_partcomponent=", node_partcomponent)
            assert (node_partcomponent, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["Win32_Directory"])) \
                   in rdflib_graph.triples((None, None, None))

    def check_query_results(query_results):
        expected_c_paths = Testing_Win32_SubDirectory_WithDir.top_level_dirs()
        c_paths_nodes = sorted([(LITT(one_c_path),) for one_c_path in expected_c_paths])

        assert c_paths_nodes == sorted(query_results)


class Testing_Win32_Directory_Win32_SubDirectory_Win32_SubDirectory(metaclass=TestBase):
    """
    This displays the sub-sub-directories of C:.
    """
    label = "Win32_Directory Win32_SubDirectory Win32_SubDirectory"
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
    expected_patterns = [
        _CimPattern(VARI('my_assoc_dir1'), 'Win32_SubDirectory', {'GroupComponent': VARI('my_dir1'), 'PartComponent': VARI('my_dir2')}),
        _CimPattern(VARI('my_assoc_dir2'), 'Win32_SubDirectory', {'GroupComponent': VARI('my_dir2'), 'PartComponent': VARI('my_dir3')}),
        _CimPattern(VARI('my_dir1'), 'Win32_Directory', {'Name': LITT('C:')}),
        _CimPattern(VARI('my_dir2'), 'Win32_Directory', {}),
        _CimPattern(VARI('my_dir3'), 'Win32_Directory', {'Name': VARI('my_dir_name3')}),
    ]

    def check_graph(rdflib_graph):
        # The graph should conytain only sub-sub-directories of "C:"
        list_directories = list(rdflib_graph.triples((None, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["Win32_Directory"]))))
        print("Loop on directories ============================================================================")
        for one_dir_node, p, o in list_directories:
            # Get its name, maybe not here.
            names_list = list(rdflib_graph.triples((one_dir_node, rdflib.URIRef(SURVOLNS["Name"]), None)))

            # If the name is here, then it is a top-level or second-level sub directory
            if len(names_list) == 1:
                path_str = str(names_list[0][2])
                path_split = split_all(path_str)
                # This is a sub-sub-directory of C:.
                assert len(path_split) == 3 or len(path_split) == 1
                assert path_split[0] == "C:"
            else:
                # Then it is a first-level directory.
                assert len(names_list) == 0

    def check_query_results(query_results):
        # Should not be empty.
        assert query_results

        # This checks the validity of each returned directory.
        for one_path_litt in query_results:
            path_str = str(one_path_litt[0])
            path_split = split_all(path_str)
            # This is a sub-sub-directory of C:.
            assert len(path_split) == 3
            assert path_split[0] == "C:"
            assert os.path.isdir(path_str)


class Testing_CIM_ProcessExecutable_CIM_DirectoryContainsFile_WithHandle(metaclass=TestBase):
    label = "CIM_ProcessExecutable CIM_DirectoryContainsFile Handle=current_pid"
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
    expected_patterns = [
        _CimPattern(VARI('my_assoc'), 'CIM_ProcessExecutable', {'Antecedent': VARI('my_file'), 'Dependent': VARI('my_process')}),
        _CimPattern(VARI('my_assoc_dir'), 'CIM_DirectoryContainsFile', {'PartComponent': VARI('my_file'), 'GroupComponent': VARI('my_dir')}),
        _CimPattern(VARI('my_process'), 'Win32_Process', {'Handle': LITT(current_pid)}),
        _CimPattern(VARI('my_file'), 'CIM_DataFile', {'Name': VARI('my_file_name')}),
        _CimPattern(VARI('my_dir'), 'Win32_Directory', {'Name': VARI('my_dir_name')}),
    ]

    def check_graph(rdflib_graph):
        process_tuples = list(rdflib_graph.triples((None, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["CIM_Process"]))))
        assert len(process_tuples) == 0

        # The current process must be in the graph and only this one.
        process_tuples = list(rdflib_graph.triples((None, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["Win32_Process"]))))
        assert len(process_tuples) == 1
        current_process_node = wmi_attributes_to_rdf_node("Win32_Process", Handle=current_pid)
        print("process_tuples[0][0]=", process_tuples[0][0])
        print("current_process_node=", current_process_node)
        assert process_tuples[0][0] == current_process_node

    def check_query_results(query_results):
        # Should not be empty.
        assert query_results
        #print("query_results=", query_results)

        dirs_set = set([str(one_tuple[0]) for one_tuple in query_results])
        print("dirs_set=", dirs_set)
        # This is Windows'fault that we have three cases for the same directory name.
        assert 'C:\\WINDOWS\\System32' in dirs_set
        assert 'C:\\WINDOWS\\SYSTEM32' in dirs_set
        assert 'C:\\WINDOWS\\system32' in dirs_set
        assert os.path.dirname(sys.executable) in dirs_set


class Testing_CIM_ProcessExecutable_FullScan(metaclass=TestBase):
    """
    This gets the directories of all executables and libraries of all processes.
    """
    label = "CIM_ProcessExecutable full scan"
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
    expected_patterns = [
        _CimPattern(VARI('my_assoc'), 'CIM_ProcessExecutable', {'Dependent': VARI('my_process'), 'Antecedent': VARI('my_file')}),
        _CimPattern(VARI('my_process'), 'Win32_Process', {'Handle': VARI('my_process_handle')}),
        _CimPattern(VARI('my_file'), 'CIM_DataFile', {'Name': VARI('my_file_name')}),
    ]

    def check_graph(rdflib_graph):
        # All processes.
        processes_set = set([s for s, p, o in rdflib_graph.triples((None, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["Win32_Process"])))])
        print("processes_set=", processes_set)

        # The current process must be in the graph.
        current_process_node = wmi_attributes_to_rdf_node("Win32_Process", Handle=current_pid)
        assert current_process_node in processes_set
        process_tuples = list(rdflib_graph.triples((current_process_node, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["Win32_Process"]))))
        print("current_process_node=", current_process_node)
        print("len(process_tuples)=", len(process_tuples))
        assert len(process_tuples) == 1

        # The executable of Python must be in the triples.
        current_executable_node = wmi_attributes_to_rdf_node("CIM_DataFile", Name=sys.executable)
        execs_tuples = list(rdflib_graph.triples((current_executable_node, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["CIM_DataFile"]))))
        assert len(execs_tuples) >= 1

    def check_query_results(query_results):
        # Should not be empty.
        assert query_results

        print("query_results=", query_results)
        # FIXME: Why in uppercase ???
        assert (LITT(sys.executable.upper()), LITT(str(current_pid)),) in query_results


class Testing_CIM_ProcessExecutable_Groups(metaclass=TestBase):
    """
    This gets the directories of all executables and libraries of all processes.
    """
    label = "CIM_ProcessExecutable group by exec"
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
    expected_patterns = [
        _CimPattern(VARI('my_assoc'), 'CIM_ProcessExecutable', {'Dependent': VARI('my_process'), 'Antecedent': VARI('my_file')}),
        _CimPattern(VARI('my_process'), 'Win32_Process', {'Handle': VARI('my_process_handle')}),
        _CimPattern(VARI('my_file'), 'CIM_DataFile', {'Name': VARI('my_file_name')}),
    ]

    def check_graph(rdflib_graph):
        # All processes.
        processes_set = set([s for s, p, o in rdflib_graph.triples((None, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["Win32_Process"])))])
        print("processes_set=", processes_set)

        # The current process must be in the graph.
        current_process_node = wmi_attributes_to_rdf_node("Win32_Process", Handle=current_pid)
        assert current_process_node in processes_set
        process_tuples = list(rdflib_graph.triples((current_process_node, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["Win32_Process"]))))
        print("current_process_node=", current_process_node)
        print("len(process_tuples)=", len(process_tuples))
        assert len(process_tuples) == 1

        # The executable of Python must be in the triples.
        current_executable_node = wmi_attributes_to_rdf_node("CIM_DataFile", Name=sys.executable)
        execs_tuples = list(rdflib_graph.triples((current_executable_node, rdflib.namespace.RDF.type, rdflib.URIRef(SURVOLNS["CIM_DataFile"]))))
        assert len(execs_tuples) >= 1

    def check_query_results(query_results):
        # Should not be empty.
        assert query_results

        print("query_results=", query_results)
        # FIXME: Why in uppercase ???
        assert (LITT(sys.executable.upper()), LITT(str(current_pid)),) in query_results


class Testing_CIM_ProcessExecutable_CIM_DirectoryContainsFile(metaclass=TestBase):
    label = "CIM_ProcessExecutable CIM_DirectoryContainsFile"
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
    expected_patterns = [
        _CimPattern(VARI('my_assoc'), 'CIM_ProcessExecutable', {'Dependent': VARI('my_process'), 'Antecedent': VARI('my_file')}),
        _CimPattern(VARI('my_assoc_dir'), 'CIM_DirectoryContainsFile', {'GroupComponent': VARI('my_dir'), 'PartComponent': VARI('my_file')}),
        _CimPattern(VARI('my_process'), 'Win32_Process', {}),
        _CimPattern(VARI('my_file'), 'CIM_DataFile', {'Name': VARI('my_file_name')}),
        _CimPattern(VARI('my_dir'), 'Win32_Directory', {'Name': VARI('my_dir_name')}),
    ]

    def check_graph(rdflib_graph):
        assert len(rdflib_graph) > 0

    def check_query_results(query_results):
        # Should not be empty.
        assert query_results

        assert (LITT(r'C:\WINDOWS\SYSTEM32'),) in query_results
        assert (LITT(r'C:\WINDOWS\SHELLEXPERIENCES'),) in query_results
        assert (LITT(r'C:\WINDOWS\SYSTEM32\WBEM'),) in query_results


class Testing_CIM_Process_CIM_DataFile_SameCaption(metaclass=TestBase):
    label = "CIM_Process CIM_DataFile Same Caption"
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
    expected_patterns = [
        _CimPattern(VARI('my_process'), 'CIM_Process', {'Caption': VARI('same_caption')}),
        _CimPattern(VARI('my_file'), 'CIM_DataFile', {'Caption': VARI('same_caption')}),
    ]

    def check_graph(rdflib_graph):
        assert len(rdflib_graph) > 0

    def check_query_results(query_results):
        # Should not be empty.
        assert query_results
        exit(0)

*/