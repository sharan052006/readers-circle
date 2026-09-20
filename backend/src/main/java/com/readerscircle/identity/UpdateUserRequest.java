package com.readerscircle.identity;

public record UpdateUserRequest(String role, Boolean deactivated, String name) {}
