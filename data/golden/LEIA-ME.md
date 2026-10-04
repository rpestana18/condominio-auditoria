# Arquivos de referência (golden files)

Documentos reais com o resultado esperado. Toda mudança em leitor ou regra roda contra eles e não pode piorar nenhum caso.

- `privado/` fica **fora do git** porque tem dados reais do condomínio. Os testes que dependem dele são pulados quando a pasta não existe.
  - `fluxo-caixa-2026-09.pdf`: fluxo de caixa de setembro/2026 do piloto (22 páginas, 21 fundos, 423 lançamentos).
  - `fluxo-caixa-2026-09.documento-lido.json`: saída do leitor Python para esse PDF (contrato v1).
  - `po-2026-2027.pdf`: PO 2026/2027 aprovada do piloto (1 página, 98 linhas: total, 9 grupos e 88 linhas).
  - `po-2026-2027.documento-lido.json`: saída do leitor Python para essa PO (contrato v1). Caso da leitura da PO
    (RF-03.1.1, RF-03.1.2 e RF-03.1.15) em `rag/.../leitura/po/InterpretadorPoProtestGoldenTest`.

Para regenerar os JSON depois de mudar o leitor Python (troque o nome do arquivo para a PO):

```bash
cd leitor
.venv/bin/python -c "from fastapi.testclient import TestClient; from app.main import app; \
r=TestClient(app).post('/v1/ler', files={'arquivo': open('../data/golden/privado/fluxo-caixa-2026-09.pdf','rb')}); \
open('../data/golden/privado/fluxo-caixa-2026-09.documento-lido.json','w').write(r.text)"
```
