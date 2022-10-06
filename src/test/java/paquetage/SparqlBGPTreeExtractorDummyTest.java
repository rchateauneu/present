package paquetage;

import org.eclipse.rdf4j.model.Statement;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

// https://www.programcreek.com/java-api-examples/?api=org.eclipse.rdf4j.query.parser.ParsedQuery

/** These tests use a dummy class.
 *
 */
public class SparqlBGPTreeExtractorDummyTest {

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

    static void HelperCheck(String sparqlQuery, String[] expectedSolution, String[] expectedStatements) throws Exception{
        SparqlBGPTreeExtractor extractor = new SparqlBGPTreeExtractor(sparqlQuery);
        Solution actualSolution = extractor.EvaluateSolution();

        List<String> actualSolutionStr = actualSolution.stream().map(row -> row.toValueString()).collect(Collectors.toList());
        Collections.sort(actualSolutionStr);
        System.out.println("Actual solution:" + actualSolutionStr.size());
        for(int index=0; index < actualSolutionStr.size(); ++index) {
            System.out.println("\t" + actualSolutionStr.get(index));
        }

        Arrays.sort(expectedSolution);
        System.out.println("Expected solution:" + expectedSolution.length);
        // System.out.println(expectedSolution);
        for(int index=0; index < expectedSolution.length; ++index) {
            System.out.println("\t" + expectedSolution[index]);
        }

        Assert.assertEquals(expectedSolution.length, actualSolutionStr.size());
        for(int index=0; index < expectedSolution.length; ++index) {
            Assert.assertEquals(expectedSolution[index], actualSolutionStr.get(index));
        }

        List<Statement> statements = extractor.SolutionToStatements(/*actualSolution*/);
        List<String> actualStatementsStr = statements.stream().map(st -> st.toString()).collect(Collectors.toList());
        Collections.sort(actualStatementsStr);

        Arrays.sort(expectedStatements);

        System.out.println("Actual statements:" + statements.size());
        for(int index=0; index < statements.size(); ++index) {
            Statement actualStatement = statements.get(index);
            String actualStr = actualStatement.toString();
            System.out.println("\t" + actualStr);
        }

        System.out.println("Expected statements:" + expectedStatements.length);
        for(int index=0; index < expectedStatements.length; ++index) {
            System.out.println("\t" + expectedStatements[index]);
        }

        Assert.assertEquals(expectedStatements.length, statements.size());
        for(int index=0; index < expectedStatements.length; ++index) {
            String actualStr = actualStatementsStr.get(index);
            Assert.assertEquals(expectedStatements[index], actualStr);
        }
    }

    @Test
    public void Parse_Check_01() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?dummy where 
                    {
                        {
                            select ?dummy1
                            where {
                                ?dummy1 cimv2:DummyClass.DummyKey "1" .
                            }
                        }
                        union
                        {
                            select ?dummy2
                            where {
                                ?dummy2 cimv2:DummyClass.DummyKey "2" .
                            }
                        }
                    }
                    
        """;
        String[] expectedSolution = {
                "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy2=null}",
                "{dummy1=null, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}"
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
                            select ?dummy where 
                            {
                                {
                                    select ?dummy1
                                    where {
                                        ?dummy1 cimv2:DummyClass.DummyKey "1" .
                                    }
                                }
                                union
                                {
                                    select ?dummy2
                                    where {
                                        ?dummy2 cimv2:DummyClass.DummyKey "2" .
                                    }
                                }
                                union
                                {
                                    select ?dummy3
                                    where {
                                        ?dummy3 cimv2:DummyClass.DummyKey "3" .
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{dummy1=null, dummy2=null, dummy3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}",
                "{dummy1=null, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, dummy3=null}",
                "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy2=null, dummy3=null}"
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
                                ?dummy10 cimv2:DummyClass.DummyKey "10" .
                                ?dummy20 cimv2:DummyClass.DummyKey "20" .
                                ?dummy30 cimv2:DummyClass.DummyKey "30" .
                            }
                """;
        String[] expectedSolution = {
                "{dummy30={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"30\" -> NODE_TYPE}, dummy10={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"10\" -> NODE_TYPE}, dummy20={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"20\" -> NODE_TYPE}}"
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
                            select ?dummy where 
                            {
                                {
                                    select ?dummy1
                                    where {
                                        ?dummy1 cimv2:DummyClass.DummyKey "1" .
                                    }
                                }
                                union
                                {
                                    select ?dummy2 where 
                                    {
                                        {
                                            select ?dummy21
                                            where {
                                                ?dummy1 cimv2:DummyClass.DummyKey "21" .
                                            }
                                        }
                                        union
                                        {
                                            select ?dummy22
                                            where {
                                                ?dummy2 cimv2:DummyClass.DummyKey "22" .
                                            }
                                        }
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy2=null}",
                "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, dummy2=null}",
                "{dummy1=null, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}}"
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
                                ?dummy10 cimv2:DummyClass.DummyKey "10" .
                            }
                """;
        String[] expectedSolution = {
                "{dummy10={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"10\" -> NODE_TYPE}}"
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
                                ?dummy1 cimv2:DummyClass.DummyKey "1" .
                                {
                                    select *
                                    where {
                                        ?dummy2 cimv2:DummyClass.DummyKey "2" .
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}"
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
                                ?dummy1 cimv2:DummyClass.DummyKey "1" .
                                {
                                    select *
                                    where {
                                        ?dummy21 cimv2:DummyClass.DummyKey "21" .
                                        ?dummy22 cimv2:DummyClass.DummyKey "22" .
                                        ?dummy23 cimv2:DummyClass.DummyKey "23" .
                                    }
                                }
                                ?dummy2 cimv2:DummyClass.DummyKey "2" .
                            }
                """;
        String[] expectedSolution = {
                "{dummy23={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"23\" -> NODE_TYPE}, dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy22={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, dummy21={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}}"
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
                                ?dummyX cimv2:DummyClass.DummyKey "1" .
                                {
                                    select *
                                    where {
                                        ?dummy21 cimv2:DummyClass.DummyKey "21" .
                                        ?dummy22 cimv2:DummyClass.DummyKey "22" .
                                        ?dummy23 cimv2:DummyClass.DummyKey "23" .
                                    }
                                }
                                ?dummy2 cimv2:DummyClass.DummyKey "2" .
                                {
                                    select *
                                    where {
                                        ?dummy31 cimv2:DummyClass.DummyKey "31" .
                                        ?dummy32 cimv2:DummyClass.DummyKey "32" .
                                        ?dummy33 cimv2:DummyClass.DummyKey "33" .
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{dummy23={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"23\" -> NODE_TYPE}, dummy22={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}, dummy33={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"33\" -> NODE_TYPE}, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, dummyX={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy21={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, dummy32={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"32\" -> NODE_TYPE}, dummy31={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"31\" -> NODE_TYPE}}"
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
                    select ?dummy where 
                    {
                        {
                            select ?dummy1
                            where {
                                ?dummy11 cimv2:DummyClass.DummyKey "11" .
                                ?dummy12 cimv2:DummyClass.DummyKey "12" .
                                ?dummy13 cimv2:DummyClass.DummyKey "13" .
                            }
                        }
                        union
                        {
                            select ?dummy2
                            where {
                                ?dummy21 cimv2:DummyClass.DummyKey "21" .
                                ?dummy22 cimv2:DummyClass.DummyKey "22" .
                                ?dummy23 cimv2:DummyClass.DummyKey "23" .
                            }
                        }
                        union
                        {
                            select ?dummy3
                            where {
                                ?dummy31 cimv2:DummyClass.DummyKey "31" .
                                ?dummy32 cimv2:DummyClass.DummyKey "32" .
                                ?dummy33 cimv2:DummyClass.DummyKey "33" .
                            }
                        }
                    }
                    
        """;
        String[] expectedSolution = {
                "{dummy12={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"12\" -> NODE_TYPE}, dummy23=null, dummy11={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"11\" -> NODE_TYPE}, dummy22=null, dummy33=null, dummy13={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"13\" -> NODE_TYPE}, dummy21=null, dummy32=null, dummy31=null}",
                "{dummy23=null, dummy12=null, dummy33={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"33\" -> NODE_TYPE}, dummy22=null, dummy11=null, dummy13=null, dummy32={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"32\" -> NODE_TYPE}, dummy21=null, dummy31={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"31\" -> NODE_TYPE}}",
                "{dummy23={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"23\" -> NODE_TYPE}, dummy12=null, dummy22={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}, dummy33=null, dummy11=null, dummy13=null, dummy21={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, dummy32=null, dummy31=null}"
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
                                            select ?dummy1
                                            where {
                                                ?dummy11 cimv2:DummyClass.DummyKey "11" .
                                                ?dummy12 cimv2:DummyClass.DummyKey "12" .
                                                ?dummy13 cimv2:DummyClass.DummyKey "13" .
                                            }
                                        }
                                        union
                                        {
                                            select ?dummy2
                                            where {
                                                ?dummy21 cimv2:DummyClass.DummyKey "21" .
                                                ?dummy22 cimv2:DummyClass.DummyKey "22" .
                                                ?dummy23 cimv2:DummyClass.DummyKey "23" .
                                            }
                                        }
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{dummy12={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"12\" -> NODE_TYPE}, dummy23=null, dummy11={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"11\" -> NODE_TYPE}, dummy22=null, dummy13={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"13\" -> NODE_TYPE}, dummy21=null}",
                "{dummy23={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"23\" -> NODE_TYPE}, dummy12=null, dummy22={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}, dummy11=null, dummy13=null, dummy21={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}}"
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
                    select ?dummy where 
                    {
                        {
                                ?dummy1 cimv2:DummyClass.DummyKey "1" .
                                ?dummy2 cimv2:DummyClass.DummyKey "2" .
                        }
                        union
                        {
                                ?dummy1 cimv2:DummyClass.DummyKey "1" .
                                ?dummy3 cimv2:DummyClass.DummyKey "3" .
                        }
                    }
                """;
        String[] expectedSolution = {
                "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy2=null, dummy3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}",
                "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, dummy3=null}",
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
                    select ?dummy where 
                    {
                                ?dummy0 cimv2:DummyClass.DummyKey "0" .
                        {
                                ?dummy1 cimv2:DummyClass.DummyKey "1" .
                        }
                        union
                        {
                                ?dummy2 cimv2:DummyClass.DummyKey "2" .
                        }
                        union
                        {
                                ?dummy3 cimv2:DummyClass.DummyKey "3" .
                        }
                    }
                """;
        String[] expectedSolution = {
                "{dummy0={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"0\" -> NODE_TYPE}, dummy1=null, dummy2=null, dummy3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}",
                "{dummy0={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"0\" -> NODE_TYPE}, dummy1=null, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, dummy3=null}",
                "{dummy0={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"0\" -> NODE_TYPE}, dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy2=null, dummy3=null}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%220%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"0\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%220%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"0\")",
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
                    select ?dummy where 
                    {
                        {
                            select ?dummy
                            where {
                                ?dummy cimv2:DummyClass.DummyKey "1" .
                            }
                        }
                        union
                        {
                            select ?dummy
                            where {
                                ?dummy cimv2:DummyClass.DummyKey "2" .
                            }
                        }
                    }
                    
        """;
        String[] expectedSolution = {
                "{dummy={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}}",
                "{dummy={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}"
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
                            select ?dummy where 
                            {
                                {
                                    select ?dummy
                                    where {
                                        ?dummy cimv2:DummyClass.DummyKey "1" .
                                    }
                                }
                                union
                                {
                                    select ?dummy
                                    where {
                                        ?dummy cimv2:DummyClass.DummyKey "2" .
                                    }
                                }
                                union
                                {
                                    select ?dummy
                                    where {
                                        ?dummy cimv2:DummyClass.DummyKey "3" .
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{dummy={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}}",
                "{dummy={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}",
                "{dummy={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}"
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
                            select ?dummy where 
                            {
                                {
                                    select ?dummy
                                    where {
                                        ?dummy cimv2:DummyClass.DummyKey "1" .
                                    }
                                }
                                union
                                {
                                    select ?dummy where 
                                    {
                                        {
                                            select ?dummy
                                            where {
                                                ?dummy cimv2:DummyClass.DummyKey "11" .
                                            }
                                        }
                                        union
                                        {
                                            select ?dummy
                                            where {
                                                ?dummy cimv2:DummyClass.DummyKey "12" .
                                            }
                                        }
                                    }
                                }
                            }
                """;
        String[] expectedSolution = {
                "{dummy={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}}",
                "{dummy={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"11\" -> NODE_TYPE}}",
                "{dummy={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"12\" -> NODE_TYPE}}"
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
                    select ?dummy1 ?dummy2 ?dummy3 where 
                    {
                        {
                            select ?dummy1 ?dummy2 ?dummy3
                            where {
                                ?dummy1 cimv2:DummyClass.DummyKey "11" .
                                ?dummy2 cimv2:DummyClass.DummyKey "12" .
                                ?dummy3 cimv2:DummyClass.DummyKey "13" .
                            }
                        }
                        union
                        {
                            select ?dummy1 ?dummy2 ?dummy3
                            where {
                                ?dummy1 cimv2:DummyClass.DummyKey "21" .
                                ?dummy2 cimv2:DummyClass.DummyKey "22" .
                                ?dummy3 cimv2:DummyClass.DummyKey "23" .
                            }
                        }
                        union
                        {
                            select ?dummy1 ?dummy2 ?dummy3
                            where {
                                ?dummy1 cimv2:DummyClass.DummyKey "31" .
                                ?dummy2 cimv2:DummyClass.DummyKey "32" .
                                ?dummy3 cimv2:DummyClass.DummyKey "33" .
                            }
                        }
                    }
                    
        """;
        String[] expectedSolution = {
            "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"11\" -> NODE_TYPE}, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"12\" -> NODE_TYPE}, dummy3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"13\" -> NODE_TYPE}}",
            "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"21\" -> NODE_TYPE}, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}, dummy3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"23\" -> NODE_TYPE}}",
            "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"31\" -> NODE_TYPE}, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"32\" -> NODE_TYPE}, dummy3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"33\" -> NODE_TYPE}}",
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
                    select ?dummy where 
                    {
                        {
                                ?dummy1 cimv2:DummyClass.DummyKey "1" .
                        }
                        union
                        {
                                ?dummy2 cimv2:DummyClass.DummyKey "2" .
                        }
                    }
                """;
        String[] expectedSolution = {
                "{dummy1=null, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}",
                "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy2=null}",
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
                    select ?dummy where 
                    {
                                ?dummy0 cimv2:DummyClass.DummyKey "0" .
                        {
                                ?dummy1 cimv2:DummyClass.DummyKey "1" .
                        }
                        union
                        {
                                ?dummy2 cimv2:DummyClass.DummyKey "2" .
                        }
                                ?dummy3 cimv2:DummyClass.DummyKey "3" .
                    }
                """;
        String[] expectedSolution = {
                "{dummy0={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"0\" -> NODE_TYPE}, dummy1=null, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, dummy3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}",
                "{dummy0={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"0\" -> NODE_TYPE}, dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy2=null, dummy3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%220%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"0\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%220%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"0\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"3\")",
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
                    select ?dummy where 
                    {
                        {
                                ?dummy1 cimv2:DummyClass.DummyKey "1" .
                        }
                        union
                        {
                            {
                                    ?dummy2 cimv2:DummyClass.DummyKey "2" .
                            }
                            union
                            {
                                    ?dummy3 cimv2:DummyClass.DummyKey "3" .
                            }
                        }
                    }
                """;
        String[] expectedSolution = {
                "{dummy1=null, dummy2=null, dummy3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}",
                "{dummy1=null, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, dummy3=null}",
                "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy2=null, dummy3=null}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%223%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"3\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    @Test
    public void Parse_Check_20() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?dummy where 
                    {
                        {
                            ?dummy cimv2:DummyClass.DummyKey "11" .
                        }
                        union
                        {
                            ?dummy cimv2:DummyClass.DummyKey "22" .
                        }
                    }
                """;
        String[] expectedSolution = {
                "{dummy={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"11\" -> NODE_TYPE}}",
                "{dummy={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"22\" -> NODE_TYPE}}"
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2211%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"11\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%2222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"22\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    /** This generates all elements of the class DummyKey */
    @Test
    public void Parse_Check_21() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?dummy_key where 
                    {
                        ?dummy cimv2:DummyClass.DummyKey ?dummy_key .
                    }
                """;
        String[] expectedSolution = new String[DummyClass.MaxElements];
        String[] expectedStatements = new String[DummyClass.MaxElements];
        for(int index=0; index < DummyClass.MaxElements; ++index) {
            expectedSolution[index] = String.format("{dummy={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"%d\" -> NODE_TYPE}, dummy_key={%d -> STRING_TYPE}}", index, index);
            expectedStatements[index] = String.format("(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%%5C%%5CDUMMY_HOST%%5CROOT%%5CCIMV2%%3ADummyClass.DummyKey%%3D%%22%d%%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"%d\")", index, index);
        }
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    @Test
    public void Parse_Check_22() throws Exception {
        String sparqlQuery = String.format("""
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?dummy where 
                    {
                        {
                            ?dummy cimv2:DummyClass.DummyKey ?dummy_key .
                        }
                        union
                        {
                            ?dummy cimv2:DummyClass.DummyKey "%d" .
                        }
                    }
                """, DummyClass.MaxElements - 1);
        String[] expectedSolution = new String[DummyClass.MaxElements + 1];
        String[] expectedStatements = new String[DummyClass.MaxElements + 1];
        for(int index=0; index < DummyClass.MaxElements; ++index) {
            expectedSolution[index] = String.format("{dummy={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"%d\" -> NODE_TYPE}, dummy_key={%d -> STRING_TYPE}}", index, index);
            expectedStatements[index] = String.format("(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%%5C%%5CDUMMY_HOST%%5CROOT%%5CCIMV2%%3ADummyClass.DummyKey%%3D%%22%d%%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"%d\")", index, index);
        }
        // One extra solution
        expectedSolution[DummyClass.MaxElements] = String.format("{dummy={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"%d\" -> NODE_TYPE}, dummy_key=null}", DummyClass.MaxElements-1);
        expectedStatements[DummyClass.MaxElements] = String.format("(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%%5C%%5CDUMMY_HOST%%5CROOT%%5CCIMV2%%3ADummyClass.DummyKey%%3D%%22%d%%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"%d\")", DummyClass.MaxElements-1, DummyClass.MaxElements-1);
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    /** Cartesian product */
    @Test
    public void Parse_Check_23() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select ?dummy1_key ?dummy2_key  where 
                    {
                        ?dummy1 cimv2:DummyClass.DummyKey ?dummy1_key .
                        ?dummy2 cimv2:DummyClass.DummyKey ?dummy2_key .
                    }
                """;
        String[] expectedSolution = new String[DummyClass.MaxElements * DummyClass.MaxElements];
        String[] expectedStatements = new String[2 * DummyClass.MaxElements * DummyClass.MaxElements];
        for(int index1=0; index1 < DummyClass.MaxElements; ++index1) {
            for(int index2=0; index2 < DummyClass.MaxElements; ++index2) {
                expectedSolution[DummyClass.MaxElements * index1 + index2] = String.format("{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"%d\" -> NODE_TYPE}, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"%d\" -> NODE_TYPE}, dummy2_key={%d -> STRING_TYPE}, dummy1_key={%d -> STRING_TYPE}}", index1, index2, index2, index1);
                expectedStatements[DummyClass.MaxElements * index1 + index2] = String.format("(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%%5C%%5CDUMMY_HOST%%5CROOT%%5CCIMV2%%3ADummyClass.DummyKey%%3D%%22%d%%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"%d\")", index1, index1);
            }
        }
        // Because there are two patterns, and at this stage, duplicates are not removed.
        for(int indexPattern = 0; indexPattern < DummyClass.MaxElements * DummyClass.MaxElements; indexPattern++) {
            expectedStatements[DummyClass.MaxElements * DummyClass.MaxElements + indexPattern] = expectedStatements[indexPattern];
        }
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    @Test
    public void Parse_Check_24() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select * where {
                            ?dummy1 cimv2:DummyClass.DummyKey "1" .
                            OPTIONAL { ?dummy2 cimv2:DummyClass.DummyKey "2" . } .
                    }
                """;
        String[] expectedSolution = {
                "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}}",
        };
        String[] expectedStatements = {
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%221%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"1\")",
                "(http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CDUMMY_HOST%5CROOT%5CCIMV2%3ADummyClass.DummyKey%3D%222%22, http://www.primhillcomputers.com/ontology/ROOT/CIMV2#DummyClass.DummyKey, \"2\")",
        };
        HelperCheck(sparqlQuery, expectedSolution, expectedStatements);
    }

    @Test
    public void Parse_Check_25() throws Exception {
        String sparqlQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    select * where {
                            ?dummy1 cimv2:DummyClass.DummyKey "1" .
                            ?dummy2 cimv2:DummyClass.DummyKey "2" .
                            OPTIONAL { ?dummy3 cimv2:DummyClass.DummyKey "3" . } .
                    }
                """;
        String[] expectedSolution = {
                "{dummy1={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"1\" -> NODE_TYPE}, dummy2={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"2\" -> NODE_TYPE}, dummy3={\\\\DUMMY_HOST\\ROOT\\CIMV2:DummyClass.DummyKey=\"3\" -> NODE_TYPE}}",
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

