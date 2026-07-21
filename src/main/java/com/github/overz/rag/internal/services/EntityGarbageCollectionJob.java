package com.github.overz.rag.internal.services;

import com.github.overz.rag.internal.repositories.EntityGraphRepository;
import com.github.overz.shared.logging.ILogger;
import com.github.overz.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * RF11: expurgo físico periódico de entidades órfãs (sem aresta {@code MENTIONS} para um
 * {@code Chunk} ativo) — hard delete, ao contrário da exclusão lógica síncrona do RF10.
 * Intervalo configurável em {@code app.gc.interval-ms}. Registrado como bean em
 * {@code RagConfig} — sem estereótipo de classe; os steps BDD chamam
 * {@link #expurgeOrphanEntities()} diretamente, sem esperar o agendamento real.
 */
@RequiredArgsConstructor
public class EntityGarbageCollectionJob {

  private static final ILogger log = LoggerFactory.of(EntityGarbageCollectionJob.class);

  private final EntityGraphRepository entityGraph;

  @Scheduled(fixedDelayString = "${app.gc.interval-ms}")
  public void expurgeOrphanEntities() {
    final var orphanIds = entityGraph.findOrphanEntities();
    if (orphanIds.isEmpty()) {
      return;
    }
    entityGraph.deleteEntities(orphanIds);
    log.info("Garbage collection: entidades órfãs removidas fisicamente: count={}", orphanIds.size());
  }

}
