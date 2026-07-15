package com.github.overz.rag;

import lombok.Builder;

import java.io.Serializable;
import java.util.UUID;

/**
 * Comando de registro de um upload que passou por toda a cadeia de validação (RF06):
 * o {@code api} valida e armazena o original; o {@code rag} persiste os metadados e o
 * histórico. O {@code id} é o mesmo usado na chave do storage — gerado antes da gravação.
 *
 * @param id             identificador do documento (também o {@code fileId} da chave RAW)
 * @param tenantId       claim {@code tenantId} do chamador (RF30)
 * @param ownerId        claim {@code sub} do chamador
 * @param filename       nome original do arquivo
 * @param extension      extensão em minúsculas, sem ponto
 * @param contentType    MIME real detectado por conteúdo (Tika)
 * @param fileSizeBytes  tamanho em bytes
 * @param sha256         hash SHA-256 do conteúdo, em hex minúsculo (RF07)
 * @param rawStorageKey  chave devolvida pelo {@code DocumentStorage} (nunca caminho cru)
 * @param correlationId  gerado no ato do upload, propagado por todo o pipeline (RF28)
 */
@Builder
public record AcceptedUpload(
  UUID id,
  String tenantId,
  String ownerId,
  String filename,
  String extension,
  String contentType,
  long fileSizeBytes,
  String sha256,
  String rawStorageKey,
  String correlationId
) implements Serializable {
}
