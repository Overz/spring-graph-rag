package com.github.overz.api.internal.security;

import com.github.overz.api.internal.dtos.CachedSession;
import com.github.overz.api.internal.repositories.PhantomTokenRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Só {@code resolve}/{@code revoke} são testáveis sem infraestrutura real: {@code issue}/
 * {@code refresh} chamam o Keycloak (via {@link PhantomTokenIssuerImpl#exchange}, sem porta
 * própria) — cobertos pelos cenários {@code @RF35} de BDD (Testcontainers).
 */
class PhantomTokenIssuerImplTest extends Assertions {

  @Test
  void resolveDevolveCallerContextQuandoTokenExisteNoRepositorio() {
    final var repository = repositoryComResultado(Optional.of(
      new CachedSession("tenant-1", "owner-1", Set.of("document:upload"), "refresh-token")));
    final var issuer = new PhantomTokenIssuerImpl(null, null, repository, null);

    assertTrue(issuer.resolve("token").isPresent());
  }

  @Test
  void resolveDevolveVazioQuandoTokenNaoExisteNoRepositorio() {
    final var repository = repositoryComResultado(Optional.empty());
    final var issuer = new PhantomTokenIssuerImpl(null, null, repository, null);

    assertTrue(issuer.resolve("token").isEmpty());
  }

  @Test
  void revokeDelegaParaODeleteDoRepositorio() {
    final var deletado = new AtomicBoolean(false);
    final var issuer = new PhantomTokenIssuerImpl(null, null, repositoryComDelete(deletado), null);

    issuer.revoke("token");

    assertTrue(deletado.get());
  }

  private PhantomTokenRepository repositoryComResultado(final Optional<CachedSession> resultado) {
    return new PhantomTokenRepository() {
      @Override
      public void save(final String token, final CachedSession session, final long ttlSeconds) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<CachedSession> find(final String token) {
        return resultado;
      }

      @Override
      public void delete(final String token) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private PhantomTokenRepository repositoryComDelete(final AtomicBoolean deletado) {
    return new PhantomTokenRepository() {
      @Override
      public void save(final String token, final CachedSession session, final long ttlSeconds) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<CachedSession> find(final String token) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void delete(final String token) {
        deletado.set(true);
      }
    };
  }

}
