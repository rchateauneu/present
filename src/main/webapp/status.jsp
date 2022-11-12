<html>
<head><title>Status page</title></head>
<body>
  <%
    double num = Math.random();
    if (num > 0.95) {
  %>
      <h2>Random value over 0.95 :</h2><p>(<%= num %>)</p>
  <%
    } else {
  %>
      <h2>Random value below 0.95 :</h2><p>(<%= num %>)</p>
  <%
    }
  %>
  <a href="<%= request.getRequestURI() %>"><h3>Retry</h3></a>
</body>
</html>