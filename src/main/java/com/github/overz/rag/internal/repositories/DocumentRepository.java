package com.github.overz.rag.internal.repositories;

import com.github.overz.rag.DocumentStatus;
import com.github.overz.rag.internal.models.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

  /**
   * Duplicidade (RF07/D7): qualquer documento ATIVO do mesmo tenant+owner com o hash cujo
   * status não seja {@code FAILED} bloqueia o reenvio — um documento já excluído
   * logicamente (RF10) não conta como duplicata: o conteúdo não está mais "presente" pro
   * dono, então reenviá-lo é um upload novo e legítimo, não um reenvio redundante.
   */
  boolean existsByTenantIdAndOwnerIdAndFileHashSha256AndStatusNotAndActiveTrue(
    String tenantId, String ownerId, String fileHashSha256, DocumentStatus status);

  /**
   * Uso de armazenamento do tenant, derivado dos documentos ativos (RF03 complemento).
   */
  @Query("select coalesce(sum(d.fileSizeBytes), 0) from DocumentEntity d "
    + "where d.tenantId = :tenantId and d.active = true")
  long sumActiveStorageBytes(@Param("tenantId") String tenantId);

  long countByTenantIdAndActiveTrue(String tenantId);

  /**
   * Maior versão já registrada para este conteúdo (0 quando inédito): reenvio legítimo
   * após {@code FAILED} entra como versão seguinte — a linha falhada permanece para
   * histórico/erros e a UNIQUE {@code (tenant, owner, hash, version)} segue satisfeita.
   */
  @Query("select coalesce(max(d.version), 0) from DocumentEntity d "
    + "where d.tenantId = :tenantId and d.ownerId = :ownerId and d.fileHashSha256 = :sha256")
  int maxVersionOf(@Param("tenantId") String tenantId, @Param("ownerId") String ownerId,
                   @Param("sha256") String sha256);

}
