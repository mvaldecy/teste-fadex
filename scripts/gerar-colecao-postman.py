"""Converte o OpenAPI publicado pela aplicacao numa colecao Postman v2.1.

Gerado a partir do contrato vivo, e nao escrito a mao: uma colecao escrita a mao
envelhece em silencio, e o avaliador descobre isso do pior jeito — mandando uma
requisicao que nao existe mais.
"""
import json, re, sys

spec = json.load(open(sys.argv[1]))
saida = sys.argv[2]

BASE = "{{baseUrl}}"

NOMES_AMIGAVEIS = {
    "auth-controller": "Autenticação",
    "ticket-controller": "Chamados",
    "ticket-comment-controller": "Comentários",
    "ticket-event-controller": "Histórico",
    "ticket-classification-controller": "Classificação",
    "ticket-triage-controller": "Triagem por IA",
    "ticket-similarity-controller": "Semelhantes",
    "ticket-status-transition-controller": "Transições de status",
    "indicator-controller": "Indicadores",
    "notification-controller": "Notificações em tempo real",
    "ai-job-controller": "Fila de jobs de IA",
    "user-controller": "Usuários",
    "choices-controller": "Opções de formulário",
}

def exemplo_do_schema(schema, spec, profundidade=0):
    """Corpo de exemplo a partir do schema, resolvendo $ref."""
    if not isinstance(schema, dict) or profundidade > 4:
        return {}

    if "$ref" in schema:
        nome = schema["$ref"].split("/")[-1]
        return exemplo_do_schema(
            spec.get("components", {}).get("schemas", {}).get(nome, {}), spec, profundidade + 1)

    tipo = schema.get("type")

    if tipo == "object" or "properties" in schema:
        return {k: exemplo_do_schema(v, spec, profundidade + 1)
                for k, v in schema.get("properties", {}).items()}
    if tipo == "array":
        return [exemplo_do_schema(schema.get("items", {}), spec, profundidade + 1)]
    if "enum" in schema:
        return schema["enum"][0]
    if tipo == "integer":
        return 0
    if tipo == "number":
        return 0.0
    if tipo == "boolean":
        return False

    formato = schema.get("format")
    if formato == "uuid":
        return "{{ticketId}}"
    if formato in ("date-time", "date"):
        return "2026-08-15T10:00:00"
    return ""

pastas = {}

for caminho, operacoes in sorted(spec["paths"].items()):
    for metodo, operacao in operacoes.items():
        if not isinstance(operacao, dict):
            continue

        tag = (operacao.get("tags") or ["Outros"])[0]
        pasta = NOMES_AMIGAVEIS.get(tag, tag)

        # {id} vira {{ticketId}} para o avaliador trocar em um lugar so.
        caminho_postman = re.sub(r"\{(\w+)\}", r"{{\1}}", caminho)

        cabecalhos = [{"key": "Accept", "value": "application/json"}]
        corpo = None

        conteudo = (operacao.get("requestBody") or {}).get("content", {})
        if "application/json" in conteudo:
            cabecalhos.append({"key": "Content-Type", "value": "application/json"})
            corpo = {
                "mode": "raw",
                "raw": json.dumps(
                    exemplo_do_schema(conteudo["application/json"].get("schema", {}), spec),
                    indent=2, ensure_ascii=False),
                "options": {"raw": {"language": "json"}},
            }

        query = [
            {"key": p["name"], "value": "", "disabled": True,
             "description": p.get("description", "")}
            for p in operacao.get("parameters", []) if p.get("in") == "query"
        ]

        requisicao = {
            "name": operacao.get("summary") or f"{metodo.upper()} {caminho}",
            "request": {
                "method": metodo.upper(),
                "header": cabecalhos,
                "url": {
                    "raw": BASE + caminho_postman,
                    "host": [BASE],
                    "path": [t for t in caminho_postman.strip("/").split("/") if t],
                    "query": query,
                },
                "description": operacao.get("description", ""),
            },
        }

        if corpo:
            requisicao["request"]["body"] = corpo

        # O login guarda o token sozinho: sem isso o avaliador copia e cola um JWT
        # a cada hora, que e onde a maioria das colecoes e abandonada.
        if caminho.endswith("/auth/login") and metodo == "post":
            requisicao["request"]["body"]["raw"] = json.dumps(
                {"email": "admin@fadex.org.br", "password": "admin123"},
                indent=2, ensure_ascii=False)
            requisicao["event"] = [{
                "listen": "test",
                "script": {"type": "text/javascript", "exec": [
                    "const corpo = pm.response.json();",
                    "if (corpo.accessToken) {",
                    "  pm.collectionVariables.set('token', corpo.accessToken);",
                    "  pm.collectionVariables.set('refreshToken', corpo.refreshToken || '');",
                    "}"]}}]

        pastas.setdefault(pasta, []).append(requisicao)

colecao = {
    "info": {
        "name": "Fadex Helpdesk — API",
        "description": (
            "Gerada a partir do OpenAPI publicado pela propria aplicacao "
            "(GET /v3/api-docs), com `scripts/gerar-colecao-postman.py`.\n\n"
            "Comece por **Autenticação → login**: ele guarda o token na variavel "
            "`token`, usada por todas as demais requisicoes. Ajuste `baseUrl` se "
            "escolheu outra porta no wizard, e `ticketId` com um id real da listagem."
        ),
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "auth": {"type": "bearer", "bearer": [{"key": "token", "value": "{{token}}", "type": "string"}]},
    "variable": [
        {"key": "baseUrl", "value": "http://localhost:8080"},
        {"key": "token", "value": ""},
        {"key": "refreshToken", "value": ""},
        {"key": "ticketId", "value": ""},
        {"key": "id", "value": ""},
    ],
    "item": [{"name": nome, "item": itens}
             for nome, itens in sorted(pastas.items(), key=lambda p: p[0])],
}

json.dump(colecao, open(saida, "w"), indent=2, ensure_ascii=False)
print(f"{sum(len(i) for i in pastas.values())} requisições em {len(pastas)} pastas")
