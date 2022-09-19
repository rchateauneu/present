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
    static void HelperCheck(String sparqlQuery, String[] expectedSolution, String[] expectedStatements) throws Exception{
        SparqlBGPTreeExtractor extractor = new SparqlBGPTreeExtractor(sparqlQuery);
        Solution actualSolution = extractor.EvaluateSolution();

        Set<String> actualSolutionStr = solutionToStringSet(actualSolution);
        System.out.println("Actual solution:");
        System.out.println(actualSolutionStr);

        Set<String> expectedSetStr = Arrays.stream(expectedSolution).collect(Collectors.toSet());
        System.out.println("Expected solution:");
        System.out.println(expectedSetStr);

        Assert.assertEquals(expectedSetStr, actualSolutionStr);

        List<Statement> statements = extractor.SolutionToStatements(actualSolution);

        System.out.println("Actual statements:");
        for(int index=0; index < statements.size(); ++index) {
            Statement actualStatement = statements.get(index);
            String actualStr = actualStatement.toString();
            System.out.println("\t" + actualStr);
        }

        System.out.println("Expected statements:");
        for(int index=0; index < expectedStatements.length; ++index) {
            System.out.println("\t" + expectedStatements[index]);
        }

        Assert.assertEquals(statements.size(), expectedStatements.length);
        for(int index=0; index < expectedStatements.length; ++index) {
            Statement actualStatement = statements.get(index);
            String actualStr = actualStatement.toString();
            Assert.assertEquals(actualStr, expectedStatements[index]);
        }
        //Assert.assertTrue(false);
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
                "{process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2=null}",
                "{process1=null, process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.1\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.2\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
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
                "{process3=null, process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2=null}",
                "{process1=null, process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, process3=null}",
                "{process1=null, process2=null, process3={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.1\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.2\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.3\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
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
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22X%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.X\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Y%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Y\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Z%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Z\")"
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
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
                "{process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2=null}",
                "{process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, process2=null}",
                "{process1=null, process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.1\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.21\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.22\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
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
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22X%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.X\")"
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
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
                "{processY={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Y\" -> NODE_TYPE}, processX={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"X\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22X%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.X\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Y%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Y\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
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
                            }
                """;
        String[] expectedSolution = {
                "{processY1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Y1\" -> NODE_TYPE}, "
                +"processY={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Y\" -> NODE_TYPE}, "
                +"processY3={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Y3\" -> NODE_TYPE}, "
                +"processX={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"X\" -> NODE_TYPE}, "
                +"processY2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Y2\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22X%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.X\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Y%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Y\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Y1%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Y1\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Y2%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Y2\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Y3%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Y3\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
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
                "{processY3={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Y3\" -> NODE_TYPE}, processY2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Y2\" -> NODE_TYPE}, processZ3={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Z3\" -> NODE_TYPE}, processY1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Y1\" -> NODE_TYPE}, processZ2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Z2\" -> NODE_TYPE}, processZ1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Z1\" -> NODE_TYPE}, processY={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"Y\" -> NODE_TYPE}, processX={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"X\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22X%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.X\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Y%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Y\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Y1%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Y1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Y2%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Y2\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Y3%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Y3\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Z1%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Z1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Z2%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Z2\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22Z3%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.Z3\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
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
                "{process33={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"33\" -> NODE_TYPE}, process22=null, process11=null, process32={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"32\" -> NODE_TYPE}, process21=null, process31={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"31\" -> NODE_TYPE}, process13=null, process23=null, process12=null}",
                "{process11={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"11\" -> NODE_TYPE}, process22=null, process33=null, process21=null, process32=null, process31=null, process13={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"13\" -> NODE_TYPE}, process12={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"12\" -> NODE_TYPE}, process23=null}",
                "{process22={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}, process33=null, process11=null, process21={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, process32=null, process31=null, process13=null, process23={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"23\" -> NODE_TYPE}, process12=null}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2211%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.11\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2212%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.12\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2213%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.13\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.21\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.22\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.23\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2231%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.31\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2232%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.32\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2233%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.33\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
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
                "{process_ba=null, process_ac={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"ac\" -> NODE_TYPE}, process_aa={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"aa\" -> NODE_TYPE}, process_bb=null, process_ab={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"ab\" -> NODE_TYPE}, process_bc=null}",
                "{process_ac=null, process_bb={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"bb\" -> NODE_TYPE}, process_aa=null, process_bc={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"bc\" -> NODE_TYPE}, process_ab=null, process_ba={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"ba\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22aa%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.aa\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22ab%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.ab\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22ac%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.ac\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22ba%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.ba\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22bb%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.bb\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22bc%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.bc\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    @Test
    public void Parse_Check_11() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process where 
                    {
                        {
                                ?process1 cimv2:DummyClass.DummyProperty "DummyKey_is.1" .
                                ?process2 cimv2:DummyClass.DummyProperty "DummyKey_is.2" .
                        }
                        union
                        {
                                ?process1 cimv2:DummyClass.DummyProperty "DummyKey_is.1" .
                                ?process3 cimv2:DummyClass.DummyProperty "DummyKey_is.3" .
                        }
                    }
                """;
        String[] expectedSolution = {
                "{process3=null, process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}",
                "{process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2=null, process3={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}",
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.2\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.3\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    @Test
    public void Parse_Check_12() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process where 
                    {
                                ?process0 cimv2:DummyClass.DummyProperty "DummyKey_is.0" .
                        {
                                ?process1 cimv2:DummyClass.DummyProperty "DummyKey_is.1" .
                        }
                        union
                        {
                                ?process2 cimv2:DummyClass.DummyProperty "DummyKey_is.2" .
                        }
                        union
                        {
                                ?process3 cimv2:DummyClass.DummyProperty "DummyKey_is.3" .
                        }
                    }
                """;
        String[] expectedSolution = {
                "{process3={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}, process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, process0={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"0\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%220%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.0\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.2\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.3\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    @Test
    public void Parse_Check_13() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process where 
                    {
                        {
                            select ?process
                            where {
                                ?process cimv2:DummyClass.DummyProperty "DummyKey_is.1" .
                            }
                        }
                        union
                        {
                            select ?process
                            where {
                                ?process cimv2:DummyClass.DummyProperty "DummyKey_is.2" .
                            }
                        }
                    }
                    
        """;
        String[] expectedSolution = {
                "{process={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}}",
                "{process={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.2\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    @Test
    public void Parse_Check_14() throws Exception {
        String sparqlQuery = """
                            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                            select ?process where 
                            {
                                {
                                    select ?process
                                    where {
                                        ?process cimv2:DummyClass.DummyProperty "DummyKey_is.1" .
                                    }
                                }
                                union
                                {
                                    select ?process
                                    where {
                                        ?process cimv2:DummyClass.DummyProperty "DummyKey_is.2" .
                                    }
                                }
                                union
                                {
                                    select ?process
                                    where {
                                        ?process cimv2:DummyClass.DummyProperty "DummyKey_is.3" .
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{process={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}}",
                "{process={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}",
                "{process={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.1\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.2\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.3\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    @Test
    public void Parse_Check_15() throws Exception {
        String sparqlQuery = """
                            prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                            prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                            select ?process where 
                            {
                                {
                                    select ?process
                                    where {
                                        ?process cimv2:DummyClass.DummyProperty "DummyKey_is.a" .
                                    }
                                }
                                union
                                {
                                    select ?process where 
                                    {
                                        {
                                            select ?process
                                            where {
                                                ?process cimv2:DummyClass.DummyProperty "DummyKey_is.aa" .
                                            }
                                        }
                                        union
                                        {
                                            select ?process
                                            where {
                                                ?process cimv2:DummyClass.DummyProperty "DummyKey_is.ab" .
                                            }
                                        }
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{process={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"a\" -> NODE_TYPE}}",
                "{process={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"aa\" -> NODE_TYPE}}",
                "{process={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"ab\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22a%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.a\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22aa%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.aa\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%22ab%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.ab\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    @Test
    public void Parse_Check_16() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process1 ?process2 ?process3 where 
                    {
                        {
                            select ?process1 ?process2 ?process3
                            where {
                                ?process1 cimv2:DummyClass.DummyProperty "DummyKey_is.11" .
                                ?process2 cimv2:DummyClass.DummyProperty "DummyKey_is.12" .
                                ?process3 cimv2:DummyClass.DummyProperty "DummyKey_is.13" .
                            }
                        }
                        union
                        {
                            select ?process1 ?process2 ?process3
                            where {
                                ?process1 cimv2:DummyClass.DummyProperty "DummyKey_is.21" .
                                ?process2 cimv2:DummyClass.DummyProperty "DummyKey_is.22" .
                                ?process3 cimv2:DummyClass.DummyProperty "DummyKey_is.23" .
                            }
                        }
                        union
                        {
                            select ?process1 ?process2 ?process3
                            where {
                                ?process1 cimv2:DummyClass.DummyProperty "DummyKey_is.31" .
                                ?process2 cimv2:DummyClass.DummyProperty "DummyKey_is.32" .
                                ?process3 cimv2:DummyClass.DummyProperty "DummyKey_is.33" .
                            }
                        }
                    }
                    
        """;
        String[] expectedSolution = {
            "{process3={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"23\" -> NODE_TYPE}, process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}}",
            "{process3={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"13\" -> NODE_TYPE}, process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"11\" -> NODE_TYPE}, process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"12\" -> NODE_TYPE}}",
            "{process3={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"33\" -> NODE_TYPE}, process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"31\" -> NODE_TYPE}, process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"32\" -> NODE_TYPE}}",
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2211%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.11\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2212%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.12\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2213%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.13\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.21\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.22\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.23\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2231%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.31\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2232%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.32\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2233%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.33\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    @Test
    public void Parse_Check_17() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process where 
                    {
                        {
                                ?process1 cimv2:DummyClass.DummyProperty "DummyKey_is.1" .
                        }
                        union
                        {
                                ?process2 cimv2:DummyClass.DummyProperty "DummyKey_is.2" .
                        }
                    }
                """;
        String[] expectedSolution = {
                "{process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}",
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.2\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    @Test
    public void Parse_Check_18() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process where 
                    {
                                ?process0 cimv2:DummyClass.DummyProperty "DummyKey_is.0" .
                        {
                                ?process1 cimv2:DummyClass.DummyProperty "DummyKey_is.1" .
                        }
                        union
                        {
                                ?process2 cimv2:DummyClass.DummyProperty "DummyKey_is.2" .
                        }
                                ?process3 cimv2:DummyClass.DummyProperty "DummyKey_is.3" .
                    }
                """;
        String[] expectedSolution = {
                "{process3={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}, process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, process0={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"0\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%220%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.0\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.3\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.2\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    @Test
    public void Parse_Check_19() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?process where 
                    {
                        {
                                ?process1 cimv2:DummyClass.DummyProperty "DummyKey_is.1" .
                        }
                        union
                        {
                            {
                                    ?process2 cimv2:DummyClass.DummyProperty "DummyKey_is.2" .
                            }
                            union
                            {
                                    ?process3 cimv2:DummyClass.DummyProperty "DummyKey_is.3" .
                            }
                        }
                    }
                """;
        String[] expectedSolution = {
                "{process1={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, process3={\\\\LAPTOP-R89KG6V1\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.2\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyProperty, \"DummyKey_is.3\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }


    // Ajouter SubQuery et Intersection et LeftJoin etc...

}

