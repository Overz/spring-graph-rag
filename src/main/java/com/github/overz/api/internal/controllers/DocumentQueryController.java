package com.github.overz.api.internal.controllers;

import com.github.overz.api.internal.dtos.DocumentSummaryResponse;
import com.github.overz.api.internal.dtos.PagedResponse;
import com.github.overz.api.internal.mappers.DocumentQueryResponseMapper;
import com.github.overz.rag.DocumentCommandApi;
import com.github.overz.shared.security.CallerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF40 — listagem paginada de documentos do tenant: visão compartilhada entre todos os
 * usuários autenticados do tenant (não restrita ao dono do documento).
 */
@RestController
@RequiredArgsConstructor
public class DocumentQueryController {

  private final DocumentCommandApi documents;

  @GetMapping(path = { "/api/v1/documents" })
  PagedResponse<DocumentSummaryResponse> list(
    @RequestParam(name = "includeInactive", defaultValue = "false") final boolean includeInactive,
    @PageableDefault(size = 20, sort = "uploadedAt", direction = Sort.Direction.DESC) final Pageable pageable,
    final CallerContext caller
  ) {
    return DocumentQueryResponseMapper.toResponse(
      documents.listDocuments(caller.tenantId(), includeInactive, pageable));
  }

}
