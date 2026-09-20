package com.readerscircle.identity;

import java.util.UUID;

public record AuthResponse(String accessToken, String refreshToken, UUID userId, String role) {}
