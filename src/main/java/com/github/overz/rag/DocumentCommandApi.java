package com.github.overz.rag;

import java.util.Optional;

/**
 * API pública do {@code rag} para comandos síncronos de documento — a única porta que o
 * {@code api} enxerga (dependência {@code api → rag} declarada no module descriptor).
 * A persistência de {@code documents}/histórico é do {@code rag} porque toda transição
 * de status pertence ao lifecycle deste módulo (SDD ingestao §6; decisão D5 do change).
 */
public interface DocumentCommandApi {

  /**
   * Persiste os metadados de um upload validado e armazenado (RF06), com o histórico
   * {@code RECEIVED → VALIDATING → UPLOADED} gravado na mesma transação (RF09; decisão
   * D6 — sem eventos até o Épico 3, o documento permanece {@code UPLOADED}).
   */
  RegisteredDocument registerAcceptedUpload(AcceptedUpload upload);

  /**
   * Apoio à validação de duplicidade (RF07): existe documento do mesmo tenant+owner com
   * este hash cujo status não é {@code FAILED}? (Decisão D7 — forma negada cobre tanto o
   * mundo atual, onde {@code UPLOADED} é o estado final alcançável, quanto o futuro com
   * estados terminais reais.)
   */
  boolean successfulDuplicateExists(String tenantId, String ownerId, String sha256);

  /**
   * Cota configurada do tenant (RF03 complemento); vazio = sem limite (D4).
   */
  Optional<TenantQuota> quotaOf(String tenantId);

  /**
   * Uso corrente do tenant, derivado dos documentos ativos (RF03 complemento).
   */
  TenantUsage usageOf(String tenantId);

}
