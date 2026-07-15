package com.github.overz.shared.internal;

import com.github.overz.shared.storage.StorageLocation;
import com.github.overz.shared.storage.StorageStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemDocumentStorageTest {

  @TempDir
  Path baseDir;

  @Test
  void gravaNaChaveSegregadaComConteudoIdentico() throws Exception {
    final var storage = new FileSystemDocumentStorage(baseDir.toString());
    final var conteudo = "conteúdo do contrato".getBytes();
    final var location = new StorageLocation(StorageStage.RAW, "acme_inc", "user-1", "doc-123", "contrato.pdf");

    final var key = storage.store(location, new ByteArrayInputStream(conteudo));

    assertThat(key).isEqualTo("/acme_inc/user-1/raw/doc-123/contrato.pdf");
    final var gravado = baseDir.resolve("acme_inc/user-1/raw/doc-123/contrato.pdf");
    assertThat(Files.readAllBytes(gravado)).isEqualTo(conteudo);
  }

  @Test
  void rejeitaSegmentoComComponenteDeCaminho() {
    assertThatThrownBy(() ->
      new StorageLocation(StorageStage.RAW, "acme_inc", "user-1", "doc-1", "../escape.pdf"))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
      new StorageLocation(StorageStage.RAW, "acme/inc", "user-1", "doc-1", "ok.pdf"))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
      new StorageLocation(StorageStage.RAW, "acme_inc", "", "doc-1", "ok.pdf"))
      .isInstanceOf(IllegalArgumentException.class);
  }

}
