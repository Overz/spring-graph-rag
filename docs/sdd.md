# SDD

SDD principal que define alguns critérios para a aplicação;

## Infra

Tudo deve estar executando localmente e sem custos.

Para isso, o arquivo `compose.yaml` deve estar bem configurado.

A ideia é que a execução local simule 100% um ambiente cloud com:

- upload de arquivos
- eventos
- base de dados
- resiliencia
- documentação (adr's, c4 model - até c3)

MinIO, Eventos (NAT's pois nao existe necessidade de complexidade), PostgreSQL,
OpenSearch (localmente), Neo4J, Monitoração (postergar).

Rodar LLM Local nao é problema, desde que o compose.yaml esteja configurado com limites coerentes para rodar demais serviços.
Tambem é bom pensar em algum modelo de embedding que aceite pt-br e ingles.
