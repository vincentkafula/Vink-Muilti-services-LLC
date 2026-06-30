package com.vink.backend.model;

/** Mirrors server/src/types/mvno.ts -> AuthPayload */
public record AuthPayload(String userId, String email, String role) {
}
