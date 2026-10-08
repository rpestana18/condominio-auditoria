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
COLUNA = {"totalImpresso": "441304.38", "fundos": "22065.22", "previstoMes": "441525.22"}
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
    return None if v is None else (dec(v["valor"]), dec(v["percentual"]), v["novaNoExercicio"])



def consultar(caminho, tok, **params):
    q = {k: v for k, v in params.items() if v is not None}
    return chamar("GET", caminho + ("?" + urllib.parse.urlencode(q) if q else ""), tok)


def soma_evidencia(cid, tok, periodo, po, alvo):
    st, ev, _ = consultar(f"/condominios/{cid}/previsto-realizado/evidencia", tok, periodo=periodo, po=po, alvo=alvo)
    if st != 200:
        return st, None, 0
    return st, sum((dec(e["valor"]) for e in ev), Decimal("0")), len(ev)


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
    _, eu, _ = chamar("GET", "/eu", adm)
    cid = eu["condominios"][0]["id"]
    print(f"Condomínio {eu['condominios'][0]['nome']} ({cid})")

    print("1. Exercícios")
    st, exercicios, _ = chamar("GET", f"/condominios/{cid}/exercicios", usu)
    salvar(a.saida, "exercicios.json", exercicios)
    conferir(st == 200 and len(exercicios) >= 2, f"GET /exercicios pelo Usuário ({st}), {len(exercicios or [])} itens")
    atual = exercicios[0]
    po_id = atual["poId"]
    conferir(atual["id"] == "po:" + po_id and atual["tipo"] == "PO" and atual["rotulo"] == "2026/2027"
             and (atual["inicio"], atual["fim"]) == ("2026-05", "2027-04"),
             f"exercício vigente {atual['rotulo']} {atual['inicio']} a {atual['fim']} ({atual['id']})")
    conferir(igual(atual["previstoMes"], PREVISTO_MES), f"previsto do mês {atual['previstoMes']} (esperado {PREVISTO_MES})")
    situacoes = {m["mes"]: m["situacao"] for m in atual["meses"]}
    conferir(len(situacoes) == 12 and situacoes.get(MES) == "COM_FLUXO"
             and all(s == "SEM_FLUXO" for m, s in situacoes.items() if m != MES),
             f"12 meses, só {MES} com fluxo: {[m for m, s in situacoes.items() if s != 'SEM_FLUXO']}")
    conferir(atual["depara"] is not None and atual["depara"]["confirmadas"] == atual["depara"]["contas"],
             f"de-para {atual['depara']}")
    conferir(atual["rubricas"] is not None and atual["rubricas"]["linhas"] > 0, f"rubricas {atual['rubricas']}")

    anterior_confirmada = exercicios[1]["tipo"] == "PO"
    if anterior_confirmada:
        print(f"  (a coluna impressa já foi substituída por {exercicios[1]['rotulo']}: comparação com a coluna e "
              f"indicadores (passos 4 e 5) pulados; use um banco novo para conferi-los)")
        conferir(bool(a.po_anterior), "coluna impressa ainda disponível para a comparação (banco sem cópia de teste)")
    else:
        coluna = exercicios[1]
        conferir(coluna["id"] == "coluna:" + po_id and coluna["tipo"] == "COLUNA_IMPRESSA"
                 and coluna["rotulo"] == "2025/2026 (coluna impressa)" and coluna["poId"] == po_id
                 and (coluna["inicio"], coluna["fim"]) == ("2025-05", "2026-04"),
                 f"coluna impressa {coluna['rotulo']} {coluna['inicio']} a {coluna['fim']} ({coluna['id']})")
        conferir(coluna["meses"] == [] and coluna["depara"] is None and coluna["rubricas"] is None,
                 "coluna só com previsto (sem meses, de-para e rubricas)")
        conferir(igual(coluna["previstoMes"], COLUNA["previstoMes"]),
                 f"previsto do mês da coluna {coluna['previstoMes']} (esperado {COLUNA['previstoMes']})")
        conferir(coluna["avisos"] == [AVISO_1_6, AVISO_TOTAL], f"avisos da coluna: {coluna['avisos']}")

    print("2. Conferência da coluna impressa")
    st, conf, _ = chamar("GET", f"/condominios/{cid}/previsoes/{po_id}/coluna-impressa", usu)
    salvar(a.saida, "coluna-impressa.json", conf)
    conferir(st == 200, f"GET coluna-impressa pelo Usuário ({st})")
    for campo, esp in COLUNA.items():
        conferir(igual(conf.get(campo), esp), f"{campo}: {conf.get(campo)} (esperado {esp})")
    conferir(conf["totalIncluiFundos"] is False, "total impresso não inclui os fundos")
    grupos = [(g["codigo"], dec(g["valor"]), g["confere"]) for g in conf["grupos"]]
    conferir(grupos == COLUNA_GRUPOS, f"grupos pela soma das linhas: {grupos}")
    g16 = next((g for g in conf["grupos"] if g["codigo"] == "1.6"), {})
    conferir(igual(g16.get("impresso"), "18525.42") and igual(g16.get("diferenca"), "-220.83"),
             f"1.6 impresso {g16.get('impresso')}, diferença {g16.get('diferenca')}")
    l1320 = next((l for g in conf["grupos"] for l in g["linhas"] if l["codigo"] == "1.3.20"), {})
    conferir(igual(l1320.get("valor"), "17195.00") and l1320.get("percentualTexto") == "-53,47%",
             f"1.3.20 {l1320.get('valor')} com o % impresso {l1320.get('percentualTexto')!r}")
    if not anterior_confirmada:
        conferir(conf["substituida"] is False and conf["poAnteriorId"] is None and conf["diferencas"] == [],
                 "sem PO anterior: não substituída e sem diferenças")
        conferir(conf["avisos"] == [AVISO_1_6, AVISO_TOTAL], f"avisos {conf['avisos']}")

    print(f"3. Previsto x realizado {MES} (referência dos passos 4 e 5)")
    st, pr, _ = consultar(f"/condominios/{cid}/previsto-realizado", usu, periodo=MES, po=po_id)
    salvar(a.saida, f"previsto-realizado-{MES}.json", pr)
    conferir(st == 200 and pr["situacao"] == "CALCULADO", f"situação {pr.get('situacao')}")
    conferir(igual(pr["totais"]["despesaRealizada"], DESPESA_REALIZADA) and igual(pr["totais"]["execucao"], "98.8"),
             f"despesa realizada {pr['totais']['despesaRealizada']}, execução {pr['totais']['execucao']}")
    _, pr_acum, _ = consultar(f"/condominios/{cid}/previsto-realizado", usu, periodo="acumulado", po=po_id)
    grupos_pr = {g["codigo"]: g for g in pr["grupos"]}
    linhas_pr = {l["linhaId"]: l for g in pr["grupos"] for l in g["linhas"]}

    if not anterior_confirmada:
        print("4. Comparar exercícios: PO 2026/2027 x coluna impressa")
        ids = f"po:{po_id},coluna:{po_id}"
        st, cmp_, _ = consultar(f"/condominios/{cid}/comparacao-exercicios", usu, exercicios=ids)
        salvar(a.saida, "comparacao-coluna.json", cmp_)
        conferir(st == 200, f"GET comparacao-exercicios pelo Usuário ({st})")
        _, padrao, _ = consultar(f"/condominios/{cid}/comparacao-exercicios", usu)
        conferir([e["id"] for e in padrao["exercicios"]] == ["po:" + po_id, "coluna:" + po_id],
                 "sem filtro: os dois exercícios mais recentes")
        conferir_comparacao_coluna(cid, usu, cmp_, po_id, grupos_pr, pr)
        print("5. Indicadores")
        conferir_indicadores(cid, adm, usu, po_id, pr, pr_acum, grupos_pr, linhas_pr, cmp_, a.saida)

    if a.po_anterior:
        print("6. Cópia de teste confirmada como 2025/2026")
        po_anterior(cid, adm, ges, usu, a, po_id, grupos_pr, pr, pr_acum, linhas_pr)
    return base.relatorio()


def conferir_comparacao_coluna(cid, usu, c, po_id, grupos_pr, pr):
    conferir([(e["id"], e["tipo"], e["rotulo"]) for e in c["exercicios"]] ==
             [("po:" + po_id, "PO", "2026/2027"), ("coluna:" + po_id, "COLUNA_IMPRESSA", "2025/2026 (coluna impressa)")],
             f"exercícios {[e['rotulo'] for e in c['exercicios']]}")
    ex_atual, ex_coluna = c["exercicios"]
    conferir(ex_atual["meses"] == [MES] and ex_coluna["periodo"] is None and ex_coluna["meses"] == [],
             f"meses somados {ex_atual['meses']}; coluna sem período ({ex_coluna['periodo']})")
    r_atual, r_coluna = c["resumo"]
    for campo, obtido, esp in (("previstoMes", r_atual["previstoMes"], PREVISTO_MES),
                               ("previstoExercicio", r_atual["previstoExercicio"], PREVISTO_EXERCICIO),
                               ("previsto", r_atual["previsto"], pr["totais"]["previsto"]),
                               ("realizado", r_atual["realizado"], pr["totais"]["despesaRealizada"]),
                               ("execucao", r_atual["execucao"], "98.8"),
                               ("coluna.previstoMes", r_coluna["previstoMes"], COLUNA["previstoMes"])):
        conferir(igual(obtido, esp), f"resumo {campo}: {obtido} (esperado {esp})")
    conferir(r_atual["mesesComFluxo"] == 1 and r_coluna["mesesComFluxo"] == 0 and r_coluna["realizado"] is None
             and r_coluna["achadosAbertos"] is None, "coluna só com previsto: sem meses, realizado e achados")
    conferir(variacao(r_atual["variacaoPrevistoMes"]) == VARIACAO_PREVISTO and r_coluna["variacaoPrevistoMes"] is None,
             f"variação do previsto do mês {variacao(r_atual['variacaoPrevistoMes'])} (esperado +10.094,91 e +2,3%)")
    exc = r_atual["maiorExcesso"] or {}
    conferir(exc.get("mes") == MES and igual(exc.get("valor"), pr["regra20"]["excesso"])
             and igual(exc.get("percentual"), pr["regra20"]["percentual"]),
             f"maior excesso {exc} = regra dos 20% de {MES}")
    conferir(r_atual["provisorio"] is True, "resumo provisório (há valor a realocar)")

    g13 = next(g for g in c["grupos"] if g["codigo"] == "1.3")
    v_atual, v_coluna = g13["valores"]
    var13 = variacao(v_atual["variacaoPrevistoMes"])
    conferir((dec(v_atual["previstoMes"]), dec(v_coluna["previstoMes"]), var13[0], var13[1]) == CONTRATOS,
             f"Contratos {v_atual['previstoMes']} x {v_coluna['previstoMes']}, variação {var13}")
    conferir(igual(v_atual["realizado"], grupos_pr["1.3"]["realizado"]) and v_coluna["realizado"] is None,
             f"Contratos realizado {v_atual['realizado']} = previsto x realizado ({grupos_pr['1.3']['realizado']})")
    st, soma, n = soma_evidencia(cid, usu, ex_atual["periodo"], po_id, v_atual["alvo"])
    conferir(st == 200 and soma == dec(grupos_pr["1.3"]["realizado"]),
             f"clique em Contratos ({v_atual['alvo']}, {ex_atual['periodo']}) abre {n} lançamentos somando {soma}")
    g19 = next(g for g in c["grupos"] if g["codigo"] == "1.9")
    conferir(g19["fundos"] is True and igual(g19["valores"][1]["previstoMes"], COLUNA["fundos"]),
             f"1.9 da coluna {g19['valores'][1]['previstoMes']}")

    def rubrica(codigo):
        return next((l for l in c["linhas"] if any(x["codigo"] == codigo for x in l["valores"][0]["linhas"])), None)

    s = rubrica("1.3.20")
    if s:
        var = variacao(s["valores"][0]["variacaoPrevistoMes"])
        conferir((dec(s["valores"][0]["previstoMes"]), dec(s["valores"][1]["previstoMes"]), var[0], var[1])
                 == SINDICATURA, f"1.3.20 {s['nome']}: {s['valores'][0]['previstoMes']} x "
                                 f"{s['valores'][1]['previstoMes']}, variação {var}")
    else:
        conferir(False, "1.3.20 na comparação por linha")
    cx = rubrica("1.3.25")
    conferir(cx is not None and igual(cx["valores"][0]["previstoMes"], CAIXA_DAGUA)
             and variacao(cx["valores"][0]["variacaoPrevistoMes"]) == D(CAIXA_DAGUA, None, True),
             f"1.3.25 nova no exercício: {cx and variacao(cx['valores'][0]['variacaoPrevistoMes'])}")
    conferir(c["semCorrespondencia"] == [], f"sem correspondência: {len(c['semCorrespondencia'])} linhas")


def conferir_indicadores(cid, adm, usu, po_id, pr, pr_acum, grupos_pr, linhas_pr, cmp_, saida):
    st, ind, _ = consultar(f"/condominios/{cid}/indicadores", usu, po=po_id)
    salvar(saida, "indicadores.json", ind)
    conferir(st == 200 and ind["poId"] == po_id and ind["rotulo"] == "2026/2027" and ind["dadosDe"],
             f"GET indicadores pelo Usuário ({st}) {ind.get('rotulo')}, dados de {ind.get('dadosDe')}")
    _, sem_po, _ = consultar(f"/condominios/{cid}/indicadores", usu)
    conferir(sem_po.get("poId") == po_id, "sem filtro: o exercício mais recente")

    def no_mes(pontos):
        return next((x for x in pontos if x["mes"] == MES), {})

    def outros_nulos(pontos, campos):
        return [x["mes"] for x in pontos if x["mes"] != MES and
                (x["situacao"] != "SEM_FLUXO" or any(x.get(k) is not None for k in campos))]

    # Gráfico 1
    ex = ind["execucaoMensal"]
    p = no_mes(ex)
    conferir(len(ex) == 12 and igual(p.get("execucao"), pr["totais"]["execucao"]) and igual(p.get("execucao"), "98.8")
             and igual(p.get("previsto"), pr["totais"]["previsto"])
             and igual(p.get("realizado"), pr["totais"]["despesaRealizada"]),
             f"gráfico 1 {MES}: {p.get('execucao')}% ({p.get('realizado')} / {p.get('previsto')}) = tela")
    errados = outros_nulos(ex, ("previsto", "realizado", "execucao"))
    conferir(not errados, f"gráfico 1: os outros 11 meses sem fluxo e nulos (errados: {errados})")
    if p.get("alvo") == "total":
        st, soma, n = soma_evidencia(cid, usu, MES, po_id, "total")
        conferir(soma == dec(pr["totais"]["despesaRealizada"]), f"clique em {MES}: {n} lançamentos somando {soma}")
    else:
        conferir(p.get("alvo") is not None, f"gráfico 1 com alvo de evidência ({p.get('alvo')})")
    # Gráfico 2
    r20, p = ind["regra20"], no_mes(ind["regra20"])
    tela = pr["regra20"]
    pares = [(k, p.get(k), tela[k]) for k in ("excesso", "percentual", "limitePercentual", "cenarioMaximo",
                                               "percentualCenarioMaximo", "acimaDoLimite", "provisorio")]
    conferir(all(igual(o, t) if not isinstance(t, bool) else o is t for _, o, t in pares),
             f"gráfico 2 {MES} = tela: {[(k, o) for k, o, _ in pares]}")
    conferir(igual(p.get("percentual"), "8.6") and igual(p.get("limitePercentual"), "20")
             and igual(p.get("percentualCenarioMaximo"), "8.8") and p.get("provisorio") is True
             and p.get("acimaDoLimite") is False,
             "gráfico 2: 8,6%, limite 20%, cenário máximo 8,8% provisório, sem alerta")
    errados = outros_nulos(r20, ("excesso", "percentual", "cenarioMaximo", "percentualCenarioMaximo", "acimaDoLimite"))
    conferir(not errados, f"gráfico 2: meses sem fluxo nulos (errados: {errados})")
    # Gráfico 3
    p = no_mes(ind["acumulado"])
    conferir(igual(p.get("realizadoAcumulado"), pr_acum["totais"]["despesaRealizada"])
             and igual(p.get("previstoAcumulado"), pr_acum["totais"]["previsto"])
             and igual(p.get("realizadoAcumulado"), DESPESA_REALIZADA),
             f"gráfico 3 {MES}: {p.get('realizadoAcumulado')} x {p.get('previstoAcumulado')} = tela acumulada")
    errados = outros_nulos(ind["acumulado"], ("previstoAcumulado", "realizadoAcumulado"))
    conferir(not errados, f"gráfico 3: meses sem fluxo nulos (errados: {errados})")
    # Gráfico 4
    series = {s["codigo"]: s for s in ind["realizadoPorGrupo"]}
    esperados = sorted(c for c in grupos_pr if c != "1.9")
    diferentes = [c for c in esperados if c not in series
                  or not igual(no_mes(series[c]["pontos"]).get("valor"), grupos_pr[c]["realizado"])]
    conferir(sorted(series) == esperados and not diferentes,
             f"gráfico 4: grupos {sorted(series)} com {MES} = tela (diferentes: {diferentes})")
    errados = [c for c, s in series.items() if outros_nulos(s["pontos"], ("valor",))]
    conferir(not errados, f"gráfico 4: meses sem fluxo nulos (errados: {errados})")
    # Gráfico 5
    md = ind["maioresDiferencas"]
    erradas = [d["codigo"] for d in md["acima"] + md["abaixo"]
               if d["linhaId"] not in linhas_pr or any(not igual(d[k], linhas_pr[d["linhaId"]][k])
                                                       for k in ("previsto", "realizado", "diferenca"))]
    conferir(len(md["acima"]) <= 10 and len(md["abaixo"]) <= 10 and not erradas,
             f"gráfico 5: {len(md['acima'])} acima e {len(md['abaixo'])} abaixo, iguais à tela (diferentes: {erradas})")
    difs = sorted((dec(l["diferenca"]) for l in linhas_pr.values()), reverse=True)
    conferir([dec(d["diferenca"]) for d in md["acima"]] == [x for x in difs if x > 0][:10]
             and [dec(d["diferenca"]) for d in md["abaixo"]] == sorted(x for x in difs if x < 0)[:10],
             "gráfico 5: as 10 maiores de cada lado, em ordem")
    vigia = next((d for d in md["acima"] if d["codigo"] == VIGIA[0]), None)
    conferir(vigia is not None and igual(vigia["diferenca"], VIGIA[1]),
             f"gráfico 5: {VIGIA[0]} entre as mais acima com {vigia and vigia['diferenca']}")
    if vigia:
        st, soma, n = soma_evidencia(cid, usu, "acumulado", po_id, vigia["alvo"])
        conferir(vigia["alvo"] == "linha:" + vigia["linhaId"] and soma == Decimal(VIGIA[2]),
                 f"clique em {VIGIA[0]} ({vigia['alvo']}, acumulado): {n} lançamentos somando {soma}")
    # Gráfico 6
    fundos_pr = {f["fundo"]: f for f in pr["fundos"]}
    for nome, (prev, arr) in FUNDOS.items():
        s = next((f for f in ind["fundos"] if f["fundo"] == nome), None)
        p = no_mes(s["pontos"]) if s else {}
        conferir(igual(p.get("arrecadado"), arr) and igual(p.get("previsto"), prev)
                 and igual(p.get("arrecadado"), fundos_pr[nome]["arrecadado"])
                 and igual(p.get("previsto"), fundos_pr[nome]["previsto"]) and s["fundoId"] == fundos_pr[nome]["fundoId"],
                 f"gráfico 6 {nome}: {p.get('arrecadado')} x {p.get('previsto')} = tela")
        conferir(s is not None and not outros_nulos(s["pontos"], ("previsto", "arrecadado")),
                 f"gráfico 6 {nome}: meses sem fluxo nulos")
    # Gráfico 7
    comp = ind["comparacao"]
    conferir([(e["id"], e["poId"]) for e in comp["exercicios"]] == [(x["id"], x["poId"] if x["tipo"] == "PO" else None)
                                                                     for x in cmp_["exercicios"]],
             f"gráfico 7: exercícios {[(e['rotulo'], e['poId']) for e in comp['exercicios']]}")
    conferir(igual(comp["exercicios"][0]["execucao"], "98.8") and comp["exercicios"][1]["rotulo"].endswith(
        "(coluna impressa)"), f"gráfico 7: execução {comp['exercicios'][0]['execucao']}; {comp['exercicios'][1]['rotulo']}")
    por_codigo = {g["codigo"]: g for g in cmp_["grupos"]}
    diferentes = [g["codigo"] for g in comp["grupos"] if g["codigo"] not in por_codigo
                  or [None if x is None else dec(x) for x in g["previstoMes"]]
                  != [None if v["previstoMes"] is None else dec(v["previstoMes"]) for v in por_codigo[g["codigo"]]["valores"]]
                  or g["alvos"] != [v["alvo"] for v in por_codigo[g["codigo"]]["valores"]]]
    conferir(not diferentes, f"gráfico 7: previsto do mês e alvos iguais ao Comparar exercícios (diferentes: {diferentes})")
    g13 = next((g for g in comp["grupos"] if g["codigo"] == "1.3"), None)
    alvo_g4 = no_mes(series["1.3"]["pontos"]).get("alvo")
    conferir(g13 is not None and len(g13["alvos"]) == 2 and g13["alvos"][1] is None and g13["alvos"][0] == alvo_g4,
             f"gráfico 7: alvos de 1.3 {g13 and g13['alvos']} (o do gráfico 4: {alvo_g4})")
    if g13 and g13["alvos"][0]:
        st, soma, n = soma_evidencia(cid, usu, comp["exercicios"][0]["periodo"] or MES, comp["exercicios"][0]["poId"],
                                     g13["alvos"][0])
        conferir(st == 200 and n > 0, f"clique em 1.3 no gráfico 7: {n} lançamentos somando {soma}")
    # Filtro de fundo
    _, fundos_cond, _ = chamar("GET", f"/condominios/{cid}/fundos", adm)
    reserva = next(f for f in fundos_cond if f["nome"] == "FUNDO DE RESERVA")
    ordinario = next(f for f in fundos_cond if f["ordinario"])
    _, so_reserva, _ = consultar(f"/condominios/{cid}/indicadores", usu, po=po_id, fundo=reserva["id"])
    _, so_cond, _ = consultar(f"/condominios/{cid}/indicadores", usu, po=po_id, fundo=ordinario["id"])
    conferir(so_reserva["execucaoMensal"] is None and [f["fundo"] for f in so_reserva["fundos"]] == ["FUNDO DE RESERVA"],
             "filtro Fundo de Reserva: só o gráfico 6, só ele")
    conferir(so_cond["fundos"] is None and igual(no_mes(so_cond["execucaoMensal"]).get("execucao"), "98.8"),
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
    _, det, _ = chamar("GET", f"/condominios/{cid}/previsoes/{po_id}", adm)
    _, fundos, _ = chamar("GET", f"/condominios/{cid}/fundos", adm)
    por_nome = {f["nome"]: f for f in fundos}
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
    pedido = {"exercicioInicio": inicio, "exercicioFim": fim, "semAta": True, "codigosEfetivos": codigos,
              "fundos": ligacoes}
    return chamar("POST", f"/condominios/{cid}/previsoes/{po_id}/confirmacao", adm, pedido)


def po_anterior(cid, adm, ges, usu, a, po_id, grupos_pr, pr, pr_acum, linhas_pr):
    _, exercicios, _ = chamar("GET", f"/condominios/{cid}/exercicios", usu)
    ja = next((e for e in exercicios if e["tipo"] == "PO" and e["rotulo"] == "2025/2026"), None)
    if ja:
        ant_id = ja["poId"]
        print(f"  (PO 2025/2026 já confirmada antes: {ant_id})")
    else:
        arquivo = copia_de_teste(a.po_anterior, a.po, a.saida)
        arq_id = base.enviar(cid, ges, arquivo, "PO")  # Gestor também envia (RF-11.2)
        resumo = base.esperar(cid, adm, arq_id)
        conferir(resumo["status"] in ("CONCLUIDO", "PRECISA_REVISAO"), f"cópia de teste lida: {resumo['status']}")
        _, previsoes, _ = chamar("GET", f"/condominios/{cid}/previsoes", adm)
        ant_id = next(x["id"] for x in previsoes if x["arquivoId"] == arq_id)
        st, det, _ = confirmar_po(cid, adm, ant_id, "2025-05", "2026-04")
        conferir(st == 200, f"cópia confirmada como 05/2025 a 04/2026 ({st})")
        if st != 200:
            print(json.dumps(det, ensure_ascii=False, indent=1))
            return

    print("  rubricas da cópia (sugeridas pela conta da PO e pelo grupo)")
    _, rub, _ = chamar("GET", f"/condominios/{cid}/previsoes/{ant_id}/rubricas", adm)
    l1320 = next((l for l in rub["linhas"] if l["codigo"] == "1.3.20"), {})
    conferir(l1320.get("motivo") == MOTIVO_1682 or l1320.get("estado") == "CONFIRMADO",
             f"1.3.20: {l1320.get('estado')} - {l1320.get('motivo')}")
    sugeridas = [l["linhaId"] for l in rub["linhas"] if l["estado"] == "SUGERIDO"]
    if sugeridas:
        _, cmp_antes, _ = consultar(f"/condominios/{cid}/comparacao-exercicios", usu)
        conferir(len(cmp_antes["semCorrespondencia"]) >= len(sugeridas),
                 f"antes de confirmar: {len(cmp_antes['semCorrespondencia'])} linhas sem correspondência "
                 f"({len(sugeridas)} sugeridas não somadas)")
        for nome, tok in (("Gestor", ges), ("Usuário", usu)):
            st, _, _ = chamar("POST", f"/condominios/{cid}/previsoes/{ant_id}/rubricas/lote", tok,
                              {"acao": "CONFIRMAR", "linhas": sugeridas})
            conferir(st == 403, f"{nome} não confirma rubricas ({st})")
        st, lote, _ = chamar("POST", f"/condominios/{cid}/previsoes/{ant_id}/rubricas/lote", adm,
                             {"acao": "CONFIRMAR", "linhas": sugeridas})
        conferir(st == 200 and lote["alteradas"] == len(sugeridas), f"lote: {lote.get('alteradas')} confirmadas")
        _, eventos, _ = chamar("GET", f"/condominios/{cid}/previsoes/{ant_id}/rubricas/eventos", adm)
        conferir(len(eventos) >= len(sugeridas), f"trilha das rubricas: {len(eventos)} eventos (um por linha)")
    _, rub, _ = chamar("GET", f"/condominios/{cid}/previsoes/{ant_id}/rubricas", adm)
    print(f"  resumo das rubricas: {rub['resumo']}")

    print("  exercícios com a PO anterior enviada")
    _, exercicios, _ = chamar("GET", f"/condominios/{cid}/exercicios", usu)
    salvar(a.saida, "exercicios-com-anterior.json", exercicios)
    conferir([e["id"] for e in exercicios[:2]] == ["po:" + po_id, "po:" + ant_id],
             f"exercícios {[e['rotulo'] for e in exercicios]}")
    ant = exercicios[1]
    conferir(ant["rotulo"] == "2025/2026" and ant["colunaImpressa"] == "coluna:" + po_id,
             f"{ant['rotulo']} substitui a coluna ({ant['colunaImpressa']})")
    conferir(len(ant["meses"]) == 12 and all(m["situacao"] == "SEM_FLUXO" for m in ant["meses"]),
             "2025/2026: 12 meses sem fluxo carregado")
    pessoal = moeda_br(grupos_pr["1.1"]["previsto"])
    aviso = f"1.1 PESSOAL: {pessoal} (PO enviada) × 37.661,43 (coluna impressa)"
    conferir(any(aviso in x for x in ant["avisos"]), f"aviso da coluna com {aviso!r}: {ant['avisos']}")
    _, conf, _ = chamar("GET", f"/condominios/{cid}/previsoes/{po_id}/coluna-impressa", usu)
    conferir(conf["substituida"] is True and conf["poAnteriorId"] == ant_id and conf["poAnteriorRotulo"] == "2025/2026"
             and "1.1" in [d["codigo"] for d in conf["diferencas"]],
             f"coluna impressa só como conferência; diferenças {[d['codigo'] for d in conf['diferencas']]}")

    print("  previsto x realizado de 2025/2026 sem fluxo")
    _, acum, _ = consultar(f"/condominios/{cid}/previsto-realizado", usu, periodo="acumulado", po=ant_id)
    zerados = [m["mes"] for m in acum["meses"] if m.get("despesaRealizada") is not None]
    conferir(len(acum["meses"]) == 12 and not zerados, f"acumulado 2025/2026: {acum['situacao']}, nenhum realizado "
                                                       f"R$ 0,00 (meses com número: {zerados})")
    st, _, _ = chamar("PUT", f"/condominios/{cid}/previsoes/{ant_id}/prorrogacao", adm,
                      {"ate": "2026-05", "justificativa": "teste: mês já coberto pela PO 2026/2027"})
    conferir(st == 422, f"prorrogação sobre mês com outra PO recusada ({st})")
    st, _, _ = chamar("PUT", f"/condominios/{cid}/previsoes/{ant_id}/prorrogacao", usu,
                      {"ate": "2026-05", "justificativa": "teste"})
    conferir(st == 403, f"Usuário não prorroga ({st})")

    print("  comparar exercícios: 2026/2027 x 2025/2026")
    _, c, _ = consultar(f"/condominios/{cid}/comparacao-exercicios", usu)
    salvar(a.saida, "comparacao-duas-pos.json", c)
    conferir([e["id"] for e in c["exercicios"]] == ["po:" + po_id, "po:" + ant_id], "sem filtro: as duas POs")
    r_atual, r_ant = c["resumo"]
    conferir(igual(r_ant["previstoMes"], PREVISTO_MES) and r_ant["mesesComFluxo"] == 0 and r_ant["realizado"] is None,
             f"2025/2026 (cópia): previsto do mês {r_ant['previstoMes']}, sem realizado")
    conferir(variacao(r_atual["variacaoPrevistoMes"]) == D("0.00", "0.0", False),
             f"variação contra a cópia {variacao(r_atual['variacaoPrevistoMes'])}")
    conferir(c["semCorrespondencia"] == [], f"sem correspondência: {[l['codigo'] for l in c['semCorrespondencia']]}")
    nao_zero = [l["nome"] for l in c["linhas"] if l["valores"][0]["variacaoPrevistoMes"] is None
                or dec(l["valores"][0]["variacaoPrevistoMes"]["valor"]) != 0]
    conferir(not nao_zero, f"linha a linha pela rubrica: todas com variação zero (diferentes: {nao_zero[:5]})")
    st, mesmos, _ = consultar(f"/condominios/{cid}/comparacao-exercicios", usu, mesmosMeses="true")
    print(f"  mesmos meses ({st}): comparando={mesmos.get('comparando') if isinstance(mesmos, dict) else mesmos}")

    _, ind, _ = consultar(f"/condominios/{cid}/indicadores", usu, po=po_id)
    salvar(a.saida, "indicadores-com-anterior.json", ind)
    comp = ind["comparacao"]
    conferir([(e["rotulo"], e["poId"]) for e in comp["exercicios"]] == [("2026/2027", po_id), ("2025/2026", ant_id)],
             f"gráfico 7 com a PO enviada: {[(e['rotulo'], e['poId']) for e in comp['exercicios']]}")
    _, ind_ant, _ = consultar(f"/condominios/{cid}/indicadores", usu, po=ant_id)
    vazios = [p["mes"] for p in ind_ant["execucaoMensal"] if p["execucao"] is not None or p["situacao"] != "SEM_FLUXO"]
    conferir(ind_ant["rotulo"] == "2025/2026" and not vazios, "indicadores de 2025/2026: 12 meses sem fluxo, nulos")
    # O exercício vigente não muda com a PO anterior
    _, pr2, _ = consultar(f"/condominios/{cid}/previsto-realizado", usu, periodo=MES, po=po_id)
    diferencas = base.comparar(pr, pr2)
    conferir(not diferencas, f"previsto x realizado de {MES} igual depois da cópia (diferenças: {diferencas[:5]})")


if __name__ == "__main__":
    sys.exit(principal())
