package com.github.overz.rag.internal.repositories;

import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.UpdateByQueryRequest;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Adaptador do {@link ChunkIndex} sobre o índice {@code chunks-v1} (alias {@code chunks},
 * `docs/sdd/dados.md` §3). {@code rag} não tem acesso a {@code shared::errors}
 * (`package-info.java`), então {@link IOException} do cliente OpenSearch vira
 * {@link UncheckedIOException} na borda do adaptador — não uma exceção de domínio.
 */
@RequiredArgsConstructor
public class OpenSearchChunkIndex implements ChunkIndex {

  public static final String INDEX_NAME = "chunks-v1";
  public static final String ALIAS = "chunks";

  private final OpenSearchClient client;

  @Override
  public void inactivateByDocumentId(final String documentId) {
    try {
      client.updateByQuery(new UpdateByQueryRequest.Builder()
        .index(ALIAS)
        .query(q -> q.term(t -> t.field("documentId").value(FieldValue.of(documentId))))
        .script(s -> s.inline(i -> i.source("ctx._source.isActive = false")))
        .build());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

}
