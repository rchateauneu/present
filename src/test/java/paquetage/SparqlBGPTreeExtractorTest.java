package paquetage;

import org.eclipse.rdf4j.model.Statement;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

// https://www.programcreek.com/java-api-examples/?api=org.eclipse.rdf4j.query.parser.ParsedQuery

public class SparqlBGPTreeExtractorTest {
    static Set<String> solutionToStringSet(Solution solution) {
        Set<String> asStrings = solution.stream().map(row -> row.toString()).collect(Collectors.toSet());
        return asStrings;
    }
    static void HelperCheck(String sparqlQuery, String[] expectedSolution) throws Exception{
        SparqlBGPTreeExtractor extractor = new SparqlBGPTreeExtractor(sparqlQuery);
        Solution solution = extractor.EvaluateSolution();
        System.out.println("Solution:");
        System.out.println(solution);

        Set<String> actualSolution = solutionToStringSet(solution);
        Set<String> expectedSet = Arrays.stream(expectedSolution).collect(Collectors.toSet());
        Assert.assertEquals(expectedSet, actualSolution);

        List<Statement> statements = extractor.SolutionToStatements(solution);
        System.out.println("Statements:");
        System.out.println(statements);
        Assert.assertTrue(false);
    }

    @Test
    public void Parse_Check_01() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process where 
                    {
                        {
                            select ?process1
                            where {
                                ?process1 cimv2:DummyClass.DummyProperty "DummyKey_is.1" .
                            }
                        }
                        union
                        {
                            select ?process2
                            where {
                                ?process2 cimv2:DummyClass.DummyProperty "DummyKey_is.2" .
                            }
                        }
                    }
                    
        """;
        String[] expectedSolution = {
                "{process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}}",
                "{process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}"
        };
        HelperCheck(sparqlQuery, expectedSolution);
    }

    @Test
    public void Parse_Check_02() throws Exception {
        String sparqlQuery = """
                            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                            select ?process where 
                            {
                                {
                                    select ?process1
                                    where {
                                        ?process1 cimv2:DummyClass.DummyProperty "DummyKey_is.1" .
                                    }
                                }
                                union
                                {
                                    select ?process2
                                    where {
                                        ?process2 cimv2:DummyClass.DummyProperty "DummyKey_is.2" .
                                    }
                                }
                                union
                                {
                                    select ?process3
                                    where {
                                        ?process3 cimv2:DummyClass.DummyProperty "DummyKey_is.3" .
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}}",
                "{process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}",
                "{process3={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}"
        };
        HelperCheck(sparqlQuery, expectedSolution);
    }

    @Test
    public void Parse_Check_03() throws Exception {

        String sparqlQuery = """
                            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                            select *
                            where {
                                ?processX cimv2:DummyClass.DummyProperty "DummyKey_is.X" .
                                ?processY cimv2:DummyClass.DummyProperty "DummyKey_is.Y" .
                                ?processZ cimv2:DummyClass.DummyProperty "DummyKey_is.Z" .
                            }
                """;
        String[] expectedSolution = {
                "{processY={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Y\" -> NODE_TYPE}, processZ={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Z\" -> NODE_TYPE}, processX={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"X\" -> NODE_TYPE}}"
        };
        HelperCheck(sparqlQuery, expectedSolution);
    }

    @Test
    public void Parse_Check_04() throws Exception {
        String sparqlQuery = """
                            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                            select ?process where 
                            {
                                {
                                    select ?process1
                                    where {
                                        ?process1 cimv2:DummyClass.DummyProperty "DummyKey_is.1" .
                                    }
                                }
                                union
                                {
                                    select ?process2 where 
                                    {
                                        {
                                            select ?process21
                                            where {
                                                ?process1 cimv2:DummyClass.DummyProperty "DummyKey_is.21" .
                                            }
                                        }
                                        union
                                        {
                                            select ?process22
                                            where {
                                                ?process2 cimv2:DummyClass.DummyProperty "DummyKey_is.22" .
                                            }
                                        }
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}}",
                "{process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}}",
                "{process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}}"
        };
        HelperCheck(sparqlQuery, expectedSolution);
    }

    @Test
    public void Parse_Check_05() throws Exception {

        String sparqlQuery = """
                            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                            select *
                            where {
                                ?processX cimv2:DummyClass.DummyProperty "DummyKey_is.X" .
                            }
                """;
        String[] expectedSolution = {
                "{processX={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"X\" -> NODE_TYPE}}"
        };
        HelperCheck(sparqlQuery, expectedSolution);
    }

    @Test
    public void Parse_Check_06() throws Exception {
        String sparqlQuery = """
                            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                            select *
                            where {
                                ?processX cimv2:DummyClass.DummyProperty "DummyKey_is.X" .
                                {
                                    select *
                                    where {
                                        ?processY cimv2:DummyClass.DummyProperty "DummyKey_is.Y" .
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{processX={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"X\" -> NODE_TYPE}}"
        };
        HelperCheck(sparqlQuery, expectedSolution);
    }

    @Test
    public void Parse_Check_07() throws Exception {
        String sparqlQuery = """
                            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                            select *
                            where {
                                ?processX cimv2:DummyClass.DummyProperty "DummyKey_is.X" .
                                {
                                    select *
                                    where {
                                        ?processY1 cimv2:DummyClass.DummyProperty "DummyKey_is.Y1" .
                                        ?processY2 cimv2:DummyClass.DummyProperty "DummyKey_is.Y2" .
                                        ?processY3 cimv2:DummyClass.DummyProperty "DummyKey_is.Y3" .
                                    }
                                }
                                ?processY cimv2:DummyClass.DummyProperty "DummyKey_is.Y" .
                                ?processZ cimv2:DummyClass.DummyProperty "DummyKey_is.Z" .
                            }
                """;
        String[] expectedSolution = {
                "{processY={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Y\" -> NODE_TYPE}, processZ={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Z\" -> NODE_TYPE}, processX={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"X\" -> NODE_TYPE}}"
        };
        HelperCheck(sparqlQuery, expectedSolution);
    }

    @Test
    public void Parse_Check_08() throws Exception {
        String sparqlQuery = """
                            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                            select *
                            where {
                                ?processX cimv2:DummyClass.DummyProperty "DummyKey_is.X" .
                                {
                                    select *
                                    where {
                                        ?processY1 cimv2:DummyClass.DummyProperty "DummyKey_is.Y1" .
                                        ?processY2 cimv2:DummyClass.DummyProperty "DummyKey_is.Y2" .
                                        ?processY3 cimv2:DummyClass.DummyProperty "DummyKey_is.Y3" .
                                    }
                                }
                                ?processY cimv2:DummyClass.DummyProperty "DummyKey_is.Y" .
                                {
                                    select *
                                    where {
                                        ?processZ1 cimv2:DummyClass.DummyProperty "DummyKey_is.Z1" .
                                        ?processZ2 cimv2:DummyClass.DummyProperty "DummyKey_is.Z2" .
                                        ?processZ3 cimv2:DummyClass.DummyProperty "DummyKey_is.Z3" .
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                ""
        };
        HelperCheck(sparqlQuery, expectedSolution);
    }

    @Test
    public void Parse_Check_09() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process where 
                    {
                        {
                            select ?process1
                            where {
                                ?process11 cimv2:DummyClass.DummyProperty "DummyKey_is.11" .
                                ?process12 cimv2:DummyClass.DummyProperty "DummyKey_is.12" .
                                ?process13 cimv2:DummyClass.DummyProperty "DummyKey_is.13" .
                            }
                        }
                        union
                        {
                            select ?process2
                            where {
                                ?process21 cimv2:DummyClass.DummyProperty "DummyKey_is.21" .
                                ?process22 cimv2:DummyClass.DummyProperty "DummyKey_is.22" .
                                ?process23 cimv2:DummyClass.DummyProperty "DummyKey_is.23" .
                            }
                        }
                        union
                        {
                            select ?process3
                            where {
                                ?process31 cimv2:DummyClass.DummyProperty "DummyKey_is.31" .
                                ?process32 cimv2:DummyClass.DummyProperty "DummyKey_is.32" .
                                ?process33 cimv2:DummyClass.DummyProperty "DummyKey_is.33" .
                            }
                        }
                    }
                    
        """;
        String[] expectedSolution = {
                "{process11={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"11\" -> NODE_TYPE}, process13={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"13\" -> NODE_TYPE}, process12={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"12\" -> NODE_TYPE}}",
                "{process22={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}, process21={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, process23={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"23\" -> NODE_TYPE}}",
                "{process33={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"33\" -> NODE_TYPE}, process32={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"32\" -> NODE_TYPE}, process31={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"31\" -> NODE_TYPE}}"
        };
        HelperCheck(sparqlQuery, expectedSolution);
    }

    /** Subquery containing an union. */
    @Test
    public void Parse_Check_10() throws Exception {
        String sparqlQuery = """
                            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                            select *
                            where {
                                {
                                    select *
                                    where {
                                        {
                                            select ?process_a
                                            where {
                                                ?process_aa cimv2:DummyClass.DummyProperty "DummyKey_is.aa" .
                                                ?process_ab cimv2:DummyClass.DummyProperty "DummyKey_is.ab" .
                                                ?process_ac cimv2:DummyClass.DummyProperty "DummyKey_is.ac" .
                                            }
                                        }
                                        union
                                        {
                                            select ?process_b
                                            where {
                                                ?process_ba cimv2:DummyClass.DummyProperty "DummyKey_is.ba" .
                                                ?process_bb cimv2:DummyClass.DummyProperty "DummyKey_is.bb" .
                                                ?process_bc cimv2:DummyClass.DummyProperty "DummyKey_is.bc" .
                                            }
                                        }
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{process_ac={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"ac\" -> NODE_TYPE}, process_aa={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"aa\" -> NODE_TYPE}, process_ab={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"ab\" -> NODE_TYPE}}",
                "{process_ba={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"ba\" -> NODE_TYPE}, process_bb={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"bb\" -> NODE_TYPE}, process_bc={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"bc\" -> NODE_TYPE}}"
        };
        HelperCheck(sparqlQuery, expectedSolution);
    }

    // Ajouter SubQuery et Intersection et LeftJoin etc...

}

