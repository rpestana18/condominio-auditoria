# Arquivos de referência (golden files)

Documentos reais com o resultado esperado. Toda mudança no leitor (`reader/`) ou numa regra roda contra eles e não pode piorar nenhum caso.

- `privado/` fica **fora do git** porque tem dados reais do condomínio. Os testes que dependem dele são pulados quando a pasta não existe.
  - `fluxo-caixa-2026-09.pdf`: fluxo de caixa de setembro/2026 do piloto (22 páginas, 21 fundos, 423 lançamentos).
  - `fluxo-caixa-2026-09.read-document.json`: saída do leitor Python para esse PDF (contrato `reader/v2`).
  - `po-2026-2027.pdf`: PO 2026/2027 aprovada do piloto (1 página, 98 linhas: total, 9 grupos e 88 linhas).
  - `po-2026-2027.read-document.json`: saída do leitor Python para essa PO (contrato `reader/v2`). Caso da leitura da PO
    (RF-03.1.1, RF-03.1.2 e RF-03.1.15) em `rag/.../parser/budget/ProtestBudgetParserGoldenTest`.
  - `po-2026-2027-planilha-apoio.xlsx`: planilha de apoio da PO 2026/2027 do piloto (origem de cada linha: contas e
    período da média, índice, método e valor final; RF-13.13), e `po-2026-2027-planilha-apoio.read-document.json`, a saída
    do leitor Python para ela (contrato `reader/v2`, células). Caso da leitura em
    `rag/.../parser/budget/SupportSheetParserGoldenTest` (86 linhas; 1.3.10 Vigia na linha 30, média × VIGILANTES RJ).
  - `fluxo-caixa-2026-09.resultado-v4.json`, `po-2026-2027.resultado-v4.json` e `po-2026-2027-planilha-apoio.resultado-v4.json`:
    mensagens `ProcessingResult` v4 que o rag publica para esses arquivos (ids fixos). São a entrada do golden do api, que
    as lê pelo contrato (`contracts/mensagens/v4`), sem usar classe do rag. Gravadas e conferidas por
    `rag/.../service/ProcessingResultGoldenTest`: sem o arquivo, o teste grava; com ele, exige saída idêntica. Os
    `*.resultado-v3.json` antigos podem ser apagados. Mudou a leitura do rag? Apague as três, rode `./gradlew :rag:test` e
    depois `./gradlew :api:test`. Os números do RF-13 revisto (Entradas e saídas, conferência das linhas, origem, ponte v5
    e fim do exercício) estão em `api/.../service/budget/CashFlowAndLineReviewGoldenTest` e `BudgetTrackingGoldenTest`.
  - `mapa-contas-fluxo-para-PO.csv`: de-para das 73 contas do piloto (planilha de sugestões do RF-03.1.5), cópia de
    `piloto-mio/`. Caso do de-para em `api/.../service/budget/AccountMappingGoldenTest`.
  - `previsto-realizado-2026-09.csv`: previsto × realizado de setembro/2026 da análise manual, cópia de `piloto-mio/`
    (caso de aceite do RF-03.1.15). Conferido linha a linha, centavo a centavo, em
    `api/.../service/budget/BudgetVsActualGoldenTest`, com os totais, grupos, regra dos 20% e fundos do RF-03.1.6 a
    RF-03.1.11.

Para regenerar os JSON depois de mudar o leitor Python (troque o nome do arquivo para a PO):

```bash
cd reader
.venv/bin/python -c "from fastapi.testclient import TestClient; from app.main import app; \
r=TestClient(app).post('/v2/read', files={'file': open('../data/golden/privado/fluxo-caixa-2026-09.pdf','rb')}); \
open('../data/golden/privado/fluxo-caixa-2026-09.read-document.json','w').write(r.text)"
```

Os `*.documento-lido.json` (contrato `leitor/v1`) não são mais lidos e podem ser apagados.
