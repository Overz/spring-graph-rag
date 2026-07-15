package com.github.overz.rag.internal.repositories;

import com.github.overz.rag.internal.models.DocumentStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentStatusHistoryRepository extends JpaRepository<DocumentStatusHistoryEntity, Long> {
}
