package com.macbackpackers.ronbot;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Shared-secret gate for internal MCP → read-api calls.
 * Health endpoint is left open for docker healthchecks.
 */
@Component
public class RonbotAuthFilter extends OncePerRequestFilter {

    @Value( "${RONBOT_TOKEN:${ronbot.token:}}" )
    private String expectedToken;

    @Override
    protected boolean shouldNotFilter( HttpServletRequest request ) {
        String path = request.getRequestURI();
        return path != null && ( path.endsWith( "/ronbot/health" ) || path.endsWith( "/actuator/health" ) );
    }

    @Override
    protected void doFilterInternal( HttpServletRequest request, HttpServletResponse response, FilterChain filterChain )
            throws ServletException, IOException {
        if ( StringUtils.isBlank( expectedToken ) ) {
            // Dev mode: token not configured — allow (log via warn once would be nicer; keep simple)
            filterChain.doFilter( request, response );
            return;
        }
        String provided = request.getHeader( "X-Ronbot-Token" );
        if ( !expectedToken.equals( provided ) ) {
            response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
            response.setContentType( "application/json" );
            response.getWriter().write( "{\"error\":\"unauthorized\"}" );
            return;
        }
        filterChain.doFilter( request, response );
    }
}
