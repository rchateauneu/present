package paquetage;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class RdfSolutionTest {

    @Test
    public void testToJsonEmpty() {
        RdfSolution solution = new RdfSolution();
        Assert.assertEquals(0, solution.size());
        Assert.assertEquals(new HashSet<String>(), solution.bindingsSet());
        String actualJson = solution.toJson(true);

        String expectedJson = """
{
    "head": {"vars": []},
    "results": {"bindings": []}
}""";

        Assert.assertEquals(expectedJson, actualJson);
    }

    @Test
    public void testToJsonOneLine_1() {
        RdfSolution solution = new RdfSolution();
        solution.add(RdfSolution.Tuple.tupleFactory()
                .addKeyValue("k1", false, "0", "http://www.w3.org/2001/XMLSchema#long"));
                // .addKeyValue("k1", false, "\"0\"^^<http://www.w3.org/2001/XMLSchema#long>\""));
        Assert.assertEquals(1, solution.size());
        Assert.assertEquals(Set.of("k1"), solution.bindingsSet());
        String actualJson = solution.toJson(true);

    /*
            {
              "head": { "vars": [ "book" , "title" ]
              } ,
              "results": {
                "bindings": [
                  {
                    "book": { "type": "uri" , "value": "http://example.org/book/book6" } ,
                    "title": { "type": "literal" , "value": "Harry Potter and the Half-Blood Prince" }
                  } ,
                  {
                    "book": { "type": "uri" , "value": "http://example.org/book/book1" } ,
                    "title": { "type": "literal" , "value": "Harry Potter and the Philosopher's Stone" }
                  }
                ]
              }
            }
     */
        String expectedJson = """
{
    "head": {"vars": ["k1"]},
    "results": {"bindings": [{"k1": {
        "datatype": "http://www.w3.org/2001/XMLSchema#long",
        "type": "literal",
        "value": "\\"0\\"^^<http://www.w3.org/2001/XMLSchema#long>\\""
    }}]}
}""";

        Assert.assertEquals(expectedJson, actualJson);
    }

    @Test
    public void testToJsonOneLine_2() {
        RdfSolution solution = new RdfSolution();
        solution.add(RdfSolution.Tuple.tupleFactory()
                .addKeyValue("k1", false, "0", "http://www.w3.org/2001/XMLSchema#long"));
                // .addKeyValue("k1", false, "\"0\"^^<http://www.w3.org/2001/XMLSchema#long>\""));
        Assert.assertEquals(1, solution.size());
        Assert.assertEquals(Set.of("k1"), solution.bindingsSet());
        String actualJson = solution.toJson(false);

        String expectedJson = """
{
    "head": {"vars": ["k1"]},
    "results": {"bindings": [{"k1": {
        "datatype": "http://www.w3.org/2001/XMLSchema#long",
        "type": "literal",
        "value": "0"
    }}]}
}""";

        Assert.assertEquals(expectedJson, actualJson);
    }

    @Test
    public void testToJsonTwoLines() throws Exception {
        RdfSolution solution = new RdfSolution();
        solution.add(RdfSolution.Tuple.tupleFactory()
                .addKeyValue("k1", false, "v1", "http://www.w3.org/2001/XMLSchema#string")
                .addKeyValue("k2", false, "2", "http://www.w3.org/2001/XMLSchema#long"));
        solution.add(RdfSolution.Tuple.tupleFactory()
                .addKeyValue("k1", false, "v2", "http://www.w3.org/2001/XMLSchema#string")
                .addKeyValue("k3", false, "3", "http://www.w3.org/2001/XMLSchema#long"));
        Assert.assertEquals(2, solution.size());
        Assert.assertEquals(Set.of("k1", "k2", "k3"), solution.bindingsSet());
        String actualJson = solution.toJson(true);

        String expectedJson = """
{
    "head": {"vars": [
        "k1",
        "k2",
        "k3"
    ]},
    "results": {"bindings": [
        {
            "k1": {
                "type": "literal",
                "value": "v1"
            },
            "k2": {
                "type": "literal",
                "value": "2"
            }
        },
        {
            "k1": {
                "type": "literal",
                "value": "v2"
            },
            "k3": {
                "type": "literal",
                "value": "3"
            }
        }
    ]}
}""";
        Assert.assertEquals(expectedJson, actualJson);
    }

    @Test
    public void testToJsonNode() {
        RdfSolution solution = new RdfSolution();
        solution.add(RdfSolution.Tuple.tupleFactory()
                .addKeyValue("k1", true, "http://some.thing", null));
        Assert.assertEquals(1, solution.size());
        Assert.assertEquals(Set.of("k1"), solution.bindingsSet());
        String actualJson = solution.toJson(true);

        String expectedJson = """
{
    "head": {"vars": ["k1"]},
    "results": {"bindings": [{"k1": {
        "type": "uri",
        "value": "http://some.thing"
    }}]}
}""";

        Assert.assertEquals(expectedJson, actualJson);
    }
}
