package com.github.overz.rag;

import lombok.Builder;

import java.io.Serializable;

/**
 * Uso corrente de um tenant, derivado dos documentos {@code is_active = true}
 * (sem contador materializado — SDD ingestao §5).
 */
@Builder
public record TenantUsage(long storageBytes, long activeFiles) implements Serializable {
}
