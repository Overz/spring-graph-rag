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
 * <p>Dependências permitidas: {@code shared} (implícito).
 * Comunicação com outros módulos exclusivamente via eventos Spring ou APIs públicas.
 */
@ApplicationModule(
    displayName = "RAG Pipeline",
    allowedDependencies = {}
)
package com.github.overz.rag;

import org.springframework.modulith.ApplicationModule;
