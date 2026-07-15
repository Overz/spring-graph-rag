/**
 * Módulo {@code rag} — pipeline completa de Retrieval-Augmented Generation.
 *
 * <p>Fronteiras públicas deste módulo:
 * <ul>
 *   <li>Ingestão e processamento de documentos (PDF, texto, etc.)</li>
 *   <li>Geração de embeddings e indexação no vector store (OpenSearch)</li>
 *   <li>Recuperação semântica de contexto relevante para queries</li>
 * </ul>
 *
 * <p>Dependências permitidas: a interface nomeada do {@code shared} de fato usada
 * ({@code logging}). Como {@code shared} declara {@code @NamedInterface} em seus
 * subpacotes, a dependência unqualified {@code "shared"} não basta.
 * Comunicação com outros módulos exclusivamente via eventos Spring ou APIs públicas.
 */
@ApplicationModule(
    displayName = "RAG Pipeline",
    allowedDependencies = { "shared::logging" }
)
package com.github.overz.rag;

import org.springframework.modulith.ApplicationModule;
