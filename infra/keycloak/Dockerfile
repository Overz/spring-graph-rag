# Keycloak do GraphRAG (ADL-008 / [0.7]) — build otimizado, padrão de referência validado.
#
# Multi-stage:
#   1. setup   — UBI 9 instala o curl num rootfs isolado; a imagem final do Keycloak
#                é baseada em ubi-micro (sem gerenciador de pacotes) e o healthcheck
#                do compose usa curl no management port 9000 (/health/ready).
#   2. builder — `kc.sh build` fixa as opções de build na imagem (db, health, metrics),
#                permitindo `start --optimized` (subida rápida, sem re-augmentação).
#   3. final   — rootfs com curl + /opt/keycloak já otimizado.
#
# O realm (data/import/graphrag-realm.json) NÃO é embutido na imagem: o compose.yaml
# monta ./infra/keycloak/data/import como volume somente-leitura. Nada do contexto
# de build é copiado para a imagem (ver .dockerignore).

ARG KC_VERSION="26.7.0"

FROM redhat/ubi9:9.5 AS setup

# Instala o curl no rootfs que será copiado para a imagem final; remove o pacote
# `setup` para não conflitar com o já presente na ubi-micro do Keycloak.
RUN dnf install --installroot /mnt/rootfs curl --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM keycloak/keycloak:${KC_VERSION} AS builder

WORKDIR /opt/keycloak

# Opções de BUILD (fixadas na imagem — as de RUNTIME ficam em .env.keycloak):
#   --db=postgres        driver/dialeto do banco (database dedicado no Postgres do projeto)
#   --health-enabled     /health/* no management port 9000 — usado pelo healthcheck do compose
#   --metrics-enabled    /metrics no management port — observabilidade (Épico 10)
# Sem --http-relative-path: usamos o path default (/realms/...).
# Sem --transaction-xa-enabled: datasource único, XA não se justifica aqui.
RUN /opt/keycloak/bin/kc.sh build \
    --db=postgres \
    --health-enabled=true \
    --metrics-enabled=true

FROM keycloak/keycloak:${KC_VERSION} AS final

WORKDIR /opt/keycloak

COPY --from=setup /mnt/rootfs /
COPY --from=builder /opt/keycloak/ /opt/keycloak/

# `start --optimized` pula a re-augmentação do build; `--import-realm` importa
# data/import/*.json (montado pelo compose) quando o realm ainda não existe.
CMD [ "start", "--optimized", "--import-realm" ]
