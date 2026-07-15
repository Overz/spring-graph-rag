package com.github.overz.shared.storage;

import java.io.InputStream;

/**
 * Porta de armazenamento de artefatos de documento (ADR-001): a implementação é detalhe
 * do adaptador (JuiceFS/POSIX hoje, S3 real amanhã) — os módulos só conhecem a chave
 * lógica devolvida. Neste épico apenas o estágio {@link StorageStage#RAW} é usado.
 */
public interface DocumentStorage {

  /**
   * Grava o conteúdo no endereço lógico e devolve a chave persistível
   * ({@link StorageLocation#key()}). A escrita é atômica: ou o artefato inteiro
   * existe na chave, ou nada existe.
   */
  String store(StorageLocation location, InputStream content);

}
