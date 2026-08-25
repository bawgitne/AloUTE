<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="com.hcmute.servletlogin.model.Category" %>


<html>

<head>
    <meta charset="UTF-8">
    <title>Category List</title>
</head>


<body>


<h2>Category List</h2>

<a href="category-form.jsp">
    Add Category
</a>

<br><br>


<table border="1">

<tr>
    <th>ID</th>
    <th>Name</th>
    <th>Description</th>
    <th>Action</th>
</tr>


<%
    List<Category> list =
        (List<Category>) request.getAttribute("categories");


    for(Category c : list){
%>


<tr>

<td>
    <%= c.getId() %>
</td>

<td>
    <%= c.getName() %>
</td>

<td>
    <%= c.getDescription() %>
</td>


<td>

<a href="categories?action=edit&id=<%=c.getId()%>">
    Edit
</a>


&nbsp;


<a href="categories?action=delete&id=<%=c.getId()%>"
   onclick="return confirm('Delete this category?')">
    Delete
</a>


</td>


</tr>


<%
    }
%>


</table>


</body>

</html>