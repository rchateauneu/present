<html>
<head><title>Status page</title></head>
<body>
  Internal status
  <%
  String computerName = paquetage.PresentUtils.computerName;
  String parentProcessId = paquetage.PresentUtils.ParentProcessId();
  String parentProcessName = paquetage.PresentUtils.ParentProcessName();
  %>
  <table border=1>
  <tr><td>Computer name</td><td><%= computerName %></td></tr>
  <tr><td>Parent process id</td><td><%= parentProcessId %></td></tr>
  <tr><td>Parent process name</td><td><%= parentProcessName %></td></tr>
  <tr><td>Ontologies path cache</td><td><%= paquetage.WmiProvider.ontologiesPathCache %></td></tr>
  </table>

  <%
  java.util.Set<String> namespaces = paquetage.WmiOntology.Namespaces();
  int countNamespaces = namespaces.size();
  %>
  Namespaces in cache: <p><%= countNamespaces %></p>

  <table>
  <%
  for(String currentNamespace: namespaces) {
  %>
  <tr><td><%= currentNamespace %></td></tr>
  <%
  }
  %>
  </table>

  <a href="<%= request.getRequestURI() %>"><h3>Retry</h3></a>
</body>
</html>