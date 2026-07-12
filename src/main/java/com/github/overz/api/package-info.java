/**
 * Módulo {@code api} — endpoints HTTP para ingestão de dados externos.
 *
 * <p>Fronteiras públicas deste módulo:
 * <ul>
 *   <li>Recebe arquivos e dados estruturados via REST</li>
 *   <li>Valida e persiste os dados recebidos</li>
 *   <li>Publica eventos para que outros módulos reajam (ex: rag indexa o novo documento)</li>
 * </ul>
 *
 * <p>Dependências permitidas: {@code shared} (implícito).
 * Comunicação com outros módulos exclusivamente via eventos Spring ou APIs públicas.
 */
@ApplicationModule(
    displayName = "API",
    allowedDependencies = { "shared" }
)
package com.github.overz.api;

import org.springframework.modulith.ApplicationModule;
