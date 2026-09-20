package com.readerscircle.auth;

/** Platform roles (SPEC-auth-identity.md). Registration always yields READER. */
public enum Role {
  ADMIN,
  ORGANIZER,
  READER
}
