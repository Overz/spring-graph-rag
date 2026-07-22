# garbage-collection-grafo Specification

## Purpose
Expurgar fisicamente (hard delete) entidades e relacionamentos órfãos do grafo
de conhecimento — nós de `Entity` que perderam toda conexão com um chunk ativo
depois de uma ou mais exclusões lógicas (RF10) — para não deixar o Neo4j
acumulando lixo indefinidamente (RF11).

## Requirements
### Requirement: Expurgo físico de entidades órfãs

Um job em background SHALL varrer periodicamente o grafo de conhecimento e remover fisicamente (hard delete) nós de `Entity` sem nenhuma aresta `MENTIONS` conectada a um `Chunk` com `isActive=true`. Relacionamentos entre duas entidades órfãs SHALL ser removidos junto. Entidade com ao menos um chunk ativo conectado SHALL NOT ser removida.

#### Scenario: Remoção física de entidade órfã

- **WHEN** uma exclusão lógica anterior deixou a entidade "Servidor Legado" sem nenhuma aresta conectada a um `Chunk` `isActive=true`, e o job de Garbage Collection é executado
- **THEN** o sistema identifica "Servidor Legado" como órfã e a remove fisicamente do banco de grafos

#### Scenario: Entidade referenciada por chunk ativo não é removida

- **WHEN** a entidade "Kubernetes" possui ao menos uma aresta conectada a um `Chunk` `isActive=true`, e o job é executado
- **THEN** "Kubernetes" não é removida

#### Scenario: Relacionamentos órfãos removidos junto

- **WHEN** as entidades órfãs "Sistema Antigo" e "Banco Legado" têm um relacionamento `DEPENDS_ON` entre si, e o job é executado
- **THEN** ambas as entidades e o relacionamento entre elas são removidos fisicamente

