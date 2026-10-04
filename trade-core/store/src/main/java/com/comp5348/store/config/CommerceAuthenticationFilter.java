package com.comp5348.store.config;

import com.comp5348.store.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Accepts only Store-issued user tokens. X-User-Id is never an identity source. */
public class CommerceAuthenticationFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final String secret;

    public CommerceAuthenticationFilter(UserRepository users, String secret) {
        this.users = users;
        this.secret = secret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ") || secret.getBytes(StandardCharsets.UTF_8).length < 32)
                    throw new IllegalArgumentException("Invalid credentials");
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                        .requireIssuer("comp5348").build().parseClaimsJws(header.substring(7)).getBody();
                if (claims.getExpiration() == null) throw new IllegalArgumentException("Missing expiration");
                Long id = Long.valueOf(claims.getSubject());
                var user = users.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown user"));
                var authentication = new UsernamePasswordAuthenticationToken(id, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute("authenticatedUserId", id);
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Invalid or expired credentials\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
