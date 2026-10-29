# PO: entendimento do negócio

Entendimento confirmado pelo usuário em 23/10/2026, escrito pelo agente `negocio`. Base para o agente `requisitos`; não é requisito.

Marcas: **[fato]** com a fonte; **[decisão]** do usuário (Q-xx do `docs/requisitos.md` §10, ou D/N com a data, deste entendimento); **[a confirmar]** quando ainda é hipótese. Os exemplos são do piloto (Mio Residencial Parque, PO 2026/2027). Código Civil é regra fixa; convenção, RI, PO, fundos e plano de contas são dados de cada condomínio.

## 0. O requisito principal e a ordem do trabalho

**O que a funcionalidade precisa entregar** [decisão, 23/10/2026]: as **entradas e as saídas mês a mês** e, para cada gasto, **qual linha da PO ele é**. O resto (economia, estouro, ponte do saldo, gráficos) deriva disso. A versão anterior criou vários menus e não entregou esse quadro principal.

**A ordem não pode ser invertida** [decisão, 23/10/2026]:

1. **Entender o documento da PO primeiro.** Identificar cada linha com tudo o que o documento traz: grupo, item, código da conta, nome da conta, empresa (no grupo 1.3), serviço e descrição, valor mensal, observação, e os fundos (1.9). ~~A PO muda de modelo de um ano e de uma administradora para outra, então essa identificação é conferida por uma pessoa antes de qualquer cálculo.~~ Revisto em 29/10/2026 (Q54): o sistema precisa ler e entender bem a PO aprovada e as linhas lidas já valem; o que merece um olhar aparece como sinal, sem passo de aprovação linha a linha. [decisão Q54]
2. **Depois distribuir os gastos.** Com as linhas identificadas, cada lançamento do extrato é ligado à sua linha pelo de-para, que o sistema faz sozinho (seção 8).
3. **Só então mostrar** entradas, saídas, previsto × realizado e a origem da economia.

O problema que levou a este entendimento foi o sistema não saber interpretar a PO: confundiu linha com número do item, com empresa e com conta (seção 9). Sem o passo 1 bem feito, todo o resto sai errado.

## 1. O que é a PO

A PO (previsão orçamentária) é o **limite de gasto mensal por rubrica que a assembleia aprova para um exercício**. Ela não é plano de contas, não é lista de fornecedores e não tem numeração confiável.

- O síndico elabora o orçamento de cada ano (CC art. 1.348, VI) e a assembleia o aprova junto com as contribuições (CC art. 1.350). [fato]
- Cada condômino paga mensalmente, até o dia 10, de acordo com o orçamento do exercício, pela fração ideal (CC art. 1.336, I; Conv. 19.1 e 19.2). [fato]
- A PO só lista despesas e os fundos de reserva e de obras; não tem linhas de receita. [fato, PO p. 1]
- Sobra do exercício passa ao seguinte, salvo outro destino dado pela AGO (Conv. 18.4); déficit é rateado em até 15 dias (Conv. 18.5). [fato]

## 2. Aprovação e exercício

- O exercício vai de março a março, com a PO aprovada todo ano. [decisão D2]
- A 2026/2027 começou no fim de maio/2026, aprovada em AGE, por causa da troca de síndico. Vai de **mai/2026 a fev/2027**; se a próxima aprovação mudar de data, ajusta-se depois. [decisão D2, N4]
- Não há mudança da PO no meio do exercício: uma PO por exercício. [decisão D3]
- Entre o fim de uma PO e a aprovação da próxima, a anterior pode ser marcada "prorrogada". [decisão Q34]
- O PDF enviado é a PO final. [decisão Q48] A ata da aprovação ainda não foi enviada (P4).

## 3. Estrutura

A unidade é a **linha**: uma rubrica com valor mensal, identificada pela posição na PO aprovada. [decisão Q41, RF-13.2]

| Coluna | O que é | Chave? |
|---|---|---|
| Item | Rótulo de posição (1.3.10) | Não: "1.3.2" aparece duas vezes |
| Identificação despesa | Conta no plano usado na PO, ou uma regra ("Rateio à parte", "Valor fixo") | Não: 1606, 1693, 1600 e 1624 aparecem em duas linhas; 1.2.1 junta duas contas |
| Descrição | Natureza do gasto; no grupo 1.3, o fornecedor do contrato | Não: Servirio e Hilton Castro têm duas linhas cada |
| Orçado anterior | Limite mensal do exercício anterior | — |
| Orçado do exercício | **Limite mensal aprovado** | — |
| % Orçado | Variação entre os orçados; nos fundos, a taxa (3%, 2%) | — |
| Observações | Como o valor foi calculado | — |

Grupos 1.1 a 1.8 são despesa (piloto: 86 linhas, 451.620,13/mês pela soma das linhas, Q30); 1.9 são os fundos de reserva (3%) e de obras (2%). O total impresso 474.201,13 inclui os fundos; o anterior, 441.304,38, não. Comparar sempre na mesma base (Q37). [fato]

## 4. Economia: o que é e o que não é

- **Economia é entrar mais dinheiro do que sair.** O saldo do mês é entradas − saídas, e a funcionalidade mostra de onde veio, ligando às linhas da PO. [decisão D1, D4]
- **Linhas recorrentes** (contas de todo mês: pessoal, contratos de prestadores, telefone, internet): previsto − realizado do mês é economia ou estouro. [decisão D4]
- **Linhas eventuais** (equipamentos, materiais, serviços avulsos, pagamentos anuais): cada compra é sinalizada e comparada à linha, para mostrar se estourou no mês e no exercício. [decisão D4] ~~Não comprar **não é** economia.~~ A sobra também é economia na linha, com a marca "eventual" ou "anual". [decisão Q52, 28/10/2026, abaixo]
- **Decisão de 28/10/2026 (Q52), que substitui a D4 no ponto "não comprar não é economia":** o usuário disse: "as sobras são economia, não? A ideia é ter visibilidade do gasto daquela linha naquele mês e podermos ver se estouramos a linha ou se economizamos na linha." Para o negócio, a pergunta de cada linha é a mesma, seja ela recorrente, eventual ou anual: no mês e no exercício, gastou-se menos (economia na linha) ou mais (estouro) do que o previsto? A sobra de uma linha eventual ou anual entra em "economia nas linhas", e a lista mostra o tipo para quem lê saber que ali pode haver compra ainda por fazer. O resto da D4 continua: linhas recorrentes comparadas no mês e no acumulado; compra eventual sinalizada e comparada à linha. Provisões continuam "reservado", não economia (Q44). [decisão Q52]
- **Provisões** (férias, rescisão, 13º): a sobra é dinheiro guardado, não economia. [decisão Q44]
- **Rateio à parte** (luz, água, gás, seguro predial): orçado zero, fora da PO. As concessionárias são contabilizadas: os pagamentos aparecem nos saldos e nos gráficos, nunca como estouro da PO. [decisão Q45, N3]
- **Entrada acima da despesa prevista**: a parte do saldo que vem de entrar mais do que a despesa prevista do mês não tem linha da PO. Aparece numa linha própria, no mês e no acumulado. [decisão N2] Ex.: set/2026, saldo do mês +43.712,45 = linhas +4.316,92; provisões +2.177,25; fora da PO −4.329,17; entrada acima da despesa prevista +41.547,45. [fato, ponte de 20/10/2026]
- Entradas e saídas de cada mês ficam registradas lado a lado, para mostrar que as saídas vêm sendo menores. [decisão N2]

## 5. Saldo: mês, acumulado e fluxo contínuo

- Sempre separar o **saldo do mês** do **saldo acumulado**, e mostrar a origem da economia de cada um. [decisão N2]
- O saldo é visto como **fluxo de caixa contínuo**, inclusive antes da PO aprovada: o saldo de 116.188,70 em 30/04/2026 era conhecido e não precisa ser separado como regra. [decisão N1]
- Duas visões: o gráfico do saldo total e outra forma de mostrar antes e depois da PO aprovada. [decisão N1]
- Piloto, fundo CONDOMÍNIO: 116.188,70 (30/04) → 297.514,48 (30/09) → 381.980,34 (20/10, mês parcial). [fato, extratos]

## 6. Fundos

| Fundo | Entra | Na PO |
|---|---|---|
| Ordinário (piloto: CONDOMÍNIO) | Cota ordinária e outras receitas | Paga as linhas 1.1 a 1.8 |
| Reserva e obras | Percentual sobre a cota | 1.9.1 e 1.9.2: compara-se o que entrou |
| Rateio à parte | Rateio das contas de luz, água, gás, seguro | Fora |
| Demais (inadimplência flutuante, cota extra, locação, eventos...) | Cotas extras e pagamentos excepcionais, sinalizados no boleto | Fora |

- A PO só prevê os fundos de reserva e de obras. [decisão D5]
- Gasto pago por outro fundo fica fora da PO: no piloto, ações trabalhistas pelo fundo de reserva, com aprovação em assembleia (Conv. 17.1 d). [decisão D7]
- O conjunto de fundos muda de um mês para outro (16 em abril, 21 em setembro, 19 em outubro de 2026). [fato]

## 7. Previsto × realizado e a regra dos 20%

- Realizado = débitos do fundo ordinário por linha, pelo de-para; mês pela data do lançamento; valor como no extrato. [decisão Q18, Q19, Q21]
- Regra dos 20% (Conv. 16.2): gasto além do previsto até 20% das despesas previstas do mês; acima, AGE. Excesso somado linha a linha. [decisão Q23]
- Mês em andamento mostra só o realizado. [decisão Q50]

## 8. De-para

- Corrente: **lançamento do extrato → conta da administradora → linha da PO**. Nunca pelo número do item, pelo número da conta ou pelo fornecedor. [decisão RF-03.1.4, RF-13]
- PO e extrato usam planos de contas diferentes (Vigia e Portaria: 1692 na PO, 1442 no extrato; 1621 é "Interfones" na PO e "Material Hidráulico" no extrato). [fato]
- O de-para é por administradora, mas o resultado final é um só: não importa quem fez a contabilidade, os números finais têm de bater. [decisão D6]
- Contas pagas pela cota ordinária sem linha na PO (1688 Outros serviços contratados e 1665 Honorários advogados) ficam em "sem linha da PO", **sem alerta**: é uma decisão, não uma falha do de-para. O usuário pretende alocá-las nas despesas administrativas numa próxima PO. [decisão O7, usuário, 29/10/2026]
- **O sistema liga sozinho** [decisão Q54, usuário, 29/10/2026]: "uma das maiores dificuldades das versões anteriores era não associar automaticamente. Não podemos ficar aprovando linha a linha. O sistema precisa ler e entender muito bem a PO aprovada e já designar. Somente sinalizar se não encontrar relação, o que acho muito difícil uma vez que a planilha com todas as linhas também está disponível caso precise." A ligação automática vale sem aprovação; o Admin pode mudar qualquer uma. Só são sinalizadas as contas sem relação encontrada e as ligações de baixa confiança.
- A **planilha de apoio** da PO é a melhor ponte: a aba de despesas lista as contas da PO com o nome e o grupo de cada uma, e a origem de cada linha diz de quais contas veio o valor. O nome da conta do extrato costuma ser igual ao nome da conta da planilha (ex.: "ADIANTAMENTO QUINZENAL" = 1547 Adiantamento Quinzenal, do grupo 1545 Verbas Salariais, que é a origem da 1.1.1). O número nunca é comparado: os planos são diferentes. [fato, golden de set/26: 63 de 73 contas ligadas assim ou pelo nome, nenhuma diferente do mapa do piloto]
- Conta sem de-para cai em "sem linha da PO" e faz a linha que pagaria o gasto parecer ter sobrado (piloto mai–set: 70.834,70, ex.: 31.749,00 de auxílio alimentação na conta 1299). Completar o de-para corrige a economia sem mudar o saldo. [fato]

## 9. Armadilhas já vividas

Cada uma trocou um conceito por outro: linha = número do item; linha = fornecedor; linha = conta; total com fundos = total sem fundos; sobra de provisão = economia; não comprar = economia; mês parcial = mês fechado; fundo de rateio = receita da PO. "Criar nova PO" (RF-03.5, em pausa) é outro assunto: propor um documento para a AGO, não acompanhar um aprovado.

## 10. O que o `requisitos` precisa rever no RF-13

0. Partir do requisito principal da seção 0: uma visão de entradas e saídas mês a mês com a linha da PO de cada gasto, e a identificação confirmada das linhas antes de qualquer distribuição. Reavaliar se os oito itens de menu do RF-13.1 servem a isso ou o escondem.
1. Tipos de linha: separar **recorrentes** e **eventuais** (hoje "pela média" está junto de "mensal"). ~~A sobra das eventuais não entra em "economia nas linhas".~~ Revisto em 28/10/2026 (Q52): entra, com a marca do tipo.
2. Ponte do saldo: a parcela "receita acima ou abaixo do previsto" passa a se chamar "entrada acima da despesa prevista", no mês e no acumulado.
3. Entradas e saídas de cada mês lado a lado.
4. Saldo em fluxo contínuo, inclusive antes da PO, com a visão antes/depois da PO aprovada.
5. Exercício 2026/2027 de mai/2026 a fev/2027.
6. Concessionárias nos saldos e gráficos, fora da PO (já coberto por Q45 e RF-12).
7. Origem de cada linha e tipos "sazonal" e "anual" (seção 11).

## 11. De onde sai cada linha (planilha de apoio)

Análise de 24/10/2026 da planilha de apoio da PO 2026/2027 (`piloto-mio/fontes/PO-2026-2027-planilha-apoio.xlsx`); tabela das 86 linhas em `piloto-mio/analises/po-2026-2027-origem-das-linhas.md`. Os valores batem com o PDF aprovado. [fato] Recomendações O1–O8 aceitas pelo usuário em 25/10/2026 ("pode seguir"). Em 29/10/2026 o usuário retirou a O3 e respondeu a O7 (abaixo).

**A corrente de cada linha** [fato]: gasto real da gestão anterior por conta → uma média → um ajuste (índice, negociação ou decisão) → valor aprovado.

- Médias: ago/2025–abr/2026 para pessoal, consumo, aquisições, administrativas, materiais e eventuais; jan–abr/2026 para os contratos (1.3), porque até ago/2025 a administradora lançava os contratos numa conta só (1688) e cada contrato ganhou conta própria em set/2025.
- Método, no piloto: 19 linhas por fórmula média × (1 + índice); 7 repetem a média; 46 têm valor digitado (negociação, decisão ou valor da proposta da outra empresa); 14 não têm conta (fixo, zero, rateio à parte).
- A observação impressa não basta para deduzir o método (ex.: "média + IGP-M" com valor digitado). O PDF não traz a origem; a planilha traz.

**O que precisa ser guardado de cada linha**, além do que a seção 0 já lista: contas de origem, período da média, valor da média, método, índice com o seu valor (e se era previsão, como o dissídio de 6,5%), valor final e motivo. A PO aprovada é sempre o valor; a origem só explica. [decisão O4, O8]

**Regras que a análise confirmou** [decisão, 25/10/2026]:

- O1: a linha vale pela descrição quando a conta impressa não combina com ela. No piloto, 1.1.2 "INSS/FGTS/PIS/IRPF" traz a conta 1546 Salário; os encargos estão na 1556 Leis Sociais, que nenhuma linha cita. O de-para liga a 1556 à 1.1.2. O sistema sinaliza conta × descrição e, no de-para, não alcança essa linha pela conta impressa (desde a Q54 não há conferência humana obrigatória).
- O2: a conta analítica ganha da conta de grupo. No piloto, 1.1.1 usa o grupo 1545, que já contém salário, adiantamento, consignado, férias e 13º; férias e 13º vão para as suas linhas (1.1.5 e 1.1.12), para nada contar duas vezes. O plano de contas precisa da hierarquia grupo → conta.
- O4: o índice é o valor aplicado. O "IGP-M" do piloto vale 4,44% (igual ao IPCA); a diferença de rótulo fica anotada.
- O5 e O6: além de recorrente, eventual e provisão (seção 4), há linhas **sazonais** (reforço de verão espalhado no ano, ex.: 1.3.7 piscina, cuja média jan–abr inclui o apoio de nov a fev) e **anuais** (ex.: 1.6.21 e-mail, 2.650 por ano contra 150/mês; 13º em nov e dez). Essas se comparam no acumulado do exercício, não no mês.
- O8: valor copiado de outra proposta tem a origem registrada como tal.

**Outros fatos que o sistema precisa respeitar** [fato]:

- Uma conta pode alimentar duas linhas (1600, 1606, 1693, 1624 no piloto), e as duas puxaram a média da conta inteira; a separação é pelo fornecedor ou pela descrição (seção 8).
- Linha pode mudar de grupo de um ano para outro (caixa d'água: de 1.8 eventual para 1.3 contrato) e pode ser zerada (interfones, controle de acesso); gasto em linha zero é estouro.
- Contas com gasto e sem linha na PO: 1556 (resolvida por O1), 1688 Outros serv. contratados (24.360,72/mês em jan–abr), 1665 Honorários advogados (4.085,75/mês) e 13 contas pequenas (3.258,88/mês). São pagas pela cota ordinária e aparecem em "sem linha da PO", sem alerta (seção 8). [decisão O7, 29/10/2026]
- Folha: as colunas visíveis somam 64.339,87/mês, perto do realizado; o "custo total" de 92.196,79 (15 funcionários) vem da aba "Folha de Pagamento Mensal" da planilha de apoio e inclui 1.857,13 por funcionário sem coluna. A dúvida O3 foi **retirada** pelo usuário em 29/10/2026: a origem de 1.1.1 e 1.1.2 mostra só o que a PO e as colunas trazem, sem "a confirmar". [decisão, 29/10/2026]
- Comparar na mesma base (Q37): o PDF imprime +7,45%, mas compara total com fundos contra total sem fundos; na mesma base o aumento é 2,34%. A proposta da outra empresa fica 10.230,58/mês acima nas despesas; o comparativo da planilha (26.654,46) usa uma versão anterior da PO e bases diferentes. Todo número comparado diz a versão, a base e o período da média.

## Fontes

PO 2026/2027 (`piloto-mio/fontes/PO-2026-2027-aprovada.pdf`), convenção e RI do piloto (`piloto-mio/fontes/`, OCR conferido no PDF), extratos de abr a out/2026, planilha de apoio da PO (`piloto-mio/fontes/PO-2026-2027-planilha-apoio.xlsx`), `piloto-mio/analises/po-2026-2027-origem-das-linhas.md`, `piloto-mio/analises/ponte-do-saldo-linha-a-linha-2026-10-20.md`, `docs/requisitos.md` (§3.1 e §10). Documento de trabalho com o histórico das perguntas: Claude Doc "PO: entendimento do negócio".
