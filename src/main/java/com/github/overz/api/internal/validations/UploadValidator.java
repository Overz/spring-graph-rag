package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.UploadRejectedException;

/**
 * Um elo da cadeia de validação de upload (RF02, SDD ingestao §2): ordem do mais barato
 * ao mais caro, explícita na lista montada em {@code UploadConfig}; a primeira falha
 * interrompe a cadeia lançando a {@link UploadRejectedException} correspondente.
 */
public interface UploadValidator {

  void validate(UploadCandidate candidate);

}
