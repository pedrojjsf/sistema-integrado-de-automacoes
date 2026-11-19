# Protocolo `stdio-jsonl-v1` (plugin de processo)

Plugin `kind: process` fala com o Core por **JSON Lines**: uma linha, um objeto JSON, em `stdin` e
`stdout`. Funciona para qualquer linguagem — Python, Node, Go, shell.

## Ciclo

```
Core                              plugin
 │  {"type":"describe"}            │
 │ ──────────────────────────────► │
 │  {"type":"describe","metadata":…,"automations":[…]}
 │ ◄────────────────────────────── │
 │                                 │
 │  {"type":"execute","runId":…}   │
 │ ──────────────────────────────► │
 │  {"type":"log",…}               │  (quantas quiser)
 │ ◄────────────────────────────── │
 │  {"type":"progress",…}          │
 │ ◄────────────────────────────── │
 │  {"type":"result",…}            │
 │ ◄────────────────────────────── │
 │                                 │
 │  {"type":"shutdown"}            │
 │ ──────────────────────────────► │
```

## 1. Handshake — `describe`

O Core envia ao subir o plugin e espera resposta em **15 s**. Sem resposta, o plugin é recusado no
carregamento (não fica processo pendurado).

```json
{"type":"describe","protocol":"stdio-jsonl-v1"}
```

Resposta:

```json
{
  "type": "describe",
  "metadata": {
    "id": "meu-processo",
    "name": "Meu Processo",
    "version": "1.0.0",
    "apiVersion": "1.0",
    "author": "Time"
  },
  "automations": [
    {
      "id": "meu-processo.rodar",
      "name": "Rodar",
      "description": "executa o trabalho",
      "persistOutputs": false,
      "params": [
        {"name": "entrada", "type": "STRING", "required": true, "sensitive": false},
        {"name": "linhas", "type": "INTEGER", "required": false, "sensitive": false}
      ]
    }
  ]
}
```

`type` aceita `STRING`, `INTEGER`, `BOOLEAN`, `PATH`, `CHOICE` (os mesmos do `ParamType`).

## 2. Execução — `execute`

```json
{"type":"execute","runId":"9ac23ec3-f875-49d3-b536-f41f382c2805","automation":"meu-processo.rodar","params":{"entrada":"arquivo.csv","linhas":"10"}}
```

Todos os parâmetros chegam como **string**. Quem converte é o plugin.

Durante a execução o plugin pode emitir livremente:

```json
{"type":"log","runId":"9ac2…","level":"INFO","message":"lendo arquivo"}
{"type":"progress","runId":"9ac2…","percent":40,"message":"processando"}
{"type":"error","runId":"9ac2…","message":"falha ao abrir"}
```

`log` e `progress` entram no log e no progresso da execução (e aparecem na interface). `error` é
tratado como falha do plugin.

E termina com **exatamente uma** resposta final:

```json
{"type":"result","runId":"9ac2…","status":"SUCCESS","message":"ok","outputs":{"processadas":"10"}}
```

```json
{"type":"result","runId":"9ac2…","status":"FAILURE","errorCode":"ARQUIVO_INVALIDO","message":"coluna X ausente"}
```

| `status` | Efeito no SIA |
|---|---|
| `SUCCESS` | execução bem-sucedida; `outputs` viram as chaves de saída |
| `FAILURE` | falha com `errorCode` exibido no histórico |
| `SKIPPED` | nada a fazer; a execução é registrada como pulada |
| desconhecido | tratado como `FAILURE` com `PLUGIN_FAILURE` |

## 3. Encerramento — `shutdown`

```json
{"type":"shutdown"}
```

O plugin deve sair **rapidamente** (o Core espera 5 s e depois mata o processo e os descendentes).

## Regras do contrato

1. **Uma linha por mensagem.** Não quebre JSON em várias linhas.
2. **`stdout` é do protocolo.** Texto solto é descartado com log — não derruba o host, mas também não
   aparece para ninguém. Use `log` para diagnóstico.
3. **`stderr` não é lido**: redirecione para o seu próprio arquivo se precisar.
4. **Timeout**: se o plugin não responder dentro do tempo (30 min por padrão), a execução falha com
   `PLUGIN_TIMEOUT` e o processo é encerrado.
5. **Morte do processo**: se o processo sair antes de `result`, a execução falha com `PLUGIN_TIMEOUT`
   e a mensagem indica o código de saída.
6. **Um run por vez por processo.** O protocolo v1 não multiplexa execuções simultâneas no mesmo
   processo (paralelismo entre plugins diferentes continua normal).
7. **`plugin.yml` continua obrigatório** — `id`, `version`, `kind: process`, `command`. Os metadados do
   `describe` prevalecem para nome e versão exibidos.

## `plugin.yml` mínimo

```yaml
id: meu-processo
name: Meu Processo
version: 1.0.0
api-version: "1.0"
kind: process
command: ["python", "main.py"]   # caminho relativo ao pacote, ou nome no PATH
ui:
  entry: ui/panel.html           # OBRIGATÓRIO: a tela do plugin
```

## Exemplo em Python

```python
import json, sys

def respond(payload):
    sys.stdout.write(json.dumps(payload) + "\n")
    sys.stdout.flush()

for line in sys.stdin:
    message = json.loads(line)
    kind = message.get("type")

    if kind == "describe":
        respond({
            "type": "describe",
            "metadata": {"id": "meu-processo", "name": "Meu Processo",
                         "version": "1.0.0", "apiVersion": "1.0"},
            "automations": [{
                "id": "meu-processo.rodar", "name": "Rodar", "description": "exemplo",
                "params": [{"name": "entrada", "type": "STRING", "required": True, "sensitive": False}],
            }],
        })
    elif kind == "execute":
        run = message["runId"]
        respond({"type": "log", "runId": run, "level": "INFO", "message": "comecando"})
        respond({"type": "result", "runId": run, "status": "SUCCESS",
                 "message": "ok", "outputs": {"entrada": message["params"]["entrada"]}})
    elif kind == "shutdown":
        break
```

## Teste rápido sem instalar

```bash
echo '{"type":"describe"}' | python main.py
echo '{"type":"execute","runId":"1","automation":"meu-processo.rodar","params":{"entrada":"x"}}' | python main.py
```

Se as duas respostas forem JSON de uma linha, o plugin cumpre o protocolo. Depois:
`POST /api/plugins/validate` e `POST /api/plugins/install`.
