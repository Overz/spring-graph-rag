package com.github.overz.bdd;

import com.github.overz.Application;
import com.github.overz.TestcontainersConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Contexto Spring dos cenários BDD (SDD qualidade-e-testes §2): app real em porta
 * aleatória + Testcontainers (Postgres/Neo4j/Keycloak/LGTM). As steps fazem chamadas
 * HTTP reais — os cenários validam orquestração e regras de ponta a ponta.
 */
@CucumberContextConfiguration
@SpringBootTest(
  classes = Application.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    // Storage POSIX (ADR-001) apontado para dentro do target — os steps de ingestão
    // asseguram chave/conteúdo direto no filesystem, sem depender do mount JuiceFS.
    "app.storage.base-dir=target/bdd-blobstore"
  }
)
@Import(TestcontainersConfiguration.class)
public class CucumberSpringConfiguration {
}
