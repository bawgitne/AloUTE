package com.hcmute.servletlogin.filter;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.*;

import java.io.IOException;

@WebFilter("/*")
public class LoginFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {


        HttpServletRequest req =
                (HttpServletRequest) request;

        HttpServletResponse res =
                (HttpServletResponse) response;


        String path = req.getRequestURI();


        HttpSession session =
                req.getSession(false);


        boolean loggedIn =
                session != null
                        && session.getAttribute("username") != null;


        // Cho phép vào login
        // hoặc đã đăng nhập
        if (path.contains("login")
                || loggedIn) {

            chain.doFilter(request, response);

        } else {

            res.sendRedirect(
                    req.getContextPath()
                            + "/login.jsp"
            );
        }
    }
}