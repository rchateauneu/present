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

public class ServletQueryJavax extends HttpServlet {

    private RepositoryWrapper repositoryWrapper = null;

    ServletQueryJavax() {
        repositoryWrapper = new RepositoryWrapper("ROOT\\CIMV2");
    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        getServletContext().log("init() called");
        WriteFile("init");
    }

    private String outputJson() {
        WriteFile("outputJson");
        return rdfJson;
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
            // queryResult = outputJson();
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

    static String rdfJson = """
            {
              "head": { "vars": [ "book" , "title" ]
              } ,
              "results": {\s
                "bindings": [
                  {
                    "book": { "type": "uri" , "value": "http://example.org/book/book6" } ,
                    "title": { "type": "literal" , "value": "Harry Potter and the Half-Blood Prince" }
                  } ,
                  {
                    "book": { "type": "uri" , "value": "http://example.org/book/book7" } ,
                    "title": { "type": "literal" , "value": "Harry Potter and the Deathly Hallows" }
                  } ,
                  {
                    "book": { "type": "uri" , "value": "http://example.org/book/book5" } ,
                    "title": { "type": "literal" , "value": "Harry Potter and the Order of the Phoenix" }
                  } ,
                  {
                    "book": { "type": "uri" , "value": "http://example.org/book/book4" } ,
                    "title": { "type": "literal" , "value": "Harry Potter and the Goblet of Fire" }
                  } ,
                  {
                    "book": { "type": "uri" , "value": "http://example.org/book/book2" } ,
                    "title": { "type": "literal" , "value": "Harry Potter and the Chamber of Secrets" }
                  } ,
                  {
                    "book": { "type": "uri" , "value": "http://example.org/book/book3" } ,
                    "title": { "type": "literal" , "value": "Harry Potter and the Prisoner Of Azkaban" }
                  } ,
                  {
                    "book": { "type": "uri" , "value": "http://example.org/book/book1" } ,
                    "title": { "type": "literal" , "value": "Harry Potter and the Philosopher's Stone" }
                  }
                ]
              }
            }
        """;

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


/*

# For the moment, it just displays the content of the input to standard error,
# so the SparQL protocol can be analysed.

# See Experimental/Test_package_sparqlwrapper.py

# http://timgolden.me.uk/python/downloads/wmi-0.6b.py

def __run_sparql_query(sparql_query):
    grph = rdflib.Graph()

    # add function directly, normally we would use setuptools and entry_points
    rdflib.plugins.sparql.CUSTOM_EVALS['custom_eval_function'] = lib_sparql_custom_evals.custom_eval_function
    query_result = grph.query(sparql_query)
    if 'custom_eval_function' in rdflib.plugins.sparql.CUSTOM_EVALS:
        del rdflib.plugins.sparql.CUSTOM_EVALS['custom_eval_function']
    return query_result

################################################################################
def __query_header(sparql_query):
    """
    Returns the variable names of an input sparql query.

    :param sparql_query:
    :return: A list of strings which are variable names.
    """
    parsed = rdflib.plugins.sparql.parser.parseQuery(sparql_query)

    # parsed = rdflib.plugins.sparql.parser.parseQuery("select ?a ?b where { ?a a ?b . }")
    # parsed = ([([], {}),
    #     SelectQuery_{'where': GroupGraphPatternSub_{'part': [TriplesBlock_{'triples': [([rdflib.term.Variable(u'a'), PathAlternative_{'part': [PathSequence_{'part': [PathElt_{'part': rdflib.term.URIRef(u'http://www.w3.org/1999/02/22-rdf-syntax-ns#type')}]}]},
    # rdflib.term.Variable(u'b')], {})]}]}, 'projection': [vars_{'var': rdflib.term.Variable(u'a')}, vars_{'var': rdflib.term.Variable(u'b')}]}], {})

    list_vars = parsed[1]['projection']
    list_names = [str(one_var['var']) for one_var in list_vars]
    return list_names


# This is a SPARQL server which executes the query with WMI data.
def Main():
    lib_util.SetLoggingConfig(logging.DEBUG)

    # https://hhs.github.io/meshrdf/sparql-and-uri-requests

    # Parameter name, SELECT queries, CONSTRUCT queries, default, help.
    # format
    # Accepts HTML*, XML, CSV, TSV or JSON
    # Accepts HTML*, XML, JSON-LD, RDF/XML, TURTLE or N3
    # Default: HTML*
    # Returns a file with the specified syntax.

    # inference
    # Accepts true or false
    # Accepts true or false
    # Default: false
    # Running a query with inference set to "true" will return results
    # for all subclasses and subproperties of those classes and properties you specify in your query.
    # For example, there are no direct instances of meshv:Descriptor,
    # but if you run a query with inference and look for rdf:type meshv:Descriptor,
    # you will get all instances of meshv:Descriptor's subclasses - meshv:TopicalDescriptor,
    # meshv:GeographicalDescriptor, meshv:PublicationType and meshv:CheckTag.
    # Running a query with inference=true may affect performance.

    # limit
    # Accepts positive integers up to 1000
    # N/A
    # Default: 1000
    # Limits the number of results per request. The maximum number of results per request for SELECT queries is 1,000.
    # This parameter does not affect CONSTRUCT queries.
    # CONSTRUCT queries will return all triples requested up to a limit of 10,000 triples.

    # offset
    # Accepts positive integers
    # N/A
    # Default: 0
    # When offset=n, this parameter will return results starting with the nth result.
    # Use this parameter to loop through multiple requests for large result sets.

    # query
    # Accepts a SELECT SPARQL query
    # Accepts a CONSTRUCT SPARQL query
    # Default: N/A
    # This parameter is required and must contain a SPARQL query. For an example of how these are formatted,
    # run a query from the SPARQL query editor and view the resulting URL.

    # year
    # Accepts "current" or a year.
    # Accepts "current" or a year.
    # Default: current
    # Queries either the current MeSH graph (http://id.nlm.nih.gov/mesh) or a versioned MeSH graph,
    # for example: (http://id.nlm.nih.gov/mesh/2015).
    import cgi
    arguments = cgi.FieldStorage()

    # See lib_uris.SmbShareUri and the HTTP server which collapses duplicated slashes "//" into one,
    # in URL, because they are interpreted as file names.
    # SparqlWrapper does not encode slashes with urllib.quote_plus(param.encode('UTF-8'), safe='/')
    # in Wrapper.py.
    # See modules CGIHTTPServer, BaseHTTPServer, CGIHTTPRequestHandler
    # 'HTTP_USER_AGENT': 'sparqlwrapper 1.8.4 (rdflib.github.io/sparqlwrapper)'
    # QUERY_STRING='query=%0A++++++++++++++++PREFIX+wmi%3A++%3Chttp%3A/www.primhillcomputers.com/ontology/wmi%23%3E%0A++++++++++++++++PREFIX+survol%3A++%3Chttp%3A/primhillcomputers.com/survol%23%3E%0A++++++++++++++++PREFIX+rdfs%3A++++%3Chttp%3A/www.w3.org/2000/01/rdf-schema%
    # 23%3E%0A++++++++++++++++SELECT+%3Fcaption%0A++++++++++++++++WHERE%0A++++++++++++++++%7B%0A++++++++++++++++++++%3Furl_user+rdf%3Atype+survol%3AWin32_UserAccount+.%0A++++++++++++++++++++%3Furl_user+survol%3AName+%27rchateau%27+.%0A++++++++++++++++++++%3Furl_user+sur
    # vol%3ACaption+%3Fcaption+.%0A++++++++++++++++++++%3Furl_user+rdfs%3AseeAlso+%22WMI%22+.%0A++++++++++++++++%7D%0A++++++++++++++++&output=json&results=json&format=json'
    sparql_query = arguments["query"].value

    # 'SERVER_SOFTWARE': 'SimpleHTTP/0.6 Python/2.7.10'
    # 'SERVER_SOFTWARE': 'SimpleHTTP/0.6 Python/3.6.3'
    logging.debug("SERVER_SOFTWARE=%s" % os.environ['SERVER_SOFTWARE'])
    if os.environ['SERVER_SOFTWARE'].startswith("SimpleHTTP"):
        # Beware, only with Python2. Not needed on Linux and Python 3.
        # Maybe because the Python class processes the CGI arguments like filenames.
        if not lib_util.is_py3 and lib_util.isPlatformWindows:
            sparql_query = re.sub("([^a-z]*)http:/([^a-z]*)", r"\1http://\2", sparql_query)
            logging.debug("Substitution 'http://' and 'http:///'")

    logging.debug("sparql_server sparql_query=%s" % sparql_query.replace(" ", "="))

    try:
        result_format = arguments["format"].value
    except KeyError:
        result_format = "JSON"

    query_result = __run_sparql_query(sparql_query)

    logging.debug("After query len(query_result)=%d" % len(query_result))
    logging.debug("After query query_result=%s" % str(query_result))

    # TODO: This does not work "select *", so maybe should read the first row.
    row_header = __query_header(sparql_query)

    # https://docs.aws.amazon.com/neptune/latest/userguide/sparql-api-reference-mime.html

    if result_format.upper() == "JSON":
        mime_format = "application/sparql-results+json"
        # https://www.w3.org/TR/2013/REC-sparql11-results-json-20130321/
        # This document describes how to serialize SPARQL results (SELECT and ASK query forms) in a JSON format.
        # {
        #     "head": {"vars": ["book", "title"]
        #              },
        #     "results": {
        #         "bindings": [
        #             {
        #                 "book": {"type": "uri", "value": "http://example.org/book/book6"},
        #                 "title": {"type": "literal", "value": "Harry Potter and the Half-Blood Prince"}
        #             },
        bindings_list = []
        for one_row in query_result:
            dict_row = {}
            for ix in range(len(row_header)):
                one_element = one_row[ix]

                if lib_kbase.IsLiteral(one_element):
                    json_element = {"type":"literal", "value": str(one_element)}
                elif lib_kbase.IsURIRef(one_element):
                    json_element = {"type":"url", "value": str(one_element)}
                else:
                    raise Exception("SparqlServer: Invalid type:%s"%str(one_element))
                one_variable = row_header[ix]
                dict_row[one_variable] = json_element

            bindings_list.append(dict_row)
        logging.debug("bindings_list=%s" % str(bindings_list))

        json_output = {
            "head": {"vars": row_header},
            "results": {"bindings": bindings_list}}
        str_output = json.dumps(json_output)
    elif result_format.upper() == "XML":
        mime_format = "application/sparql-results+xml"
        # https://www.w3.org/TR/rdf-sparql-XMLres/
        # This document describes an XML format for the variable binding and boolean results formats provided by the SPARQL query language for RDF
        # <?xml version="1.0"?>
        # <sparql xmlns="http://www.w3.org/2005/sparql-results#">
        #   <head>
        #     <variable name="x"/>
        #     <variable name="hpage"/>
        #   </head>
        #
        #   <results>
        #     <result>
        #       <binding name="x"> ... </binding>
        #       <binding name="hpage"> ... </binding>
        #     </result>

        root = ET.Element("sparql")
        head = ET.SubElement(root, "head")
        for one_variable in row_header:
            ET.SubElement(head, "variable", name=one_variable)

        results = ET.SubElement(root, "results")
        for one_row in query_result:
            result = ET.SubElement(results, "result")
            for ix in range(len(row_header)):
                one_variable = row_header[ix]
                ET.SubElement(result, "binding", name=one_variable).text = one_row[ix]

        str_output = ET.tostring(root, encoding='utf8', method='xml')
        logging.debug("str_output=%s" % str_output)

    else:
        raise Exception("Results format %s not implemented yet" % result_format)

    logging.debug("result_format=%s str_output=%s", result_format, str_output)

    arr_headers = [
        ('Access-Control-Allow-Origin', '*'),
        ('Access-Control-Allow-Methods', 'POST,GET,OPTIONS'),
        ('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept'),
        ('Content-Length', str(str_output)),
    ]
    lib_util.WrtHeader(mime_format, arr_headers)
    lib_util.WrtAsUtf(str_output)
*/