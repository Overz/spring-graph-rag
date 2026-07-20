package com.github.overz.api.internal.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.io.Serializable;

/**
 * Sem {@code @Jacksonized}: Jackson desserializa records via construtor canônico sem
 * precisar do builder — e {@code @Jacksonized} não compilaria aqui (Jackson 3 é
 * compile-scope neste projeto, a anotação de builder do Jackson clássico não é).
 */
@Builder
public record LoginRequest(
  @NotBlank String username,
  @NotBlank String password
) implements Serializable {
}
