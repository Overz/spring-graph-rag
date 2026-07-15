package com.github.overz.shared.storage;

import lombok.Builder;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Endereço lógico de um artefato no storage (RF05): centraliza o formato da chave
 * {@code /{tenantId}/{ownerId}/{estágio}/{fileId}/{filename}} para que nenhum chamador
 * monte caminho na mão. A chave é o que o Postgres persiste — nunca caminho de
 * filesystem cru (a tradução para filesystem é do adaptador).
 *
 * @param stage    estágio do artefato ({@link StorageStage})
 * @param tenantId tenant dono da partição (RF30)
 * @param ownerId  usuário dono ({@code sub} do JWT)
 * @param fileId   identificador do documento
 * @param filename nome do arquivo dentro da partição
 */
@Builder
public record StorageLocation(
  StorageStage stage,
  String tenantId,
  String ownerId,
  String fileId,
  String filename
) implements Serializable {

  public StorageLocation {
    Objects.requireNonNull(stage, "stage");
    requireSafeSegment(tenantId, "tenantId");
    requireSafeSegment(ownerId, "ownerId");
    requireSafeSegment(fileId, "fileId");
    requireSafeSegment(filename, "filename");
  }

  /**
   * Chave lógica do artefato — o valor persistido em {@code raw_storage_key} etc.
   */
  public String key() {
    return "/%s/%s/%s/%s/%s".formatted(tenantId, ownerId, stage.directory(), fileId, filename);
  }

  /**
   * Segmentos da chave na ordem do caminho, para adaptadores baseados em diretório.
   */
  public List<String> segments() {
    return List.of(tenantId, ownerId, stage.directory(), fileId, filename);
  }

  private static void requireSafeSegment(final String value, final String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Segmento de chave de storage vazio: " + name);
    }
    if (value.contains("/") || value.contains("\\") || value.contains("..")) {
      throw new IllegalArgumentException(
        "Segmento de chave de storage com componente de caminho inválido: " + name);
    }
  }

}
