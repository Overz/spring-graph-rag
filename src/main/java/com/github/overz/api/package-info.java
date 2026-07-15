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
 * <p>Dependências permitidas: as interfaces nomeadas do {@code shared} de fato usadas
 * (errors/logging/security/storage/support) e a API pública do {@code rag}
 * ({@code DocumentCommandApi} — comandos/consultas síncronos, SDD arquitetura). Como
 * {@code shared} declara {@code @NamedInterface} em seus subpacotes, a dependência
 * unqualified {@code "shared"} não basta — o Modulith exige {@code "shared::<nome>"}
 * por interface efetivamente usada.
 * Comunicação com outros módulos exclusivamente via eventos Spring ou APIs públicas.
 */
@ApplicationModule(
    displayName = "API",
    allowedDependencies = {
        "shared::errors", "shared::logging", "shared::security", "shared::storage", "shared::support",
        "rag"
    }
)
package com.github.overz.api;

import org.springframework.modulith.ApplicationModule;
