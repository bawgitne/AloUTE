<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>


<html>

<head>

    <meta charset="UTF-8">

    <title>Add Category</title>

</head>


<body>


<h2>Add Category</h2>


<%
    String error =
            (String) request.getAttribute("error");

    String name =
            request.getParameter("name");

    String description =
            request.getParameter("description");


    if(name == null){
        name = "";
    }

    if(description == null){
        description = "";
    }


    if(error != null){
%>

<p style="color:red">
    <%= error %>
</p>

<%
    }
%>



<form action="categories?action=insert"
      method="post">


    <label>
        Name:
    </label>

    <br>


    <input type="text"
           name="name"
           value="<%=name%>">


    <br><br>



    <label>
        Description:
    </label>

    <br>


    <input type="text"
           name="description"
           value="<%=description%>">


    <br><br>



    <button type="submit">
        Save
    </button>


</form>


<br>


<a href="categories">
    Back to Category List
</a>



</body>

</html>