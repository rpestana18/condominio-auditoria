# ADR 0009: Entradas e saídas, linhas conferidas e tipos revistos (RF-13 revisto)

- **Status:** proposta (26/10/2026), aguardando o usuário
- **Requisitos atendidos:** revisão do RF-13 de 26/10/2026 (RF-13.11 a RF-13.17, menu e tela inicial revistos), a partir de `docs/negocio/po.md`
- **Complementa:** ADR 0004 (cálculo na consulta, de-para), ADR 0007 (tipo da linha, grade e ponte). Não cria serviço, fila nova ou canal gRPC (ADR 0002).
- **Número:** a ADR 0008 fica reservada para o padrão de logs, já anunciado ao usuário em 23/10/2026.
- **Não decide:** a Q52 (onde fica a sobra das linhas eventuais). A Decisão 5 mostra o que muda em cada resposta.

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

**O3:** só muda o texto da origem das linhas 1.1.1 e 1.1.2. A estrutura não depende da resposta.

## Decisão 4: tela "Entradas e saídas" calculada na consulta (RF-13.11, RF-13.16)

**Recomendação:** uma função pura nova, `MonthlyCashFlow` (`service/calculator`). Ela usa os dados que já existem:
- os lançamentos com as suas marcas (recibo de cota, transferência, ajuste, de-para, realocação; ADR 0007, Decisão 4);
- os saldos de cada fundo (`FundBalance`).

Para cada mês ela monta as entradas, as saídas por linha da PO e por bloco, as transferências, o saldo do mês e o saldo acumulado, e confere a soma das saídas com os débitos do extrato (D6). Sem tabela nova.

Comportamento:
- Linhas ainda não conferidas: as saídas saem agrupadas por conta do extrato (Decisão 1).
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
| `LINE_SAVINGS` | Economia nas linhas: recorrentes e sazonais |
| `PROVISIONS_RESERVED` | Reservado nas provisões |
| `UNUSED_OCCASIONAL` | Eventuais e anuais: previsto não usado |
| `INCOME_ABOVE_PLANNED_EXPENSE` | Entrada acima da despesa prevista (substitui `INCOME_VS_PLANNED`) |
| `OUTSIDE_BUDGET` | Fora da PO |
| `TRANSFERS` | Transferências entre fundos |
| `UNEXPLAINED` | Não explicado |

Regras:
- Cada coluna de mês carrega também o saldo do mês e o saldo acumulado (N2).
- A soma de `LINE_SAVINGS`, `PROVISIONS_RESERVED` e `UNUSED_OCCASIONAL` é igual à diferença entre o previsto e o realizado nas linhas, e o teste confere isso.
- O caso de setembro do RF-13.15 entra no golden: 6.494,17 nas três parcelas, entrada acima da despesa prevista +41.547,45 e não explicado 0,00.

**Q52:**
- Se o usuário escolher "junto da entrada acima da despesa", `UNUSED_OCCASIONAL` deixa de existir, e `INCOME_ABOVE_PLANNED_EXPENSE` passa a comparar só com o previsto das linhas recorrentes, sazonais e de provisão.
- É uma troca dentro da função, e o resto desta ADR não muda.

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
