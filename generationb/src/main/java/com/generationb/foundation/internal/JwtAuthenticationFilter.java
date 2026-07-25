package com.generationb.foundation.internal;

import com.generationb.foundation.BrandContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final BrandContext brandContext;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, BrandContext brandContext) {
        this.jwtUtil = jwtUtil;
        this.brandContext = brandContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                Claims claims = jwtUtil.extractClaims(token);
                String email = claims.getSubject();
                String brandIdStr = claims.get("brand_id", String.class);
                String userIdStr = claims.get("user_id", String.class);
                String role = claims.get("role", String.class);

                if (email != null && brandIdStr != null) {
                    UUID brandId = UUID.fromString(brandIdStr);
                    UUID userId = userIdStr != null ? UUID.fromString(userIdStr) : null;

                    brandContext.setBrandId(brandId);
                    brandContext.setUserId(userId);
                    BrandContext.setCurrentBrandId(brandId);
                    BrandContext.setCurrentUserId(userId);

                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            email, null, Collections.singletonList(authority)
                    );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            BrandContext.clear();
        }
    }
}
