package com.github.overz;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifica a integridade da estrutura modular da aplicação.
 *
 * <p>Este teste garante que:
 * <ul>
 *   <li>Nenhum módulo acessa diretamente pacotes internos de outro módulo</li>
 *   <li>As dependências entre módulos respeitam o que foi declarado em {@code @ApplicationModule}</li>
 *   <li>Não existem dependências cíclicas entre módulos</li>
 * </ul>
 *
 * <p>Em caso de violação, o teste falha com uma mensagem descritiva indicando
 * exatamente qual classe está violando qual fronteira.
 */
class ModularityTest {

  private static final ApplicationModules MODULES = ApplicationModules.of(Application.class);

  /**
   * Verifica todas as fronteiras modulares declaradas via {@code @ApplicationModule}.
   * Falha se qualquer módulo depender de um pacote {@code internal} de outro módulo
   * ou de um módulo não declarado em {@code allowedDependencies}.
   */
  @Test
  void modulesShouldRespectDeclaredDependencies() {
    MODULES.verify();
  }

  /**
   * Imprime no console uma visão geral dos módulos detectados e suas dependências.
   * Útil para debug e documentação da arquitetura.
   */
  @Test
  void printModuleOverview() {
    MODULES.forEach(System.out::println);
  }

  /**
   * Gera documentação da arquitetura modular em {@code target/modulith-docs/}.
   * Inclui diagrama de componentes (PlantUML/Mermaid) e tabela de dependências.
   */
  @Test
  void generateModuleDocumentation() {
    new Documenter(MODULES)
        .writeModulesAsPlantUml()
        .writeIndividualModulesAsPlantUml()
        .writeAggregatingDocument();
  }

}
