/**
 * Módulo {@code mcp} — servidor MCP (Model Context Protocol) para agentes de IA.
 *
 * <p>Fronteiras públicas deste módulo:
 * <ul>
 *   <li>Expõe tools/resources via MCP para que agentes de IA interajam com o sistema</li>
 *   <li>Permite que a IA consulte dados da base, execute buscas e acione pipelines</li>
 * </ul>
 *
 * <p>Dependências permitidas: {@code shared} (implícito).
 * Comunicação com outros módulos exclusivamente via eventos Spring ou APIs públicas.
 */
@ApplicationModule(
    displayName = "MCP Server",
    allowedDependencies = {}
)
package com.github.overz.mcp;

import org.springframework.modulith.ApplicationModule;
