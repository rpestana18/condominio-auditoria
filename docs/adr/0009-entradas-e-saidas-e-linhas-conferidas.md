# ADR 0009: Entradas e saídas, linhas conferidas e tipos revistos (RF-13 revisto)

- **Status:** aprovada pelo usuário em 27/10/2026 (merge do PR #62); Decisão 3 na opção B e Q52 na opção "parcela própria", recomendações, enquanto os cartões não forem respondidos; Q52 respondida pelo usuário em 28/10/2026 (ponte versão 5, ver a Decisão 5); **revisão de 29/10/2026** (Q54): Decisão 1 revista e Decisão 8 nova (de-para automático), aprovadas pela decisão do usuário de 29/10/2026
- **Requisitos atendidos:** revisão do RF-13 de 26/10/2026 (RF-13.11 a RF-13.17, menu e tela inicial revistos), a partir de `docs/negocio/po.md`
- **Complementa:** ADR 0004 (cálculo na consulta, de-para), ADR 0007 (tipo da linha, grade e ponte). Não cria serviço, fila nova ou canal gRPC (ADR 0002).
- **Número:** a ADR 0008 fica reservada para o padrão de logs, já anunciado ao usuário em 23/10/2026.
- **Q52:** ~~implementada na opção "parcela própria" (`UNUSED_OCCASIONAL`), a recomendada, até a resposta do cartão.~~ Respondida pelo usuário em 28/10/2026: a sobra das linhas eventuais e anuais é economia nas linhas. `UNUSED_OCCASIONAL` e `BalanceBridge.OCCASIONAL_SURPLUS_APART` saíram, e a ponte passou à versão 5 (ver a Decisão 5).

## Contexto

Estado do código (26/10/2026, `main` depois do PR #61):
- `linha_po` (`BudgetLine`) guarda as sete colunas da PO, a posição e o tipo da linha (`SpendingProfile`: `MONTHLY`, `PROVISION`, `ANNUAL`, `SEPARATE_APPORTIONMENT`, V19), com a tabela de eventos `SpendingProfileEvent`.
- A PO já tem início e fim do exercício (`Budget.fiscalYearStart` e `fiscalYearEnd`), informados pelo Admin na confirmação. A grade e os indicadores já percorrem de um ao outro. O piloto foi confirmado com o fim que o Admin informou na época.
- A grade (`BudgetTrackingGrid`) e a ponte (`BalanceBridge`, versão 3) são funções puras calculadas na consulta (ADR 0007, Decisão 3).
- O leitor (`reader`, contrato `contracts/reader/v2`) já lê `.xlsx` e devolve o valor de cada célula como texto, sem a fórmula. Quem interpreta a PO é o `rag` (`rag.parser`), que manda as linhas à api pela fila (`contracts/mensagens/v3`).
- O PDF da PO imprime código e nome da conta ("1546 - Salário"), o que permite comparar a conta com a descrição (O1).
- A última migração é a V20.

São sete decisões. O usuário aprova ou troca cada uma em separado.

---

## Decisão 1: conferência das linhas (RF-13.12)

> **Revisão de 29/10/2026 (Q54, decisão do usuário):** "uma das maiores dificuldades das versões anteriores era não associar automaticamente. Não podemos ficar aprovando linha a linha. O sistema precisa ler e entender muito bem a PO aprovada e já designar. Somente sinalizar se não encontrar relação, o que acho muito difícil uma vez que a planilha com todas as linhas também está disponível caso precise."
> As linhas lidas da PO aprovada passam a valer sozinhas. O estado `PENDING` continua no banco, sem migração, e é exibido como "automática"; `REVIEWED` é a marca opcional do Admin, com a mesma trilha. `BudgetLineReviewService.state` responde sempre `reviewed = true` e sem mensagem, então nenhuma tela fica sem números por linha e o aviso `LINES_NOT_REVIEWED` não é mais emitido (o código que o tratava fica, sem uso). Os avisos de `BudgetLineReviewCheck` viram sinais informativos: a tela mostra, nada bloqueia. O texto abaixo vale como histórico.

**Recomendação:** a V21 cria em `linha_po` o estado da conferência (`PENDING`, `REVIEWED`), quem conferiu e quando. Cada mudança vai para uma tabela de eventos só de inserção, como a do tipo da linha. Os avisos são calculados na consulta, numa função pura, `BudgetLineReviewCheck`, com regras fixas e testadas:
- conta impressa × descrição sem palavra em comum (O1);
- conta que aparece em mais de uma linha;
- item repetido;
- orçado zero;
- conta marcada como conta de grupo (Decisão 3).

O Admin confere linha a linha ou com "conferir todas". As telas que distribuem gasto por linha perguntam a um só serviço se as linhas estão conferidas. A resposta segue o padrão do aviso do de-para pendente (PR #58): o passo pendente vai na resposta, sem erro 4xx.

Na migração, as linhas das POs já confirmadas ficam `PENDING`. O piloto passa pela conferência uma vez, como pede o requisito.

Alternativa recusada: tratar a confirmação da PO inteira (RF-03.1.2) como conferência. Ela não registra linha a linha o que foi conferido, e o requisito pede isso.

## Decisão 2: tipos revistos (RF-13.14)

**Recomendação:** a V21 também troca os valores de `SpendingProfile`:

| Antes | Depois |
|---|---|
| `MONTHLY` (mensal) | `RECURRING` (recorrente) |
| `ANNUAL` (anual ou esporádica) | `ANNUAL` (anual) |
| (não havia) | `OCCASIONAL` (eventual) |
| (não havia) | `SEASONAL` (sazonal) |
| `PROVISION` | sem mudança |
| `SEPARATE_APPORTIONMENT` | sem mudança |

Os tipos já confirmados são mantidos com o nome novo, e as sugestões são recalculadas. Onde a sugestão nova difere do tipo confirmado, a tela mostra "a sugestão mudou". Assim a conferência da P9 aparece para o usuário em vez de sumir.

A função `SpendingProfileSuggestion` segue a ordem do RF-13.14:
1. rateio à parte;
2. provisão;
3. "anual" na observação;
4. meses do ano na descrição ou na observação;
5. grupo de aquisição, materiais ou serviços eventuais;
6. recorrente.

O nome do grupo vem da própria PO lida (1.5, 1.7 e 1.8 no piloto), nunca do número do grupo.

Os termos novos entram no glossário da ADR 0006 antes do código: `RECURRING`, `OCCASIONAL`, `SEASONAL`, linha conferida (`reviewed`) e entrada acima da despesa prevista (`incomeAbovePlannedExpense`).

## Decisão 3: origem de cada linha e a planilha de apoio (RF-13.13)

| Opção | Prós | Contras |
|---|---|---|
| A. O leitor passa a devolver também a fórmula de cada célula (`contracts/reader` v3); o `rag` lê o método pela fórmula | É a fonte mais fiel: "média × (1 + índice)" está escrito | Muda o contrato do leitor, o leitor, o `rag` e a fila no mesmo PR, só para mostrar uma explicação |
| **B. Contrato do leitor sem mudança. O `rag` lê os valores da planilha (aba da PO e aba das despesas), acha as colunas pelo cabeçalho e deduz o método pelos números: final = média × (1 + índice) até R$ 0,01 → "média × índice"; final = média → "igual à média"; senão → "valor digitado". A fila ganha a origem por linha (`contracts/mensagens/v4`, `rag` e api no mesmo PR). O Admin corrige qualquer campo** | Não mexe no leitor. Os métodos do piloto (19 por índice, 7 iguais à média, 46 digitados) são deduzidos só pelos números | A dedução falha se um valor digitado coincidir com a conta, o que é raro e fica visível para o Admin corrigir |
| C. Sem leitura: o Admin digita a origem de cada linha | Nada muda no `rag` nem na fila | 86 linhas digitadas à mão, o mesmo cansaço que o usuário recusou no de-para |

**Recomendação: B.** A origem fica numa tabela própria, `origem_linha_po` (`BudgetLineOrigin`), 1:1 com a linha, com os campos:
- contas de origem;
- período e valor da média;
- método;
- nome e valor do índice, e se o índice era previsão;
- valor final e motivo;
- fonte: planilha ou Admin, com arquivo, aba, linha e hash (RF-00, rastreabilidade).

A origem nunca entra no cálculo. O previsto continua sendo o valor da PO (O4). Quando o valor final da planilha difere do da PO, a tela mostra os dois e um aviso, sem trocar o previsto.

**Hierarquia de contas (O2):** a hierarquia não vem de nenhum documento do piloto de forma confiável, por isso fica com a pessoa. Na conferência (Decisão 1), o Admin marca "conta de grupo" numa linha (no piloto, 1.1.1 com 1545). A sugestão do de-para (`NameSuggestion`) ganha duas regras:
- descrição específica ganha da linha marcada como grupo (férias vão para 1.1.5, 13º para 1.1.12);
- quando a conta impressa não combina com a descrição, vale a descrição (O1; leis sociais vão para 1.1.2).

O piloto sugere a marca na 1.1.1 porque o nome "Verbas Salariais" não é o de uma conta analítica, e o Admin confirma.

**O3:** só muda o texto da origem das linhas 1.1.1 e 1.1.2. A estrutura não depende da resposta. (Retirada pelo usuário em 29/10/2026: a origem mostra só o que a PO e as colunas trazem.)

**Hierarquia de contas, revisão de 29/10/2026:** a planilha de apoio traz a hierarquia de forma confiável pelas somas (a conta-mãe soma as contas logo abaixo, mês a mês). O `rag` deduz a mãe de cada conta da aba de despesas e a envia na mensagem v5 (Decisão 8). A marca "conta de grupo" do Admin continua valendo nas regras de nome.

## Decisão 4: tela "Entradas e saídas" calculada na consulta (RF-13.11, RF-13.16)

**Recomendação:** uma função pura nova, `MonthlyCashFlow` (`service/calculator`). Ela usa os dados que já existem:
- os lançamentos com as suas marcas (recibo de cota, transferência, ajuste, de-para, realocação; ADR 0007, Decisão 4);
- os saldos de cada fundo (`FundBalance`).

Para cada mês ela monta as entradas, as saídas por linha da PO e por bloco, as transferências, o saldo do mês e o saldo acumulado, e confere a soma das saídas com os débitos do extrato (D6). Sem tabela nova.

Comportamento:
- ~~Linhas ainda não conferidas: as saídas saem agrupadas por conta do extrato (Decisão 1).~~ Desde a Q54 as linhas valem sozinhas; só os meses sem PO confirmada saem por conta do extrato.
- Meses antes do início do exercício: vêm com a marca `BEFORE_BUDGET` e sem linha da PO, o que dá o fluxo contínuo do N1.
- **O7:** a função não muda com a resposta. Ela só decide se a conta da 1688 ou da 1665 vai para "sem linha da PO" ou para outro fundo, conforme o extrato.

API nova (contrato em `contracts/openapi.yaml`, `pnpm generate-api` no frontend):
- `GET /cash-flow/monthly?fund=&from=&to=`, com `fund=all` para todos os fundos;
- `GET /cash-flow/entries?fund=&month=&line=&block=`: a lista de lançamentos com a linha de cada um, o arquivo e a página.

O gráfico do saldo contínuo da "Origem do saldo" usa a mesma resposta mensal.

## Decisão 5: ponte versão 4 (RF-13.15)

**Recomendação:** `BalanceBridge` passa à versão 4. Parcelas do fundo ordinário, nesta ordem:

| Código | Parcela |
|---|---|
| `LINE_SAVINGS` | Economia nas linhas: recorrentes e sazonais (desde a versão 5, também eventuais e anuais, com a marca do tipo) |
| `PROVISIONS_RESERVED` | Reservado nas provisões |
| ~~`UNUSED_OCCASIONAL`~~ | ~~Eventuais e anuais: previsto não usado~~ (saiu na versão 5, Q52, 28/10/2026: essas linhas entram em `LINE_SAVINGS`) |
| `INCOME_ABOVE_PLANNED_EXPENSE` | Entrada acima da despesa prevista (substitui `INCOME_VS_PLANNED`) |
| `OUTSIDE_BUDGET` | Fora da PO |
| `TRANSFERS` | Transferências entre fundos |
| `UNEXPLAINED` | Não explicado |

Regras:
- Cada coluna de mês carrega também o saldo do mês e o saldo acumulado (N2).
- A soma de `LINE_SAVINGS` e `PROVISIONS_RESERVED` (na versão 4, também `UNUSED_OCCASIONAL`) é igual à diferença entre o previsto e o realizado nas linhas, e o teste confere isso.
- O caso de setembro do RF-13.15 entra no golden: 6.494,17 nas três parcelas, entrada acima da despesa prevista +41.547,45 e não explicado 0,00.

**Q52:**
- ~~Se o usuário escolher "junto da entrada acima da despesa", `UNUSED_OCCASIONAL` deixa de existir, e `INCOME_ABOVE_PLANNED_EXPENSE` passa a comparar só com o previsto das linhas recorrentes, sazonais e de provisão.~~
- ~~É uma troca dentro da função, e o resto desta ADR não muda.~~
- **Respondida em 28/10/2026** (usuário: "as sobras são economia, não? A ideia é ter visibilidade do gasto daquela linha naquele mês e podermos ver se estouramos a linha ou se economizamos na linha."). Nenhuma das duas opções do cartão: o saldo das linhas `OCCASIONAL` e `ANNUAL` entra em `LINE_SAVINGS` ("Economia nas linhas"), e cada linha da lista leva o seu tipo (`effectiveProfile`) para a tela marcar "eventual" ou "anual". O item `UNUSED_OCCASIONAL` saiu do enum, do contrato e da tela, junto com a chave `OCCASIONAL_SURPLUS_APART`. `INCOME_ABOVE_PLANNED_EXPENSE` continua comparando com o previsto de todas as linhas. A ponte passou à **versão 5**; a soma `LINE_SAVINGS + PROVISIONS_RESERVED` de set/2026 continua 6.494,17. O resto desta ADR não muda.

## Decisão 6: fim do exercício (RF-13.17)

**Recomendação:** nenhuma coluna nova. O campo `fiscalYearEnd` já existe.
- Na confirmação, a sugestão do fim passa a ser o mês anterior ao início do próximo exercício pela regra "março a março" (D2). No piloto, isso dá fev/2027.
- O Admin ganha a ação "corrigir o fim do exercício", com trilha, para POs já confirmadas. No piloto, ela é usada uma vez se o fim gravado não for fev/2027.
- A grade passa a ter 10 colunas sem nenhuma mudança no cálculo.

## Decisão 7: menu e tela inicial (menu revisto, Q53)

**Recomendação:** só frontend.
- "Entradas e saídas" vira a rota de entrada da seção.
- "Previsto × realizado" sai do menu, mas a rota continua existindo. Clicar no cabeçalho de um mês da grade leva a ela, e os endereços antigos funcionam.
- O resumo da tela inicial usa os códigos da ponte versão 4.

## Decisão 8: de-para automático (Q54, 29/10/2026)

**Status:** aprovada pela decisão do usuário de 29/10/2026 (citação na revisão da Decisão 1). Substitui, no de-para, "sugestão nunca é confirmada sozinha" (Q51; ADR 0004, Decisão 5).

**Estado da ligação** (`account_mapping.status`, sem migração destrutiva):
- `AUTOMATIC` (novo): ligação feita pelo sistema que já vale no cálculo. A V22 troca as linhas `SUGGESTED` de `account_mapping` por `AUTOMATIC`; a trilha (`account_mapping_event`) não é tocada e os eventos antigos continuam `SUGGESTED`. O código não grava mais `SUGGESTED`.
- `CONFIRMED`: o Admin conferiu (opcional). O sistema nunca troca.
- `REJECTED`: o Admin recusou a ligação. A conta vai para "sem linha da PO" **com** aviso e o sistema não liga de novo.
- O que vale no cálculo é `EffectiveAccountMapping.effective` (antes `confirmed`): `AUTOMATIC` e `CONFIRMED`.
- Escolha do Admin (`setTarget`) com `confirm = false` fica `AUTOMATIC` com origem `ADMIN`, e o sistema nunca a troca.

**Destino novo** `WITHOUT_LINE` ("SEM LINHA DA PO", O7): decisão do Admin de deixar a conta fora das linhas, sem aviso, sem achado e fora do limite do provisório da ponte. Aceito também na planilha guardada. V22 amplia os `check` de `account_mapping` e `account_mapping_sheet_item`.

**Ordem das regras** (`AccountMappingService`, funções puras em `service.calculator`, sem IA):
1. versão anterior da PO (só `CONFIRMED`; `PreviousVersionCopy`);
2. planilha guardada do condomínio (`account_mapping_sheet_item`, origem `SPREADSHEET`);
3. planilha de apoio da PO (`BudgetSheetBridge`, origem nova `BUDGET_SHEET`): nome da conta do extrato normalizado (sem acento, pontuação e palavras vazias, abreviações por extenso, **mantendo os números**) igual ao nome de **uma** conta da aba de despesas; a linha é a única cuja origem (`budget_line_origin.source_accounts`) cita a conta; senão, sobe pela conta-mãe; linha com aviso O1 (`ACCOUNT_DESCRIPTION_MISMATCH`) ou marcada "vale a descrição" não é alcançada pela conta; várias linhas → decide `NameSuggestion` entre elas (nome, favorecido, descrição);
4. nome (`NameSuggestion`, as regras da Q51).

**Baixa confiança** (`account_mapping.low_confidence`, V22; regra objetiva, testada): vale no cálculo e aparece no filtro "só exceções" (`EXCEPTIONS`). Só depois das quatro regras acima, nesta ordem: (i) conta irmã na planilha de apoio (nenhuma linha cita a conta nem a mãe; **uma** linha cita outra conta da mesma mãe); (ii) empate pelo nome desfeito só pela descrição (fora da ponte; dentro da ponte, entre linhas que citam a mesma conta, é ligação firme); (iii) `weakMatch`: uma palavra do nome, fora das genéricas (`GENERIC_WORDS`), em **uma** só linha; (iv) `supplierMatch`: favorecido dos lançamentos com **duas ou mais** palavras, fora das de empresa (`COMPANY_WORDS`), na descrição de **uma** só linha do grupo de contratos. Confirmar limpa a marca.

**Contas da planilha de apoio** (`budget_sheet_account`, V22): código, nome, conta-mãe, arquivo, aba, linha e hash, por versão da PO; uma planilha nova substitui as contas da anterior. Chegam na **mensagem v5** (`contracts/mensagens/v5`: `budgetLineOrigins.accounts`, com `parentCode` deduzido pelo `rag` pelas somas da aba). A planilha não vai para o banco (ADR 0002 e CLAUDE.md).

**Quando roda:** ao confirmar a PO, ao gravar um extrato ou a planilha de apoio (evento `BudgetChanged`, `AccountMappingSuggestionListener`, depois do commit) e ao abrir a tela "De-para". Só mexe nas contas do sistema (sem ligação ou `AUTOMATIC`/`SUGGESTED` de uma regra); grava só o que mudou, um evento `AUTOMATIC` por conta, autor "sistema".

**Avisos:** `NO_CONFIRMED_MAPPING` (previsto × realizado, grade e ponte) e o provisório da ponte (PR #58) contam só as contas **sem linha encontrada** e as recusadas, nunca as `WITHOUT_LINE`. O texto passa a "N contas do extrato sem linha encontrada na PO". A regra do achado de conta sem linha (`UnmappedAccountRule`) vai à versão 2 e ignora `WITHOUT_LINE`.

**API:** `AccountMappingSummaryResponse` (`automatic`, `confirmed`, `lowConfidence`, `rejected`, `withoutMapping`), `automaticBySource` (com `budgetSheet`), filtros `EXCEPTIONS`, `AUTOMATIC` e `LOW_CONFIDENCE`, `lowConfidence` na conta e no evento. "Confirmar todas" continua, opcional.

**Medida no golden do piloto** (`AccountMappingGoldenTest`, set/26, 73 contas do extrato): sem o mapa e com a planilha de apoio, 63 ligadas (4 de baixa confiança), 0 diferentes do mapa, 10 sem linha encontrada (três delas, 1064, 1324 e 1327, não são linha nem no mapa); sem a planilha de apoio, 58 ligadas, 3 diferentes do mapa, todas de baixa confiança; com o mapa guardado, 73 iguais ao mapa e os números de set/26 do `BudgetVsActualGoldenTest` não mudam.

Alternativas recusadas: comparar o número da conta do extrato com o da PO (RF-00.8; no piloto, 1621 é Interfones na PO e Material Hidráulico no extrato); IA (o fluxo tem de funcionar com o modo `DESLIGADO`); ligar por similaridade sem limiar objetivo (não dá para testar).

---

## Plano de implementação (depois da aprovação, um PR para a fase inteira)

1. `arquiteto`: termos no glossário da ADR 0006 e `docs/arquitetura.md`.
2. `mcp`: `contracts/mensagens/v4` (origem da linha) e `contracts/openapi.yaml` (cash-flow, conferência, origem, ponte v4).
3. `backend`:
   - V21 (conferência, tipos novos, `origem_linha_po`);
   - `BudgetLineReviewCheck`, `SpendingProfileSuggestion` revista, `MonthlyCashFlow` e `BalanceBridge` v4;
   - regras O1 e O2 no `NameSuggestion`;
   - correção do fim do exercício;
   - testes com os números do RF-13 revisto.
4. `ingestao`: leitura da planilha de apoio no `rag.parser` (Decisão 3) e mensagem v4. O golden da planilha do piloto fica em `data/golden/privado/`.
5. `frontend`:
   - tela "Entradas e saídas" e a lista de lançamentos;
   - conferência e origem em "PO aprovada";
   - tipos novos;
   - gráfico do saldo contínuo;
   - menu e tela inicial.
6. `mcp`: teste ponta a ponta com os extratos de abr a out/2026 do piloto.

## Perguntas para o usuário

1. Aprova as Decisões 1, 2, 4, 6 e 7?
2. Decisão 3: B (o sistema lê a planilha de apoio e deduz o método pelos números), A (o leitor passa a ler as fórmulas) ou C (o Admin digita)?
3. Decisão 5: depende da Q52, que está no cartão de decisão.
