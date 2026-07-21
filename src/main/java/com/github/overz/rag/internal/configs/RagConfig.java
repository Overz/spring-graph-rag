package com.github.overz.rag.internal.configs;

import com.github.overz.rag.DocumentCommandApi;
import com.github.overz.rag.internal.repositories.ChunkIndex;
import com.github.overz.rag.internal.repositories.DocumentGraphRepository;
import com.github.overz.rag.internal.repositories.DocumentRepository;
import com.github.overz.rag.internal.repositories.DocumentStatusHistoryRepository;
import com.github.overz.rag.internal.repositories.EntityGraphRepository;
import com.github.overz.rag.internal.repositories.Neo4jDocumentGraphRepository;
import com.github.overz.rag.internal.repositories.Neo4jEntityGraphRepository;
import com.github.overz.rag.internal.repositories.OpenSearchChunkIndex;
import com.github.overz.rag.internal.repositories.ProcessingErrorRepository;
import com.github.overz.rag.internal.repositories.TenantQuotaRepository;
import com.github.overz.rag.internal.services.DocumentLifecycleService;
import com.github.overz.rag.internal.services.EntityGarbageCollectionJob;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * Configuração central dos beans do módulo {@code rag}: os services não levam
 * estereótipo — o wiring inteiro do módulo é visível aqui. Os repositórios JPA
 * (Postgres) são interfaces Spring Data, registradas pela autoconfiguração; os
 * repositórios de grafo (Neo4j) são classes escritas à mão sobre {@link Neo4jClient}
 * (ver Javadoc de {@code Neo4jDocumentGraphRepository}/{@code Neo4jEntityGraphRepository}).
 */
@Configuration
@EnableScheduling
class RagConfig {

  @Bean
  DocumentCommandApi documentCommandApi(
    final DocumentRepository documents,
    final DocumentStatusHistoryRepository history,
    final TenantQuotaRepository quotas,
    final ProcessingErrorRepository processingErrors,
    final DocumentGraphRepository documentGraph,
    final ChunkIndex chunkIndex
  ) {
    return new DocumentLifecycleService(documents, history, quotas, processingErrors, documentGraph, chunkIndex);
  }

  /**
   * Schema Neo4j mínimo pro ciclo de vida (RF10/RF11, `docs/sdd/dados.md` §4, ADR
   * pendente): {@code CREATE ... IF NOT EXISTS} é idempotente, roda a cada start sem
   * efeito depois da primeira vez. Escopo reduzido do desenho completo do SDD — a
   * constraint de existência de {@code openSearchId} (RF23) e o índice vetorial de
   * entidade (RF32) exigem recursos de Enterprise/Épico 6 e ficam pra quando esse épico
   * de fato criar esses campos; aqui só o que RF10/RF11 usam de verdade.
   */
  @Bean
  ApplicationRunner knowledgeGraphSchemaInitializer(final Neo4jClient neo4jClient) {
    return args -> List.of(
      "CREATE CONSTRAINT document_id IF NOT EXISTS FOR (d:Document) REQUIRE d.id IS UNIQUE",
      "CREATE CONSTRAINT chunk_id IF NOT EXISTS FOR (c:Chunk) REQUIRE c.id IS UNIQUE",
      "CREATE CONSTRAINT entity_id IF NOT EXISTS FOR (e:Entity) REQUIRE e.id IS UNIQUE",
      "CREATE INDEX chunk_tenant_active IF NOT EXISTS FOR (c:Chunk) ON (c.tenantId, c.isActive)"
    ).forEach(statement -> neo4jClient.query(statement).run());
  }

  @Bean
  ChunkIndex chunkIndex(final OpenSearchClient openSearchClient) {
    return new OpenSearchChunkIndex(openSearchClient);
  }

  @Bean
  DocumentGraphRepository documentGraphRepository(final Neo4jClient neo4jClient) {
    return new Neo4jDocumentGraphRepository(neo4jClient);
  }

  @Bean
  EntityGraphRepository entityGraphRepository(final Neo4jClient neo4jClient) {
    return new Neo4jEntityGraphRepository(neo4jClient);
  }

  @Bean
  EntityGarbageCollectionJob entityGarbageCollectionJob(final EntityGraphRepository entityGraph) {
    return new EntityGarbageCollectionJob(entityGraph);
  }

  /**
   * Índice OpenSearch mínimo pro ciclo de vida (RF10, `docs/sdd/dados.md` §3): mapeamento
   * completo já especificado pelo SDD (inclui {@code embedding} knn_vector) — só o
   * conteúdo real chega no Épico 5/6, o índice em si já pode existir hoje. Idempotente:
   * cria só se {@code chunks-v1} ainda não existir.
   */
  @Bean
  ApplicationRunner chunkIndexSchemaInitializer(final OpenSearchClient openSearchClient) {
    return args -> {
      final var exists = openSearchClient.indices()
        .exists(new ExistsRequest.Builder().index(OpenSearchChunkIndex.INDEX_NAME).build())
        .value();
      if (exists) {
        return;
      }
      openSearchClient.indices().create(new CreateIndexRequest.Builder()
        .index(OpenSearchChunkIndex.INDEX_NAME)
        .aliases(OpenSearchChunkIndex.ALIAS, a -> a)
        .settings(s -> s.knn(true))
        .mappings(chunkIndexMapping())
        .build());
    };
  }

  private TypeMapping chunkIndexMapping() {
    return new TypeMapping.Builder()
      .properties("content", p -> p.text(t -> t))
      .properties("embedding", p -> p.knnVector(k -> k
        .dimension(768)
        .method(m -> m.name("hnsw").spaceType("cosinesimil").engine("lucene"))))
      .properties("chunkId", p -> p.keyword(k -> k))
      .properties("parentChunkId", p -> p.keyword(k -> k))
      .properties("documentId", p -> p.keyword(k -> k))
      .properties("ownerId", p -> p.keyword(k -> k))
      .properties("tenantId", p -> p.keyword(k -> k))
      .properties("isActive", p -> p.boolean_(b -> b))
      .properties("version", p -> p.integer(i -> i))
      .build();
  }

}
