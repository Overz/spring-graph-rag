package com.github.overz.rag.internal.repositories;

public interface DocumentGraphRepository {

  /**
   * Exclusão lógica com isolamento de grafo (RF10): marca o {@code Document} e todos os
   * seus {@code Chunk}s como {@code isActive=false}. Documento sem representação no Neo4j
   * ainda (Épico 5/6 não populou) é um no-op silencioso — nada a marcar.
   */
  void markInactive(String documentId);

}
