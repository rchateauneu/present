package paquetage;

import com.google.common.collect.Sets;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Values;
import org.junit.Assert;
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
public class StatementsGenerationTest {
    ValueFactory factory = SimpleValueFactory.getInstance();

    static private void CompareKeyValue(ObjectPattern.PredicateObjectPair a, String predicate, boolean isVariable, String content) {
        Assert.assertEquals(predicate, a.shortPredicate);
        Assert.assertEquals(a.variableName, a.variableName);
        if(a.variableName == null)
            Assert.assertEquals(ValueTypePair.fromString(content), a.objectContent);
    }

    static private Resource WbemPathToIriCIMV2(String valueString) throws Exception {
        return WmiOntology.wbemPathToIri("ROOT\\CIMV2", valueString);
    }

    @Test
    /***
     * Create statements from BGPs and variable-value pairs.
     */
    public void StatementsGenerationFromBGPs_1() throws Exception {
        String sparqlQuery = """
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name
            where {
                ?my_dir rdf:type cimv2:Win32_Directory .
                ?my_dir cimv2:Name ?dir_name .
            }
        """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery, false);
        Assert.assertEquals(extractor.setBindings, Sets.newHashSet("dir_name"));

        Assert.assertEquals(extractor.patternsAsArray().size(), 1);

        // Check the exact content of the BGP.
        ObjectPattern patternWin32_Directory = extractor.findObjectPattern("my_dir");
        Assert.assertEquals("Win32_Directory", patternWin32_Directory.subjectClassname);
        Assert.assertEquals("ROOT\\CIMV2", patternWin32_Directory.subjectNamespace);
        Assert.assertEquals(1, patternWin32_Directory.membersList.size());
        CompareKeyValue(patternWin32_Directory.membersList.get(0), "Name", true, "dir_name");

        // Now it generates triples from the patterns, forcing the values of the single variable.
        String dirIri = "\\\\ANY_MACHINE\\ROOT\\CIMV2:Win32_Directory.Name=C:";
        Solution rows = new Solution();
        rows.add(new Solution.Row(Map.of(
                "my_dir", new ValueTypePair(dirIri, ValueTypePair.ValueType.NODE_TYPE),
                "dir_name", ValueTypePair.fromString("C:"))));

        List<Statement> statements = extractor.generateStatements(rows);

        // Now check the content of the generated statements.
        Assert.assertEquals(2, statements.size());

        Assert.assertTrue(statements.contains(factory.createStatement(
                WbemPathToIriCIMV2(dirIri),
                Values.iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                Values.iri(WmiProvider.toCIMV2("Win32_Directory")))
        ));
        Assert.assertTrue(statements.contains(factory.createStatement(
                WbemPathToIriCIMV2(dirIri),
                Values.iri(WmiProvider.toCIMV2("Name")),
                Values.literal("C:"))
        ));
    }

    @Test
    /***
     * Create statements from BGPs and variable-value pairs.
     */
    public void StatementsGenerationFromBGPs_2() throws Exception {
        String sparqlQuery = """
            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
            select ?dir_name
            where {
                ?my_dir rdf:type cimv2:Win32_Directory .
                ?my_dir cimv2:Name ?dir_name .
            }
        """;

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery, false);
        Assert.assertEquals(extractor.setBindings, Sets.newHashSet("dir_name"));

        Assert.assertEquals(extractor.patternsAsArray().size(), 1);

        // Check the exact content of the BGP.
        ObjectPattern patternWin32_Directory = extractor.findObjectPattern("my_dir");
        Assert.assertEquals("Win32_Directory", patternWin32_Directory.subjectClassname);
        Assert.assertEquals("ROOT\\CIMV2", patternWin32_Directory.subjectNamespace);
        Assert.assertEquals(1,patternWin32_Directory.membersList.size());
        CompareKeyValue(patternWin32_Directory.membersList.get(0), "Name", true, "dir_name");

        // Now it generates statements from the patterns, forcing the values of the single variable.
        String dirIriC = "\\\\ANY_MACHINE\\ROOT\\CIMV2:Win32_Directory.Name=C:";
        String dirIriD = "\\\\ANY_MACHINE\\ROOT\\CIMV2:Win32_Directory.Name=D:";
        Solution rows = new Solution();
        rows.add(
                new Solution.Row(Map.of(
                        "my_dir", new ValueTypePair(dirIriC, ValueTypePair.ValueType.NODE_TYPE),
                        "dir_name", ValueTypePair.fromString("C:"))));
        rows.add(
                new Solution.Row(Map.of(
                        "my_dir", new ValueTypePair(dirIriD, ValueTypePair.ValueType.NODE_TYPE),
                        "dir_name", ValueTypePair.fromString("D:"))));

        List<Statement> statements = extractor.generateStatements(rows);

        for(Statement statement: statements) {
            System.out.println("T=" + statement);
        }

        // Now check the content of the generated statements.
        Assert.assertEquals(4, statements.size());

        Assert.assertTrue(statements.contains(factory.createStatement(
                WbemPathToIriCIMV2(dirIriC),
                Values.iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                Values.iri(WmiProvider.toCIMV2("Win32_Directory")))
        ));
        Assert.assertTrue(statements.contains(factory.createStatement(
                WbemPathToIriCIMV2(dirIriC),
                Values.iri(WmiProvider.toCIMV2("Name")),
                Values.literal("C:"))
        ));
        Assert.assertTrue(statements.contains(factory.createStatement(
                WbemPathToIriCIMV2(dirIriD),
                Values.iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                Values.iri(WmiProvider.toCIMV2("Win32_Directory")))
        ));
        Assert.assertTrue(statements.contains(factory.createStatement(
                WbemPathToIriCIMV2(dirIriD),
                Values.iri(WmiProvider.toCIMV2("Name")),
                Values.literal("D:"))
        ));
    }

    /** This test the extraction of statements from a simple Sparql query,
     * and after that, tests the replacement of variables with a set of actual values.
     *
     * @throws Exception
     */
    @Test
    public void StatementsGenerationFromBGPs_3() throws Exception {
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

        SparqlBGPExtractor extractor = new SparqlBGPExtractor(sparqlQuery, false);
        Assert.assertEquals(extractor.setBindings, Sets.newHashSet("dir_caption", "dir_name"));

        Assert.assertEquals(extractor.patternsAsArray().size(), 1);

        // Check the exact content of the BGP.
        ObjectPattern patternWin32_Directory = extractor.findObjectPattern("my_dir");
        Assert.assertEquals("Win32_Directory", patternWin32_Directory.subjectClassname);
        Assert.assertEquals("ROOT\\CIMV2", patternWin32_Directory.subjectNamespace);
        Assert.assertEquals(2, patternWin32_Directory.membersList.size());
        CompareKeyValue(patternWin32_Directory.membersList.get(1), "Caption", true, "dir_caption");
        CompareKeyValue(patternWin32_Directory.membersList.get(0), "Name", true, "dir_name");

        // Now it generates statements from the patterns, forcing the values of the single variable.
        String dirIri = "\\\\ANY_MACHINE\\ROOT\\CIMV2:Win32_Directory.Name=C:";
        Solution rows = new Solution();
        rows.add(
            new Solution.Row(Map.of(
                "my_dir", new ValueTypePair(dirIri, ValueTypePair.ValueType.NODE_TYPE),
                "dir_name", ValueTypePair.fromString("C:"),
                "dir_caption", ValueTypePair.fromString("This is a text"))));

        List<Statement> statements = extractor.generateStatements(rows);

        for(Statement statement: statements) {
            System.out.println("T=" + statement);
        }
        Assert.assertEquals(3, statements.size());

        Assert.assertTrue(statements.contains(factory.createStatement(
                WbemPathToIriCIMV2(dirIri),
                Values.iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                Values.iri(WmiProvider.toCIMV2("Win32_Directory")))
        ));
        Assert.assertTrue(statements.contains(factory.createStatement(
                WbemPathToIriCIMV2(dirIri),
                Values.iri(WmiProvider.toCIMV2("Name")),
                Values.literal("C:"))
        ));
        Assert.assertTrue(statements.contains(factory.createStatement(
                WbemPathToIriCIMV2(dirIri),
                Values.iri(WmiProvider.toCIMV2("Caption")),
                Values.literal("This is a text"))
        ));
    }

}

