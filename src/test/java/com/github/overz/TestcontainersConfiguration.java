package com.github.overz;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  LgtmStackContainer grafanaLgtmContainer() {
    return new LgtmStackContainer(DockerImageName.parse("grafana/otel-lgtm:latest"));
  }

  // TODO: Neo4jContainer/PostgreSQLContainer deixaram de ser genéricos no Testcontainers 2.0.5,
  // quebrando a assinatura usada aqui. Comentado até revisarmos a API nova.
  // @Bean
  // @ServiceConnection
  // Neo4jContainer<?> neo4jContainer() {
  //   return new Neo4jContainer<>(DockerImageName.parse("neo4j:latest"));
  // }

  // @Bean
  // @ServiceConnection
  // PostgreSQLContainer<?> postgresContainer() {
  //   return new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));
  // }

}
