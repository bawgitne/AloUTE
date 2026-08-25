<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%@ page import="com.hcmute.servletlogin.model.Category" %>


<%
    Category c =
            (Category) request.getAttribute("category");


    String error =
            (String) request.getAttribute("error");
%>


<html>

<head>

    <meta charset="UTF-8">

    <title>Edit Category</title>

</head>


<body>


<h2>Edit Category</h2>



<%
    if(error != null){
%>

<p style="color:red">
    <%= error %>
</p>

<%
    }
%>




<form action="categories?action=update"
      method="post">



    <input type="hidden"
           name="id"
           value="<%=c.getId()%>">



    Name:

    <br>

    <input type="text"
           name="name"
           value="<%= c.getName() == null ? "" : c.getName() %>">


    <br><br>



    Description:

    <br>

    <input type="text"
           name="description"
           value="<%= c.getDescription() == null ? "" : c.getDescription() %>">



    <br><br>


    <button type="submit">
        Update
    </button>


</form>


<br>


<a href="categories">
    Back
</a>



</body>

</html>