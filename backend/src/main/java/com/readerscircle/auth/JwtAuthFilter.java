package com.readerscircle.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless JWT authentication. Sets {@code ROLE_<role>} from the access token's claims.
 * Refresh tokens carry no role claim and are therefore rejected here by design.
 * Invalid/expired tokens clear the context and fall through to the 401 entry point —
 * never to an authenticated session.
 *
 * <p>Blocked users ({@code active=false}) are rejected on every request even when the
 * token itself is still valid — this terminates their active sessions immediately.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwt;
  private final UserRepository users;

  public JwtAuthFilter(JwtService jwt, UserRepository users) {
    this.jwt = jwt;
    this.users = users;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null
        && header.startsWith("Bearer ")
        && SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        Claims claims = jwt.parse(header.substring(7)).getPayload();
        UUID userId = UUID.fromString(claims.getSubject());
        Role role = Role.valueOf(claims.get("role", String.class));
        boolean active =
            users.findById(userId).map(User::isActive).orElse(false);
        if (!active) {
          SecurityContextHolder.clearContext();
        } else {
          var auth =
              new UsernamePasswordAuthenticationToken(
                  userId.toString(),
                  null,
                  List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
          auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      } catch (JwtException | IllegalArgumentException e) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
