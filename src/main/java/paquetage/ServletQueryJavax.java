package paquetage;

import org.apache.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

// Tomcat 9
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ServletQueryJavax extends HttpServlet {
    final static private Logger logger = Logger.getLogger(ServletQueryJavax.class);

    private RepositoryWrapper repositoryWrapper = null;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        getServletContext().log("init() called");
        WriteFile("init");
        repositoryWrapper = new RepositoryWrapper("ROOT\\CIMV2");
        WriteFile("after repositoryWrapper creation");
    }

    static int counter = 0;
    void WriteFile(String message) {
        String fileName = "C:\\Users\\rchat\\Developpement\\present_DVL\\present_solution\\present\\src\\main\\webapp\\WEB-INF\\classes\\paquetage\\Output.txt";
        try {
            FileWriter fileWriter = new FileWriter(fileName, true);
            BufferedWriter out = new BufferedWriter(fileWriter);
            out.write("Hello:" + counter + ":" + message + "\n");
            ++counter;
            out.flush();
            out.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        WriteFile("doGet getQueryString=" + request.getQueryString());
        ProcessQuery(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        WriteFile("doPost getQueryString=" + request.getQueryString());
        ProcessQuery(request, response);
    }

    private String QueryToJson(String sparqlQuery) throws Exception {
        logger.debug("sparqlQuery=" + sparqlQuery);
        RdfSolution listRows = repositoryWrapper.executeQuery(sparqlQuery);
        String jsonResult = listRows.toJson(true);
        return jsonResult;
    }

    private void ProcessQuery(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        /*
        Header of Wikidata Sparql GUI:
            :method: GET
            :scheme: https
            Accept: application/sparql-results+json
            Accept-Encoding: gzip, deflate, br
            Accept-Language: en-GB,en;q=0.9,en-US;q=0.8,fr;q=0.7
            Cache-Control: no-cache
            Cookie: GeoIP=GB:ENG:Acton:51.51:-0.27:v4; WMF-Last-Access-Global=02-Sep-2023
            Pragma: no-cache
         */
        String acceptHeader = request.getHeader("Accept");
        getServletContext().log("acceptHeader=" + acceptHeader);
        logger.debug("acceptHeader=" + acceptHeader);

        response.setContentType("text/html;charset=UTF-8");
        java.lang.String sparqlQuery = request.getParameter("query");
        getServletContext().log("sparqlQuery=" + sparqlQuery);
        WriteFile("sparqlQuery=" + sparqlQuery);

        logger.debug("sparqlQuery=" + sparqlQuery);
        /*
        Comment afficher des labels et des images ?
        Voir ce qui existe.

        On ne sait pas executer ceci:

		SELECT
            ?p
            (SAMPLE(?pl) AS ?pl_)
            (COUNT(?o) AS ?count )
            (group_concat(?ol;separator=", ") AS ?ol_)
		WHERE {
		    <http://www.primhillcomputers.com/ontology/ROOT/CIMV2#%5C%5CLAPTOP-R89KG6V1%5CROOT%5CCIMV2%3AWin32_Process.Handle%3D%2223284%22/entity/Q378619> ?p ?o .
		    ?o <http://www.w3.org/2000/01/rdf-schema#label> ?ol .
		    FILTER ( LANG(?ol) = "en" )
		    ?s <http://wikiba.se/ontology#directClaim> ?p .
		    ?s rdfs:label ?pl .
		    FILTER ( LANG(?pl) = "en" )
		} group by ?p

         */



        // TODO: Should parse "acceptHeader=application/sparql-results+json"
        java.lang.String resultFormat = request.getParameter("format");
        getServletContext().log("resultFormat=" + resultFormat);
        WriteFile("resultFormat=" + resultFormat);

        String mimeFormat;
        if(resultFormat == null) {
            WriteFile("resultFormat is null");
            logger.debug("resultFormat is null");
            resultFormat = "JSON";
        }
        logger.debug("resultFormat=" + resultFormat);
        WriteFile("resultFormat=" + resultFormat);

        RdfSolution listRows = null;
        try {
            listRows = repositoryWrapper.executeQuery(sparqlQuery);
        }
        catch(Exception exc) {
            throw new ServletException(exc);
        }

        String queryResult;
        if(resultFormat.equalsIgnoreCase("JSON")) {
            mimeFormat = "application/sparql-results+json";
            queryResult = listRows.toJson(false);
        }
        else if(resultFormat.equalsIgnoreCase("XML")) {
            // FIXME: This is not tested.
            mimeFormat = "application/sparql-results+xml";
            queryResult = listRows.toJson(true);
        }
        else {
            throw new ServletException("Format not implemented:" + resultFormat);
        }
        getServletContext().log("mimeFormat=" + mimeFormat);

        response.addHeader("Content-Type", mimeFormat + ";charset=utf-8");
        // CORS-enabled site. This is set in web.xml
        /*
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "POST,GET,OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
        */
        response.addIntHeader("Content-Length", queryResult.length());

        WriteFile("queryResult.length()=" + queryResult.length());
        response.getWriter().print(queryResult);
    }

    @Override
    public void destroy() {
        WriteFile("destroy");
        getServletContext().log("destroy() called");
    }

    // NOT TESTED.
    static String rdfXml = """
            <?xml version="1.0"?>
            <rdf:RDF
            xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
            xmlns:cd="http://www.recshop.fake/cd#">
            <rdf:Description
             rdf:about="http://www.recshop.fake/cd/Empire Burlesque">
              <cd:artist>Bob Dylan</cd:artist>
              <cd:country>USA</cd:country>
              <cd:company>Columbia</cd:company>
              <cd:price>10.90</cd:price>
              <cd:year>1985</cd:year>
            </rdf:Description>
            <rdf:Description
             rdf:about="http://www.recshop.fake/cd/Hide your heart">
              <cd:artist>Bonnie Tyler</cd:artist>
              <cd:country>UK</cd:country>
              <cd:company>CBS Records</cd:company>
              <cd:price>9.90</cd:price>
              <cd:year>1988</cd:year>
            </rdf:Description>
            </rdf:RDF>
        """;

}
