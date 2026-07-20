package com.github.overz.api.internal.repositories;

import com.github.overz.api.internal.dtos.CachedSession;

import java.util.Optional;

/**
 * Store do phantom token (ADR-004 D3) — trocável sem tocar {@code PhantomTokenIssuerImpl};
 * hoje só existe a implementação Redis ({@link PhantomTokenRedisRepository}). Extraída como
 * interface para permitir stub à mão em teste de unidade (sem Mockito).
 */
public interface PhantomTokenRepository {

  void save(String token, CachedSession session, long ttlSeconds);

  Optional<CachedSession> find(String token);

  void delete(String token);

}
