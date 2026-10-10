#!/usr/bin/env python3
"""Ponta a ponta da Análise da PO (ADR 0005, passo 8), pela API, com o sistema inteiro rodando.

Roteiro, a partir do estado deixado pelo e2e do previsto x realizado (scripts/e2e-previsto-realizado.py; com
--preparar ele roda antes):
  1. GET /exercicios: PO 2026/2027 com setembro carregado e a coluna "Orçado anterior" como 2025/2026 (RF-11.4, 11.5);
  2. GET /previsoes/{poId}/coluna-impressa: total impresso, fundos, grupos, 1.3.20 e avisos (RF-11.5);
  3. GET /previsto-realizado de 09/2026 e do acumulado: a referência dos passos 4 e 5;
  4. GET /comparacao-exercicios: PO 2026/2027 x coluna impressa (RF-11.6), com a evidência do valor clicado;
  5. GET /indicadores: os 7 gráficos, com setembro igual ao GET do previsto x realizado de 09/2026, centavo a centavo
     (RF-11.10 a RF-11.12);
  6. opcional (--po-anterior): envia uma cópia de teste da PO e confirma como 2025/2026; confere a substituição da
     coluna impressa, o aviso por grupo, as rubricas sugeridas e a comparação entre duas POs (RF-11.5, 11.6, 11.7).
     A cópia fica confirmada no banco: rode o passo 6 só num banco de teste. Depois dele a coluna impressa deixa de
     ser o exercício anterior, e os passos 4 e 5 só voltam a rodar num banco novo.

Os arquivos de entrada são parâmetros (os reais ficam em data/golden/privado, fora do git). Só biblioteca padrão.

Uso:
  python3 scripts/e2e-analise-po.py --saida /tmp/e2e-analise
  python3 scripts/e2e-analise-po.py --preparar \\
      --fluxo data/golden/privado/fluxo-caixa-2026-09.pdf --po data/golden/privado/po-2026-2027.pdf \\
      --mapa data/golden/privado/mapa-contas-fluxo-para-PO.csv \\
      --po-anterior data/golden/privado/po-2026-2027.pdf --saida /tmp/e2e-analise
Com --po-anterior igual ao arquivo da PO atual, o script grava em --saida uma cópia com bytes a mais depois do %%EOF
(o conteúdo da página é o mesmo; o hash muda, então o envio não é recusado como repetido).
Variáveis: API (padrão http://localhost:8081/api), KEYCLOAK (padrão http://localhost:8180/realms/condominio).
Sai com código 1 se alguma conferência falhar.
"""
import argparse
import hashlib
import importlib.util
import json
import os
import sys
import urllib.parse
from decimal import Decimal

_spec = importlib.util.spec_from_file_location(
    "e2e_previsto_realizado", os.path.join(os.path.dirname(os.path.abspath(__file__)), "e2e-previsto-realizado.py"))
base = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(base)

conferir, chamar, token, dec, moeda_br = base.conferir, base.chamar, base.token, base.dec, base.moeda_br
falhas = base.falhas  # a mesma lista: --preparar soma as falhas dos dois roteiros



def D(*valores):
    """Tupla com os textos convertidos em Decimal (o resto fica como está)."""
    return tuple(Decimal(x) if isinstance(x, str) else x for x in valores)


MES = "2026-09"
# RF-11.4 e RF-03.1.6: setembro/2026 do piloto
PREVISTO_MES = "451620.13"
PREVISTO_EXERCICIO = "5419441.56"
DESPESA_REALIZADA = "446176.89"
# RF-11.5 (soma das linhas, Q29/Q30). O 419.239,16 e o +7,7% do texto do RF-11.5/11.6 estão errados (correção
# pendente com o agente de requisitos): o previsto do mês da coluna é a soma das linhas sem fundos, 441.525,22.
COLUNA = {"printedTotal": "441304.38", "funds": "22065.22", "monthlyPlanned": "441525.22"}
COLUNA_GRUPOS = [("1.1", Decimal("37661.43"), True), ("1.2", Decimal("565.00"), True),
                 ("1.3", Decimal("348631.55"), True), ("1.4", Decimal("0.00"), True), ("1.5", Decimal("2350.00"), True),
                 ("1.6", Decimal("18746.25"), False), ("1.7", Decimal("19300.00"), True),
                 ("1.8", Decimal("14270.99"), True), ("1.9", Decimal("22065.22"), True)]
AVISO_1_6 = ("Grupo 1.6 DESPESAS ADMINISTRATIVAS: subtotal impresso 18.525,42; soma das linhas 18.746,25 (diferença "
             "220,83). Vale a soma das linhas.")
AVISO_TOTAL = ("Nesta coluna o total impresso (441.304,38) não inclui os fundos: confere com a soma dos subtotais sem "
               "os fundos (441.304,39).")
# RF-11.6: 451.620,13 - 441.525,22 = +10.094,91 (+2,3%)
VARIACAO_PREVISTO = D("10094.91", "2.3", False)
CONTRATOS = D("336274.18", "348631.55", "-12357.37", "-3.5")
SINDICATURA = D("8000.00", "17195.00", "-9195.00", "-53.5")
CAIXA_DAGUA = "1518.93"
# RF-11.11 e RF-11.12
VIGIA = ("1.3.10", "6793.38", "86816.34")
FUNDOS = {"FUNDO DE RESERVA": ("13548.60", "14260.79"), "OBRAS / REFORMAS / INFRA": ("9032.40", "9705.06")}
MOTIVO_1682 = "mesma conta da PO e mesmo grupo: 1682 - Sindicatura Profissional, 1.3"


def igual(a, b):
    """Números da API comparados como Decimal (None só é igual a None)."""
    return (a is None and b is None) or (a is not None and b is not None and dec(a) == dec(b))


def variacao(v):
    """(valor, percentual, novaNoExercicio) como Decimal (o JSON chega como float: 8000.00 vira 8000.0)."""
    return None if v is None else (dec(v["amount"]), dec(v["percentage"]), v["newInFiscalYear"])



def consultar(caminho, tok, **params):
    q = {k: v for k, v in params.items() if v is not None}
    return chamar("GET", caminho + ("?" + urllib.parse.urlencode(q) if q else ""), tok)


def soma_evidencia(cid, tok, periodo, po, alvo):
    st, ev, _ = consultar(f"/condominiums/{cid}/budget-vs-actual/evidence", tok, period=periodo, budget=po, target=alvo)
    if st != 200:
        return st, None, 0
    return st, sum((dec(e["amount"]) for e in ev), Decimal("0")), len(ev)


def salvar(saida, nome, obj):
    with open(os.path.join(saida, nome), "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)


def preparar(a):
    """Roda o e2e do previsto x realizado com os mesmos arquivos (deixa a PO 2026/2027 e setembro prontos)."""
    faltam = [n for n in ("fluxo", "po", "mapa") if not getattr(a, n)]
    if faltam:
        raise SystemExit(f"--preparar exige --{', --'.join(faltam)}")
    print("0. Preparação: e2e do previsto x realizado")
    argv = sys.argv
    sys.argv = [argv[0], "--fluxo", a.fluxo, "--po", a.po, "--mapa", a.mapa, "--saida", a.saida, "--mes", MES]
    if a.csv:
        sys.argv += ["--csv", a.csv]
    try:
        base.principal()
    finally:
        sys.argv = argv
    print()


def principal():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--preparar", action="store_true", help="roda antes o e2e do previsto x realizado")
    p.add_argument("--fluxo")
    p.add_argument("--po")
    p.add_argument("--mapa")
    p.add_argument("--csv")
    p.add_argument("--po-anterior", help="cópia de teste da PO a confirmar como 2025/2026 (opcional)")
    p.add_argument("--saida", default=".")
    a = p.parse_args()
    os.makedirs(a.saida, exist_ok=True)
    if a.preparar:
        preparar(a)

    adm, ges, usu = token("admin"), token("gestor"), token("usuario")
    _, eu, _ = chamar("GET", "/me", adm)
    cid = eu["condominiums"][0]["id"]
    print(f"Condomínio {eu['condominiums'][0]['name']} ({cid})")

    print("1. Exercícios")
    st, exercicios, _ = chamar("GET", f"/condominiums/{cid}/fiscal-years", usu)
    salvar(a.saida, "exercicios.json", exercicios)
    conferir(st == 200 and len(exercicios) >= 2, f"GET /exercicios pelo Usuário ({st}), {len(exercicios or [])} itens")
    atual = exercicios[0]
    po_id = atual["budgetId"]
    conferir(atual["id"] == "budget:" + po_id and atual["type"] == "PO" and atual["label"] == "2026/2027"
             and (atual["start"], atual["end"]) == ("2026-05", "2027-04"),
             f"exercício vigente {atual['label']} {atual['start']} a {atual['end']} ({atual['id']})")
    conferir(igual(atual["monthlyPlanned"], PREVISTO_MES), f"previsto do mês {atual['monthlyPlanned']} (esperado {PREVISTO_MES})")
    situacoes = {m["month"]: m["status"] for m in atual["months"]}
    conferir(len(situacoes) == 12 and situacoes.get(MES) == "WITH_CASH_FLOW"
             and all(s == "NO_CASH_FLOW" for m, s in situacoes.items() if m != MES),
             f"12 meses, só {MES} com fluxo: {[m for m, s in situacoes.items() if s != 'NO_CASH_FLOW']}")
    conferir(atual["mapping"] is not None and atual["mapping"]["confirmed"] == atual["mapping"]["accounts"],
             f"de-para {atual['mapping']}")
    conferir(atual["budgetItems"] is not None and atual["budgetItems"]["lines"] > 0, f"rubricas {atual['budgetItems']}")

    anterior_confirmada = exercicios[1]["type"] == "PO"
    if anterior_confirmada:
        print(f"  (a coluna impressa já foi substituída por {exercicios[1]['label']}: comparação com a coluna e "
              f"indicadores (passos 4 e 5) pulados; use um banco novo para conferi-los)")
        conferir(bool(a.po_anterior), "coluna impressa ainda disponível para a comparação (banco sem cópia de teste)")
    else:
        coluna = exercicios[1]
        conferir(coluna["id"] == "column:" + po_id and coluna["type"] == "PRINTED_COLUMN"
                 and coluna["label"] == "2025/2026 (coluna impressa)" and coluna["budgetId"] == po_id
                 and (coluna["start"], coluna["end"]) == ("2025-05", "2026-04"),
                 f"coluna impressa {coluna['label']} {coluna['start']} a {coluna['end']} ({coluna['id']})")
        conferir(coluna["months"] == [] and coluna["mapping"] is None and coluna["budgetItems"] is None,
                 "coluna só com previsto (sem meses, de-para e rubricas)")
        conferir(igual(coluna["monthlyPlanned"], COLUNA["monthlyPlanned"]),
                 f"previsto do mês da coluna {coluna['monthlyPlanned']} (esperado {COLUNA['monthlyPlanned']})")
        conferir(coluna["warnings"] == [AVISO_1_6, AVISO_TOTAL], f"avisos da coluna: {coluna['warnings']}")

    print("2. Conferência da coluna impressa")
    st, conf, _ = chamar("GET", f"/condominiums/{cid}/budgets/{po_id}/printed-column", usu)
    salvar(a.saida, "coluna-impressa.json", conf)
    conferir(st == 200, f"GET coluna-impressa pelo Usuário ({st})")
    for campo, esp in COLUNA.items():
        conferir(igual(conf.get(campo), esp), f"{campo}: {conf.get(campo)} (esperado {esp})")
    conferir(conf["totalIncludesFunds"] is False, "total impresso não inclui os fundos")
    grupos = [(g["code"], dec(g["amount"]), g["matches"]) for g in conf["groups"]]
    conferir(grupos == COLUNA_GRUPOS, f"grupos pela soma das linhas: {grupos}")
    g16 = next((g for g in conf["groups"] if g["code"] == "1.6"), {})
    conferir(igual(g16.get("printed"), "18525.42") and igual(g16.get("difference"), "-220.83"),
             f"1.6 impresso {g16.get('printed')}, diferença {g16.get('difference')}")
    l1320 = next((l for g in conf["groups"] for l in g["lines"] if l["code"] == "1.3.20"), {})
    conferir(igual(l1320.get("amount"), "17195.00") and l1320.get("percentageText") == "-53,47%",
             f"1.3.20 {l1320.get('amount')} com o % impresso {l1320.get('percentageText')!r}")
    if not anterior_confirmada:
        conferir(conf["superseded"] is False and conf["previousBudgetId"] is None and conf["differences"] == [],
                 "sem PO anterior: não substituída e sem diferenças")
        conferir(conf["warnings"] == [AVISO_1_6, AVISO_TOTAL], f"avisos {conf['warnings']}")

    print(f"3. Previsto x realizado {MES} (referência dos passos 4 e 5)")
    st, pr, _ = consultar(f"/condominiums/{cid}/budget-vs-actual", usu, period=MES, budget=po_id)
    salvar(a.saida, f"previsto-realizado-{MES}.json", pr)
    conferir(st == 200 and pr["status"] == "CALCULATED", f"situação {pr.get('status')}")
    conferir(igual(pr["totals"]["actualExpense"], DESPESA_REALIZADA) and igual(pr["totals"]["execution"], "98.8"),
             f"despesa realizada {pr['totals']['actualExpense']}, execução {pr['totals']['execution']}")
    _, pr_acum, _ = consultar(f"/condominiums/{cid}/budget-vs-actual", usu, period="cumulative", budget=po_id)
    grupos_pr = {g["code"]: g for g in pr["groups"]}
    linhas_pr = {l["lineId"]: l for g in pr["groups"] for l in g["lines"]}

    if not anterior_confirmada:
        print("4. Comparar exercícios: PO 2026/2027 x coluna impressa")
        ids = f"po:{po_id},coluna:{po_id}"
        st, cmp_, _ = consultar(f"/condominiums/{cid}/fiscal-year-comparison", usu, fiscalYears=ids)
        salvar(a.saida, "comparacao-coluna.json", cmp_)
        conferir(st == 200, f"GET comparacao-exercicios pelo Usuário ({st})")
        _, padrao, _ = consultar(f"/condominiums/{cid}/fiscal-year-comparison", usu)
        conferir([e["id"] for e in padrao["fiscalYears"]] == ["budget:" + po_id, "column:" + po_id],
                 "sem filtro: os dois exercícios mais recentes")
        conferir_comparacao_coluna(cid, usu, cmp_, po_id, grupos_pr, pr)
        print("5. Indicadores")
        conferir_indicadores(cid, adm, usu, po_id, pr, pr_acum, grupos_pr, linhas_pr, cmp_, a.saida)

    if a.po_anterior:
        print("6. Cópia de teste confirmada como 2025/2026")
        po_anterior(cid, adm, ges, usu, a, po_id, grupos_pr, pr, pr_acum, linhas_pr)
    return base.relatorio()


def conferir_comparacao_coluna(cid, usu, c, po_id, grupos_pr, pr):
    conferir([(e["id"], e["type"], e["label"]) for e in c["fiscalYears"]] ==
             [("budget:" + po_id, "PO", "2026/2027"), ("column:" + po_id, "PRINTED_COLUMN", "2025/2026 (coluna impressa)")],
             f"exercícios {[e['label'] for e in c['fiscalYears']]}")
    ex_atual, ex_coluna = c["fiscalYears"]
    conferir(ex_atual["months"] == [MES] and ex_coluna["period"] is None and ex_coluna["months"] == [],
             f"meses somados {ex_atual['months']}; coluna sem período ({ex_coluna['period']})")
    r_atual, r_coluna = c["summary"]
    for campo, obtido, esp in (("monthlyPlanned", r_atual["monthlyPlanned"], PREVISTO_MES),
                               ("fiscalYearPlanned", r_atual["fiscalYearPlanned"], PREVISTO_EXERCICIO),
                               ("planned", r_atual["planned"], pr["totals"]["planned"]),
                               ("actual", r_atual["actual"], pr["totals"]["actualExpense"]),
                               ("execution", r_atual["execution"], "98.8"),
                               ("coluna.monthlyPlanned", r_coluna["monthlyPlanned"], COLUNA["monthlyPlanned"])):
        conferir(igual(obtido, esp), f"resumo {campo}: {obtido} (esperado {esp})")
    conferir(r_atual["monthsWithCashFlow"] == 1 and r_coluna["monthsWithCashFlow"] == 0 and r_coluna["actual"] is None
             and r_coluna["openFindings"] is None, "coluna só com previsto: sem meses, realizado e achados")
    conferir(variacao(r_atual["monthlyPlannedVariation"]) == VARIACAO_PREVISTO and r_coluna["monthlyPlannedVariation"] is None,
             f"variação do previsto do mês {variacao(r_atual['monthlyPlannedVariation'])} (esperado +10.094,91 e +2,3%)")
    exc = r_atual["largestOverrun"] or {}
    conferir(exc.get("month") == MES and igual(exc.get("amount"), pr["rule20"]["overrun"])
             and igual(exc.get("percentage"), pr["rule20"]["percentage"]),
             f"maior excesso {exc} = regra dos 20% de {MES}")
    conferir(r_atual["provisional"] is True, "resumo provisório (há valor a realocar)")

    g13 = next(g for g in c["groups"] if g["code"] == "1.3")
    v_atual, v_coluna = g13["values"]
    var13 = variacao(v_atual["monthlyPlannedVariation"])
    conferir((dec(v_atual["monthlyPlanned"]), dec(v_coluna["monthlyPlanned"]), var13[0], var13[1]) == CONTRATOS,
             f"Contratos {v_atual['monthlyPlanned']} x {v_coluna['monthlyPlanned']}, variação {var13}")
    conferir(igual(v_atual["actual"], grupos_pr["1.3"]["actual"]) and v_coluna["actual"] is None,
             f"Contratos realizado {v_atual['actual']} = previsto x realizado ({grupos_pr['1.3']['actual']})")
    st, soma, n = soma_evidencia(cid, usu, ex_atual["period"], po_id, v_atual["target"])
    conferir(st == 200 and soma == dec(grupos_pr["1.3"]["actual"]),
             f"clique em Contratos ({v_atual['target']}, {ex_atual['period']}) abre {n} lançamentos somando {soma}")
    g19 = next(g for g in c["groups"] if g["code"] == "1.9")
    conferir(g19["funds"] is True and igual(g19["values"][1]["monthlyPlanned"], COLUNA["funds"]),
             f"1.9 da coluna {g19['values'][1]['monthlyPlanned']}")

    def rubrica(codigo):
        return next((l for l in c["lines"] if any(x["code"] == codigo for x in l["values"][0]["lines"])), None)

    s = rubrica("1.3.20")
    if s:
        var = variacao(s["values"][0]["monthlyPlannedVariation"])
        conferir((dec(s["values"][0]["monthlyPlanned"]), dec(s["values"][1]["monthlyPlanned"]), var[0], var[1])
                 == SINDICATURA, f"1.3.20 {s['name']}: {s['values'][0]['monthlyPlanned']} x "
                                 f"{s['values'][1]['monthlyPlanned']}, variação {var}")
    else:
        conferir(False, "1.3.20 na comparação por linha")
    cx = rubrica("1.3.25")
    conferir(cx is not None and igual(cx["values"][0]["monthlyPlanned"], CAIXA_DAGUA)
             and variacao(cx["values"][0]["monthlyPlannedVariation"]) == D(CAIXA_DAGUA, None, True),
             f"1.3.25 nova no exercício: {cx and variacao(cx['values'][0]['monthlyPlannedVariation'])}")
    conferir(c["unmatched"] == [], f"sem correspondência: {len(c['unmatched'])} linhas")


def conferir_indicadores(cid, adm, usu, po_id, pr, pr_acum, grupos_pr, linhas_pr, cmp_, saida):
    st, ind, _ = consultar(f"/condominiums/{cid}/indicators", usu, budget=po_id)
    salvar(saida, "indicadores.json", ind)
    conferir(st == 200 and ind["budgetId"] == po_id and ind["label"] == "2026/2027" and ind["dataAsOf"],
             f"GET indicadores pelo Usuário ({st}) {ind.get('label')}, dados de {ind.get('dataAsOf')}")
    _, sem_po, _ = consultar(f"/condominiums/{cid}/indicators", usu)
    conferir(sem_po.get("budgetId") == po_id, "sem filtro: o exercício mais recente")

    def no_mes(pontos):
        return next((x for x in pontos if x["month"] == MES), {})

    def outros_nulos(pontos, campos):
        return [x["month"] for x in pontos if x["month"] != MES and
                (x["status"] != "NO_CASH_FLOW" or any(x.get(k) is not None for k in campos))]

    # Gráfico 1
    ex = ind["monthlyExecution"]
    p = no_mes(ex)
    conferir(len(ex) == 12 and igual(p.get("execution"), pr["totals"]["execution"]) and igual(p.get("execution"), "98.8")
             and igual(p.get("planned"), pr["totals"]["planned"])
             and igual(p.get("actual"), pr["totals"]["actualExpense"]),
             f"gráfico 1 {MES}: {p.get('execution')}% ({p.get('actual')} / {p.get('planned')}) = tela")
    errados = outros_nulos(ex, ("planned", "actual", "execution"))
    conferir(not errados, f"gráfico 1: os outros 11 meses sem fluxo e nulos (errados: {errados})")
    if p.get("target") == "total":
        st, soma, n = soma_evidencia(cid, usu, MES, po_id, "total")
        conferir(soma == dec(pr["totals"]["actualExpense"]), f"clique em {MES}: {n} lançamentos somando {soma}")
    else:
        conferir(p.get("target") is not None, f"gráfico 1 com alvo de evidência ({p.get('target')})")
    # Gráfico 2
    r20, p = ind["rule20"], no_mes(ind["rule20"])
    tela = pr["rule20"]
    pares = [(k, p.get(k), tela[k]) for k in ("overrun", "percentage", "limitPercentage", "maxScenario",
                                               "maxScenarioPercentage", "aboveLimit", "provisional")]
    conferir(all(igual(o, t) if not isinstance(t, bool) else o is t for _, o, t in pares),
             f"gráfico 2 {MES} = tela: {[(k, o) for k, o, _ in pares]}")
    conferir(igual(p.get("percentage"), "8.6") and igual(p.get("limitPercentage"), "20")
             and igual(p.get("maxScenarioPercentage"), "8.8") and p.get("provisional") is True
             and p.get("aboveLimit") is False,
             "gráfico 2: 8,6%, limite 20%, cenário máximo 8,8% provisório, sem alerta")
    errados = outros_nulos(r20, ("overrun", "percentage", "maxScenario", "maxScenarioPercentage", "aboveLimit"))
    conferir(not errados, f"gráfico 2: meses sem fluxo nulos (errados: {errados})")
    # Gráfico 3
    p = no_mes(ind["cumulative"])
    conferir(igual(p.get("cumulativeActual"), pr_acum["totals"]["actualExpense"])
             and igual(p.get("cumulativePlanned"), pr_acum["totals"]["planned"])
             and igual(p.get("cumulativeActual"), DESPESA_REALIZADA),
             f"gráfico 3 {MES}: {p.get('cumulativeActual')} x {p.get('cumulativePlanned')} = tela acumulada")
    errados = outros_nulos(ind["cumulative"], ("cumulativePlanned", "cumulativeActual"))
    conferir(not errados, f"gráfico 3: meses sem fluxo nulos (errados: {errados})")
    # Gráfico 4
    series = {s["code"]: s for s in ind["actualByGroup"]}
    esperados = sorted(c for c in grupos_pr if c != "1.9")
    diferentes = [c for c in esperados if c not in series
                  or not igual(no_mes(series[c]["points"]).get("amount"), grupos_pr[c]["actual"])]
    conferir(sorted(series) == esperados and not diferentes,
             f"gráfico 4: grupos {sorted(series)} com {MES} = tela (diferentes: {diferentes})")
    errados = [c for c, s in series.items() if outros_nulos(s["points"], ("amount",))]
    conferir(not errados, f"gráfico 4: meses sem fluxo nulos (errados: {errados})")
    # Gráfico 5
    md = ind["largestDifferences"]
    erradas = [d["code"] for d in md["above"] + md["below"]
               if d["lineId"] not in linhas_pr or any(not igual(d[k], linhas_pr[d["lineId"]][k])
                                                       for k in ("planned", "actual", "difference"))]
    conferir(len(md["above"]) <= 10 and len(md["below"]) <= 10 and not erradas,
             f"gráfico 5: {len(md['above'])} acima e {len(md['below'])} abaixo, iguais à tela (diferentes: {erradas})")
    difs = sorted((dec(l["difference"]) for l in linhas_pr.values()), reverse=True)
    conferir([dec(d["difference"]) for d in md["above"]] == [x for x in difs if x > 0][:10]
             and [dec(d["difference"]) for d in md["below"]] == sorted(x for x in difs if x < 0)[:10],
             "gráfico 5: as 10 maiores de cada lado, em ordem")
    vigia = next((d for d in md["above"] if d["code"] == VIGIA[0]), None)
    conferir(vigia is not None and igual(vigia["difference"], VIGIA[1]),
             f"gráfico 5: {VIGIA[0]} entre as mais acima com {vigia and vigia['difference']}")
    if vigia:
        st, soma, n = soma_evidencia(cid, usu, "cumulative", po_id, vigia["target"])
        conferir(vigia["target"] == "line:" + vigia["lineId"] and soma == Decimal(VIGIA[2]),
                 f"clique em {VIGIA[0]} ({vigia['target']}, acumulado): {n} lançamentos somando {soma}")
    # Gráfico 6
    fundos_pr = {f["fund"]: f for f in pr["funds"]}
    for nome, (prev, arr) in FUNDOS.items():
        s = next((f for f in ind["funds"] if f["fund"] == nome), None)
        p = no_mes(s["points"]) if s else {}
        conferir(igual(p.get("collected"), arr) and igual(p.get("planned"), prev)
                 and igual(p.get("collected"), fundos_pr[nome]["collected"])
                 and igual(p.get("planned"), fundos_pr[nome]["planned"]) and s["fundId"] == fundos_pr[nome]["fundId"],
                 f"gráfico 6 {nome}: {p.get('collected')} x {p.get('planned')} = tela")
        conferir(s is not None and not outros_nulos(s["points"], ("planned", "collected")),
                 f"gráfico 6 {nome}: meses sem fluxo nulos")
    # Gráfico 7
    comp = ind["comparison"]
    conferir([(e["id"], e["budgetId"]) for e in comp["fiscalYears"]] == [(x["id"], x["budgetId"] if x["type"] == "PO" else None)
                                                                     for x in cmp_["fiscalYears"]],
             f"gráfico 7: exercícios {[(e['label'], e['budgetId']) for e in comp['fiscalYears']]}")
    conferir(igual(comp["fiscalYears"][0]["execution"], "98.8") and comp["fiscalYears"][1]["label"].endswith(
        "(coluna impressa)"), f"gráfico 7: execução {comp['fiscalYears'][0]['execution']}; {comp['fiscalYears'][1]['label']}")
    por_codigo = {g["code"]: g for g in cmp_["groups"]}
    diferentes = [g["code"] for g in comp["groups"] if g["code"] not in por_codigo
                  or [None if x is None else dec(x) for x in g["monthlyPlanned"]]
                  != [None if v["monthlyPlanned"] is None else dec(v["monthlyPlanned"]) for v in por_codigo[g["code"]]["values"]]
                  or g["targets"] != [v["target"] for v in por_codigo[g["code"]]["values"]]]
    conferir(not diferentes, f"gráfico 7: previsto do mês e alvos iguais ao Comparar exercícios (diferentes: {diferentes})")
    g13 = next((g for g in comp["groups"] if g["code"] == "1.3"), None)
    alvo_g4 = no_mes(series["1.3"]["points"]).get("target")
    conferir(g13 is not None and len(g13["targets"]) == 2 and g13["targets"][1] is None and g13["targets"][0] == alvo_g4,
             f"gráfico 7: alvos de 1.3 {g13 and g13['targets']} (o do gráfico 4: {alvo_g4})")
    if g13 and g13["targets"][0]:
        st, soma, n = soma_evidencia(cid, usu, comp["fiscalYears"][0]["period"] or MES, comp["fiscalYears"][0]["budgetId"],
                                     g13["targets"][0])
        conferir(st == 200 and n > 0, f"clique em 1.3 no gráfico 7: {n} lançamentos somando {soma}")
    # Filtro de fundo
    _, fundos_cond, _ = chamar("GET", f"/condominiums/{cid}/funds", adm)
    reserva = next(f for f in fundos_cond if f["name"] == "FUNDO DE RESERVA")
    ordinario = next(f for f in fundos_cond if f["operating"])
    _, so_reserva, _ = consultar(f"/condominiums/{cid}/indicators", usu, budget=po_id, fund=reserva["id"])
    _, so_cond, _ = consultar(f"/condominiums/{cid}/indicators", usu, budget=po_id, fund=ordinario["id"])
    conferir(so_reserva["monthlyExecution"] is None and [f["fund"] for f in so_reserva["funds"]] == ["FUNDO DE RESERVA"],
             "filtro Fundo de Reserva: só o gráfico 6, só ele")
    conferir(so_cond["funds"] is None and igual(no_mes(so_cond["monthlyExecution"]).get("execution"), "98.8"),
             "filtro fundo Condomínio: sem o gráfico 6, 98,8% em setembro")


def copia_de_teste(caminho, po_atual, saida):
    """Mesmo arquivo da PO atual: grava uma cópia com bytes a mais depois do %%EOF (o hash muda)."""
    conteudo = open(caminho, "rb").read()
    if po_atual and hashlib.sha256(conteudo).digest() != hashlib.sha256(open(po_atual, "rb").read()).digest():
        return caminho
    destino = os.path.join(saida, "po-2025-2026-copia-de-teste.pdf")
    with open(destino, "wb") as f:
        f.write(conteudo + b"\n% copia de teste da PO para o exercicio 2025/2026 (e2e-analise-po)\n")
    return destino


def confirmar_po(cid, adm, po_id, inicio, fim):
    """Mesmo pedido do e2e do previsto x realizado: códigos repetidos renumerados e fundos 1.9 ligados."""
    _, det, _ = chamar("GET", f"/condominiums/{cid}/budgets/{po_id}", adm)
    _, fundos, _ = chamar("GET", f"/condominiums/{cid}/funds", adm)
    por_nome = {f["name"]: f for f in fundos}
    codigos = []
    for rep in det["repeatedCodes"]:
        linhas = sorted(rep["lines"], key=lambda l: l["position"])
        grupo = rep["printedCode"].rsplit(".", 1)[0]
        usados = {l["effectiveCode"] for l in det["lines"]}
        prox = max(int(c.rsplit(".", 1)[1]) for c in usados if c.startswith(grupo + ".") and c.count(".") == 2)
        for i, l in enumerate(linhas[1:], 1):
            codigos.append({"lineId": l["lineId"], "code": f"{grupo}.{prox + i}"})
    ligar = {"1.9.1": "FUNDO DE RESERVA", "1.9.2": "OBRAS / REFORMAS / INFRA"}
    ligacoes = [{"lineId": l["id"], "fundId": por_nome[ligar[l["effectiveCode"]]]["id"]}
                for l in det["lines"] if l["fundLine"] and l["effectiveCode"] in ligar]
    pedido = {"fiscalYearStart": inicio, "fiscalYearEnd": fim, "withoutMinutes": True, "effectiveCodes": codigos,
              "funds": ligacoes}
    return chamar("POST", f"/condominiums/{cid}/budgets/{po_id}/confirmation", adm, pedido)


def po_anterior(cid, adm, ges, usu, a, po_id, grupos_pr, pr, pr_acum, linhas_pr):
    _, exercicios, _ = chamar("GET", f"/condominiums/{cid}/fiscal-years", usu)
    ja = next((e for e in exercicios if e["type"] == "PO" and e["label"] == "2025/2026"), None)
    if ja:
        ant_id = ja["budgetId"]
        print(f"  (PO 2025/2026 já confirmada antes: {ant_id})")
    else:
        arquivo = copia_de_teste(a.po_anterior, a.po, a.saida)
        arq_id = base.enviar(cid, ges, arquivo, "PO")  # Gestor também envia (RF-11.2)
        resumo = base.esperar(cid, adm, arq_id)
        conferir(resumo["status"] in ("COMPLETED", "NEEDS_REVIEW"), f"cópia de teste lida: {resumo['status']}")
        _, previsoes, _ = chamar("GET", f"/condominiums/{cid}/budgets", adm)
        ant_id = next(x["id"] for x in previsoes if x["fileId"] == arq_id)
        st, det, _ = confirmar_po(cid, adm, ant_id, "2025-05", "2026-04")
        conferir(st == 200, f"cópia confirmada como 05/2025 a 04/2026 ({st})")
        if st != 200:
            print(json.dumps(det, ensure_ascii=False, indent=1))
            return

    print("  rubricas da cópia (sugeridas pela conta da PO e pelo grupo)")
    _, rub, _ = chamar("GET", f"/condominiums/{cid}/budgets/{ant_id}/budget-items", adm)
    l1320 = next((l for l in rub["lines"] if l["code"] == "1.3.20"), {})
    conferir(l1320.get("reason") == MOTIVO_1682 or l1320.get("status") == "CONFIRMED",
             f"1.3.20: {l1320.get('status')} - {l1320.get('reason')}")
    sugeridas = [l["lineId"] for l in rub["lines"] if l["status"] == "SUGGESTED"]
    if sugeridas:
        _, cmp_antes, _ = consultar(f"/condominiums/{cid}/fiscal-year-comparison", usu)
        conferir(len(cmp_antes["unmatched"]) >= len(sugeridas),
                 f"antes de confirmar: {len(cmp_antes['unmatched'])} linhas sem correspondência "
                 f"({len(sugeridas)} sugeridas não somadas)")
        for nome, tok in (("Gestor", ges), ("Usuário", usu)):
            st, _, _ = chamar("POST", f"/condominiums/{cid}/budgets/{ant_id}/budget-items/batch", tok,
                              {"action": "CONFIRM", "lines": sugeridas})
            conferir(st == 403, f"{nome} não confirma rubricas ({st})")
        st, lote, _ = chamar("POST", f"/condominiums/{cid}/budgets/{ant_id}/budget-items/batch", adm,
                             {"action": "CONFIRM", "lines": sugeridas})
        conferir(st == 200 and lote["changed"] == len(sugeridas), f"lote: {lote.get('changed')} confirmadas")
        _, eventos, _ = chamar("GET", f"/condominiums/{cid}/budgets/{ant_id}/budget-items/events", adm)
        conferir(len(eventos) >= len(sugeridas), f"trilha das rubricas: {len(eventos)} eventos (um por linha)")
    _, rub, _ = chamar("GET", f"/condominiums/{cid}/budgets/{ant_id}/budget-items", adm)
    print(f"  resumo das rubricas: {rub['summary']}")

    print("  exercícios com a PO anterior enviada")
    _, exercicios, _ = chamar("GET", f"/condominiums/{cid}/fiscal-years", usu)
    salvar(a.saida, "exercicios-com-anterior.json", exercicios)
    conferir([e["id"] for e in exercicios[:2]] == ["budget:" + po_id, "budget:" + ant_id],
             f"exercícios {[e['label'] for e in exercicios]}")
    ant = exercicios[1]
    conferir(ant["label"] == "2025/2026" and ant["printedColumn"] == "column:" + po_id,
             f"{ant['label']} substitui a coluna ({ant['printedColumn']})")
    conferir(len(ant["months"]) == 12 and all(m["status"] == "NO_CASH_FLOW" for m in ant["months"]),
             "2025/2026: 12 meses sem fluxo carregado")
    pessoal = moeda_br(grupos_pr["1.1"]["planned"])
    aviso = f"1.1 PESSOAL: {pessoal} (PO enviada) × 37.661,43 (coluna impressa)"
    conferir(any(aviso in x for x in ant["warnings"]), f"aviso da coluna com {aviso!r}: {ant['warnings']}")
    _, conf, _ = chamar("GET", f"/condominiums/{cid}/budgets/{po_id}/printed-column", usu)
    conferir(conf["superseded"] is True and conf["previousBudgetId"] == ant_id and conf["previousBudgetLabel"] == "2025/2026"
             and "1.1" in [d["code"] for d in conf["differences"]],
             f"coluna impressa só como conferência; diferenças {[d['code'] for d in conf['differences']]}")

    print("  previsto x realizado de 2025/2026 sem fluxo")
    _, acum, _ = consultar(f"/condominiums/{cid}/budget-vs-actual", usu, period="cumulative", budget=ant_id)
    zerados = [m["month"] for m in acum["months"] if m.get("actualExpense") is not None]
    conferir(len(acum["months"]) == 12 and not zerados, f"acumulado 2025/2026: {acum['status']}, nenhum realizado "
                                                       f"R$ 0,00 (meses com número: {zerados})")
    st, _, _ = chamar("PUT", f"/condominiums/{cid}/budgets/{ant_id}/extension", adm,
                      {"until": "2026-05", "justification": "teste: mês já coberto pela PO 2026/2027"})
    conferir(st == 422, f"prorrogação sobre mês com outra PO recusada ({st})")
    st, _, _ = chamar("PUT", f"/condominiums/{cid}/budgets/{ant_id}/extension", usu,
                      {"until": "2026-05", "justification": "teste"})
    conferir(st == 403, f"Usuário não prorroga ({st})")

    print("  comparar exercícios: 2026/2027 x 2025/2026")
    _, c, _ = consultar(f"/condominiums/{cid}/fiscal-year-comparison", usu)
    salvar(a.saida, "comparacao-duas-pos.json", c)
    conferir([e["id"] for e in c["fiscalYears"]] == ["budget:" + po_id, "budget:" + ant_id], "sem filtro: as duas POs")
    r_atual, r_ant = c["summary"]
    conferir(igual(r_ant["monthlyPlanned"], PREVISTO_MES) and r_ant["monthsWithCashFlow"] == 0 and r_ant["actual"] is None,
             f"2025/2026 (cópia): previsto do mês {r_ant['monthlyPlanned']}, sem realizado")
    conferir(variacao(r_atual["monthlyPlannedVariation"]) == D("0.00", "0.0", False),
             f"variação contra a cópia {variacao(r_atual['monthlyPlannedVariation'])}")
    conferir(c["unmatched"] == [], f"sem correspondência: {[l['code'] for l in c['unmatched']]}")
    nao_zero = [l["name"] for l in c["lines"] if l["values"][0]["monthlyPlannedVariation"] is None
                or dec(l["values"][0]["monthlyPlannedVariation"]["amount"]) != 0]
    conferir(not nao_zero, f"linha a linha pela rubrica: todas com variação zero (diferentes: {nao_zero[:5]})")
    st, mesmos, _ = consultar(f"/condominiums/{cid}/fiscal-year-comparison", usu, sameMonths="true")
    print(f"  mesmos meses ({st}): comparando={mesmos.get('comparing') if isinstance(mesmos, dict) else mesmos}")

    _, ind, _ = consultar(f"/condominiums/{cid}/indicators", usu, budget=po_id)
    salvar(a.saida, "indicadores-com-anterior.json", ind)
    comp = ind["comparison"]
    conferir([(e["label"], e["budgetId"]) for e in comp["fiscalYears"]] == [("2026/2027", po_id), ("2025/2026", ant_id)],
             f"gráfico 7 com a PO enviada: {[(e['label'], e['budgetId']) for e in comp['fiscalYears']]}")
    _, ind_ant, _ = consultar(f"/condominiums/{cid}/indicators", usu, budget=ant_id)
    vazios = [p["month"] for p in ind_ant["monthlyExecution"] if p["execution"] is not None or p["status"] != "NO_CASH_FLOW"]
    conferir(ind_ant["label"] == "2025/2026" and not vazios, "indicadores de 2025/2026: 12 meses sem fluxo, nulos")
    # O exercício vigente não muda com a PO anterior
    _, pr2, _ = consultar(f"/condominiums/{cid}/budget-vs-actual", usu, period=MES, budget=po_id)
    diferencas = base.comparar(pr, pr2)
    conferir(not diferencas, f"previsto x realizado de {MES} igual depois da cópia (diferenças: {diferencas[:5]})")


if __name__ == "__main__":
    sys.exit(principal())
