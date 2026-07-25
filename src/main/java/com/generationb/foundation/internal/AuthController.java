package com.generationb.foundation.internal;

import com.generationb.foundation.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody Map<String, String> request) {
        String email = request.getOrDefault("email", "user@agency.com");
        String role = request.getOrDefault("role", "ADMIN");
        UUID brandId = UUID.fromString(request.getOrDefault("brandId", "11111111-1111-1111-1111-111111111111"));
        UUID userId = UUID.fromString(request.getOrDefault("userId", "22222222-2222-2222-2222-222222222222"));

        String token = jwtUtil.generateToken(email, brandId, userId, role);
        return ApiResponse.of(Map.of("token", token));
    }
}
