package com.github.overz.api.internal.configs;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;
import java.util.Optional;

public enum FileType {
  PDF,
  JPG,
  JPEG,
  PNG,
  CSV,
  JSON,
  XML,
  TXT,
  MD,
  ;

  /** Config binding (app.upload.accepted-types) — chave inválida no application.yaml deve falhar rápido. */
  @JsonCreator
  public static FileType fromString(String type) {
    return find(type).orElseThrow(() -> new EnumConstantNotPresentException(FileType.class, type));
  }

  /** Entrada de usuário (extensão de arquivo enviado) — pode não corresponder a nenhum tipo conhecido. */
  public static Optional<FileType> find(String type) {
    return Arrays.stream(values()).filter(v -> v.name().equalsIgnoreCase(type)).findFirst();
  }
}
