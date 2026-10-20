package com.aloute.security.jwt;

import com.aloute.security.principal.CustomUserDetailsService;
import com.aloute.security.principal.UserPrincipal;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String COOKIE_NAME="aloute_access_token";
    private final JwtSessionService jwtSessionService;

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String token=readCookie(request); if (token==null) token=readBearer(request);
        if (token!=null && SecurityContextHolder.getContext().getAuthentication()==null) {
            try {
                UserPrincipal p = jwtSessionService.authenticate(token);
                if (p.isEnabled()) {
                    UsernamePasswordAuthenticationToken a=new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities());
                    a.setDetails(new WebAuthenticationDetailsSource().buildDetails(request)); SecurityContextHolder.getContext().setAuthentication(a);
                }
            } catch (org.springframework.security.core.AuthenticationException | IllegalArgumentException ex) { SecurityContextHolder.clearContext(); }
        }
        chain.doFilter(request,response);
    }
    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies=request.getCookies(); if (cookies==null) return null;
        for (Cookie c:cookies) if (COOKIE_NAME.equals(c.getName()) && c.getValue()!=null && !c.getValue().isBlank()) return c.getValue();
        return null;
    }
    private String readBearer(HttpServletRequest request) {
        String h=request.getHeader(HttpHeaders.AUTHORIZATION); if (h==null) return null;
        String prefix="Bearer "; return h.regionMatches(true,0,prefix,0,prefix.length()) && h.length()>prefix.length() ? h.substring(prefix.length()).trim() : null;
    }
}
