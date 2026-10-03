# Arquivos de referência (golden files)

Documentos reais com o resultado esperado. Toda mudança em leitor ou regra roda contra eles e não pode piorar nenhum caso.

- `privado/` fica **fora do git** porque tem dados reais do condomínio. Os testes que dependem dele são pulados quando a pasta não existe.
  - `fluxo-caixa-2026-09.pdf`: fluxo de caixa de setembro/2026 do piloto (22 páginas, 21 fundos, 423 lançamentos).
  - `fluxo-caixa-2026-09.documento-lido.json`: saída do leitor Python para esse PDF (contrato v1).

Para regenerar o JSON depois de mudar o leitor Python:

```bash
cd leitor
.venv/bin/python -c "from fastapi.testclient import TestClient; from app.main import app; \
r=TestClient(app).post('/v1/ler', files={'arquivo': open('../data/golden/privado/fluxo-caixa-2026-09.pdf','rb')}); \
open('../data/golden/privado/fluxo-caixa-2026-09.documento-lido.json','w').write(r.text)"
```
