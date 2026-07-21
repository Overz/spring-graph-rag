package com.github.overz;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  private static final int KEYCLOAK_PORT = 8080;

  @Bean
  @ServiceConnection
  LgtmStackContainer grafanaLgtmContainer() {
    return new LgtmStackContainer(DockerImageName.parse("grafana/otel-lgtm:latest"));
  }

  // API 2.x ([0.2]): containers non-generic; versões casadas com o compose.yaml
  // (fonte da verdade da infra — regra de coerência do CLAUDE.md).
  @Bean
  @ServiceConnection
  Neo4jContainer neo4jContainer() {
    return new Neo4jContainer(DockerImageName.parse("neo4j:5.26.28-community"));
  }

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"));
  }

  // Não existe módulo Testcontainers não-genérico pra Redis (mesma situação do Keycloak
  // abaixo) — @ServiceConnection(name="redis") já é reconhecido pelo Spring Boot pra
  // GenericContainer, sem precisar de DynamicPropertyRegistrar (phantom-token, ADR-004).
  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> redisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:8.8.0"))
      .withExposedPorts(6379);
  }

  // Keycloak com o MESMO realm JSON do compose (SDD qualidade-e-testes §3 — um realm só,
  // dev e teste idênticos). start-dev aqui é aceitável: o modo de storage difere, o realm não.
  @Bean
  GenericContainer<?> keycloakContainer() {
    return new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.7.0"))
      .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
      .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
      .withCopyFileToContainer(
        MountableFile.forHostPath("infra/keycloak/data/import/graphrag-realm.json"),
        "/opt/keycloak/data/import/graphrag-realm.json")
      .withCommand("start-dev", "--import-realm")
      .withExposedPorts(KEYCLOAK_PORT)
      .waitingFor(Wait.forHttp("/realms/graphrag").forPort(KEYCLOAK_PORT)
        .withStartupTimeout(Duration.ofMinutes(3)));
  }

  // Não há @ServiceConnection para Keycloak — o issuer é registrado como propriedade dinâmica.
  @Bean
  DynamicPropertyRegistrar keycloakProperties(final GenericContainer<?> keycloakContainer) {
    return registry -> registry.add(
      "spring.security.oauth2.resourceserver.jwt.issuer-uri",
      () -> "http://%s:%d/realms/graphrag"
        .formatted(keycloakContainer.getHost(), keycloakContainer.getMappedPort(KEYCLOAK_PORT)));
  }

  // Épico 2 (ciclo de vida): schema mínimo de chunk (ChunkIndex). Sem @ServiceConnection
  // pronto pra `spring.ai.vectorstore.opensearch.uris` (não é um tipo de conexão coberto
  // pelo Spring Boot) — mesma estratégia do Keycloak, propriedade dinâmica.
  @Bean
  OpensearchContainer<?> opensearchContainer() {
    return new OpensearchContainer<>(DockerImageName.parse("opensearchproject/opensearch:3.7.0"));
  }

  @Bean
  DynamicPropertyRegistrar opensearchProperties(final OpensearchContainer<?> opensearchContainer) {
    return registry -> registry.add(
      "spring.ai.vectorstore.opensearch.uris",
      // getHttpHostAddress() já devolve o esquema (http://host:port) — não prefixar de novo.
      opensearchContainer::getHttpHostAddress);
  }

}
