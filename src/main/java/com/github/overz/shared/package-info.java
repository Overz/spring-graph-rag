/**
 * Módulo {@code shared} — biblioteca compartilhada entre todos os módulos.
 *
 * <p>Este módulo é declarado como compartilhado via {@code @Modulithic(sharedModules = "shared")}
 * em {@link com.github.overz.Application}, o que significa que todos os outros módulos
 * podem depender dele sem declaração explícita em {@code allowedDependencies}.
 *
 * <p>Conteúdo típico deste módulo:
 * <ul>
 *   <li>Value Objects e tipos primitivos de domínio</li>
 *   <li>Interfaces e contratos compartilhados</li>
 *   <li>Eventos de domínio publicados entre módulos</li>
 *   <li>Utilitários e helpers transversais</li>
 *   <li>Configurações de infraestrutura compartilhada</li>
 * </ul>
 *
 * <p><strong>Atenção:</strong> evite colocar lógica de negócio aqui.
 * Este módulo deve conter apenas tipos e contratos sem comportamento de domínio específico.
 */
@ApplicationModule(displayName = "Shared Code")
package com.github.overz.shared;

import org.springframework.modulith.ApplicationModule;
