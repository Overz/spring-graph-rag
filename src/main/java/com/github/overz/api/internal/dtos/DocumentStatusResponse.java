package com.github.overz.api.internal.dtos;

import lombok.Builder;

import java.io.Serializable;

/** Corpo de {@code GET /api/v1/documents/{id}/status} (RF09). */
@Builder
public record DocumentStatusResponse(String status) implements Serializable {
}
