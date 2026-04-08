package com.smartqueue.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CORRELATION_ID_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Generate a correlation ID to trace this specific web request across all
        // internal logs
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(CORRELATION_ID_KEY, correlationId);
        response.setHeader("X-Correlation-Id", correlationId);

        try {
            authenticateRequest(request);
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL DEFENSIVE CHECK: Tomcat utilizes thread-pools. If doFilter throws an
            // exception and we
            // don't manually clear the MDC in a finally block, the next completely
            // unrelated request hitting
            // this same thread will inherit the old correlation ID, making our tracing logs
            // completely useless.
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    private void authenticateRequest(HttpServletRequest request) {
        try {
            String jwt = extractJwtFromRequest(request);

            if (!StringUtils.hasText(jwt) || !jwtTokenProvider.validateToken(jwt)) {
                // Anonymous request or invalid token. Simply return and let the downstream
                // Spring Security config strictly decide if the specific endpoint requires auth
                // or not.
                return;
            }

            String email = jwtTokenProvider.getEmailFromToken(jwt);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities());

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (UsernameNotFoundException ex) {
            // Edge case: Token is cryptographically valid, but the user was hard-deleted
            // from the database mid-session
            log.warn("Valid JWT received but associated user account [{}] no longer exists in database",
                    ex.getMessage());
            SecurityContextHolder.clearContext();
        } catch (Exception ex) {
            // Downgrade from ERROR to DEBUG. Hackers or bots spamming malformed tokens
            // shouldn't
            // wake up the on-call engineer with Sev-1 ERROR alerts in Datadog/Sentry.
            log.debug("Authentication context resolution failed. Token may be malformed or expired. Reason: {}",
                    ex.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null; // Signals this isn't a Bearer Token auth attempt
    }
}
