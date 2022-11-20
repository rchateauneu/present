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
    final static private Logger logger = Logger.getLogger(SparqlBGPTreeExtractor.class);

    private RepositoryWrapper repositoryWrapper = null;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        getServletContext().log("init() called");
        WriteFile("init");
        repositoryWrapper = new RepositoryWrapper("ROOT\\CIMV2");
        WriteFile("after repositoryWrapper creation");
    }

    private String outputXml() {
        WriteFile("outputXml");
        return rdfXml;
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
        RdfSolution listRows = repositoryWrapper.ExecuteQuery(sparqlQuery);
        String jsonResult = listRows.ToJson();
        return jsonResult;
    }

    private void ProcessQuery(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        java.lang.String sparqlQuery = request.getParameter("query");
        getServletContext().log("sparqlQuery=" + sparqlQuery);
        WriteFile("sparqlQuery=" + sparqlQuery);
        java.lang.String resultFormat = request.getParameter("format");
        getServletContext().log("resultFormat=" + resultFormat);
        WriteFile("resultFormat=" + resultFormat);

        String mimeFormat;
        String queryResult;
        if(resultFormat == null) {
            WriteFile("resultFormat is null");
            resultFormat = "JSON";
        }
        WriteFile("resultFormat=" + resultFormat);
        if(resultFormat.equalsIgnoreCase("JSON")) {
            mimeFormat = "application/sparql-results+json";
            try {
                queryResult = QueryToJson(sparqlQuery);
            }
            catch(Exception exc) {
                throw new ServletException(exc);
            }
        }
        else if(resultFormat.equalsIgnoreCase("XML")) {
            mimeFormat = "application/sparql-results+xml";
            queryResult = outputXml();
        }
        else {
            throw new ServletException("Format not implemented:" + resultFormat);
        }
        getServletContext().log("mimeFormat=" + mimeFormat);

        response.addHeader("Content-Type", mimeFormat + ";charset=utf-8");
        // CORS-enabled site.
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "POST,GET,OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
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
