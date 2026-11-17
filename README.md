# SIA — Sistema Integrado de Automações

Plataforma de automação extensível: um **Core** pequeno que descobre, autoriza e executa **plugins**,
agenda execuções e orquestra tudo em **workflows** — sem conhecer a lógica de nenhuma automação.

- **Core:** Java 23, SQLite, daemon HTTP com REST, CLI cliente, permissões deny-by-default.
- **Plugins:** em Java (`kind: java`) ou em qualquer linguagem (`kind: process`, JSON Lines).
- **Regra da casa:** nada sensível em banco ou log — só *quem*, *quando* e *o quê*.

## Rodar

```bash
./mvnw -DskipTests package

# daemon (config de exemplo em config/local.yml)
java -jar sia-server/target/sia-server-1.0.0-SNAPSHOT-all.jar config/local.yml

# cliente
java -jar sia-cli/target/sia-cli-1.0.0-SNAPSHOT-all.jar --token dev-token doctor
java -jar sia-cli/target/sia-cli-1.0.0-SNAPSHOT-all.jar --token dev-token plugins
java -jar sia-cli/target/sia-cli-1.0.0-SNAPSHOT-all.jar --token dev-token run receitanet.diag
```

## Escrever um plugin

Guia completo: [docs/PLUGIN_GUIDE.md](docs/PLUGIN_GUIDE.md).

```bash
mvn archetype:generate -DinteractiveMode=false \
  -DarchetypeGroupId=com.accord -DarchetypeArtifactId=sia-plugin-archetype \
  -DarchetypeVersion=1.0.0-SNAPSHOT \
  -DgroupId=com.accord.plugins -DartifactId=meu-plugin -Dversion=1.0.0 \
  -DpluginId=meu-plugin -Dpackage=com.accord.plugins.meu
```

O projeto gerado compila e testa com o `PluginTestKit` sem edição.

## Módulos

| Módulo | Papel |
|---|---|
| `sia-api` | contratos públicos do plugin (única dependência: `slf4j-api`) |
| `sia-core` | loader, engine, scheduler, workflow, persistência, permissões |
| `sia-auth` | papéis, autorização e tokens (OIDC pendente) |
| `sia-server` | daemon HTTP (Javalin) |
| `sia-cli` | cliente do daemon |
| `sia-testkit` | harness para autores de plugin |
| `sia-plugin-archetype` | geração de projeto de plugin |
| `integrations/receitanet-bx` | primeiro plugin real (Receitanet BX) |
| `examples/hello-plugin` | plugin mínimo de referência |

## Verificar

```bash
./mvnw clean verify     # 191 testes + gate de cobertura (core ≥ 80%)
```

Decisões registradas em [docs/adr/](docs/adr/README.md); plano de execução em
[sia-core-plan.md](sia-core-plan.md).
