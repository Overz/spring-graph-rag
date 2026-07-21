package com.github.overz.rag.internal.repositories;

/**
 * Índice de chunks no OpenSearch (`docs/sdd/dados.md` §3). Épico 2 só precisa da
 * inativação em massa por documento (RF10) — indexação real do conteúdo chega no
 * Épico 5/6, quando o pipeline de chunking/embedding existir de fato.
 */
public interface ChunkIndex {

  void inactivateByDocumentId(String documentId);

}
