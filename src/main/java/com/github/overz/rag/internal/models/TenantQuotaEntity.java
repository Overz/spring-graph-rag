package com.github.overz.rag.internal.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Linha de {@code tenant_quotas} (RF03 complemento). Sem linha = sem limite (D4).
 */
@Entity
@Table(name = "tenant_quotas")
@Getter
@Setter
@NoArgsConstructor
public class TenantQuotaEntity {

  @Id
  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "max_storage_bytes", nullable = false)
  private long maxStorageBytes;

  @Column(name = "max_active_files", nullable = false)
  private int maxActiveFiles;

}
