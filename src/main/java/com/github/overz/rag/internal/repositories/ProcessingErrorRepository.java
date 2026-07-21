package com.github.overz.rag.internal.repositories;

import com.github.overz.rag.internal.models.ProcessingErrorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingErrorRepository extends JpaRepository<ProcessingErrorEntity, Long> {
}
