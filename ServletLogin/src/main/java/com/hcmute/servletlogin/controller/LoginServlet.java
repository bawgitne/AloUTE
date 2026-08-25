package com.hcmute.servletlogin.controller;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import java.io.IOException;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws IOException, ServletException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");

        if ("admin".equals(username)
                && "123".equals(password)) {

            HttpSession session = request.getSession();
            session.setAttribute("username", username);

            Cookie cookie = new Cookie("username", username);
            cookie.setMaxAge(60 * 60);
            response.addCookie(cookie);

            response.sendRedirect(
                    request.getContextPath() + "/welcome.jsp"
            );

        } else {
            response.sendRedirect("login.jsp");
        }
    }
}