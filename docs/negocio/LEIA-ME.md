# Entendimento do negócio

Pasta do agente `negocio` (`.claude/agents/negocio.md`). Cada arquivo explica um assunto do condomínio no mundo real, antes de virar requisito: o que é, quem decide, base legal (Código Civil, arts. 1.331 a 1.358), o que vem da convenção, do RI e das atas de cada condomínio, como aparece nos documentos do piloto e as dúvidas em aberto.

Um entendimento só entra aqui depois que o usuário o confirma. A partir dele, o agente `requisitos` escreve os RFs em `docs/requisitos.md`.

Em cada arquivo, o texto separa:
- **fato**, com a fonte (documento, cláusula ou página);
- **decisão do usuário**, com o número (Q-xx) ou a data;
- **hipótese**, marcada "a confirmar", com a pergunta que a resolve.

Os documentos reais do piloto (convenção, RI, PO, extratos) não ficam no git; ver `data/golden/LEIA-ME.md`.
