package com.readerscircle.identity;

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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

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
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      try {
        var claims = jwt.parse(token);
        if ("access".equals(claims.get("type", String.class))) {
          UUID userId = UUID.fromString(claims.getSubject());
          var user = users.findById(userId).orElse(null);
          if (user == null || user.isDeactivated()) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"invalid credentials\"}");
            return;
          }
          Role role = user.getRole();
          var auth =
              new UsernamePasswordAuthenticationToken(
                  new AuthUser(userId, role),
                  null,
                  List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      } catch (JwtException | IllegalArgumentException e) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(req, res);
  }
}
