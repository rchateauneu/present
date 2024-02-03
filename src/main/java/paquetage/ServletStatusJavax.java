package paquetage;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

// Tomcat 9
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ServletStatusJavax extends HttpServlet {

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        getServletContext().log("init() called");
        WriteFile("init");
    }

    /*
    This is the entry point of all Servlet handling.
    The Servlet container instantiates your Servlet class and invokes this method on the generated instance
    if it determines that your Servlet should handle a request.
    HttpServlet is an abstract class which implements this method by delegating
    to the appropriate doGet, doPost, doXyz methods, depending on the HTTP method used in the request.
     */

    /*
        https://www.w3.org/TR/2013/REC-sparql11-results-json-20130321/
        This document describes how to serialize SPARQL results (SELECT and ASK query forms) in a JSON format.
    */
    private String outputJson() {
        long processId = ProcessHandle.current().pid();
        WriteFile("outputJson");
        String outputJson = String.format("""
        {
            "head": {"vars": ["pid", "status"]
                     },
            "results": {
                "bindings": [
                    {
                        "pid": {"type": "literal", "value": "%d"},
                        "status": {"type": "literal", "value": "Running"}
                    }
                ]
            }
        }
        """, processId);
        return outputJson;
    }

    private String outputXml() {
        WriteFile("outputXml");
        String outputXml = """
        <?xml version="1.0"?>
        <sparql xmlns="http://www.w3.org/2005/sparql-results#">
          <head>
            <variable name="x"/>
            <variable name="hpage"/>
          </head>
          <results>
            <result>
              <binding name="x"> ... </binding>
              <binding name="hpage"> ... </binding>
            </result>
          </results>
        </sparql>
        </xml>
        """;
        return outputXml;
    }

    /*
    This is for temporary debugging only.
     */
    void WriteFile(String message) {
        String fileName = "C:\\Users\\rchat\\Developpement\\present_DVL\\present_solution\\present\\src\\main\\webapp\\WEB-INF\\classes\\paquetage\\Output.txt";
        try {
            FileWriter fileWriter = new FileWriter(fileName, true);
            BufferedWriter out = new BufferedWriter(fileWriter);
            out.write("Message:" + message + "\n");
            String currentJavaBinary = PresentUtils.currentJavaBinary();
            out.write("currentJavaBinary:" + currentJavaBinary + "\n");
            out.flush();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        WriteFile("doGet");
        response.setContentType("text/html;charset=UTF-8");
        java.lang.String sparqlQuery = request.getParameter("query");
        getServletContext().log("sparqlQuery=" + sparqlQuery);
        java.lang.String resultFormat = request.getParameter("format");
        getServletContext().log("resultFormat=" + resultFormat);

        String mimeFormat;
        String queryResult;
        if(resultFormat == null) {
            WriteFile("resultFormat is null");
            resultFormat = "JSON";
        }
        WriteFile("resultFormat=" + resultFormat);
        if(resultFormat.equalsIgnoreCase("JSON")) {
            mimeFormat = "application/sparql-results+json";
            queryResult = outputJson();
        }
        else if(resultFormat.equalsIgnoreCase("XML")) {
            mimeFormat = "application/sparql-results+xml";
            queryResult = outputXml();
        }
        else {
            mimeFormat = "application/sparql-results+json";
            queryResult = outputJson();
        }
        getServletContext().log("mimeFormat=" + mimeFormat);
        // FIXME: Should do that with TomCat, because we want to serve static files too.
        response.addHeader("Content-Type", mimeFormat + ";charset=utf-8");
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
}
