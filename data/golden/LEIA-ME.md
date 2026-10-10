# Arquivos de referência (golden files)

Documentos reais com o resultado esperado. Toda mudança em leitor ou regra roda contra eles e não pode piorar nenhum caso.

- `privado/` fica **fora do git** porque tem dados reais do condomínio. Os testes que dependem dele são pulados quando a pasta não existe.
  - `fluxo-caixa-2026-09.pdf`: fluxo de caixa de setembro/2026 do piloto (22 páginas, 21 fundos, 423 lançamentos).
  - `fluxo-caixa-2026-09.documento-lido.json`: saída do leitor Python para esse PDF (contrato v1).
  - `po-2026-2027.pdf`: PO 2026/2027 aprovada do piloto (1 página, 98 linhas: total, 9 grupos e 88 linhas).
  - `po-2026-2027.documento-lido.json`: saída do leitor Python para essa PO (contrato v1). Caso da leitura da PO
    (RF-03.1.1, RF-03.1.2 e RF-03.1.15) em `rag/.../parser/budget/ProtestBudgetParserGoldenTest`.
  - `fluxo-caixa-2026-09.resultado-v3.json` e `po-2026-2027.resultado-v3.json`: mensagens `ProcessingResult` v3
    que o rag publica para esses dois arquivos (ids fixos). São a entrada do golden do api, que as lê pelo contrato
    (`contracts/mensagens/v3`), sem usar classe do rag. Gravadas e conferidas por `rag/.../service/ProcessingResultGoldenTest`:
    sem o arquivo, o teste grava; com ele, exige saída idêntica. Os `*.resultado-v2.json` antigos podem ser apagados. Mudou a leitura do rag? Apague as duas, rode
    `./gradlew :rag:test` e depois `./gradlew :api:test`.
  - `mapa-contas-fluxo-para-PO.csv`: de-para das 73 contas do piloto (planilha de sugestões do RF-03.1.5), cópia de
    `piloto-mio/`. Caso do de-para em `api/.../service/budget/AccountMappingGoldenTest`.
  - `previsto-realizado-2026-09.csv`: previsto × realizado de setembro/2026 da análise manual, cópia de `piloto-mio/`
    (caso de aceite do RF-03.1.15). Conferido linha a linha, centavo a centavo, em
    `api/.../service/budget/BudgetVsActualGoldenTest`, com os totais, grupos, regra dos 20% e fundos do RF-03.1.6 a
    RF-03.1.11.

Para regenerar os JSON depois de mudar o leitor Python (troque o nome do arquivo para a PO):

```bash
cd leitor
.venv/bin/python -c "from fastapi.testclient import TestClient; from app.main import app; \
r=TestClient(app).post('/v1/ler', files={'arquivo': open('../data/golden/privado/fluxo-caixa-2026-09.pdf','rb')}); \
open('../data/golden/privado/fluxo-caixa-2026-09.documento-lido.json','w').write(r.text)"
```
