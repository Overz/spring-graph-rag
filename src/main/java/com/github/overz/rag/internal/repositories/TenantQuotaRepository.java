package com.github.overz.rag.internal.repositories;

import com.github.overz.rag.internal.models.TenantQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantQuotaRepository extends JpaRepository<TenantQuotaEntity, String> {
}
