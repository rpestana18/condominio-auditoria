# ADR 0005: Análise da PO (vários exercícios, comparação e indicadores)

- **Status:** aprovada pelo usuário em 07/10/2026 (todas as recomendações, perguntas 1 a 6)
- **Complementa:** ADR 0004 (reaproveita a leitura da PO, o de-para, o `CalculoPrevistoRealizado` e o cálculo na consulta, sem mudar nenhum deles). Não muda a ADR 0002: não há fila, canal gRPC nem serviço novo.
- **Requisitos atendidos:** RF-11.1 a RF-11.7, RF-11.9 a RF-11.13, com as respostas Q31 a Q36 de 06/10/2026. RF-11.8 e RF-11.14 (exportação) estão adiados pelo usuário (Q36). RF-11.15 (projeção) é só esboço e não é decidido aqui.
- **Não decide:** PO de outras administradoras (RF-00.5), ferramentas MCP (RF-08.1), exportação, projeção.

## Contexto

O RF-11 deixa para esta ADR: (1) onde fica a correspondência de linhas entre exercícios; (2) se o resultado de vários exercícios é calculado na consulta ou guardado; (3) o formato das consultas da API; (4) os tipos de gráfico. As respostas Q32 e Q34 acrescentam: (5) como representar a "PO anterior pela coluna impressa" e a troca pelo arquivo; (6) como guardar a PO prorrogada.

Estado real do código (07/10/2026, `main` em `9f454f1`):
- `backend.orcamento` já guarda várias POs por condomínio (`previsao_orcamentaria`, V7), com exercício, versão e substituição por reaprovação (`ConfirmacaoPrevisao.sobrepostas`). A confirmação já recusa sobreposição que não seja reaprovação, o que atende o RF-11.2.
- `linha_po.orcado_anterior` guarda a coluna "Orçado anterior" de **todas** as linhas, inclusive as de grupo e de total (V7). A conferência dessa coluna pode ser feita na consulta, sem leitura nova.
- `CalculoPrevistoRealizado.calcular(Entrada)` é uma função pura. O período `Acumulado` já devolve os 12 meses do exercício (`MesExercicio`), com situação de cada mês, previsto e realizado. `ConsultaPrevistoRealizado` monta a entrada a partir do banco, a cada consulta (ADR 0004, Decisão 5).
- A API `GET /previsto-realizado?periodo=&po=&fundo=` já aceita a PO. A tela filtra pela versão da PO, não pelo exercício.
- O Recharts já está no frontend. A última migração no `main` é a V13.

Fatos que pesam no desenho:
- A **mesma conta da PO aparece em mais de uma linha** da mesma PO (1606 em 1.3.5 e 1.7.2; 1693 em 1.3.7 e 1.8.1; 1624 em 1.3.2 e 1.8.8; ADR 0004, Contexto). A sugestão "pela mesma conta da PO" do RF-11.7, sozinha, juntaria essas linhas.
- Volume: um exercício tem cerca de 5 mil lançamentos por condomínio (ADR 0004, Decisão 5). Comparar 5 exercícios é calcular 5 acumulados, cerca de 25 mil lançamentos numa consulta.

São seis decisões. O usuário aprova ou troca cada uma em separado (seção "Perguntas para o usuário").

---

## Decisão 1: onde fica a correspondência de linhas entre exercícios (RF-11.7, Q33)

| Opção | Prós | Contras |
|---|---|---|
| A. Pares de linhas entre dois exercícios (`linha_origem` ↔ `linha_destino`), com estado, como o de-para | Literal ao RF-11.7. Simples para dois exercícios | Para 5 exercícios vira uma cadeia de pares (2025→2026→2027...). Divisão e junção de linhas viram grafos difíceis de somar. A comparação de 3 exercícios precisa percorrer a cadeia |
| **B. Rubrica do condomínio: cada linha da PO é ligada a uma rubrica (catálogo do condomínio). Linhas de exercícios diferentes com a mesma rubrica correspondem** | Compara qualquer número de exercícios por uma chave só. Junção e divisão ficam naturais: várias linhas da mesma PO na mesma rubrica são somadas e mostradas juntas (RF-11.7, "grupo de linhas somado dos dois lados"). É o mesmo "catálogo de linhas" que o RF-03.5 ("Criar nova PO") e a fase 3 precisam, e onde a rubrica nova "fora da PO" do RF-02B.3 vai entrar | Um conceito novo (rubrica) para o Admin entender. Se o Admin ligar uma linha velha e duas novas à mesma rubrica, perde o detalhe da divisão (mas não erra a soma) |
| C. Sem tabela: casar na consulta pela conta da PO | Nada a guardar | Contraria o RF-11.7 (só vale depois da confirmação do Admin). Junta linhas diferentes com a mesma conta (1606 em 1.3.5 e 1.7.2) |

**Recomendação: B.** Detalhes:
- Tabelas novas no schema `backend` (próxima migração livre, hoje V14):

| Tabela | Conteúdo | Observações |
|---|---|---|
| `rubrica` | condomínio, nome, grupo (1.1 a 1.9), criada a partir de qual linha, quem, quando | Nasce da confirmação: a primeira PO confirmada gera uma rubrica por linha de despesa e de fundo, já "confirmada" (é a própria PO). O Admin pode renomear; a rubrica nunca é apagada |
| `linha_rubrica` | linha da PO, rubrica, estado (`SUGERIDO`, `CONFIRMADO`, `RECUSADO`), origem (`PRIMEIRA_PO`, `CONTA_PO`, `VERSAO_ANTERIOR`, `MANUAL`), motivo | Única por linha: cada linha tem uma rubrica (várias linhas podem ter a mesma). Linha sem rubrica confirmada cai em "sem correspondência" (RF-11.6) |
| `evento_rubrica` | quem, quando, linha, rubrica e estado anteriores e novos | Só de inserção, com o mesmo gatilho do `evento_depara` (V8) |

- **Sugestão** para as linhas de uma PO nova ou anterior: a rubrica cuja linha confirmada tem a **mesma conta da PO e o mesmo grupo** (ex.: "1682 - Sindicatura Profissional" no grupo 1.3). Se mais de uma rubrica casar, ou nenhuma, não há sugestão e o Admin escolhe na lista (ou cria uma rubrica). Motivo mostrado: "mesma conta da PO e mesmo grupo: 1682 - Sindicatura Profissional, 1.3". Nova versão do mesmo exercício (reaprovação) herda a sugestão das linhas iguais, como o de-para (RF-03.1.4). Sem IA, funciona em `DESLIGADO`.
- A comparação por linha (RF-11.6, visão 3) agrupa por rubrica: em cada exercício, soma as linhas confirmadas naquela rubrica e mostra quais linhas entraram.
- Permissões e trilha como no de-para: só o Admin confirma, troca, recusa ou cria rubrica; Gestor e Usuário recebem 403.
- *Ponto para o `requisitos`*: o RF-11.7 fala em "mesma conta da PO". Pelo fato acima, a proposta é exigir também o mesmo grupo. Os critérios do RF-11.7 continuam valendo (1682 casa; 1598 Bombas e 1624 Caixa D'água ficam separadas).

---

## Decisão 2: calcular vários exercícios na consulta ou guardar (RF-11.4 e RF-11.6)

| Opção | Prós | Contras |
|---|---|---|
| **A. Na consulta, como hoje: uma função pura nova, `ComparacaoExercicios`, recebe o resultado do `CalculoPrevistoRealizado` de cada exercício (acumulado) e monta a comparação** | Nada fica velho (mudar de-para ou rubrica vale na hora). Nenhum número de um exercício isolado muda, porque é a mesma função (premissa 1 do RF-11). Testável sem banco | Comparar 5 exercícios é fazer 5 cálculos por abertura de tela |
| B. Guardar o resultado de cada mês fechado numa tabela | Leitura rápida em qualquer volume | Precisa invalidar a cada mudança de de-para, rubrica, realocação ou reprocesso. É o risco que a ADR 0004 (Decisão 5 B) já recusou |
| C. View materializada no PostgreSQL | Pouco código Java | A regra de cálculo sairia do Java testado para o SQL, duplicada. Mesmo problema de atualização da B |

**Recomendação: A.** Detalhes:
- `ConsultaPrevistoRealizado` ganha um método que devolve o `Calculo` do acumulado de uma PO já montado, para ser chamado uma vez por exercício. Os lançamentos de cada exercício são lidos numa consulta por exercício (cerca de 5 mil linhas).
- `ComparacaoExercicios` (pacote `backend.orcamento`) não acessa banco nem relógio. Entrada: os cálculos dos exercícios, as rubricas e o filtro "mesmos meses". Saída: as três visões do RF-11.6. Regras:
  - variação em R$ = atual − anterior; em % só com base diferente de zero, `BigDecimal` com 10 casas, exibida com 1 casa "meio para cima" (mesma regra da ADR 0004); base zero = "nova no exercício";
  - "mesmos meses": interseção dos meses (pelo número do mês) com situação "com fluxo" nos exercícios escolhidos; previsto e realizado somados só nesses meses;
  - "maior excesso mensal" e "meses acima do limite" vêm do `Regra20` de cada mês, calculado pela mesma função com o período `Mes`.
- Se o volume crescer (muitos exercícios por condomínio, muitos acessos), entra um cache por condomínio e exercício, invalidado pelos mesmos eventos que já disparam o recálculo de achados (`MudancaOrcamento`). Não entra agora.

---

## Decisão 3: "PO anterior pela coluna impressa" e a troca pelo arquivo (RF-11.5, Q32)

| Opção | Prós | Contras |
|---|---|---|
| **A. Exercício virtual montado na consulta a partir de `linha_po.orcado_anterior` da PO, sem tabela nova** | Nada a guardar. A troca pelo arquivo é automática: se existe PO confirmada para o exercício anterior, ela é usada; se não, a coluna. Não mexe na regra "uma PO por mês" | A consulta precisa decidir qual é o "exercício anterior" de cada PO |
| B. Criar uma `previsao_orcamentaria` sintética a partir da coluna | Reaproveita toda a tela de PO | Uma PO sem arquivo próprio quebra a regra "todo dado com arquivo, página e hash de origem" e a "uma PO por mês". Trocar pelo arquivo exigiria apagar ou substituir a sintética |

**Recomendação: A.** Regras:
- **Exercício anterior de uma PO X** = a PO confirmada mais recente cujo exercício termina antes do início de X (meses prorrogados não contam, Decisão 4). Se ela existe, é usada (com realizado, se houver fluxo). Se não existe, a coluna "Orçado anterior" de X vira o exercício virtual "AAAA/AAAA (coluna impressa)", com o rótulo de `coluna_orcado_anterior` e só previsto.
- **Conferência da coluna** (RF-11.5, mesma regra do RF-03.1.2 e da Q30): soma das linhas da coluna contra o subtotal de grupo e o total impressos na mesma coluna, tolerância de R$ 0,01. Divergência maior mostra o grupo com as duas somas. Fundos (1.9) e previsto do mês seguem a soma das linhas, como na PO confirmada.
- **Aviso de diferença** entre a PO anterior enviada e a coluna: calculado na consulta, por grupo, sem tabela. Não é achado.
- A coluna não tem rubricas próprias: cada valor está na linha da PO X, então a comparação por linha usa a própria linha de X, sem correspondência.

---

## Decisão 4: PO prorrogada (RF-11.3, Q34)

| Opção | Prós | Contras |
|---|---|---|
| **A. Colunas novas em `previsao_orcamentaria`: `prorrogada_ate` (mês), justificativa, quem e quando; evento no `evento_previsao` que já existe** | Uma alteração pequena na tabela que já tem exercício e substituição. A escolha da PO do mês fica num lugar só | Nenhum relevante |
| B. Tabela `prorrogacao` separada | Permite várias prorrogações da mesma PO | Não há caso para mais de uma prorrogação por PO; mais uma tabela |

**Recomendação: A.** Regras:
- Só o Admin, com justificativa obrigatória (RF-11.3). Recusada se algum mês prorrogado já tem PO confirmada.
- **PO do mês**: (1) a PO confirmada cujo exercício cobre o mês; senão (2) a PO cuja prorrogação cobre o mês, com o aviso "PO prorrogada"; senão (3) "sem PO aprovada para este mês". O mês prorrogado usa o de-para da PO prorrogada.
- Se depois for confirmada uma PO cujo exercício cobre um mês prorrogado, a confirmação **vale** e a prorrogação é encurtada até o mês anterior ao início da nova, com evento automático na trilha. *Ponto para o `requisitos`*: confirmar essa regra.
- Meses prorrogados **não entram no acumulado do exercício**, que continua com 12 meses (RF-03.1.10). Aparecem depois dos 12, marcados "prorrogado", com os números do mês. *Ponto para o `requisitos`*: confirmar.

---

## Decisão 5: consultas da API (RF-11.4, RF-11.6, RF-11.10 a RF-11.12)

| Opção | Prós | Contras |
|---|---|---|
| **A. Endpoints novos que devolvem os números prontos: lista de exercícios, comparação e indicadores** | O frontend só desenha: os gráficos não calculam nada (premissa 4 do RF-11). Os números dos gráficos e das tabelas saem da mesma função | Mais três endpoints no contrato |
| B. O frontend chama `previsto-realizado` mês a mês e monta comparação e gráficos | Nenhum endpoint novo | O cálculo de variação, "mesmos meses" e maiores diferenças iria para o TypeScript, fora do `BigDecimal` e do teste do golden. Contraria a regra de dinheiro do CLAUDE.md |

**Recomendação: A.** Endpoints novos em `contracts/openapi.yaml`, sob `/api/condominios/{condominioId}` (nomes finais com o agente `mcp`):
- `GET /exercicios`: exercícios do mais recente para o mais antigo, cada um com a PO (ou "coluna impressa"), meses com e sem fluxo, prorrogação, estado do de-para e das rubricas.
- `GET /comparacao-exercicios?exercicios=<poId|coluna:poId>,...&fundo=&mesmosMeses=`: as três visões do RF-11.6, com o alvo de cada valor para abrir a evidência (RF-11.6, clique).
- `GET /indicadores?po=&fundo=`: as séries dos 7 gráficos do RF-11.11, cada ponto com o alvo da evidência (RF-11.12) e a marca "sem fluxo carregado" ou "provisório" quando couber.
- Rubricas: `GET /rubricas`, `POST /rubricas` (criar), `PUT /previsoes/{poId}/rubricas/{linhaId}`, `POST /previsoes/{poId}/rubricas/lote`, `POST /previsoes/{poId}/rubricas/sugestoes`, `GET /previsoes/{poId}/rubricas/eventos`. Escrita só do Admin.
- `PUT /previsoes/{poId}/prorrogacao` (Admin) e `DELETE` para desfazer, com evento.
- O `GET /previsto-realizado` atual não muda. A tela passa a escolher o exercício pela lista do `GET /exercicios` e manda o `po` daquele exercício.
- Sem mudança no gRPC (`contracts/grpc/`) nem nas mensagens da fila.

---

## Decisão 6: gráficos (RF-11.10 a RF-11.13)

Sem alternativa relevante: o Recharts já está no projeto (T2, ADR 0004 Decisão 6) e a Q36 tirou o gráfico do PDF. Registro do desenho:
- Um componente por gráfico em `frontend/src/componentes/indicadores/`, todos lendo o `GET /indicadores`.
- Execução mensal (1): barras por mês, linha de referência em 100%. Regra dos 20% (2): barras do excesso em %, linha de referência em 20%, marca do cenário máximo. Acumulado (3): duas linhas, previsto e realizado. Realizado por grupo (4): barras empilhadas por mês. Maiores diferenças (5): barras horizontais, 10 acima e 10 abaixo. Fundos (6): barras agrupadas, arrecadado × previsto. Entre exercícios (7): barras agrupadas por grupo e exercício.
- Mês sem fluxo: o valor vai como nulo (não zero) e o gráfico mostra a marca "sem fluxo carregado" no eixo (RF-11.11).
- Cores neutras em todos os gráficos; vermelho só na barra do gráfico 2 cujo excesso passa de 20%, pela mesma comparação exata do backend (`excesso × 100 > previsto × 20`), que vem pronta no ponto. O frontend não compara valores.
- Cada gráfico tem a tabela alternativa com os mesmos pontos (RF-11.10).
- Nenhuma biblioteca nova.

---

## Impacto em cada serviço e agente responsável

| Serviço | O que muda | Agente |
|---|---|---|
| `leitor`, `rag` | **Nada** | — |
| `backend` | Migração V14 (rubricas e prorrogação); `ComparacaoExercicios`; escolha da PO do mês com prorrogação; exercício virtual da coluna impressa; endpoints de exercícios, comparação, indicadores, rubricas e prorrogação | `backend` |
| `mcp` | Nenhuma ferramenta nova. Valida o `openapi.yaml` e escreve o teste ponta a ponta | `mcp` |
| `frontend` | Menu "Análise da PO" (Q31); filtro de exercício na tela atual; telas "Comparar exercícios" e "Indicadores"; tela de rubricas para o Admin (junto da tela "PO"); `pnpm gerar-api` | `frontend` |
| `contracts/` | Só `openapi.yaml` | `mcp` |
| `docs/` | Esta ADR e `arquitetura.md` | `arquiteto` |
| `infra/` | Nada | — |

## Bibliotecas e peças

Nenhuma biblioteca, extensão do PostgreSQL ou serviço novo.

## Ordem de implementação

Um PR por passo. O golden de setembro/2026 (RF-03.1.15) roda em todos e não pode mudar.

| # | Passo | Agente | O que é testado |
|---|---|---|---|
| 1 | Migração V14, rubricas (geração na primeira PO, sugestão por conta e grupo, confirmação, trilha) | `backend` | RF-11.7: 1682 sugerida com o motivo; 1598 e 1624 separadas; 1606 em 1.3.5 e 1.7.2 em rubricas diferentes; 403 para Gestor; um evento por linha no lote; `update` e `delete` na trilha recusados |
| 2 | PO do mês com prorrogação | `backend` | RF-11.3: 04/2026 "sem PO aprovada"; prorrogada até 04/2026 usa a PO anterior com aviso; sem justificativa ou sem Admin, recusada; PO nova encurta a prorrogação |
| 3 | Exercício virtual da coluna impressa e `GET /exercicios` | `backend` | RF-11.5: 441.304,38; 22.065,22; previsto do mês 441.525,22 pela soma das linhas (o total impresso 441.304,38 não inclui os fundos) e os grupos; 1.3.20 17.195,00 → 8.000,00; troca automática quando a PO anterior é confirmada; aviso de diferença com a cópia de teste (37.000,00 × 37.661,43) |
| 4 | `ComparacaoExercicios` e `GET /comparacao-exercicios` | `backend` | RF-11.6: +10.094,91 e +2,3%; Contratos −12.357,37 e −3,5%; 1.3.25 "nova no exercício"; "mesmos meses"; golden de setembro sem mudança |
| 5 | `GET /indicadores` | `backend` | RF-11.11: 98,8%; 8,6% e cenário 8,8% "provisório"; 1.3.10 +6.793,38 entre as maiores; fundos 14.260,79 × 13.548,60 e 9.705,06 × 9.032,40; meses sem fluxo nulos |
| 6 | Menu, filtro de exercício, "Comparar exercícios" e rubricas | `frontend` | `pnpm build`; RF-11.1 por perfil; clique leva à evidência |
| 7 | "Indicadores" | `frontend` | RF-11.10 a RF-11.13: tabela alternativa igual ao gráfico; vermelho só acima de 20% |
| 8 | Ponta a ponta | `mcp` | Pela API: PO 2026/2027 e setembro, comparação com a coluna impressa; cópia de teste como 2025/2026; indicadores iguais à tela de previsto × realizado |

Os passos 1, 2 e 3 podem correr em paralelo. O frontend começa quando o `openapi.yaml` do passo 3 estiver publicado.

## Consequências

- O condomínio passa a ter um **catálogo de rubricas**, que a fase 3 e o RF-03.5 ("Criar nova PO") vão reaproveitar, e onde a rubrica "fora da PO" do RF-02B.3 vai entrar quando for entregue.
- Os números continuam sem cópia gravada. Comparar mais exercícios custa mais consultas por abertura de tela; o cache fica previsto, não implementado.
- O `rag` e o `leitor` não mudam; a PO anterior de outra administradora continua esperando o RF-00.5.

## Pontos para o agente `requisitos` (lacunas encontradas; resolvidos no RF-11.3 e no RF-11.7 em 07/10/2026)

1. Sugestão da correspondência: exigir mesma conta da PO **e mesmo grupo**, porque a mesma conta aparece em mais de uma linha (Decisão 1).
2. PO confirmada sobre meses já prorrogados: a confirmação vale e a prorrogação é encurtada? (Decisão 4)
3. Meses prorrogados fora do acumulado do exercício, mostrados depois dos 12 meses? (Decisão 4)

## Notas da implementação

- 08/10/2026: os números dos critérios de teste dos passos 3 e 4 foram corrigidos para os valores conferidos pelo `backend` e pelo teste ponta a ponta (PR #32). A decisão não mudou.
- **Para confirmação do usuário:** "exercício anterior" foi implementado como a PO confirmada que cobre o mês anterior ao início do exercício (`ServicoExercicios.anterior`). Confirme se é essa a regra desejada; nada novo foi decidido aqui.

## Perguntas para o usuário

Respondidas pelo usuário em 07/10/2026: **Sim** em todas, seguindo as recomendações.

1. Decisão 1: correspondência entre exercícios por um catálogo de **rubricas** do condomínio (reaproveitado depois na "Criar nova PO")? **Sim (recomendado)** / Não (pares de linhas entre dois exercícios)
2. Decisão 2: comparação calculada na consulta, sem gravar resultado, como no previsto × realizado? **Sim (recomendado)** / Não
3. Decisão 3: "coluna impressa" montada na consulta, trocada sozinha pela PO anterior quando ela for confirmada? **Sim (recomendado)** / Não
4. Decisão 4: prorrogação como campo da própria PO, com a PO nova encurtando a prorrogação e os meses prorrogados fora do acumulado? **Sim (recomendado)** / Não
5. Decisão 5: endpoints novos que devolvem comparação e indicadores prontos, sem cálculo no frontend? **Sim (recomendado)** / Não
6. Decisão 6: gráficos com o Recharts que já está no projeto, vermelho só acima de 20%? **Sim (recomendado)** / Não
