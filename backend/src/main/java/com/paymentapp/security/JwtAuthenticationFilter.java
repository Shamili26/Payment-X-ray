package com.paymentapp.security;

import com.paymentapp.entity.UserSession;
import com.paymentapp.repository.UserSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Name of the httpOnly cookie that carries the JWT. */
    public static final String JWT_COOKIE = "jwt";

    /**
     * Only persist a refreshed {@code lastActivityAt} when it has drifted by at
     * least this many seconds. Avoids a DB write on every single request while
     * still keeping the idle clock accurate to the second for timeout purposes.
     */
    private static final long ACTIVITY_WRITE_THROTTLE_SECONDS = 30;

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserSessionRepository sessionRepository;

    /** Idle (inactivity) timeout in minutes; a session idle longer than this is logged out. */
    @Value("${app.session.idle-timeout-minutes:15}")
    private long idleTimeoutMinutes;

    @Autowired
    public JwtAuthenticationFilter(JwtService jwtService,
                                   @Lazy UserDetailsService userDetailsService,
                                   UserSessionRepository sessionRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.sessionRepository = sessionRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String jwt = resolveToken(request);

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String username = jwtService.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // The signature/expiry must be valid AND a matching session must
                // still be active. This is what makes logout / revocation work:
                // once the session row is deactivated, the token is rejected even
                // though it has not yet expired.
                if (jwtService.isTokenValid(jwt, userDetails) && isSessionActive(jwt)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /** Reads the JWT from the httpOnly cookie, falling back to the Authorization header. */
    private String resolveToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (JWT_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isEmpty()) {
                    return cookie.getValue();
                }
            }
        }
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * True only if a non-revoked, non-expired, non-idle session exists for this
     * token. A session whose last activity is older than the configured idle
     * window is deactivated here (permanent logout, same as revocation) so it
     * cannot be reused; otherwise its {@code lastActivityAt} is refreshed
     * (throttled) to keep the idle clock sliding with genuine activity.
     */
    private boolean isSessionActive(String jwt) {
        Optional<UserSession> sessionOpt = sessionRepository.findByTokenHash(TokenHasher.sha256Hex(jwt));
        if (sessionOpt.isEmpty()) {
            return false;
        }
        UserSession session = sessionOpt.get();

        final LocalDateTime now = LocalDateTime.now();

        // Revoked, or past the absolute (e.g. 24h) cap.
        if (!session.isActive()
                || (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(now))) {
            return false;
        }

        // Idle timeout: last activity older than the configured window.
        LocalDateTime lastActivity = session.getLastActivityAt() != null
                ? session.getLastActivityAt() : session.getCreatedAt();
        if (lastActivity != null && lastActivity.plusMinutes(idleTimeoutMinutes).isBefore(now)) {
            session.setActive(false);
            sessionRepository.save(session);
            log.info("Session for user {} logged out after {} min of inactivity",
                    session.getUser().getUsername(), idleTimeoutMinutes);
            return false;
        }

        // Active request: slide the idle clock forward (throttled write).
        if (lastActivity == null
                || lastActivity.plusSeconds(ACTIVITY_WRITE_THROTTLE_SECONDS).isBefore(now)) {
            session.setLastActivityAt(now);
            sessionRepository.save(session);
        }
        return true;
    }
}