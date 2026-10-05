#!/usr/bin/env python3
"""Ponta a ponta do previsto x realizado (ADR 0004, passo 11), pela API, com o sistema inteiro rodando.

Roteiro: envia o fluxo e a PO, confirma o fundo ordinário, confirma a PO (exercício, código repetido, fundos 1.9),
carrega a planilha do de-para e confirma as contas, abre o mês, exporta PDF e Excel, compara com o esperado e
reprocessa o fluxo para mostrar que nenhum número muda.

Os arquivos de entrada são parâmetros (os reais ficam em data/golden/privado, fora do git). Só biblioteca padrão do
Python; se o pdfplumber estiver instalado (venv do leitor), o texto do PDF também é conferido.

Uso:
  python3 scripts/e2e-previsto-realizado.py \
      --fluxo data/golden/privado/fluxo-caixa-2026-09.pdf \
      --po data/golden/privado/po-2026-2027.pdf \
      --mapa data/golden/privado/mapa-contas-fluxo-para-PO.csv \
      --csv data/golden/privado/previsto-realizado-2026-09.csv \
      --saida /tmp/e2e
Variáveis: API (padrão http://localhost:8081/api), KEYCLOAK (padrão http://localhost:8180/realms/condominio).
Sai com código 1 se alguma conferência falhar.
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile
import xml.etree.ElementTree as ET
from decimal import Decimal

API = os.environ.get("API", "http://localhost:8081/api")
KEYCLOAK = os.environ.get("KEYCLOAK", "http://localhost:8180/realms/condominio")

# Caso de aceite do RF-03.1.15 (os mesmos números do PrevistoRealizadoGoldenTest)
ESPERADO = {
    "totais.despesaRealizada": "446176.89",
    "totais.emLinhas": "445125.96",
    "totais.previsto": "451620.13",
    "totais.diferenca": "-5443.24",
    "totais.execucao": "98.8",
    "conferencia.debitosDoFundo": "449455.13",
    "ajustes.total": "3278.24",
    "aRealocar.total": "1050.93",
    "semLinhaPo.total": "0.00",
    "regra20.excesso": "38880.19",
    "regra20.percentual": "8.6",
    "regra20.limite": "90324.03",
    "regra20.cenarioMaximo": "39931.12",
}
FUNDOS_ESPERADOS = {  # nome no fluxo: (linha, previsto, arrecadado, diferença, execução)
    "FUNDO DE RESERVA": ("1.9.1", "13548.60", "14260.79", "712.19", "105.3"),
    "OBRAS / REFORMAS / INFRA": ("1.9.2", "9032.40", "9705.06", "672.66", "107.4"),
}

falhas = []


def conferir(ok, texto):
    print(("  OK    " if ok else "  FALHA ") + texto)
    if not ok:
        falhas.append(texto)


def token(usuario):
    dados = urllib.parse.urlencode({"grant_type": "password", "client_id": "dev-cli", "username": usuario,
                                    "password": usuario}).encode()
    with urllib.request.urlopen(KEYCLOAK + "/protocol/openid-connect/token", dados) as r:
        return json.load(r)["access_token"]


def chamar(metodo, caminho, tok, corpo=None, tipo=None, bruto=False):
    cab = {"Authorization": "Bearer " + tok}
    dados = None
    if corpo is not None and tipo is None:
        dados, cab["Content-Type"] = json.dumps(corpo).encode(), "application/json"
    elif corpo is not None:
        dados, cab["Content-Type"] = corpo, tipo
    req = urllib.request.Request(API + caminho, data=dados, method=metodo, headers=cab)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            conteudo = r.read()
            return r.status, (conteudo if bruto else (json.loads(conteudo) if conteudo else None)), dict(r.headers)
    except urllib.error.HTTPError as e:
        conteudo = e.read()
        try:
            return e.code, json.loads(conteudo), dict(e.headers)
        except ValueError:
            return e.code, conteudo.decode(errors="replace"), dict(e.headers)


def multipart(caminho_arquivo, tipo="application/octet-stream"):
    fronteira = uuid.uuid4().hex
    with open(caminho_arquivo, "rb") as f:
        conteudo = f.read()
    nome = os.path.basename(caminho_arquivo)
    corpo = (f"--{fronteira}\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"{nome}\"\r\n"
             f"Content-Type: {tipo}\r\n\r\n").encode() + conteudo + f"\r\n--{fronteira}--\r\n".encode()
    return corpo, "multipart/form-data; boundary=" + fronteira


def enviar(cid, tok, caminho_arquivo, categoria):
    corpo, tipo = multipart(caminho_arquivo, "application/pdf")
    st, r, _ = chamar("POST", f"/condominios/{cid}/arquivos?categoria={categoria}", tok, corpo, tipo)
    if st == 202:
        return r["id"]
    if st == 409:  # mesmo conteúdo já enviado: usa o existente
        _, lista, _ = chamar("GET", f"/condominios/{cid}/arquivos?categoria={categoria}", tok)
        nome = os.path.basename(caminho_arquivo)
        return next(a["id"] for a in lista if a["nome"] == nome)
    raise SystemExit(f"envio de {caminho_arquivo} recusado: {st} {r}")


def esperar(cid, tok, arquivo_id, depois_de=None, prazo=300):
    fim = time.time() + prazo
    while time.time() < fim:
        _, a, _ = chamar("GET", f"/condominios/{cid}/arquivos/{arquivo_id}", tok)
        resumo = a.get("arquivo", a)
        if resumo["status"] in ("CONCLUIDO", "PRECISA_REVISAO", "FALHOU") and (
                depois_de is None or (resumo.get("processadoEm") or "") > depois_de):
            return resumo
        time.sleep(2)
    raise SystemExit(f"arquivo {arquivo_id} não terminou em {prazo}s")


def valor(obj, caminho):
    for parte in caminho.split("."):
        obj = None if obj is None else obj.get(parte)
    return obj


def dec(v):
    return None if v is None else Decimal(str(v))


def br(v):
    return Decimal(v.strip().replace(".", "").replace(",", "."))


def moeda_br(v):
    q = Decimal(v).quantize(Decimal("0.01"))
    inteiro, centavos = f"{abs(q):.2f}".split(".")
    return ("-" if q < 0 else "") + f"{int(inteiro):,}".replace(",", ".") + "," + centavos


def numeros_xlsx(conteudo):
    """Todos os números das células do .xlsx (sem biblioteca: o arquivo é um zip de XML)."""
    import io
    numeros = set()
    with zipfile.ZipFile(io.BytesIO(conteudo)) as z:
        abas = [n for n in z.namelist() if n.startswith("xl/worksheets/sheet")]
        ns = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
        for aba in abas:
            raiz = ET.fromstring(z.read(aba))
            for c in raiz.iterfind(".//m:c", ns):
                v = c.find("m:v", ns)
                if v is not None and c.get("t") in (None, "n"):
                    try:
                        numeros.add(Decimal(v.text).quantize(Decimal("0.01")))
                    except Exception:
                        pass
        nomes = z.read("xl/workbook.xml").decode()
    return numeros, nomes


def principal():
    p = argparse.ArgumentParser()
    p.add_argument("--fluxo", required=True)
    p.add_argument("--po", required=True)
    p.add_argument("--mapa", required=True)
    p.add_argument("--csv", help="previsto-realizado esperado, linha a linha (opcional)")
    p.add_argument("--mes", default="2026-09")
    p.add_argument("--exercicio", default="2026-05:2027-04")
    p.add_argument("--saida", default=".")
    a = p.parse_args()
    os.makedirs(a.saida, exist_ok=True)

    adm, usu = token("admin"), token("usuario")
    _, eu, _ = chamar("GET", "/eu", adm)
    cid = eu["condominios"][0]["id"]
    print(f"Condomínio {eu['condominios'][0]['nome']} ({cid})")

    print("1. Envio do fluxo e da PO")
    fluxo_id = enviar(cid, adm, a.fluxo, "BALANCETE")
    po_arquivo = enviar(cid, adm, a.po, "PO")
    fluxo = esperar(cid, adm, fluxo_id)
    po_arq = esperar(cid, adm, po_arquivo)
    conferir(fluxo["status"] == "CONCLUIDO", f"fluxo {fluxo['status']}, {fluxo.get('totalLancamentos')} lançamentos")
    conferir(po_arq["status"] in ("CONCLUIDO", "PRECISA_REVISAO"), f"PO {po_arq['status']} {po_arq.get('mensagem') or ''}")

    print("2. Fundo ordinário")
    _, fundos, _ = chamar("GET", f"/condominios/{cid}/fundos", adm)
    por_nome = {f["nome"]: f for f in fundos}
    ordinario = next(f for f in fundos if f["nome"].upper().startswith("CONDOM"))
    if not ordinario["ordinario"]:
        st, _, _ = chamar("PUT", f"/condominios/{cid}/fundo-ordinario", adm, {"fundoId": ordinario["id"]})
        conferir(st == 204, f"fundo ordinário = {ordinario['nome']} ({st})")

    print("3. Confirmação da PO")
    _, previsoes, _ = chamar("GET", f"/condominios/{cid}/previsoes", adm)
    po = next(x for x in previsoes if x["arquivoId"] == po_arquivo)
    _, det, _ = chamar("GET", f"/condominios/{cid}/previsoes/{po['id']}", adm)
    if po["estado"] != "CONFIRMADA":
        codigos = []
        for rep in det["codigosRepetidos"]:
            linhas = sorted(rep["linhas"], key=lambda l: l["ordem"])
            grupo = rep["codigoImpresso"].rsplit(".", 1)[0]
            usados = {l["codigoEfetivo"] for l in det["linhas"]}
            prox = max(int(c.rsplit(".", 1)[1]) for c in usados if c.startswith(grupo + ".") and c.count(".") == 2)
            for i, l in enumerate(linhas[1:], 1):
                codigos.append({"linhaId": l["linhaId"], "codigo": f"{grupo}.{prox + i}"})
        ligar = {"1.9.1": "FUNDO DE RESERVA", "1.9.2": "OBRAS / REFORMAS / INFRA"}
        ligacoes = [{"linhaId": l["id"], "fundoId": por_nome[ligar[l["codigoEfetivo"]]]["id"]}
                    for l in det["linhas"] if l["linhaDeFundo"] and l["codigoEfetivo"] in ligar]
        ini, fim = a.exercicio.split(":")
        pedido = {"exercicioInicio": ini, "exercicioFim": fim, "semAta": True, "codigosEfetivos": codigos,
                  "fundos": ligacoes}
        st, _, _ = chamar("POST", f"/condominios/{cid}/previsoes/{po['id']}/confirmacao", usu, pedido)
        conferir(st == 403, f"Usuário não confirma a PO ({st})")
        st, det, _ = chamar("POST", f"/condominios/{cid}/previsoes/{po['id']}/confirmacao", adm, pedido)
        conferir(st == 200, f"PO confirmada ({st}) códigos {[c['codigo'] for c in codigos]}, {len(ligacoes)} fundos")
        if st != 200:
            print(json.dumps(det, ensure_ascii=False, indent=1))
            return relatorio()

    print("4. De-para pela planilha")
    corpo, tipo = multipart(a.mapa, "text/csv")
    st, plan, _ = chamar("POST", f"/condominios/{cid}/previsoes/{po['id']}/depara/planilha", adm, corpo, tipo)
    conferir(st == 200, f"planilha: {plan.get('aceitas')} aceitas, {len(plan.get('ignoradas', []))} ignoradas, "
                        f"{len(plan.get('recusadas', []))} recusadas")
    _, lista, _ = chamar("GET", f"/condominios/{cid}/previsoes/{po['id']}/depara?filtro=SUGERIDO", adm)
    sugeridas = [c["conta"] for c in lista["contas"]]
    if sugeridas:
        st, lote, _ = chamar("POST", f"/condominios/{cid}/previsoes/{po['id']}/depara/lote", adm,
                             {"acao": "CONFIRMAR", "contas": sugeridas})
        conferir(st == 200, f"lote: {lote.get('alteradas')} confirmadas")
    _, lista, _ = chamar("GET", f"/condominios/{cid}/previsoes/{po['id']}/depara", adm)
    conferir(lista["resumo"]["confirmadas"] == 73 and lista["resumo"]["contas"] == 73,
             f"de-para: {lista['resumo']}")

    print(f"5. Previsto x realizado {a.mes}")
    st, r, _ = chamar("GET", f"/condominios/{cid}/previsto-realizado?periodo={a.mes}", adm)
    json.dump(r, open(os.path.join(a.saida, f"previsto-realizado-{a.mes}.json"), "w"), ensure_ascii=False, indent=1)
    conferir(st == 200 and r["situacao"] == "CALCULADO", f"situação {r.get('situacao')} {r.get('mensagem') or ''}")
    for caminho, esp in ESPERADO.items():
        obtido = dec(valor(r, caminho))
        conferir(obtido is not None and obtido == Decimal(esp), f"{caminho}: {obtido} (esperado {esp})")
    conferir(r["depara"] == {"contas": 73, "confirmadas": 73, "semDeparaConfirmado": 0}, f"depara {r['depara']}")
    conferir(r["conferencia"]["confere"] is True, "conferência com o fluxo confere")
    conferir(r["regra20"]["acimaDoLimite"] is False, "regra dos 20% não acima do limite")
    for nome, (cod, prev, arr, dif, exe) in FUNDOS_ESPERADOS.items():
        f = next((x for x in r["fundos"] if x.get("fundo") == nome), None)
        conferir(f is not None and f["linhaCodigo"] == cod and [dec(f[k]) for k in
                 ("previsto", "arrecadado", "diferenca", "execucao")] == [Decimal(x) for x in (prev, arr, dif, exe)],
                 f"fundo {nome}: {f and [f['linhaCodigo'], f['previsto'], f['arrecadado'], f['diferenca'], f['execucao']]}")
    linhas = {l["codigo"]: l for g in r["grupos"] for l in g["linhas"]}
    if a.csv:
        erradas, conferidas = [], 0
        for lin in open(a.csv, encoding="utf-8").read().splitlines()[1:]:
            c = lin.split(";")
            if not c[0][:1].isdigit() or c[0].startswith("1.9."):
                continue
            l = linhas.get(c[0])
            conferidas += 1
            if l is None or (dec(l["previsto"]), dec(l["realizado"]), dec(l["diferenca"])) != (br(c[2]), br(c[3]), br(c[4])) \
                    or sorted(l["contasFluxo"]) != sorted(c[5].split()):
                erradas.append(c[0])
        conferir(not erradas and conferidas == 70, f"CSV linha a linha: {conferidas} linhas, diferentes: {erradas}")

    print("6. Exportação")
    exportados = {}
    for formato in ("pdf", "xlsx"):
        st, conteudo, cab = chamar("GET", f"/condominios/{cid}/previsto-realizado/exportacao?formato={formato}"
                                   f"&periodo={a.mes}", usu, bruto=True)
        conferir(st == 200 and isinstance(conteudo, bytes) and len(conteudo) > 1000,
                 f"{formato}: {st}, {len(conteudo) if isinstance(conteudo, bytes) else conteudo} bytes, "
                 f"{cab.get('Content-Disposition')}")
        if st == 200:
            caminho = os.path.join(a.saida, f"previsto-realizado-{a.mes}.{formato}")
            open(caminho, "wb").write(conteudo)
            exportados[formato] = conteudo
    chave = [Decimal(ESPERADO[k]) for k in ("totais.despesaRealizada", "totais.previsto", "regra20.excesso",
                                            "regra20.limite", "aRealocar.total", "ajustes.total")]
    if "xlsx" in exportados:
        numeros, abas = numeros_xlsx(exportados["xlsx"])
        faltam = [str(x) for x in chave if x not in numeros]
        faltam_linhas = [c for c, l in linhas.items() if dec(l["realizado"]).quantize(Decimal("0.01")) not in numeros]
        conferir(not faltam and not faltam_linhas, f"Excel tem os números do JSON (faltam {faltam}; linhas {faltam_linhas[:5]})")
        conferir('name="Resumo"' in abas and "Evid" in abas, "Excel com as abas Resumo e Evidência")
    if "pdf" in exportados:
        conferir(exportados["pdf"][:4] == b"%PDF", "PDF começa com %PDF")
        try:
            import io
            import pdfplumber
            with pdfplumber.open(io.BytesIO(exportados["pdf"])) as pdf:
                texto = "\n".join(pg.extract_text() or "" for pg in pdf.pages)
            faltam = [moeda_br(x) for x in chave if moeda_br(x) not in texto]
            conferir(not faltam, f"PDF tem os números do JSON (faltam {faltam}); {len(texto)} caracteres")
            conferir("PROVISÓRIO" in texto, "PDF marcado PROVISÓRIO (há valor a realocar)")
        except ImportError:
            print("  (pdfplumber ausente: texto do PDF não conferido)")

    print("7. Reprocessar o fluxo não muda nenhum número")
    _, achados_antes, _ = chamar("GET", f"/condominios/{cid}/achados?competencia={a.mes}", adm)
    antes = esperar(cid, adm, fluxo_id)
    st, _, _ = chamar("POST", f"/condominios/{cid}/arquivos/{fluxo_id}/reprocessar", adm)
    conferir(st == 202, f"reprocesso aceito ({st})")
    depois = esperar(cid, adm, fluxo_id, depois_de=antes["processadoEm"])
    conferir(depois["status"] == "CONCLUIDO" and depois["totalLancamentos"] == antes["totalLancamentos"],
             f"fluxo reprocessado: {depois['status']}, {depois['totalLancamentos']} lançamentos")
    time.sleep(3)  # recálculo dos achados roda depois do commit
    _, r2, _ = chamar("GET", f"/condominios/{cid}/previsto-realizado?periodo={a.mes}", adm)
    json.dump(r2, open(os.path.join(a.saida, f"previsto-realizado-{a.mes}-reprocessado.json"), "w"),
              ensure_ascii=False, indent=1)
    diferencas = comparar(r, r2)
    conferir(not diferencas, f"JSON idêntico depois do reprocesso (diferenças: {diferencas[:5]})")
    _, achados_depois, _ = chamar("GET", f"/condominios/{cid}/achados?competencia={a.mes}", adm)
    resumo = lambda xs: sorted((x["regra"], x["alvo"] if isinstance(x["alvo"], str) else json.dumps(x["alvo"]),
                                x["estado"], x["id"]) for x in xs)
    conferir(resumo(achados_antes) == resumo(achados_depois),
             f"achados iguais depois do reprocesso ({len(achados_antes)} antes, {len(achados_depois)} depois)")
    return relatorio()


def comparar(a, b, caminho=""):
    if isinstance(a, dict) and isinstance(b, dict):
        out = []
        for k in sorted(set(a) | set(b)):
            out += comparar(a.get(k), b.get(k), f"{caminho}.{k}")
        return out
    if isinstance(a, list) and isinstance(b, list):
        if len(a) != len(b):
            return [f"{caminho}: {len(a)} itens x {len(b)}"]
        return [d for i, (x, y) in enumerate(zip(a, b)) for d in comparar(x, y, f"{caminho}[{i}]")]
    return [] if a == b else [f"{caminho}: {a} -> {b}"]


def relatorio():
    print()
    print("RESULTADO: " + ("tudo confere" if not falhas else f"{len(falhas)} falha(s)"))
    for f in falhas:
        print("  - " + f)
    return 1 if falhas else 0


if __name__ == "__main__":
    sys.exit(principal())
