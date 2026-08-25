package com.hcmute.servletlogin.controller;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import java.io.IOException;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws IOException, ServletException {


        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }


        Cookie cookie = new Cookie("username", "");

        cookie.setMaxAge(0);

        response.addCookie(cookie);


        response.sendRedirect("login.jsp");
    }
}