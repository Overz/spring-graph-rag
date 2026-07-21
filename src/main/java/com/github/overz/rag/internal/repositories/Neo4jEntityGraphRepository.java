package com.github.overz.rag.internal.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;
import java.util.Map;

/**
 * Adaptador de {@link EntityGraphRepository} via {@link Neo4jClient} direto — mesmo
 * motivo de {@link Neo4jDocumentGraphRepository}: {@code Neo4jRepository} com
 * {@code @Query} custom se mostrou instável neste ambiente (NPE em
 * {@code Neo4jTemplate$DefaultExecutableQuery} para queries com subquery {@code EXISTS}).
 */
@RequiredArgsConstructor
public class Neo4jEntityGraphRepository implements EntityGraphRepository {

  private final Neo4jClient neo4jClient;

  @Override
  public List<String> findOrphanEntities() {
    return neo4jClient.query("""
        MATCH (e:Entity)
        WHERE NOT EXISTS { MATCH (e)<-[:MENTIONS]-(c:Chunk) WHERE c.isActive = true }
        RETURN e.id AS id
        """)
      .fetchAs(String.class)
      .mappedBy((typeSystem, record) -> record.get("id").asString())
      .all()
      .stream().toList();
  }

  @Override
  public void deleteEntities(final List<String> entityIds) {
    neo4jClient.query("MATCH (e:Entity) WHERE e.id IN $entityIds DETACH DELETE e")
      .bindAll(Map.of("entityIds", entityIds))
      .run();
  }

}
