# PLAN-03 — Eventos, Fila e Carga (RF12, RF13, RF39)

> *Feature:* `processamento/eventos-e-fila.feature`
> *User story:* como sistema, quero processar de forma assíncrona, idempotente e justa entre tenants, sem que um pico derrube a aplicação.

**[3.1] Eventos internos (Modulith)** `[M · Must]` — após a persistência inicial, publicar eventos de domínio via `ApplicationEventPublisher` com o *event publication registry* do Modulith (entrega at-least-once, reemissão de eventos incompletos); `correlationId` propagado em todos (RF12, RF28).

**[3.2] Consumo idempotente e isolado** `[M · Must]` — consumir o mesmo evento duas vezes não duplica efeito; falha no processamento de um documento não afeta os demais (RF13).

**[3.3] Controle de carga (QUEUED)** `[M · Must]` — payload acumulado acima de 10MB entra em `QUEUED` e a extração é delegada estritamente ao consumo assíncrono controlado, evitando OOM (RF03/RF13).

**[3.4] NATS como broker externo** `[G · Should]` — adicionar NATS ao `compose.yaml` e mover os eventos do pipeline para ele quando os limites dos eventos internos aparecerem (design do RF12 já prevê a transição). Validar a integração Java (cliente `nats-spring`/binder) e registrar em ADR própria.

**[3.5] Fair queueing por tenant** `[M · Should]` — limite de concorrência por tenant no listener (mitigação imediata do "noisy neighbor"); particionamento pleno por tenant vem com o NATS (RF39).
