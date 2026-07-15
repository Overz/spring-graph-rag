package com.github.overz.shared.storage;

/**
 * Estágios de artefato de documento no Object Storage (ADR-001, com adendo):
 * o original cru, o extraído intermediário (RF27) e o Markdown transformado (RF17).
 */
public enum StorageStage {

  RAW("raw"),
  EXTRACTED("extracted"),
  TRANSFORMED("transformed");

  private final String directory;

  StorageStage(final String directory) {
    this.directory = directory;
  }

  public String directory() {
    return directory;
  }

}
