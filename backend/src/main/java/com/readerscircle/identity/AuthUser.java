package com.readerscircle.identity;

import java.util.UUID;

public record AuthUser(UUID userId, Role role) {}
