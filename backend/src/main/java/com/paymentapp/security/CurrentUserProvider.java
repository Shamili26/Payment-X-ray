package com.paymentapp.security;

import com.paymentapp.entity.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the currently authenticated {@link User} from the security context.
 * The {@code JwtAuthenticationFilter} stores our {@link User} entity as the
 * authentication principal, so we can safely cast it here.
 */
@Component
public class CurrentUserProvider {

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return (User) auth.getPrincipal();
        }
        throw new AccessDeniedException("No authenticated user found in the security context");
    }
}

