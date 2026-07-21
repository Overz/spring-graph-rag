package com.github.overz.rag.internal.services;

import com.github.overz.rag.internal.repositories.EntityGraphRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class EntityGarbageCollectionJobTest extends Assertions {

  @Test
  void naoChamaExclusaoQuandoNaoHaEntidadesOrfas() {
    final var chamouExclusao = new AtomicBoolean(false);
    final var job = new EntityGarbageCollectionJob(entityGraphRepository(List.of(), chamouExclusao));

    job.expurgeOrphanEntities();

    assertFalse(chamouExclusao.get());
  }

  @Test
  void excluiFisicamenteAsEntidadesOrfasEncontradas() {
    final var chamouExclusao = new AtomicBoolean(false);
    final var job = new EntityGarbageCollectionJob(entityGraphRepository(List.of("entity-1"), chamouExclusao));

    job.expurgeOrphanEntities();

    assertTrue(chamouExclusao.get());
  }

  private EntityGraphRepository entityGraphRepository(final List<String> orphanIds, final AtomicBoolean chamouExclusao) {
    return new EntityGraphRepository() {
      @Override
      public List<String> findOrphanEntities() {
        return orphanIds;
      }

      @Override
      public void deleteEntities(final List<String> entityIds) {
        assertEquals(orphanIds, entityIds);
        chamouExclusao.set(true);
      }
    };
  }

}
