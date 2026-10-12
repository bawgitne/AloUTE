package com.aloute.security.config;

import com.aloute.security.handler.AccessDeniedHandler;
import com.aloute.security.handler.AuthenticationFailureHandler;
import com.aloute.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean DaoAuthenticationProvider authenticationProvider(UserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider p=new DaoAuthenticationProvider(); p.setUserDetailsService(uds); p.setPasswordEncoder(encoder); return p;
    }

    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception { return c.getAuthenticationManager(); }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
            CsrfCookieFilter csrfCookieFilter, AuthenticationFailureHandler authFailure,
            AccessDeniedHandler accessDenied) throws Exception {
        CookieCsrfTokenRepository csrf=CookieCsrfTokenRepository.withHttpOnlyFalse(); csrf.setCookiePath("/");
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);
        RequestMatcher bearerRequest = request -> {
            String value = request.getHeader(HttpHeaders.AUTHORIZATION);
            return value != null && value.regionMatches(true, 0, "Bearer ", 0, 7);
        };
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers(bearerRequest)
                        .ignoringRequestMatchers(SecurityWhitelist.PUBLIC_POST))
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .exceptionHandling(e -> e.authenticationEntryPoint(authFailure).accessDeniedHandler(accessDenied))
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.FORWARD, jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, SecurityWhitelist.PUBLIC_POST).permitAll()
                        .requestMatchers(HttpMethod.GET, SecurityWhitelist.PUBLIC_GET).permitAll()
                        .requestMatchers(SecurityWhitelist.PUBLIC_WEB).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrfCookieFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
