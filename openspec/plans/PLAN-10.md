# PLAN-10 — Observabilidade e RNFs `[postergado]`

Conforme `sdd.md`, o aprofundamento de monitoração fica **postergado** — o que já existe permanece (LGTM no compose, OTel wiring na app, traces/logs/métricas fluindo) e é suficiente para desenvolver. Quando este épico for ativado:

- Métricas por etapa do pipeline: latência, profundidade de fila, taxa de retry, volume em DLQ (RF28 complemento) e dashboards correspondentes.
- Metas dos RNFs definidas antes de teste de carga: RNF01 (p95 < 3s na recuperação, ponto de partida), RNF02 (throughput de ingestão sem degradar consulta), RNF03 (backup/RPO/RTO de Neo4j e OpenSearch — bases não reconstruíveis sem reprocessar tudo), RNF04 (disponibilidade de consulta vs. ingestão).
