# RNF-01 — Latência de Consulta

A latência de resposta de RF25/RF26 (excluindo o tempo de geração da própria LLM, que varia por provedor) deve ter meta de p95 definida antes da fase de testes de carga. Sugestão de partida: p95 < 3s para a etapa de recuperação (grafo + vetor + fusão).
