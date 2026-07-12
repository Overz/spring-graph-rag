package com.github.overz;

import org.springframework.boot.SpringApplication;

/**
 * Entrypoint de desenvolvimento com Testcontainers.
 *
 * <p>Inicia a aplicação real substituindo o Docker Compose por containers gerenciados
 * automaticamente. Use via:
 * <pre>{@code
 *   ./mvnw spring-boot:test-run
 * }</pre>
 */
public class TestApplication {

  public static void main(String[] args) {
    SpringApplication
        .from(Application::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }

}
