# RNF-03 — Backup e Recuperação de Desastres

Neo4j e OpenSearch são bases stateful e centrais — dados perdidos nelas não são reconstruíveis a partir de outra fonte sem reprocessar todo o histórico de arquivos. Deve haver rotina de backup periódico com RPO (Recovery Point Objective) e RTO (Recovery Time Objective) definidos para ambas as bases.
