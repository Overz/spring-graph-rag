package com.github.overz.rag.internal.repositories;

import com.github.overz.rag.internal.models.DocumentStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentStatusHistoryRepository extends JpaRepository<DocumentStatusHistoryEntity, Long> {

  /** Histórico completo em ordem cronológica (RF09). */
  List<DocumentStatusHistoryEntity> findByDocumentIdOrderByOccurredAtAsc(UUID documentId);

}
