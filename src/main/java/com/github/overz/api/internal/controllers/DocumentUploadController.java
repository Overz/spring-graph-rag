package com.github.overz.api.internal.controllers;

import com.github.overz.api.internal.dtos.UploadAcceptedResponse;
import com.github.overz.api.internal.mappers.UploadResponseMapper;
import com.github.overz.api.internal.services.DocumentUploadService;
import com.github.overz.shared.security.CallerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * RF01 — {@code POST /api/v1/documents}: recebe o multipart, delega o fluxo do aceite e
 * responde {@code 202}. Identidade só via {@link CallerContext} (claims — RF30); a rota
 * exige a role {@code document:upload} (regra no {@code SecurityConfig}).
 */
@RestController
@RequiredArgsConstructor
public class DocumentUploadController {

  private final DocumentUploadService documentUploadService;

  @PostMapping(path = "/api/v1/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  UploadAcceptedResponse upload(
    @RequestParam("file") final MultipartFile file,
    final CallerContext caller
  ) {
    return UploadResponseMapper.toResponse(documentUploadService.accept(file, caller));
  }

}
