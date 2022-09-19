package paquetage;

import org.eclipse.rdf4j.model.Statement;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

// https://www.programcreek.com/java-api-examples/?api=org.eclipse.rdf4j.query.parser.ParsedQuery

public class SparqlBGPTreeExtractorTest {

    private String backupPrefixComputer;
    @Before
    public void setUp() throws Exception {
        backupPrefixComputer = PresentUtils.prefixComputer;
        PresentUtils.prefixComputer = "\\\\DUMMY_HOST";
    }

    //@Override
    @After
    public void tearDown() throws Exception {
        PresentUtils.prefixComputer = backupPrefixComputer;
    }


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
                                ?process1 cimv2:DummyClass.DummyKey "1" .
                            }
                        }
                        union
                        {
                            select ?process2
                            where {
                                ?process2 cimv2:DummyClass.DummyKey "2" .
                            }
                        }
                    }
                    
        """;
        String[] expectedSolution = {
                "{process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2=null}",
                "{process1=null, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
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
                                        ?process1 cimv2:DummyClass.DummyKey "1" .
                                    }
                                }
                                union
                                {
                                    select ?process2
                                    where {
                                        ?process2 cimv2:DummyClass.DummyKey "2" .
                                    }
                                }
                                union
                                {
                                    select ?process3
                                    where {
                                        ?process3 cimv2:DummyClass.DummyKey "3" .
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{process3=null, process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2=null}",
                "{process1=null, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, process3=null}",
                "{process1=null, process2=null, process3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"3\")",
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
                                ?process10 cimv2:DummyClass.DummyKey "10" .
                                ?process20 cimv2:DummyClass.DummyKey "20" .
                                ?process30 cimv2:DummyClass.DummyKey "30" .
                            }
                """;
        String[] expectedSolution = {
                "{process10={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"10\" -> NODE_TYPE}, process20={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"20\" -> NODE_TYPE}, process30={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"30\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2210%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"10\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2220%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"20\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2230%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"30\")"
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
                                        ?process1 cimv2:DummyClass.DummyKey "1" .
                                    }
                                }
                                union
                                {
                                    select ?process2 where 
                                    {
                                        {
                                            select ?process21
                                            where {
                                                ?process1 cimv2:DummyClass.DummyKey "21" .
                                            }
                                        }
                                        union
                                        {
                                            select ?process22
                                            where {
                                                ?process2 cimv2:DummyClass.DummyKey "22" .
                                            }
                                        }
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2=null}",
                "{process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, process2=null}",
                "{process1=null, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"21\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"22\")",
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
                                ?process10 cimv2:DummyClass.DummyKey "10" .
                            }
                """;
        String[] expectedSolution = {
                "{process10={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"10\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2210%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"10\")"
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
                                ?process1 cimv2:DummyClass.DummyKey "1" .
                                {
                                    select *
                                    where {
                                        ?process2 cimv2:DummyClass.DummyKey "2" .
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
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
                                ?process1 cimv2:DummyClass.DummyKey "1" .
                                {
                                    select *
                                    where {
                                        ?process21 cimv2:DummyClass.DummyKey "21" .
                                        ?process22 cimv2:DummyClass.DummyKey "22" .
                                        ?process23 cimv2:DummyClass.DummyKey "23" .
                                    }
                                }
                                ?process2 cimv2:DummyClass.DummyKey "2" .
                            }
                """;
        String[] expectedSolution = {
                "{process22={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}, process21={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, process23={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"23\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"21\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"22\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"23\")",
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
                                ?processX cimv2:DummyClass.DummyKey "1" .
                                {
                                    select *
                                    where {
                                        ?process21 cimv2:DummyClass.DummyKey "21" .
                                        ?process22 cimv2:DummyClass.DummyKey "22" .
                                        ?process23 cimv2:DummyClass.DummyKey "23" .
                                    }
                                }
                                ?process2 cimv2:DummyClass.DummyKey "2" .
                                {
                                    select *
                                    where {
                                        ?process31 cimv2:DummyClass.DummyKey "31" .
                                        ?process32 cimv2:DummyClass.DummyKey "32" .
                                        ?process33 cimv2:DummyClass.DummyKey "33" .
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{process22={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}, process33={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"33\" -> NODE_TYPE}, process21={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, process32={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"32\" -> NODE_TYPE}, process31={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"31\" -> NODE_TYPE}, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, processX={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process23={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"23\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"21\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"22\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"23\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2231%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"31\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2232%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"32\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2233%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"33\")",
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
                                ?process11 cimv2:DummyClass.DummyKey "11" .
                                ?process12 cimv2:DummyClass.DummyKey "12" .
                                ?process13 cimv2:DummyClass.DummyKey "13" .
                            }
                        }
                        union
                        {
                            select ?process2
                            where {
                                ?process21 cimv2:DummyClass.DummyKey "21" .
                                ?process22 cimv2:DummyClass.DummyKey "22" .
                                ?process23 cimv2:DummyClass.DummyKey "23" .
                            }
                        }
                        union
                        {
                            select ?process3
                            where {
                                ?process31 cimv2:DummyClass.DummyKey "31" .
                                ?process32 cimv2:DummyClass.DummyKey "32" .
                                ?process33 cimv2:DummyClass.DummyKey "33" .
                            }
                        }
                    }
                    
        """;
        String[] expectedSolution = {
                "{process33={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"33\" -> NODE_TYPE}, process22=null, process11=null, process32={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"32\" -> NODE_TYPE}, process21=null, process31={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"31\" -> NODE_TYPE}, process13=null, process23=null, process12=null}",
                "{process11={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"11\" -> NODE_TYPE}, process22=null, process33=null, process21=null, process32=null, process31=null, process13={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"13\" -> NODE_TYPE}, process12={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"12\" -> NODE_TYPE}, process23=null}",
                "{process22={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}, process33=null, process11=null, process21={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, process32=null, process31=null, process13=null, process23={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"23\" -> NODE_TYPE}, process12=null}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2211%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"11\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2212%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"12\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2213%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"13\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"21\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"22\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"23\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2231%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"31\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2232%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"32\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2233%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"33\")",
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
                                            select ?process1
                                            where {
                                                ?process11 cimv2:DummyClass.DummyKey "11" .
                                                ?process12 cimv2:DummyClass.DummyKey "12" .
                                                ?process13 cimv2:DummyClass.DummyKey "13" .
                                            }
                                        }
                                        union
                                        {
                                            select ?process2
                                            where {
                                                ?process21 cimv2:DummyClass.DummyKey "21" .
                                                ?process22 cimv2:DummyClass.DummyKey "22" .
                                                ?process23 cimv2:DummyClass.DummyKey "23" .
                                            }
                                        }
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{process22={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}, process11=null, process21={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, process13=null, process23={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"23\" -> NODE_TYPE}, process12=null}",
                "{process11={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"11\" -> NODE_TYPE}, process22=null, process21=null, process13={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"13\" -> NODE_TYPE}, process12={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"12\" -> NODE_TYPE}, process23=null}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2211%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"11\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2212%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"12\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2213%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"13\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"21\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"22\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"23\")",
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
                                ?process1 cimv2:DummyClass.DummyKey "1" .
                                ?process2 cimv2:DummyClass.DummyKey "2" .
                        }
                        union
                        {
                                ?process1 cimv2:DummyClass.DummyKey "1" .
                                ?process3 cimv2:DummyClass.DummyKey "3" .
                        }
                    }
                """;
        String[] expectedSolution = {
                "{process3=null, process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}",
                "{process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2=null, process3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}",
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"3\")",
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
                                ?process0 cimv2:DummyClass.DummyKey "0" .
                        {
                                ?process1 cimv2:DummyClass.DummyKey "1" .
                        }
                        union
                        {
                                ?process2 cimv2:DummyClass.DummyKey "2" .
                        }
                        union
                        {
                                ?process3 cimv2:DummyClass.DummyKey "3" .
                        }
                    }
                """;
        String[] expectedSolution = {
                "{process3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}, process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, process0={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"0\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%220%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"0\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"3\")",
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
                                ?process cimv2:DummyClass.DummyKey "1" .
                            }
                        }
                        union
                        {
                            select ?process
                            where {
                                ?process cimv2:DummyClass.DummyKey "2" .
                            }
                        }
                    }
                    
        """;
        String[] expectedSolution = {
                "{process={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}}",
                "{process={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
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
                                        ?process cimv2:DummyClass.DummyKey "1" .
                                    }
                                }
                                union
                                {
                                    select ?process
                                    where {
                                        ?process cimv2:DummyClass.DummyKey "2" .
                                    }
                                }
                                union
                                {
                                    select ?process
                                    where {
                                        ?process cimv2:DummyClass.DummyKey "3" .
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{process={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}}",
                "{process={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}",
                "{process={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"3\")",
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
                                        ?process cimv2:DummyClass.DummyKey "1" .
                                    }
                                }
                                union
                                {
                                    select ?process where 
                                    {
                                        {
                                            select ?process
                                            where {
                                                ?process cimv2:DummyClass.DummyKey "11" .
                                            }
                                        }
                                        union
                                        {
                                            select ?process
                                            where {
                                                ?process cimv2:DummyClass.DummyKey "12" .
                                            }
                                        }
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{process={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"12\" -> NODE_TYPE}}",
                "{process={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}}",
                "{process={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"11\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2211%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"11\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2212%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"12\")",
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
                                ?process1 cimv2:DummyClass.DummyKey "11" .
                                ?process2 cimv2:DummyClass.DummyKey "12" .
                                ?process3 cimv2:DummyClass.DummyKey "13" .
                            }
                        }
                        union
                        {
                            select ?process1 ?process2 ?process3
                            where {
                                ?process1 cimv2:DummyClass.DummyKey "21" .
                                ?process2 cimv2:DummyClass.DummyKey "22" .
                                ?process3 cimv2:DummyClass.DummyKey "23" .
                            }
                        }
                        union
                        {
                            select ?process1 ?process2 ?process3
                            where {
                                ?process1 cimv2:DummyClass.DummyKey "31" .
                                ?process2 cimv2:DummyClass.DummyKey "32" .
                                ?process3 cimv2:DummyClass.DummyKey "33" .
                            }
                        }
                    }
                    
        """;
        String[] expectedSolution = {
            "{process3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"23\" -> NODE_TYPE}, process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}}",
            "{process3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"13\" -> NODE_TYPE}, process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"11\" -> NODE_TYPE}, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"12\" -> NODE_TYPE}}",
            "{process3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"33\" -> NODE_TYPE}, process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"31\" -> NODE_TYPE}, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"32\" -> NODE_TYPE}}",
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2211%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"11\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2212%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"12\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2213%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"13\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"21\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"22\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"23\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2231%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"31\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2232%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"32\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2233%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"33\")",
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
                                ?process1 cimv2:DummyClass.DummyKey "1" .
                        }
                        union
                        {
                                ?process2 cimv2:DummyClass.DummyKey "2" .
                        }
                    }
                """;
        String[] expectedSolution = {
                "{process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}",
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
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
                                ?process0 cimv2:DummyClass.DummyKey "0" .
                        {
                                ?process1 cimv2:DummyClass.DummyKey "1" .
                        }
                        union
                        {
                                ?process2 cimv2:DummyClass.DummyKey "2" .
                        }
                                ?process3 cimv2:DummyClass.DummyKey "3" .
                    }
                """;
        String[] expectedSolution = {
                "{process3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}, process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, process0={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"0\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%220%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"0\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"3\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
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
                                ?process1 cimv2:DummyClass.DummyKey "1" .
                        }
                        union
                        {
                            {
                                    ?process2 cimv2:DummyClass.DummyKey "2" .
                            }
                            union
                            {
                                    ?process3 cimv2:DummyClass.DummyKey "3" .
                            }
                        }
                    }
                """;
        String[] expectedSolution = {
                "{process1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, process2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, process3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
            "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"3\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }


    // Ajouter SubQuery et Intersection et LeftJoin etc...

}

