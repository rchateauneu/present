package paquetage;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Queries sent by Wikidata Sparql GUI: wikibase/queryService/ui/resultBrowser/GraphResultBrowserNodeBrowser.js
public class WikidataGuiTest {
    private RepositoryWrapper repositoryWrapper = null;
    private static long currentPid = ProcessHandle.current().pid();
    private static String currentPidStr = String.valueOf(currentPid);
    // The current process is always there, so it is possible to select its properties.
    // "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.laksjfhdlaksjhdflakjshdflha";
    // http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Directory.Name%3D%22C%3A%5C%5CWindows%22
    private String currentProcessUri = null;

    static private String predicateCreationDate = "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.CreationDate";

    private String uriWmi32ProcessHandle = null;

    private String uri_CIM_ProcessExecutable_Dependent = null;

    @Before
    public void setUp() throws Exception {
        repositoryWrapper = new RepositoryWrapper("ROOT\\CIMV2");
        currentProcessUri = WmiOntology.createUriVarArgs("Win32_Process", "Handle", currentPidStr);
        // "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.Handle"
        uriWmi32ProcessHandle = WmiOntology.namespaces_url_prefix + "ROOT/CIMV2#" + "Win32_Process.Handle";

        uri_CIM_ProcessExecutable_Dependent = WmiOntology.namespaces_url_prefix + "ROOT/CIMV2#" + "CIM_ProcessExecutable.Dependent";
/*
uri_CIM_ProcessExecutable_Dependent
?_2_assoc cimv2:CIM_ProcessExecutable.Dependent ?_1_process .

 */

    }

    //@Override
    @After
    public void tearDown() throws Exception {
        repositoryWrapper = null;
    }

    /*
    https://www.w3.org/TR/rdf-sparql-query/#func-lang
    Returns the language tag of ltrl, if it has one.
    It returns "" if ltrl has no language tag.
    Note that the RDF data model does not include literals with an empty language tag.
    Survol does not set languages yet.

    In Wikidata Gui, it does not seem possible to set the language to "",
    so we have to add the language to the label ...
    ... language tag of an RDF literal (e.g. the EN in ?p foaf:name "Robert"@EN
    It is only for the labels, not for the names, i.e. the property:
    rdfs:label = http://www.w3.org/2000/01/rdf-schema#label

    All text from WMI is in English.
    */
    private static String setLanguage(String patternQuery) {
        return patternQuery.replace("[AUTO_LANGUAGE]", "en");
    }

    /* This ensure that if the subject is constant, the WMI class is correctly extracted.
    */
    @Test
    public void testSPARQL_ConstantProcessNoProperty() throws Exception {
        String patternQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    SELECT ?process_property ?property_value
                    WHERE {
                        <{processUri}> ?process_property ?property_value .
                        }
                """;
        String sparqlQuery = setLanguage(patternQuery).replace("{processUri}", currentProcessUri);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);

        // Because the process is present, it must have at least one property.
        Assert.assertTrue(listRows.size() > 0);
        System.out.println("listRows=" + listRows);

        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("process_property", "property_value"), singleRow.keySet());

        // Most of not all properties should have one value only.
        Map<String, String> setPropValues = listRows
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getAsUri("process_property"),
                        row -> row.getAsLiteral("property_value")));

        System.out.println("setPropValues=" + setPropValues);
        System.out.println("iri handle="+setPropValues.get(WmiProvider.toCIMV2("Handle")));

        System.out.println("iri Handle="+WmiProvider.toCIMV2("Handle"));
        Assert.assertEquals("\""+currentPidStr+"\"", setPropValues.get(WmiProvider.toCIMV2("Handle")));
        Assert.assertEquals("\"java.exe\"", setPropValues.get(WmiProvider.toCIMV2("Name")));
        Assert.assertEquals("\"Win32_ComputerSystem\"", setPropValues.get(WmiProvider.toCIMV2("CSCreationClassName")));
        Assert.assertEquals("\"Win32_Process\"", setPropValues.get(WmiProvider.toCIMV2("CreationClassName")));
    }

    /* This ensures that if the subject is a constant, one of its properties is correct.
    */
    @Test
    public void testSPARQL_ConstantProcessCheckHandle() throws Exception {
        String patternQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
                    SELECT ?handle
                    WHERE {
                        <{processUri}> cimv2:Win32_Process.Handle ?handle .
                        }
                """;
        String sparqlQuery = setLanguage(patternQuery).replace("{processUri}", currentProcessUri);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);

        for(RdfSolution.Tuple singleRow : listRows) {
            System.out.println("    singleRow=" + singleRow);
        }
        Assert.assertEquals(1, listRows.size());

        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("handle"), singleRow.keySet());

        String processHandle = singleRow.getAsLiteral("handle");
        Assert.assertEquals("\"" + currentPidStr + "\"", processHandle);
    }

    /*
    This checks that the process uri is correct.
     */
    @Test
    public void testSPARQL_CurrentProcessCheckProcessUri() throws Exception {
        String patternQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    SELECT ?proc_uri
                    WHERE {
                        ?proc_uri cimv2:Win32_Process.Handle {processId} .
                        }
                """;
        String sparqlQuery = setLanguage(patternQuery).replace("{processId}", currentPidStr);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        System.out.println("listRows=" + listRows);

        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("proc_uri"), singleRow.keySet());

        String processUri = singleRow.getAsUri("proc_uri");
        Assert.assertEquals(currentProcessUri, processUri);
    }

    /*
    This checks that the property uri is correct.
     */
    @Test
    public void testSPARQL_CurrentProcessCheckPropertyUri() throws Exception {
        String patternQuery = """
                    prefix cimv2:  <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#>
                    SELECT ?proc_uri
                    WHERE {
                        ?proc_uri <{wmi32process_handle_property_uri}> {processId} .
                        }
                """;
        String sparqlQuery = setLanguage(patternQuery)
                .replace("{processId}", currentPidStr)
                .replace("{wmi32process_handle_property_uri}", uriWmi32ProcessHandle);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);

        Assert.assertEquals(1, listRows.size());
        System.out.println("listRows=" + listRows);

        RdfSolution.Tuple singleRow = listRows.get(0);
        Assert.assertEquals(Set.of("proc_uri"), singleRow.keySet());

        String processUri = singleRow.getAsUri("proc_uri");
        Assert.assertEquals(currentProcessUri, processUri);
    }



    /* This is to test that the logic used for other tests makes sense.
    * If the subject is a variable, the type is needed to select from WMI.
    * The type can be given explicitly with rdf:type, or implicitly with a property.
    * However, if the type is not given but the subject is known, then the type can be determined
    * from the URI, and WMI is usable.
    * */
    @Test
    public void testSPARQL_CurrentProcessNoType() throws Exception {
        String patternQuery = """
            SELECT ?p ?o
            WHERE {
                <{processUri}> ?p ?o .
			} LIMIT 50
        """;
        String sparqlQuery = setLanguage(patternQuery).replace("{processUri}", currentProcessUri);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        System.out.println("listRows=" + listRows.size());
        Assert.assertTrue(listRows.size() > 0);
        //for(RdfSolution.Tuple tuple : listRows) {
        //    System.out.println(tuple);
        //    Assert.assertEquals(tuple, "");
        //}
        Map<String, String> setPropValues = listRows
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getAsUri("p"),
                        row -> row.getAsLiteral("o")));
        System.out.println("setPropValues=" + setPropValues);
        System.out.println("iri Handle="+WmiProvider.toCIMV2("Handle"));
        Assert.assertEquals("\""+currentPidStr+"\"", setPropValues.get(WmiProvider.toCIMV2("Handle")));
        Assert.assertEquals("\"java.exe\"", setPropValues.get(WmiProvider.toCIMV2("Name")));
        Assert.assertEquals("\"Win32_ComputerSystem\"", setPropValues.get(WmiProvider.toCIMV2("CSCreationClassName")));
    }


    /*
    SELECT ?p (SAMPLE(?pl) AS ?pl_) (COUNT(?o) AS ?count ) (group_concat(?ol;separator=", ") AS ?ol_)  WHERE {<http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Process.Handle%3D%22876%22> ?p ?o .   ?o <http://www.w3.org/2000/01/rdf-schema#label> ?ol .    FILTER ( LANG(?ol) = "en" )    ?s <http://wikiba.se/ontology#directClaim> ?p .    ?s rdfs:label ?pl .    FILTER ( LANG(?pl) = "en" )} group by ?p
    SELECT ?p (SAMPLE(?pl) AS ?pl_) (COUNT(?o) AS ?count ) (group_concat(?ol;separator=", ") AS ?ol_)  WHERE {<http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Process.Handle%3D%22876%22> ?p ?o .   ?o <http://www.w3.org/2000/01/rdf-schema#label> ?ol .    FILTER ( LANG(?ol) = "fr" )    ?s <http://wikiba.se/ontology#directClaim> ?p .    ?s rdfs:label ?pl .    FILTER ( LANG(?pl) = "fr" )} group by ?p
    */

    /*
    http://www.primhillcomputers.com/ontology/ROOT/CIMV2#CIM_ManagedSystemElement
    http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.CreationDate
    http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Process.Handle%3D%22876%22
    */

    /*
    The directClaim property is a predicate that links a property to a statement that directly makes use of it
    */

    @Test
    public void testSPARQL_PROPERTIES() throws Exception {
        /*
            SELECT
                ?p (SAMPLE(?pl) AS ?pl_) (COUNT(?o) AS ?count )
                (group_concat(?ol;separator=", ") AS ?ol_)
            WHERE {
                <http://www.wikidata.org/entity/Q5496635> ?p ?o .
                ?o <http://www.w3.org/2000/01/rdf-schema#label> ?ol .
                FILTER ( LANG(?ol) = "en" )
                ?s <http://wikiba.se/ontology#directClaim> ?p .
                ?s rdfs:label ?pl .
                FILTER ( LANG(?pl) = "en" )
            } group by ?p
        */
        String patternQuery = """
            SELECT ?p (SAMPLE(?pl) AS ?pl_) (COUNT(?o) AS ?count ) (group_concat(?ol;separator=", ") AS ?ol_)
            WHERE {
			   <{processUri}> ?p ?o .
			   ?o <http://www.w3.org/2000/01/rdf-schema#label> ?ol .
			    FILTER ( LANG(?ol) = "[AUTO_LANGUAGE]" )
			    ?s <http://wikiba.se/ontology#directClaim> ?p .
			    ?s rdfs:label ?pl .
			    FILTER ( LANG(?pl) = "[AUTO_LANGUAGE]" )
			} group by ?p
        """;
        String sparqlQuery = setLanguage(patternQuery).replace("{processUri}", currentProcessUri);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        System.out.println("listRows=" + listRows.size());
        Assert.assertTrue(listRows.size() > 0);
    }

    /*
    select * from CIM_ProcessExecutable where Antecedent="\\\\LAPTOP-R89KG6V1\\root\\cimv2:CIM_DataFile.Name=\"C:\\\\WINDOWS\\\\System32\\\\DriverStore\\\\FileRepository\\\\iigd_dch.inf_amd64_ea63d1eddd5853b5\\\\igdinfo64.dll\""'
    */

    /*
    If the predicate type is not a node, Sparql has no idea of it, and might ask the label.
    So, a label must be generated anyway, and WMI cannot be used for this.

    Ca ne peut pas marcher ici:
    processUri="http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Process.Handle%3D%222404%22"
     ... qui est parse en ceci: \\LAPTOP-R89KG6V1\ROOT\CIMV2:Win32_Process.Handle="2404"
    propertyUri="http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.CreationDate"
        <{processUri}> <{propertyUri}> ?date_value .
        ?date_value <http://www.w3.org/2000/01/rdf-schema#label> ?date_label .

    D'une part ?date_value est un literal, mais doit aussi etre un iri.
    Utilisons un predicat qui est un IRI.

    Probleme: Ca n'existe que dans les associators !
    */
    @Test
    public void testSPARQL_ENTITES() throws Exception {
        String patternQuery = """
            SELECT ?date_value ?date_label
            WHERE {
                <{processUri}> <{propertyUri}> ?date_value .
                ?date_value <http://www.w3.org/2000/01/rdf-schema#label> ?date_label .
                FILTER ( LANG(?date_label) = "[AUTO_LANGUAGE]" )
			} LIMIT 50
        """;
        String sparqlQuery = setLanguage(patternQuery)
                .replace("{processUri}", currentProcessUri)
                .replace("{propertyUri}", predicateCreationDate);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        System.out.println("listRows=" + listRows.size());
        // A literal value has no label.
        Assert.assertEquals(0, listRows.size());
    }

    /*
    Ca ne peut pas marcher ici:
    processUri="http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Process.Handle%3D%222404%22"
     ... qui est parse en ceci: \\LAPTOP-R89KG6V1\ROOT\CIMV2:Win32_Process.Handle="2404"
    propertyUri="http://www.primhillcomputers.com/ontology/ROOT/CIMV2#Win32_Process.CreationDate"
        <{processUri}> <{propertyUri}> ?date_value .
        ?date_value <http://www.w3.org/2000/01/rdf-schema#label> ?date_label .

    D'une part ?date_value est un literal, mais doit aussi etre un iri.
    Utilisons un predicat qui est un IRI.
    Mais ca ne peut pas marcher avec Win32_Process : Il faut passer par des associators.
    */
    @Test
    public void testSPARQL_ENTITES_NoFilter() throws Exception {
        String patternQuery = """
            SELECT ?date_value ?date_label
            WHERE {
                <{processUri}> <{propertyUri}> ?date_value .
                ?date_value <http://www.w3.org/2000/01/rdf-schema#label> ?date_label .
			} LIMIT 50
        """;
        String sparqlQuery = setLanguage(patternQuery)
                .replace("{processUri}", currentProcessUri)
                .replace("{propertyUri}", predicateCreationDate);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        System.out.println("listRows=" + listRows.size());
        // Cannot find an object because a literal value has no label.
        Assert.assertEquals(0, listRows.size());
    }

    @Test
    public void testSPARQL_ProcessLabelOnly() throws Exception {
        String patternQuery = """
            SELECT ?process_label
            WHERE {
                <{processUri}> <http://www.w3.org/2000/01/rdf-schema#label> ?process_label .
			} LIMIT 50
        """;
        String sparqlQuery = setLanguage(patternQuery)
                .replace("{processUri}", currentProcessUri);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple tuple = listRows.get(0);
        Assert.assertEquals(Set.of("process_label"), tuple.keySet());
        Assert.assertEquals("\"java.exe\"", tuple.getAsLiteral("process_label"));
    }

    @Test
    public void testSPARQL_PropertyLabelOnly() throws Exception {
        String patternQuery = """
            SELECT ?predicate_label
            WHERE {
                <{propertyUri}> <http://www.w3.org/2000/01/rdf-schema#label> ?predicate_label .
			} LIMIT 50
        """;
        String sparqlQuery = setLanguage(patternQuery)
                .replace("{propertyUri}", predicateCreationDate);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        Assert.assertEquals(1, listRows.size());
        RdfSolution.Tuple tuple = listRows.get(0);
        Assert.assertEquals(Set.of("predicate_label"), tuple.keySet());
        Assert.assertEquals("\"\"Win32_Process.CreationDate\"@en\"", tuple.getAsLiteral("predicate_label"));
    }

    // Typical URI : "http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Directory.Name%3D%22C%3A%5C%5CWindows%22"

    @Test
    public void testSPARQL_PROPERTIES_INCOMING() throws Exception {
        String patternQuery = """
            SELECT ?p (SAMPLE(?pl) AS ?pl_) (COUNT(?o) AS ?count ) (group_concat(?ol;separator=", ") AS ?ol_)
            WHERE {
			    ?o ?p <{processUri}> .
			    ?o <http://www.w3.org/2000/01/rdf-schema#label> ?ol .
			    FILTER ( LANG(?ol) = "[AUTO_LANGUAGE]" )
			    ?s <http://wikiba.se/ontology#directClaim> ?p .
			    ?s rdfs:label ?pl .
			    FILTER ( LANG(?pl) = "[AUTO_LANGUAGE]" )
			} group by ?p
            """;
        String sparqlQuery = setLanguage(patternQuery)
                .replace("{processUri}", currentProcessUri);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        System.out.println("listRows=" + listRows.size());
        Assert.assertTrue(listRows.size() > 0);
        for(RdfSolution.Tuple tuple : listRows) {
            System.out.println(tuple);
            //Assert.assertEquals(tuple, "");
        }
        Map<String, String> setPropValues = listRows
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getAsUri("process_property"),
                        row -> row.getAsLiteral("property_value")));
        System.out.println("setPropValues=" + setPropValues);
        Assert.assertEquals(currentPidStr, setPropValues.get("Handle"));
        Assert.assertEquals("Wmi32_Process", setPropValues.get("rdf:type"));
    }

    @Test
    public void testSPARQL_PROPERTIES_INCOMING_NoFilter() throws Exception {
        String patternQuery = """
            SELECT ?p (SAMPLE(?pl) AS ?pl_) (COUNT(?o) AS ?count ) (group_concat(?ol;separator=", ") AS ?ol_)
            WHERE {
			    ?o ?p <{processUri}> .
			    ?o <http://www.w3.org/2000/01/rdf-schema#label> ?ol .
			    ?s <http://wikiba.se/ontology#directClaim> ?p .
			    ?s rdfs:label ?pl .
			} group by ?p
            """;
        String sparqlQuery = setLanguage(patternQuery)
                .replace("{processUri}", currentProcessUri);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        System.out.println("listRows=" + listRows.size());
        Assert.assertTrue(listRows.size() > 0);
        for(RdfSolution.Tuple tuple : listRows) {
            System.out.println(tuple);
            //Assert.assertEquals(tuple, "");
        }
        Map<String, String> setPropValues = listRows
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getAsUri("process_property"),
                        row -> row.getAsLiteral("property_value")));
        System.out.println("setPropValues=" + setPropValues);
        Assert.assertEquals(currentPidStr, setPropValues.get("Handle"));
        Assert.assertEquals("Wmi32_Process", setPropValues.get("rdf:type"));
    }

    @Test
    public void testSPARQL_ENTITES_INCOMING_PrePreTest() throws Exception {
        String patternQuery = """
            SELECT ?subj ?obj
            WHERE {
                ?subj <{propertyUri}> ?obj .
		    }
         """;
        String sparqlQuery = setLanguage(patternQuery)
                .replace("{processUri}", currentProcessUri)
                .replace("{propertyUri}", uri_CIM_ProcessExecutable_Dependent);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        System.out.println("listRows=" + listRows.size());

        // There must be one executable and probably several libraries.
        Assert.assertTrue(listRows.size() > 0);

        Set<String> nodesProcesses = listRows.nodeValuesSet("obj");
        System.out.println("nodesProcesses=" + nodesProcesses);
        Assert.assertTrue(nodesProcesses.contains(currentProcessUri));
    }

    @Test
    public void testSPARQL_ENTITES_INCOMING_PreTest() throws Exception {
        String patternQuery = """
            SELECT ?subj
            WHERE {
                ?subj <{propertyUri}> <{processUri}> .
		    }
         """;
        String sparqlQuery = setLanguage(patternQuery)
                .replace("{processUri}", currentProcessUri)
                .replace("{propertyUri}", uri_CIM_ProcessExecutable_Dependent);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        System.out.println("listRows=" + listRows.size());

        // There must be one executable and probably several libraries.
        Assert.assertTrue(listRows.size() > 0);
        Set<String> setPropValues = listRows
                .stream()
                .map(row -> row.getAsUri("subj"))
                .collect(Collectors.toSet());
        System.out.println("setPropValues=" + setPropValues);

        Set<String> setClasses = setPropValues
                .stream()
                .map(iri -> WmiOntology.splitIRI(iri).Token)
                .collect(Collectors.toSet());
        System.out.println("setClasses=" + setClasses);

        Assert.assertEquals(Set.of("CIM_ProcessExecutable"), setClasses);
    }

    @Test
    public void testSPARQL_ENTITES_INCOMING() throws Exception {
        String patternQuery = """
            SELECT ?o ?ol
            WHERE {
                ?o <{propertyUri}> <{processUri}> .
			    ?o <http://www.w3.org/2000/01/rdf-schema#label> ?ol .
			    FILTER ( LANG(?ol) = "[AUTO_LANGUAGE]" )
		    } LIMIT 50
         """;
        String sparqlQuery = setLanguage(patternQuery)
                .replace("{processUri}", currentProcessUri)
                .replace("{propertyUri}", uri_CIM_ProcessExecutable_Dependent);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        System.out.println("listRows=" + listRows.size());

        // There must be one executable and probably several libraries.
        Assert.assertTrue(listRows.size() > 0);
    }

    @Test
    public void testSPARQL_ENTITES_INCOMING_LANG() throws Exception {
        String patternQuery = """
            SELECT ?o ?ol (LANG(?ol) as ?lang)
            WHERE {
                ?o <{propertyUri}> <{processUri}> .
			    ?o <http://www.w3.org/2000/01/rdf-schema#label> ?ol .
		    } LIMIT 50
         """;
        String sparqlQuery = setLanguage(patternQuery)
                .replace("{processUri}", currentProcessUri)
                .replace("{propertyUri}", uri_CIM_ProcessExecutable_Dependent);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        System.out.println("listRows=" + listRows.size());

        // There must be one executable and probably several libraries.
        Assert.assertTrue(listRows.size() > 0);
    }

    @Test
    public void testSPARQL_ENTITES_INCOMING_NoFilter() throws Exception {
        String patternQuery = """
            SELECT ?o ?ol
            WHERE {
                ?o <{propertyUri}> <{processUri}> .
			    ?o <http://www.w3.org/2000/01/rdf-schema#label> ?ol .
		    } LIMIT 50
         """;
        String sparqlQuery = setLanguage(patternQuery)
                .replace("{processUri}", currentProcessUri)
                .replace("{propertyUri}", uri_CIM_ProcessExecutable_Dependent);
        System.out.println("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        System.out.println("listRows=" + listRows.size());

        // There must be one executable and probably several libraries.
        Assert.assertTrue(listRows.size() > 0);
        for(RdfSolution.Tuple currentRow : listRows) {
            String olValue = currentRow.getAsLiteral("ol");
            System.out.println("    ol=" + olValue);
            Assert.assertTrue(olValue.endsWith("@en\""));
        }
    }
}

/*
?_3_assoc cimv2:CIM_ProcessExecutable.Dependent ?_2_process .

?_2_assoc cimv2:CIM_ProcessExecutable.Dependent ?_1_process .
?_2_assoc cimv2:CIM_ProcessExecutable.Antecedent ?_3_file .
*/
