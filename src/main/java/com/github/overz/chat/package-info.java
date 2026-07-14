/**
 * Módulo {@code chat} — responsável pela interface de conversação com a IA.
 *
 * <p>Fronteiras públicas deste módulo:
 * <ul>
 *   <li>Recebe mensagens do usuário via WebSocket/HTTP</li>
 *   <li>Delega a consulta ao módulo {@code rag} via evento ou API pública</li>
 *   <li>Mantém histórico de conversa (chat memory)</li>
 * </ul>
 *
 * <p>Dependências permitidas: {@code shared} (implícito).
 * Comunicação com outros módulos exclusivamente via eventos Spring ou APIs públicas.
 */
@ApplicationModule(
    displayName = "Chat LLM",
    allowedDependencies = {}
)
package com.github.overz.chat;

import org.springframework.modulith.ApplicationModule;
