package paquetage;

import com.google.common.collect.Sets;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Values;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

// https://www.programcreek.com/java-api-examples/?api=org.eclipse.rdf4j.query.parser.ParsedQuery



/**
 * This is used after WMI values are calculated, so they can be inserted in a repository.
 * Here is how the whole mechanism works:
 * (1) Extracts the BGPs from a Sparql query.
 * (2) Assemble the BGPs by instances, with their attributes.
 * (3) Associate the instances to WMI classes.
 * (4) Get the WMI objects and the values, based on the BGPs.
 * (5) Build RDF triples with these values.
 * (6) Inserts these triples in the original RDF repository.
 * (7) Run the Sparql query on this repository.
 */
public class TriplesGenerationTest {
    ValueFactory factory = SimpleValueFactory.getInstance();

    static ObjectPattern FindObjectPattern(SparqlBGPExtractor extractor, String variable) {
        ObjectPattern pattern = extractor.patternsMap.get(variable);
        Assert.assertNotEquals(null, pattern);
        Assert.assertEquals(variable, pattern.VariableName);
        return pattern;
    }

    static void CompareKeyValue(ObjectPattern.PredicateObjectPair a, String predicate, boolean isVariable, String content) {
        Assert.assertEquals(predicate, a.Predicate());
        Assert.assertEquals(isVariable, a.isVariable());
        Assert.assertEquals(content, a.Content());
    }

    @Test
    /***
     * Create triples from BGPs and variable-value pairs.
     */
    public void TriplesGenerationFromBGPs_1() throws Exception {
        String sparqlQuery = """
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name
            where {
                ?my_dir rdf:type cimv2:Win32_Directory .
                ?my_dir cimv2:Name ?dir_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("dir_name"));

        Assert.assertEquals(extractor.patternsAsArray().size(), 1);

        // Check the exact content of the BGP.
        ObjectPattern patternWin32_Directory = FindObjectPattern(extractor, "my_dir");
        Assert.assertEquals(patternWin32_Directory.className, PresentUtils.toCIMV2("Win32_Directory"));
        Assert.assertEquals(patternWin32_Directory.Members.size(), 1);
        CompareKeyValue(patternWin32_Directory.Members.get(0), PresentUtils.toCIMV2("Name"), true, "dir_name");

        // Now it generates triples from the patterns, forcing the values of the single variable.
        String dirIri = "any_iri_will_do";
        Solution rows = new Solution();
        rows.add(new Solution.Row(Map.of(
                "my_dir", new Solution.Row.ValueTypePair(dirIri, GenericProvider.ValueType.NODE_TYPE),
                "dir_name", new Solution.Row.ValueTypePair("C:", GenericProvider.ValueType.STRING_TYPE))));

        List<Triple> triples = extractor.GenerateTriples(rows);

        // Now check the content of the generated triples.
        Assert.assertEquals(2, triples.size());

        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIri),
                Values.iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                Values.iri(PresentUtils.toCIMV2("Win32_Directory")))
        ));
        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIri),
                Values.iri(PresentUtils.toCIMV2("Name")),
                Values.literal("C:"))
        ));
    }

    @Test
    /***
     * Create triples from BGPs and variable-value pairs.
     */
    public void TriplesGenerationFromBGPs_2() throws Exception {
        String sparqlQuery = """
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name
            where {
                ?my_dir rdf:type cimv2:Win32_Directory .
                ?my_dir cimv2:Name ?dir_name .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("dir_name"));

        Assert.assertEquals(extractor.patternsAsArray().size(), 1);

        // Check the exact content of the BGP.
        ObjectPattern patternWin32_Directory = FindObjectPattern(extractor, "my_dir");
        Assert.assertEquals(patternWin32_Directory.className, PresentUtils.toCIMV2("Win32_Directory"));
        Assert.assertEquals(patternWin32_Directory.Members.size(), 1);
        CompareKeyValue(patternWin32_Directory.Members.get(0), PresentUtils.toCIMV2("Name"), true, "dir_name");

        // Now it generates triples from the patterns, forcing the values of the single variable.
        String dirIriC = "iriC";
        String dirIriD = "iriD";
        Solution rows = new Solution();
        rows.add(
                new Solution.Row(Map.of(
                        "my_dir", new Solution.Row.ValueTypePair(dirIriC, GenericProvider.ValueType.NODE_TYPE),
                        "dir_name", new Solution.Row.ValueTypePair("C:", GenericProvider.ValueType.STRING_TYPE))));
        rows.add(
                new Solution.Row(Map.of(
                        "my_dir", new Solution.Row.ValueTypePair(dirIriD, GenericProvider.ValueType.NODE_TYPE),
                        "dir_name", new Solution.Row.ValueTypePair("D:", GenericProvider.ValueType.STRING_TYPE))));

        List<Triple> triples = extractor.GenerateTriples(rows);

        for(Triple triple: triples) {
            System.out.println("T=" + triple);
        }

        // Now check the content of the generated triples.
        Assert.assertEquals(4, triples.size());

        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIriC),
                Values.iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                Values.iri(PresentUtils.toCIMV2("Win32_Directory")))
        ));
        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIriC),
                Values.iri(PresentUtils.toCIMV2("Name")),
                Values.literal("C:"))
        ));
        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIriD),
                Values.iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                Values.iri(PresentUtils.toCIMV2("Win32_Directory")))
        ));
        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIriD),
                Values.iri(PresentUtils.toCIMV2("Name")),
                Values.literal("D:"))
        ));
    }

    /** This test the xtraction of triples from a simple Sparql query,
     * and after that, tests the replacement of variables with a set of actual values.
     *
     * @throws Exception
     */
    @Test
    public void TriplesGenerationFromBGPs_3() throws Exception {
        String sparqlQuery = """
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name ?dir_caption
            where {
                ?my_dir rdf:type cimv2:Win32_Directory .
                ?my_dir cimv2:Name ?dir_name .
                ?my_dir cimv2:Caption ?dir_caption .
            }
        """;
        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery);
        Assert.assertEquals(extractor.bindings, Sets.newHashSet("dir_caption", "dir_name"));

        Assert.assertEquals(extractor.patternsAsArray().size(), 1);

        // Check the exact content of the BGP.
        ObjectPattern patternWin32_Directory = FindObjectPattern(extractor, "my_dir");
        Assert.assertEquals(patternWin32_Directory.className, PresentUtils.toCIMV2("Win32_Directory"));
        Assert.assertEquals(patternWin32_Directory.Members.size(), 2);
        CompareKeyValue(patternWin32_Directory.Members.get(1), PresentUtils.toCIMV2("Caption"), true, "dir_caption");
        CompareKeyValue(patternWin32_Directory.Members.get(0), PresentUtils.toCIMV2("Name"), true, "dir_name");

        // Now it generates triples from the patterns, forcing the values of the single variable.
        String dirIri = "arbitrary_iri";
        Solution rows = new Solution();
        rows.add(
            new Solution.Row(Map.of(
                "my_dir", new Solution.Row.ValueTypePair(dirIri, GenericProvider.ValueType.NODE_TYPE),
                "dir_name", new Solution.Row.ValueTypePair("C:", GenericProvider.ValueType.STRING_TYPE),
                "dir_caption", new Solution.Row.ValueTypePair("This is a text", GenericProvider.ValueType.STRING_TYPE))));

        List<Triple> triples = extractor.GenerateTriples(rows);

        for(Triple triple: triples) {
            System.out.println("T=" + triple);
        }
        Assert.assertEquals(3, triples.size());

        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIri),
                Values.iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                Values.iri(PresentUtils.toCIMV2("Win32_Directory")))
        ));
        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIri),
                Values.iri(PresentUtils.toCIMV2("Name")),
                Values.literal("C:"))
        ));
        Assert.assertTrue(triples.contains(factory.createTriple(
                WmiOntology.WbemPathToIri(dirIri),
                Values.iri(PresentUtils.toCIMV2("Caption")),
                Values.literal("This is a text"))
        ));
    }

}

