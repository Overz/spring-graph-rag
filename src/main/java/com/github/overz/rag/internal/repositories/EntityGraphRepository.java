package com.github.overz.rag.internal.repositories;

import java.util.List;

public interface EntityGraphRepository {

  /**
   * Garbage collection (RF11): entidades sem nenhuma aresta {@code MENTIONS} conectada a
   * um {@code Chunk} {@code isActive=true} — candidatas a remoção física.
   */
  List<String> findOrphanEntities();

  /**
   * Hard delete (RF11): remove os nós e qualquer relacionamento deles ({@code DETACH
   * DELETE}) — cobre entidades conectadas entre si (ex. {@code DEPENDS_ON} entre duas
   * órfãs), removendo o relacionamento junto.
   */
  void deleteEntities(List<String> entityIds);

}
