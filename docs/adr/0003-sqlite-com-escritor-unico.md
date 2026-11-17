# ADR-0003 — SQLite com escritor único

- **Status:** aceito
- **Data:** 2026-11-13
- **Relacionado:** D4, T5

## Contexto

O SIA é ferramenta interna de máquina única: histórico de execuções, agenda, estado de workflow e
chave-valor de plugin. Postgres exigiria operar servidor, backup e conexão para um volume de dados
que cabe em poucos MB por ano.

## Decisão

SQLite em `data/sia.db`, com:

- `journal_mode=WAL`, `busy_timeout=5000`, `foreign_keys=ON` em toda conexão;
- **`SingleWriter`**: todas as escritas passam por uma thread única com fila e retry em
  `SQLITE_BUSY`;
- leitura concorrente em conexões próprias;
- migrações versionadas por `PRAGMA user_version` (V1…V5), aplicadas antes de carregar plugin.

## Consequências

- Um processo dono do banco: o `InstanceLock` (`sia.db.lock`) recusa um segundo daemon.
- CLI nunca abre o SQLite — só fala HTTP.
- Transação nunca fica aberta durante IO externo (chamada de plugin/processo), para não bloquear a
  fila.
- Backup é `VACUUM INTO` periódico.

## Alternativas descartadas

| Alternativa | Por que não |
|---|---|
| Postgres | infra desproporcional para uma máquina |
| H2/SQLite em memória | perde histórico entre reinícios |
| JSON em disco | sem transação, sem consulta, corrompe em escrita concorrente |
