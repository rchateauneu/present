# present

A virtual knowledge graph based on WMI.

Virtual Repository Over Wmi/Wbem : WROW

Bridge Sparql <=> Wql

# How to run the Sparql endpoint
Jar file MyMavenProject-1.0-SNAPSHOT.jar

Starting TomCat : Run Tomcat 9.0

URL is [http://localhost:8180/present/query](http://localhost:8180/present/query)

Sample query:
```
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT * WHERE {
  ?sub ?pred ?obj .
} LIMIT 10
```

# TODO
* Ontotext plugin, see [GraphDB Plugins](https://graphdb.ontotext.com/documentation/10.0/plug-ins.html)
* Display with [d3.js](d3.js) and [d3sparql.js](http://biohackathon.org/d3sparql/) . Are these the sources : [https://github.com/ktym/d3sparql](https://github.com/ktym/d3sparql) ?

# OMI
* wsMan connection to OMI: https://github.com/OpenNMS/wsman GitHub - OpenNMS/wsman: A WS-Man client for Java A pure Java WS-Man client implemented using JAX-WS & CXF. Artifacts are available in Maven Central.
* SqlServer provider: https://flylib.com/books/en/2.679.1.46/1/ : SQL WMI classes = MSSQL_Column, MSSQL_Database, MSSQL_ForeignKey etc...
* OMI provider in Python : https://github.com/microsoft/omi-script-provider

Some details about OMI here: https://github.com/microsoft/omi

# How to start the OMI server:


```
sudo /opt/omi/bin/service_control restart
```
OMI query examples from [Github OMI](https://github.com/microsoft/omi)

```
/opt/omi/bin/omicli ei root/omi OMI_Identify
```

Displaying OMI help:

```
/opt/omi/bin/omicli
/opt/omi/bin/omicli gc root/omi OMI_Identif
```

TODO:
Reimplement the sparql command DESCRIBE which will return for a given CIM object:
- Its attributes.
- Associated objects (associators).
- related scripts (As SeeAlso)

How to execute scripts ? Is it possible to use them as variables in a FROM sparql clause ?