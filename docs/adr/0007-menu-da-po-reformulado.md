# ADR 0007: Menu da PO reformulado (acompanhamento, fundos e origem do saldo)

- **Status:** proposta, aguardando aprovação do usuário
- **Requisitos atendidos:** RF-13.1 a RF-13.10, com as respostas Q41 a Q48 de 20/10/2026
- **Complementa:** ADR 0004 (cálculo na consulta, de-para, fundos) e ADR 0005 (vários exercícios). Não muda a ADR 0002: não há serviço, fila ou canal gRPC novo, e o contrato do leitor não muda.
- **Não decide:** OCR de PDF em imagem (Decisão 6), troca de administradora, exportação.

## Contexto

Estado do código (20/10/2026, `main` em `8b869a6`):
- `linha_po` (`BudgetLine`) já tem `id`, `position` (ordem de leitura), `printed_code`, `effective_code`, `mark` e as sete colunas da PO.
- `BudgetConfirmationService.validateCodes` exige um `effective_code` distinto quando o código impresso se repete. É a "restrição de numeração" que o usuário recusou.
- `BudgetVsActualCalculator` é uma função pura que calcula um mês ou o acumulado. `FundBalance` já guarda, por arquivo e fundo, o saldo inicial, os créditos, os débitos e o saldo final. `SourceFile` guarda o período do arquivo (`periodStart`, `periodEnd`).
- A última migração é a V18.

Fatos dos extratos do piloto (20/10/2026):
- O extrato de outubro foi emitido em 20/10, mas o cabeçalho diz "PERÍODO DE 01/10/2026 À 31/10/2026". **O período impresso não revela que o mês é parcial.** Só a data "PROCESSADO EM" revela, e o leitor não a extrai.
- Os extratos de abril a agosto, outubro e o reenvio de setembro foram gerados com "Microsoft Print to PDF" e não têm texto (cada página é uma imagem). O setembro original, salvo pelo navegador com "Salvar como PDF", tem texto. O leitor (`pdfplumber`) não lê imagem.
- O conjunto de fundos muda de um mês para outro: são 16 em abril, 21 em setembro e 19 em outubro.

São seis decisões. O usuário aprova ou troca cada uma em separado.

---

## Decisão 1: identidade da linha e fim da restrição de numeração (RF-13.2)

**Recomendação:** a linha é o próprio registro `linha_po` (o `id`, na ordem de `position`). `effective_code` passa a ser opcional, só um rótulo de exibição: quando está vazio, vale o `printed_code`. `validateCodes` deixa de recusar a confirmação por código repetido e só gera o aviso informativo "item repetido na PO". Nada no cálculo usa o código como chave (de-para, realocação e rubrica já usam o `id` da linha). Não há migração de dados: os `effective_code` já gravados continuam como rótulo.

Alternativa recusada: renumerar sozinho (ex.: 1.3.25). Ela inventa um número que não está no documento.

## Decisão 2: tipo da linha (RF-13.3)

**Recomendação:** a V19 cria uma coluna em `linha_po` com o tipo (`MONTHLY`, `PROVISION`, `ANNUAL`, `SEPARATE_APPORTIONMENT`), o estado (`SUGGESTED`, `CONFIRMED`) e uma tabela de eventos só de inserção, como a do de-para. A sugestão é uma função pura (`SpendingProfileSuggestion`) com regras fixas e testadas:
- a marca "Rateio à parte" vira `SEPARATE_APPORTIONMENT`;
- uma observação com "anual" ou "no ano" vira `ANNUAL`;
- uma conta ou descrição com "férias", "rescis" ou "13" (13º salário) vira `PROVISION`;
- todo o resto vira `MONTHLY`.

Sem IA, funciona no modo `DESLIGADO`. Só o Admin confirma. O tipo nunca muda o previsto.

## Decisão 3: grade e ponte calculadas na consulta (RF-13.4 e RF-13.6)

**Recomendação:** calcular na consulta, como manda a ADR 0004 (Decisão 5), com duas funções puras novas em `service/calculator`:
- `BudgetTrackingGrid`: chama o `BudgetVsActualCalculator` para cada mês do exercício e monta a grade (linha × mês), o acumulado e o saldo da linha. Os números de cada célula são exatamente os da tela "Previsto × realizado", e o golden de setembro continua igual.
- `BalanceBridge`: recebe os saldos (`FundBalance`), os lançamentos classificados e a grade. Devolve as parcelas do RF-13.6 e o "não explicado" (saldo final − saldo inicial − soma das parcelas). Todos os valores são `BigDecimal` com 2 casas.

API nova (contrato em `contracts/openapi.yaml`, `pnpm generate-api` no frontend):
- `GET /budget-tracking?budget=`: a grade;
- `GET /funds/movements?from=&to=`: todos os fundos, mês a mês;
- `GET /balance-bridge?fund=&from=&to=`: a ponte, com `fund=all` para a soma de todos.

## Decisão 4: classificação dos créditos e débitos dos fundos (RF-13.5)

**Recomendação:** reaproveitar as marcações que já existem nos lançamentos: recibo de cota (Q25), transferência entre fundos e ajuste (RF-03.1.6), de-para e realocação. O que não tiver marcação vai para "outros créditos" ou "outros débitos", e nunca fica escondido. Não há tabela nova.

## Decisão 5: mês parcial (RF-13.10)

| Opção | Prós | Contras |
|---|---|---|
| **A. Parcial quando o arquivo é enviado até o último dia do período (`uploadedAt` ≤ `periodEnd`). O Gestor ou o Admin pode desmarcar, com trilha** | Sem mudança de contrato. Pega o caso real (outubro enviado em 20/10) | Um extrato emitido antes do fim do mês e enviado depois dele passaria como completo. Por isso o Gestor também pode marcar o mês como parcial |
| B. O leitor extrai "PROCESSADO EM" (`contracts/reader` v3, rag e api no mesmo PR) | Usa o próprio documento | Muda três serviços e o contrato por um campo |

**Recomendação: A.** A V19 cria no arquivo (`SourceFile`) a marca de parcial e quem a confirmou. Se um dia a B for necessária, ela entra como evolução, sem quebrar a A.

## Decisão 6: PDF em imagem (OCR)

| Opção | Prós | Contras |
|---|---|---|
| **A. Sem OCR agora. O usuário gera os extratos de novo com "Salvar como PDF". O sistema avisa "PDF sem texto: gere de novo com 'Salvar como PDF'"** | Nenhuma biblioteca nova. O leitor continua determinístico | Depende de o usuário refazer os arquivos |
| B. OCR no leitor (ex.: Tesseract) | Lê qualquer PDF | Biblioteca e imagem novas no `reader`, leitura com erro de dígito em valores (risco alto para dinheiro), precisa de golden próprio |

**Recomendação: A.** O aviso de "PDF sem texto" sai do `reader` pela resposta 422 que ele já usa para arquivo ilegível, com o texto do aviso. Não há campo novo no contrato.

---

## Plano de implementação (depois da aprovação)

1. `api`: V19 (tipo da linha e mês parcial), Decisão 1 em `BudgetConfirmationService`, `SpendingProfileSuggestion`, `BudgetTrackingGrid`, `BalanceBridge`, serviços, controllers e testes, com o golden de setembro sem mudança.
2. `contracts/openapi.yaml` e `pnpm generate-api`.
3. `frontend`: menu do RF-13.1, telas "Acompanhamento da PO", "Fundos", "Origem do saldo" e "PO aprovada", e o resumo na tela inicial.
4. `reader`: aviso de PDF sem texto.

Tudo num PR só (preferência do usuário: um PR por fase).

## Perguntas para o usuário

1. Decisão 1 (linha pelo registro, código só como rótulo): aprovar?
2. Decisão 2 (tipo da linha com sugestão por regras fixas): aprovar?
3. Decisão 3 (grade e ponte calculadas na consulta, três endpoints novos): aprovar?
4. Decisão 4 (classificação pelas marcações que já existem): aprovar?
5. Decisão 5 A (parcial pela data de envio, com ajuste manual): aprovar?
6. Decisão 6 A (sem OCR; refazer os PDFs com "Salvar como PDF"): aprovar?
