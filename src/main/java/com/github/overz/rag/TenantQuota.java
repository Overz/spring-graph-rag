package com.github.overz.rag;

import lombok.Builder;

import java.io.Serializable;

/**
 * Cota configurada de um tenant (RF03 complemento). Ausência de cota = sem limite
 * (decisão D4 do change epico-1) — por isso a consulta devolve {@code Optional} vazio,
 * não uma cota "infinita" sintética.
 */
@Builder
public record TenantQuota(long maxStorageBytes, int maxActiveFiles) implements Serializable {
}
