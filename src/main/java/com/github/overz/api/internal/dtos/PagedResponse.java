package com.github.overz.api.internal.dtos;

import lombok.Builder;

import java.io.Serializable;
import java.util.List;

/** Envelope de paginação genérico, reusado por qualquer listagem paginada da API. */
@Builder
public record PagedResponse<T>(
  List<T> content,
  int page,
  int size,
  long totalElements,
  int totalPages
) implements Serializable {
}
