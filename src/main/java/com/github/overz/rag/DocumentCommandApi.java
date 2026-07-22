package com.github.overz.rag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

  /**
   * Status atual do documento (RF09). Leitura é compartilhada entre todo o tenant — só o
   * tenant precisa bater; documento inexistente ou de outro tenant SHALL responder vazio
   * por igual, {@code api} mapeia pra 404 limpo sem distinguir o motivo real da negativa.
   * Documento excluído logicamente também responde vazio (RF10): "status atual" não se
   * aplica a algo que não existe mais.
   */
  Optional<DocumentStatus> statusOf(UUID documentId, String tenantId);

  /**
   * Histórico completo de transições do documento, em ordem cronológica (RF09) — mesma
   * regra de visibilidade tenant-wide de {@link #statusOf}, exceto que sobrevive à
   * exclusão lógica (auditoria, RF31): documento excluído continua respondendo o
   * histórico completo, incluindo o próprio evento de exclusão.
   */
  Optional<List<DocumentHistoryEntry>> historyOf(UUID documentId, String tenantId);

  /**
   * Listagem paginada dos documentos do tenant (RF40) — visão compartilhada entre todos
   * os usuários do tenant; {@code includeInactive=false} (padrão) restringe aos
   * documentos ainda ativos.
   */
  Page<DocumentSummary> listDocuments(String tenantId, boolean includeInactive, Pageable pageable);

  /**
   * Exclusão lógica com isolamento de grafo (RF10): {@code is_active=false} no Postgres,
   * no nó {@code Document}/seus {@code Chunk}s (Neo4j) e nos vetores correspondentes
   * (OpenSearch) — síncrono (design.md D2). Ao contrário de {@link #statusOf}, aqui a
   * negativa por dono diferente é explícita (RF30): {@code FORBIDDEN} quando o tenant
   * bate mas o dono não, {@code NOT_FOUND} quando o documento não existe ou é de outro
   * tenant.
   */
  DocumentCommandOutcome deleteDocument(UUID documentId, String tenantId, String ownerId);

  /**
   * Substituição de versão (RF10 complemento): a versão anterior segue o fluxo de
   * exclusão lógica de {@link #deleteDocument} e a nova é registrada como
   * {@code version+1}, reiniciando em {@code UPLOADED} — mesma regra de
   * visibilidade/permissão de {@link #deleteDocument}, verificada contra o tenant/dono do
   * {@code newUpload}.
   */
  VersionReplacementResult replaceVersion(UUID previousDocumentId, AcceptedUpload newUpload);

}
