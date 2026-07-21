package com.github.overz.rag.internal.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.Map;

/**
 * Adaptador de {@link DocumentGraphRepository} via {@link Neo4jClient} direto — não via
 * {@code Neo4jRepository} com {@code @Query}: empiricamente, o mecanismo de derivação de
 * query do Spring Data Neo4j 8.1.0 se mostrou instável para Cypher com {@code WITH}/
 * {@code OPTIONAL MATCH} encadeados (grava silenciosamente nada, sem lançar exceção).
 * Mesmo padrão já usado por {@link OpenSearchChunkIndex} sobre o cliente raw.
 */
@RequiredArgsConstructor
public class Neo4jDocumentGraphRepository implements DocumentGraphRepository {

  private final Neo4jClient neo4jClient;

  @Override
  public void markInactive(final String documentId) {
    neo4jClient.query("""
        MATCH (d:Document {id: $documentId})
        SET d.isActive = false
        WITH d
        OPTIONAL MATCH (d)-[:HAS_CHUNK]->(c:Chunk)
        SET c.isActive = false
        """)
      .bindAll(Map.of("documentId", documentId))
      .run();
  }

}
