# Documento de Requisitos — Sistema de Auditoria e Contabilidade do Condomínio

Versão 0.3 · 03/10/2026 (previsto × realizado) · Status: **rascunho para validação do usuário**
Elaborado em conjunto pelos papéis de *Agente de Requisitos* e *Agente Arquiteto*.

---

## 1. Objetivo

Delegar ao sistema a **conferência contábil e a auditoria mensal** das contas do condomínio e **antecipar a previsão orçamentária** dos próximos anos.

O sistema recebe os documentos que já existem hoje (balancetes, extratos, POs, contratos, folha etc.), extrai os dados, cruza cada lançamento com seu comprovante e com o extrato bancário, aponta divergências e mostra tudo em um painel com gráficos e números.

### 1.1 Fora do escopo (nesta fase)

- Integração com administradora (ex.: Superlógica) ou com bancos. Toda entrada é por **upload manual**.
- Atualização em tempo real.
- Execução de pagamentos ou qualquer escrita em sistemas externos. O sistema **só lê e analisa**.
- Conformidade LGPD (decisão do usuário: MVP local; mascaramento entra antes de ir para a nuvem — ver §9).

---

## 2. Decisões já tomadas pelo usuário

| # | Tema | Decisão |
|---|------|---------|
| D1 | Formatos de arquivo | PDF, Excel (.xlsx/.xls) e Word (.docx) |
| D2 | Origem dos dados | Upload manual pela interface; sem integração com administradora |
| D3 | Banco de dados | Sim, para guardar os dados já processados e não reprocessar a cada exibição |
| D4 | Repositório | Monorepo |
| D5 | Hospedagem | MVP **local**; nuvem só depois de pronto |
| D6 | Perfis | Usuário, Gestor, Admin (ver §4) |
| D7 | LGPD | Não tratada no MVP local; mascaramento obrigatório antes da nuvem |
| D8 | Saídas | Sob demanda do usuário; dashboard com gráficos e números |
| D9 | Módulos | Ingestão, RAG, Backend, Frontend, MCP |
| D10 | Agentes | Requisitos, Arquiteto, Ingestão, RAG, Backend, Frontend, MCP |
| D11 | Governança | Requisitos + Arquiteto definem tudo antes dos agentes de desenvolvimento; **o usuário decide as tecnologias** |
| D12 | Abrangência | **Produto genérico, para qualquer condomínio.** O Código Civil é regra fixa do sistema; convenção, RI, fundos, rubricas e parâmetros são **dados de cada condomínio** |
| D13 | Implantação | Cadastro inicial do condomínio exige o upload de **convenção, RI e PO aprovada**. Depois entram POs passadas, contratos, balancetes etc. |

---

## 3. Base legal e boas práticas (o que o sistema pode, deve ou não deve fazer)

> Pesquisa feita na web. Com a decisão D12, as regras ficam em **duas camadas**:
> - **Camada legal (fixa, igual para todos)**: Código Civil e leis federais. Fica no código, versionada.
> - **Camada do condomínio (configurável)**: convenção, RI e deliberações de assembleia. Na implantação de cada condomínio, a IA extrai os parâmetros desses documentos (rateio, juros, multas, fundo de reserva, alçadas, existência de conselho) e um Admin confirma. Os itens marcados **[PENDENTE-CONV]** abaixo são, portanto, **parâmetros por condomínio**, preenchidos na implantação.
> - Se um parâmetro da convenção violar a lei (ex.: multa de atraso de 10%), o sistema avisa e aplica o limite legal na auditoria.

### 3.1 Código Civil (Lei 10.406/2002, arts. 1.331 a 1.358) — impacto no sistema

| Artigo | O que diz (resumo) | Requisito derivado |
|---|---|---|
| 1.334 | A convenção define a quota de cada unidade, a forma de administração, a competência das assembleias e as sanções. | Parâmetros de rateio, multas e alçadas devem ser **configuráveis**, carregados da convenção. [PENDENTE-CONV] |
| 1.336, I | O condômino contribui na proporção da fração ideal, **salvo disposição em contrário na convenção**. | Regra de rateio por fração ideal como padrão; permitir rateio alternativo. Validar cada cobrança contra a fração da unidade. |
| 1.336, §1º | Em atraso: correção monetária, juros convencionados (ou os do art. 406) e **multa de até 2%**. | Regra de auditoria: **alertar multa de atraso acima de 2%** e juros diferentes do convencionado/legal. Lembrar que o art. 406 foi alterado pela Lei 14.905/2024 (taxa legal = Selic menos IPCA) — índice precisa ser parâmetro com vigência. |
| 1.336, §2º | Multa por descumprimento de deveres (obras, fachada, uso) limitada a **5× a contribuição mensal**. | Regra: alertar multas disciplinares acima desse teto. |
| 1.337 | Condômino reiteradamente faltoso: multa até 5× (com ¾ dos condôminos); antissocial: até 10×. | Regra: alertar multas acima de 10× e verificar existência de deliberação em ata. |
| 1.341 | Obras voluptuárias exigem 2/3; úteis, maioria; necessárias podem ser feitas pelo síndico; urgentes e caras exigem comunicação imediata à assembleia. | Regra: despesas classificadas como "obra" sem ata/PO correspondente geram alerta para o conselho. |
| 1.347 | Síndico eleito por até 2 anos. | Cadastro de gestões (síndico, período) para contextualizar relatórios. |
| 1.348, VI | Compete ao síndico **elaborar o orçamento** de receita e despesa de cada ano. | A PO aprovada é a **referência oficial** para comparar previsto × realizado. |
| 1.348, VII | Cobrar contribuições e multas. | Painel de inadimplência e de multas aplicadas. |
| 1.348, VIII | **Prestar contas à assembleia anualmente e quando exigidas**. | Relatórios anuais e sob demanda, exportáveis. |
| 1.348, IX | Realizar o seguro da edificação. | Regra: verificar existência e vigência da apólice (contrato categoria "Seguro"). |
| 1.349 | Assembleia pode destituir o síndico por irregularidades ou por não prestar contas. | O sistema **não acusa ninguém**: aponta divergências com evidência e deixa a conclusão para humanos (ver §3.3). |
| 1.350 | Assembleia anual obrigatória para aprovar **orçamento, contribuições e prestação de contas**. | Ciclo anual: PO aprovada → acompanhamento mensal → relatório consolidado para a assembleia. |
| 1.351–1.353 | Quóruns de deliberação. | Fora do cálculo do sistema; só registrar a referência à ata que aprovou cada PO. |
| 1.354-A | Assembleias podem ser eletrônicas (Lei 14.309/2022). | Atas em PDF de assembleia virtual são aceitas como fonte. |
| 1.356 | Conselho fiscal (3 membros, até 2 anos) **dá parecer sobre as contas do síndico**. | O perfil de uso principal é o do conselho: relatório de auditoria estruturado para apoiar o parecer. [PENDENTE-CONV: confirmar se existe conselho e suas atribuições] |

Projeto em tramitação: **PL 4.072/2019** (federal) propõe exigir balancete mensal com receitas, despesas e inadimplência a pedido dos condôminos. Não é lei; o sistema já atende ao que ele pede.

### 3.2 Boas práticas de prestação de contas (fontes de mercado)

- **Pasta mensal** em ordem padrão: boleto → nota fiscal legível → instrução/autorização de pagamento → documentos de suporte (orçamentos, contratos, ARTs, laudos) → comprovante de pagamento.
- **Rastreabilidade**: toda receita e despesa deve poder ser reconstruída a partir dos documentos.
- **Conciliação bancária**: extrato completo do mês, com banco/agência/conta, conferido lançamento a lançamento.
- **Documentação trabalhista**: folha, ponto, horas extras, adicionais, descontos, encargos (INSS, FGTS) e férias/13º provisionados.
- **Estornos** justificados por escrito.
- **Fundo de reserva**: percentual e uso definidos na convenção [PENDENTE-CONV]; uso fora da finalidade é alerta.
- **Guarda de documentos** (orientação geral, confirmar com contador): fiscais/contábeis ao menos 5 anos; trabalhistas e previdenciários 5 anos ou mais; contratos e atas por prazo indeterminado. O sistema **nunca apaga** arquivos-fonte; apenas versiona.

### 3.3 Restrições de conduta do sistema

1. O sistema produz **indícios com evidência**, não acusações. Todo alerta mostra o documento, a página/linha e a regra que o gerou.
2. Cálculos financeiros são **determinísticos** (código), não "estimados" por IA. A IA classifica, extrai, explica e sugere; o número final vem de regra auditável.
3. Toda alteração de dado (upload, reclassificação, correção manual) fica em **trilha de auditoria** com usuário, data e valor anterior.
4. Arquivo-fonte original é imutável (guardado com hash). Correções criam nova versão.

---

## 4. Perfis e permissões

Os perfis valem **por condomínio**: a mesma pessoa pode ser Gestor em um condomínio e Usuário em outro. Acima deles existe o **Super-admin da plataforma**, que cadastra condomínios e o primeiro Admin de cada um, e é o único que liga ou desliga módulos contratáveis de cada condomínio (RF-10).


| Ação | Usuário | Gestor | Admin |
|---|:-:|:-:|:-:|
| Ver dashboard, relatórios e lista de arquivos | ✔ | ✔ | ✔ |
| Exportar relatórios (PDF/Excel) | ✔ | ✔ | ✔ |
| Fazer perguntas ao assistente (RAG) | ✔ | ✔ | ✔ |
| Enviar, substituir e categorizar arquivos-fonte | — | ✔ | ✔ |
| Reprocessar arquivos, marcar alerta como resolvido/justificado | — | ✔ | ✔ |
| Editar regras de auditoria e parâmetros (frações, multas, índices) | — | — | ✔ |
| Gerenciar usuários e perfis | — | — | ✔ |
| Ver trilha de auditoria completa | — | — | ✔ |
| Excluir (lógica) arquivos e dados | — | — | ✔ |

Login local com usuário e senha no MVP. **Questão aberta Q4**: o gestor pode marcar como "justificado" um alerta sobre um lançamento que ele mesmo enviou? Sugestão: sim, mas com justificativa obrigatória e registro.

---

## 5. Categorias de arquivos

Obrigatórias (pedidas pelo usuário): **PO (previsão orçamentária)**, **Contratos**, **Balancetes**.

**Na implantação** são exigidas: Convenção, Regimento Interno e PO aprovada do ano. Sem elas o condomínio fica com status "implantação incompleta" e a auditoria só aplica a camada legal.

O exemplo real de set/2026 (ver `04-analise-fluxo-setembro-2026.md`) mostrou que o "balancete" da administradora é um **fluxo de caixa por fundos** (21 fundos: ordinário, reserva, energia, água, obras, seguro etc.). Por isso, **Fundo** passa a ser um conceito central: cada condomínio cadastra seus fundos, com finalidade e regra de uso, e todo lançamento pertence a um fundo.

Propostas para cobrir tudo que impacta o balancete (validar):

| Categoria | Exemplos | Uso principal |
|---|---|---|
| PO / Previsão orçamentária | PO aprovada do ano, POs anteriores | Previsto × realizado; série histórica; previsão |
| Balancete mensal | Demonstrativo de receitas e despesas | Base de lançamentos a auditar |
| Extrato bancário | Conta corrente, aplicações, fundo de reserva | Conciliação |
| Pasta de prestação de contas / Comprovantes | NFs, boletos, recibos, comprovantes de pagamento | Casar lançamento ↔ comprovante |
| Contratos | Portaria, limpeza, elevadores, seguro, manutenção, administradora | Valor e reajuste esperados; vigência |
| Folha e encargos | Holerites, resumo de folha, guias INSS/FGTS, férias, 13º | Conferir despesas de pessoal |
| Atas de assembleia | AGO, AGE, aprovação de PO e obras | Autorização de despesas extraordinárias |
| Convenção e Regimento Interno | Convenção, RI | Regras de rateio, multas, alçadas |
| Inadimplência / Cobrança | Relatório de inadimplentes, acordos | Receita prevista × recebida |
| Orçamentos e cotações | Propostas de fornecedores | Suporte a despesas extraordinárias |
| Laudos e certificados | AVCB, laudos de elevador, ARTs | Vigência e obrigações (alertas de vencimento) |
| Outros | — | Catch-all com classificação manual |

Cada arquivo carrega: categoria, competência (mês/ano de referência), data de envio, quem enviou, hash, versão, status de processamento.

---

## 6. Requisitos funcionais

### RF-00 Multi-condomínio e implantação
- RF-00.1 Cadastro de vários condomínios, com dados totalmente isolados entre eles.
- RF-00.2 Assistente de implantação em etapas: dados do condomínio (nome, CNPJ, administradora, contas bancárias) → upload de **Convenção, RI e PO aprovada** (obrigatórios) → a IA extrai unidades e frações, fundos, rubricas, rateio, juros, multas, fundo de reserva e alçadas → Admin revisa e confirma cada parâmetro com o trecho de origem → condomínio fica "ativo".
- RF-00.3 Depois de ativo: carga histórica opcional (POs passadas, balancetes, contratos, folha), sem ordem obrigatória.
- RF-00.4 Seletor de condomínio na interface para quem tem acesso a mais de um.
- RF-00.5 Parsers por **layout de administradora**, reaproveitáveis entre condomínios da mesma administradora.
- RF-00.6 Alteração de convenção ou RI gera nova versão dos parâmetros com data de vigência. Auditorias antigas continuam usando a versão da época.
- RF-00.7 **Hierarquia de fontes**: uma deliberação de assembleia posterior prevalece sobre o texto da convenção ou do RI no mesmo tema (ex.: arrendamento do bar da piscina, decidido depois do RI de 2016). Cada parâmetro mostra qual fonte está valendo e desde quando.
- RF-00.8 **De-para de contas**: a PO e o fluxo da administradora podem usar planos de contas diferentes (no piloto, o código 1621 é Interfones na PO e Material Hidráulico no fluxo). O casamento é feito por uma tabela de-para por condomínio, confirmada pelo Admin, nunca pelo número. A sugestão automática pelo nome é opcional, e o fluxo funciona sem IA (modo `DESLIGADO`). Detalhado em RF-03.1.4 e RF-03.1.5.
- RF-00.9 **Fundo em recomposição planejada**: o gestor pode marcar um fundo negativo como investimento deliberado. O sistema passa a acompanhar a tendência em vez de alertar pelo prazo de rateio.


### RF-01 Ingestão de documentos
- RF-01.1 Upload de PDF, XLSX/XLS e DOCX, individual ou em lote, com escolha de categoria e competência.
- RF-01.2 Sugerir categoria e competência automaticamente (IA), com confirmação do gestor.
- RF-01.3 Extrair texto e tabelas. PDF digital por extração direta; **PDF escaneado por OCR** (questão aberta Q1: existem escaneados?).
- RF-01.4 Normalizar em lançamentos estruturados: data, descrição, fornecedor/favorecido, CNPJ/CPF, conta contábil/rubrica, valor, documento de origem, página/linha.
- RF-01.5 Detectar duplicidade de arquivo (hash) e de lançamento.
- RF-01.6 Mostrar o status de cada arquivo: recebido → extraído → validado → indexado (ou erro com motivo).
- RF-01.7 **Editar a categoria de um arquivo já enviado** (pedido do usuário, 03/10/2026): na tela Arquivos, cada arquivo tem um botão "Editar" que abre a escolha da nova categoria (lista do §5), com a categoria atual marcada. Só Gestor e Admin veem o botão (§4, "categorizar arquivos-fonte"). Ao salvar, o arquivo é **reprocessado com a nova categoria**: os dados extraídos sob a categoria antiga (lançamentos, saldos de fundo e conferências) são apagados e só voltam se a nova categoria produzir esses dados. Hoje só "Balancetes e fluxos de caixa" gera lançamentos. O original não é alterado, renomeado nem movido; muda só o registro no banco.
  - Dado um Usuário (perfil sem permissão), quando abre a tela Arquivos, então o botão "Editar" não aparece, e a API recusa a troca de categoria com 403.
  - Dado um Gestor ou Admin, quando clica em "Editar", então vê as categorias do §5 com a atual marcada e um aviso: "Trocar a categoria reprocessa o arquivo e substitui os dados extraídos dele."
  - Dado um fluxo de caixa enviado por engano como "Contratos", quando o Gestor troca para "Balancetes e fluxos de caixa", então o arquivo é reprocessado e os lançamentos aparecem, sem duplicar.
  - Dado um arquivo enviado por engano como "Balancetes e fluxos de caixa", com lançamentos gravados, quando o Gestor troca para outra categoria, então os lançamentos, saldos e conferências desse arquivo somem do banco e dos totais, e o arquivo passa a aparecer na nova categoria.
  - Dado o mesmo valor de categoria já atual, quando o usuário salva, então nada é reprocessado.
  - Dado um arquivo em processamento, quando alguém tenta trocar a categoria, então a troca é recusada com a mensagem "O arquivo já está sendo processado".
  - Dada qualquer troca de categoria, quando ela é salva, então fica registrado quem, quando, a categoria anterior e a nova (§3.3, item 3). *Proposta: a trilha de auditoria (RF-07.4) ainda não existe no código; até lá, o registro fica numa tabela de histórico de categoria do arquivo, que depois migra para a trilha.*
  - Dado o arquivo original, depois da troca, então o hash e o conteúdo baixado continuam idênticos aos do envio.

### RF-02 Auditoria e conciliação (núcleo)
- RF-02.1 **Conciliação balancete × extrato**: cada lançamento do balancete casado com um movimento bancário (valor, data com tolerância, favorecido).
- RF-02.2 **Conciliação lançamento × comprovante**: cada despesa com NF/recibo/comprovante correspondente.
- RF-02.3a **Por fundo**: saldo anterior + entradas − saídas = saldo atual de cada fundo; soma dos fundos = posição financeira; alerta para fundo com saldo negativo, para uso de fundo fora da finalidade e para valores negativos em colunas de crédito ou débito; transferências entre fundos são eliminadas na consolidação.
- RF-02.3 **Conferência aritmética**: somas de grupos, saldo inicial + receitas − despesas = saldo final; saldo final do mês = saldo inicial do mês seguinte; saldo do balancete = saldo do extrato.
- RF-02.4 **Contrato × pagamento**: valor pago dentro do contratado, reajuste conforme índice e data-base, pagamento fora da vigência.
- RF-02.5 **Folha × lançamentos**: total da folha, encargos e provisões batem com o balancete.
- RF-02.6 **Regras legais/convencionais** do §3 (multa > 2%, multas acima dos tetos, obra sem ata, seguro vencido, uso do fundo de reserva).
- RF-02.7 **Anomalias**: lançamento duplicado, valor fora do padrão histórico da rubrica, fornecedor novo, pagamento em fim de semana/feriado, valor redondo atípico, rubrica que não existe na PO.
- RF-02.8 Cada achado tem severidade (crítico/atenção/informativo), evidência (documentos + localização) e estado (aberto, justificado, resolvido, falso positivo) com comentário.
- RF-02.9 **Justificativa com fonte**: o gestor pode justificar um achado apontando um documento (ata, contrato, convenção). Se existir ata que autoriza a despesa (ex.: uso do fundo de reserva para ações trabalhistas), o sistema encontra a ata via RAG, sugere a justificativa e passa a aceitar automaticamente os lançamentos seguintes da mesma autorização (ex.: as parcelas restantes), dentro do valor e do prazo aprovados.
- RF-02.10 **Regras de política do condomínio**: políticas aprovadas em assembleia viram parâmetros que mudam a leitura dos fundos. Exemplo: **inadimplência flutuante**, em que o fundo de inadimplentes é consumido de propósito quando a inadimplência não afeta o caixa. Nesse caso, a queda do fundo é esperada e só gera alerta se a inadimplência real voltar a pressionar o caixa ordinário.

### RF-02B Reclassificação de meios de pagamento
Alguns lançamentos chegam numa linha que é **meio de pagamento** e não natureza de despesa (ex.: Mercado Pago, cartão de crédito do condomínio, reembolsos).
- RF-02B.1 Admin marca quais contas/fornecedores são meio de pagamento (no piloto: Mercado Pago e 1064 Desp. Cartão Crédito).
- RF-02B.2 Fila "a realocar" com todas as compras feitas por esses meios, com descrição, valor e comprovante.
- RF-02B.3 Para cada compra, a IA **sugere a rubrica correta** com base no histórico e nas rubricas da PO. O gestor confirma, escolhe outra ou **cria uma nova rubrica** (que fica marcada como "fora da PO" até a próxima previsão).
- RF-02B.4 A realocação é uma **camada do sistema**: não altera o lançamento original da administradora, que continua rastreável. O previsto × realizado usa a rubrica realocada; enquanto não é realocada, a compra fica no grupo "a realocar" e não entra em nenhuma linha da PO (detalhado em RF-03.1.7).
- RF-02B.5 Realocações aprendidas viram regras (ex.: "tinta" vai para Material de Pintura) e são aplicadas sozinhas nos meses seguintes, com revisão do gestor.

### RF-03 Orçamento e previsão
- RF-03.1 Previsto (PO) × realizado por linha da PO, mês a mês, por fundo e acumulado no exercício da PO. Detalhado em RF-03.1.1 a RF-03.1.15, ao fim desta seção.
- RF-03.2 Série histórica com as POs e balancetes de anos anteriores. Detalhado no menu "Análise da PO" (RF-11.1 a RF-11.9).
- RF-03.3 (esboço da fase 3 no RF-11.15) Projeção do próximo ano por rubrica considerando histórico, contratos vigentes (reajustes), dissídio dos funcionários e índices (IPCA/IGP-M) — com cenários (base, otimista, pessimista) e a premissa de cada número visível.
- RF-03.4 Simulação da cota condominial por unidade resultante da projeção.
- RF-03.5 **Tela "Criar nova PO"** (a ser desenhada junto com o usuário):
  - parte da PO aprovada vigente, com o realizado acumulado e a projeção de cada linha ao lado;
  - mostra o **catálogo de linhas (rubricas) do condomínio**, inclusive as que não estão na PO atual, para incluir, excluir ou renomear;
  - sugere o valor de cada linha com base em histórico, contratos e reajustes (índice e data-base), dissídio e as premissas da coluna "Observações" da PO atual (ex.: "média jan/abr + IPCA");
  - permite editar o valor e a premissa de cada linha e simular a cota por unidade (fração ideal) e os fundos (reserva e obras em %);
  - compara com a PO anterior (coluna "%"), como no documento aprovado;
  - exporta no formato de apresentação para a AGO e guarda versões (rascunho, apresentada, aprovada com a ata).
  - Pendente: o usuário vai enviar o catálogo completo de linhas, além das que estão na PO.

#### Detalhamento do RF-03.1: Previsto × realizado (entrega aprovada pelo usuário, 03/10/2026)

Origem: pedido do usuário de 03/10/2026 ("ler a PO aprovada, ligar cada conta da PO às contas do balancete (de-para) e mostrar, mês a mês e por fundo, o previsto contra o realizado, com as diferenças"); CC art. 1.348, VI (o síndico elabora o orçamento) e art. 1.350 (a assembleia aprova o orçamento), ver §3.1; Convenção do piloto, cláusulas 10.2, 16.1 IX, 16.2, 18.4 e 20.1; análise manual de setembro/2026 do piloto (`piloto-mio/06-previsto-realizado-2026-09.md` e `previsto-realizado-2026-09.csv`), que é o **caso de aceite**; de-para sugerido (`mapa-contas-fluxo-para-PO.csv`); implantação do piloto (`05-implantacao-parametros.md`). Este bloco detalha RF-00.8, RF-02B.4, RF-03.1, RF-05.6, RF-06.1 e o §8, item 5. Não cria funcionalidade além delas.

Situação atual (03/10/2026): o `rag` já interpreta o Fluxo de Caixa da administradora em lançamentos por fundo e conta. A PO pode ser enviada como arquivo na categoria PO, mas **não é lida**: nenhum valor previsto está gravado. Não existe tabela de-para nem tela de previsão. O previsto × realizado de setembro foi feito à mão, fora do sistema.

Fora destes requisitos (decisão do arquiteto em ADR, com aprovação do usuário): em qual serviço e com qual técnica a PO é lida (extração do PDF, reconhecimento do layout); onde e como ficam guardados a PO lida, o de-para e o resultado; o contrato entre os serviços para a PO lida (`contracts/`); o método da sugestão automática pelo nome; quando o cálculo roda (ao enviar, ao confirmar ou sob demanda); a técnica de geração de PDF e Excel e de gráficos. Ficam fora também, para requisito próprio: ferramenta MCP de previsto × realizado (RF-08.1); PO de outras administradoras (RF-00.5); projeção e "Criar nova PO" (RF-03.3 a RF-03.5); auditoria de energia, água e gás por arrecadado × conta paga (achados A2 e A3 da análise de setembro).

Premissas (padrões adotados; o usuário pode mudar):
1. A primeira PO lida é o PDF da administradora do piloto (`fontes/PO-2026-2027-aprovada.pdf`, uma página). O layout de outras administradoras entra depois (RF-00.5).
2. O Admin cadastra e confirma o de-para. Uma sugestão automática pelo nome pode existir, mas todo o fluxo funciona sem IA (modo `DESLIGADO`, RF-09).
3. Uma conta do fluxo sem de-para confirmado aparece como "sem linha da PO" e **nunca é somada em silêncio a outra linha**.
4. As diferenças são **indícios com evidência** (lançamentos, arquivo, página e hash de origem), nunca conclusões (§3.3). O sistema não escreve a causa de uma diferença.
5. Q18 a Q26 foram respondidas pelo usuário em 04/10/2026, seguindo as recomendações (§10). As respostas reproduzem a análise manual de setembro: mês pela data do lançamento, valor como está no fluxo, acumulado do exercício da PO, fundos de reserva e de obras comparados pela arrecadação, "rateio à parte" fora da comparação e excesso da regra dos 20% somado linha a linha.

Termos usados abaixo:
- **PO**: previsão orçamentária aprovada, com arquivo, página e hash de origem, versão, exercício e ata de aprovação (quando enviada).
- **Exercício**: os 12 meses de vigência da PO. É parâmetro da PO (mês inicial e final), não do calendário. No piloto: mai/2026 a abr/2027 (informado pelo gestor em 03/10/2026; a ata que aprovou a PO está pendente, P4).
- **Linha da PO**: item impresso com código (ex.: 1.3.20), conta da PO (ex.: 1682 - Sindicatura Profissional), descrição, orçado do exercício anterior, orçado do exercício (valor **mensal**), coluna "%" e coluna "Observações".
- **Grupo**: subtotal impresso na PO: 1.1 Pessoal, 1.2 Consumo/utilidades, 1.3 Serviços - contratos efetivos, 1.4 Tarifas públicas, 1.5 Aquisição de bens, 1.6 Despesas administrativas, 1.7 Materiais/suprimentos, 1.8 Serviços e 1.9 Fundos.
- **Previsto do mês**: soma das linhas de despesa (grupos 1.1 a 1.8), sem os fundos (1.9). Piloto: R$ 451.620,13, pela soma das linhas. O total impresso menos os fundos impressos dá 451.620,12; a diferença de R$ 0,01 é arredondamento da planilha de origem (tolerância do RF-03.1.2, Q30).
- **Conta do fluxo**: código e nome da conta usados pela administradora no fluxo de caixa (ex.: 1442 Vigia e Portaria). É outro plano de contas, diferente do da PO.
- **De-para**: ligação de cada conta do fluxo a **um** destino: uma linha da PO; "Ajuste (não é despesa)"; "Meio de pagamento (a realocar, RF-02B)"; ou "Transferência entre fundos". Estados: sugerido, confirmado, recusado. Várias contas do fluxo podem ir para a mesma linha (ex.: 1225, 1227 e 1232 → 1.1.1 Salários).
- **Realizado**: soma dos débitos do fundo Condomínio no mês, por linha da PO, pelo de-para confirmado e pelas realocações (RF-02B.4), sem ajustes e sem transferências entre fundos.
- **Sem linha da PO**: conta do fluxo com lançamento no mês e sem de-para confirmado. Fica em grupo próprio.
- **Diferença**: realizado − previsto. Positiva = acima do previsto; negativa = abaixo.
- **Execução**: realizado ÷ previsto, em %.
- **Excesso do mês**: soma das diferenças positivas, linha a linha, das linhas da PO (regra dos 20%, Conv. 16.2). Piloto, set/2026: R$ 38.880,19.
- **Acumulado**: soma dos meses do exercício que já têm fluxo de caixa carregado.
- Valores em reais com 2 casas, sem arredondamento intermediário. *Proposta: percentuais exibidos com 1 casa, arredondamento "meio para cima" (ex.: 8,609% → 8,6%).*

**Leitura da PO**
- RF-03.1.1 **Ler a PO aprovada** no layout da administradora do piloto, a partir do arquivo enviado na categoria PO. Cada linha é gravada com código, conta da PO, descrição, orçado anterior, orçado do exercício, "%" e Observações, mais arquivo, página e hash de origem. O arquivo original não é alterado. "%" e Observações são guardados como texto lido e não entram em cálculo. Linhas com "Rateio à parte", "Negociada isenção", "Sem valor" ou "Valor fixo (sem referência)" na coluna de conta são lidas com essa marca, e não como conta.
  - Dado `PO-2026-2027-aprovada.pdf`, quando a leitura termina, então a linha 1.3.20 tem conta "1682 - Sindicatura Profissional", descrição "Obm - Sergio Diniz", orçado 2025/2026 R$ 17.195,00, orçado 2026/2027 R$ 8.000,00, "%" "-53,47%" e Observações "Pro-labore Síndico", com página 1 e o hash do arquivo.
  - Dado a mesma PO, quando lida, então a linha 1.3.23 tem conta "1621 - Interfones", valor R$ 0,00 e Observações "Manutenção R$4.100,00 out/25".
  - Dado a mesma PO, quando lida, então as linhas 1.4.1 (Força e Luz), 1.4.2 (Água e Esgoto), 1.4.3 (Gás) e 1.6.15 (Seguro predial) têm valor R$ 0,00 e a marca "rateio à parte".
- RF-03.1.2 **Conferir a leitura** contra o próprio documento: a soma das linhas de cada grupo é igual ao subtotal impresso; a soma dos grupos é igual ao total impresso; o previsto do mês é o total menos os fundos. Também aponta código de linha repetido. Se algo não bate, a PO fica "lida com divergência", mostra onde está a diferença e não é usada no previsto × realizado até o Admin corrigir e confirmar.
  - **Tolerância de arredondamento** (decisão do usuário, 04/10/2026, Q30): diferença de até **R$ 0,01** entre a soma das linhas e o subtotal ou total impresso não é divergência. Vira aviso informativo "diferença de arredondamento", a PO não fica "lida com divergência" e os cálculos usam a soma das linhas. Diferença maior que R$ 0,01 segue a regra acima.
  - Dado a PO do piloto, quando a conferência roda, então batem exatamente: Pessoal 69.193,86; Consumo/utilidades 694,05; Tarifas públicas 0,00; Aquisição de bens 2.850,00; Administrativas 17.388,04; Materiais 15.200,00; Serviços 10.020,00; total 474.201,13. E batem dentro da tolerância, com aviso de arredondamento: Contratos (linhas 336.274,18; impresso 336.274,17) e Fundos (linhas 22.581,00; impresso 22.581,01). A PO fica "lida", não "lida com divergência". Previsto do mês pela soma das linhas: 451.620,13.
  - Dado uma cópia de teste da PO com um subtotal impresso diferente da soma das linhas, quando lida, então o estado é "lida com divergência", o grupo divergente é mostrado com as duas somas, e a tela de previsto × realizado mostra "PO não confirmada" em vez de números.
  - Dado a PO do piloto, que imprime o código **1.3.2 duas vezes** (Bombas, R$ 3.000,00, e Caixa D'água, R$ 1.518,93), quando lida, então o sistema aponta "código repetido" e o Admin dá um código distinto à segunda linha antes de confirmar (a análise manual usou 1.3.25). Nenhuma das duas linhas é descartada nem somada à outra.
  - **Subtotal impresso errado na origem** (Q29, pendente; critérios seguem a recomendação). Origem: ADR 0004, Decisão 8 (o Admin não edita valores lidos); §3.3, itens 3 e 4 (original imutável, alteração na trilha). Quando a leitura confere com o PDF e o erro está no próprio documento, o Admin pode **confirmar ciente da divergência**, com justificativa obrigatória. Vale só para as conferências de soma (subtotal de grupo, total e previsto do mês); código repetido continua exigindo código distinto. Os valores lidos nunca são editados. Os cálculos usam sempre as **linhas**: subtotal do grupo = soma das linhas; previsto do mês = soma das linhas dos grupos 1.1 a 1.8; fundos = valores das linhas 1.9.x. A divergência fica visível na tela e na exportação como aviso, não como achado (matriz abaixo).
    - Dado a cópia de teste da PO com o subtotal de Pessoal impresso 69.193,00 (soma das linhas 69.193,86), quando o Admin confirma "ciente da divergência" com justificativa, então a PO fica confirmada, o grupo Pessoal usa 69.193,86, o previsto do mês é 451.620,13, e a tela e o cabeçalho da exportação mostram "PO confirmada com divergência: Pessoal impresso 69.193,00; soma das linhas 69.193,86".
    - Dado a mesma cópia, quando o Admin tenta confirmar ciente sem justificativa, então a confirmação é recusada.
    - Dado a confirmação ciente, quando salva, então a trilha registra quem, quando, as conferências que falharam (com as duas somas) e a justificativa.
    - Dado um Gestor ou Usuário, quando tenta confirmar ciente (tela ou API), então a ação é recusada (403).
    - Dado a PO do piloto com o 1.3.2 repetido ainda sem código distinto, quando o Admin tenta confirmar ciente, então a confirmação é recusada: "código repetido" não é divergência de soma.
    - *Se Q29 for "Não": PO com divergência de soma nunca é confirmada; o caminho é enviar a PO corrigida (nova versão).*
- RF-03.1.3 **Confirmar a PO e o exercício**: o Admin confirma a leitura e informa o exercício (mês inicial e final) e a ata que aprovou a PO. O início do exercício vem da data da ata. Sem ata, o Admin informa o exercício, que fica marcado "sem ata" como pendência de implantação. PO aprovada depois do 1º trimestre gera só um **aviso informativo** (Conv. 10.2; decisão do gestor em 03/10/2026, `05-implantacao-parametros.md` §8), nunca achado. Reaprovação cria nova versão da PO; comparações já exportadas mantêm a versão usada (como no RF-00.6). Só uma PO vale para cada mês de cada condomínio.
  - Dado a PO do piloto confirmada com exercício 05/2026 a 04/2027, quando o usuário abre o previsto × realizado de 09/2026, então essa PO é usada; e quando abre 04/2026, então aparece "sem PO aprovada para este mês".
  - Dado a PO aprovada em maio/2026, quando confirmada, então aparece o aviso informativo "PO aprovada fora do 1º trimestre (Conv. 10.2)" e nenhum achado é criado.
  - Dado a PO do piloto, quando confirmada, então os fundos lidos são reserva 3% (R$ 13.548,60/mês) e obras 2% (R$ 9.032,40/mês), conferidos como 3% e 2% de 451.620,13. Na coluna "%", as linhas 1.9.1 e 1.9.2 trazem a **taxa do fundo** (3,00% e 2,00%), não a variação contra a PO anterior como nas demais linhas; o sistema não as trata como variação.
  - Dado um percentual do fundo de reserva na PO acima do máximo da convenção (Conv. 20.1: até 5%), quando a PO é confirmada, então é criado um achado "atenção". Piloto: 3%, sem achado.

**De-para (detalha RF-00.8 e RF-07.2)**
- RF-03.1.4 **Tabela de-para por condomínio e por versão da PO**: cada conta do fluxo vai para exatamente um destino (Termos). O casamento **nunca** usa o número da conta. Só o Admin cria, altera, confirma e recusa; Gestor e Usuário só consultam. Toda alteração vai para a trilha de auditoria (RF-07.4) com quem, quando, destino anterior e novo. Numa nova versão da PO, o de-para da versão anterior é oferecido como sugestão, e nenhuma conta herda a confirmação: como toda sugestão, só vale depois de confirmada pelo Admin (RF-03.1.5, "sugestão nunca é confirmada sozinha"; premissa 2). Linha **igual** à da versão anterior = mesmo código efetivo, mesma conta da PO e mesma descrição; o valor orçado pode mudar (ADR 0004, Decisão 4). Contas ligadas a linha igual entram "sugerido" com o motivo "igual à versão anterior" e podem ser confirmadas em lote. Contas ligadas a linha que mudou ou deixou de existir entram "sugerido" com o motivo "linha mudou" (mostrando antes e depois) ou ficam sem sugestão, com o motivo "a linha X da versão anterior não existe nesta versão". Os destinos especiais (ajuste, a realocar, transferência) seguem a mesma regra. Os meses da versão anterior continuam com o de-para dela (RF-00.6). Alterar o de-para recalcula o previsto × realizado dos meses afetados.
  - Dado a conta do fluxo 1621 "Material Hidráulico" e a linha da PO 1.3.23 "1621 - Interfones", quando o de-para é sugerido ou confirmado, então a ligação 1621 → 1.3.23 nunca é proposta por coincidência de número; no piloto, o destino sugerido é 1.7.8 Material Hidráulico.
  - Dado as 73 contas do `mapa-contas-fluxo-para-PO.csv` carregadas, todas "sugerido", quando o usuário abre setembro/2026, então nenhuma conta entra em linha da PO: os 176 débitos (R$ 449.455,13) aparecem em "sem linha da PO", as linhas da PO aparecem com o previsto e realizado "de-para pendente", e o aviso "73 contas sem de-para confirmado" é exibido.
  - Dado o Admin que confirma as 73 sugestões sem mudança, quando o usuário abre setembro/2026, então o resultado é o do RF-03.1.6.
  - Dado um Gestor ou Usuário, quando tenta confirmar ou alterar o de-para (tela ou API), então a ação é recusada.
  - Dado o Admin que muda a conta 1284 (M1 Auditoria Preventiva, R$ 990,00) de 1.3.15 para outra linha, quando salva, então a trilha registra o destino anterior (1.3.15) e o novo, e o realizado de 1.3.15 em setembro passa de 990,00 para 0,00.
  - Dado (exemplo hipotético) uma versão 2 da PO do piloto, igual à versão 1 exceto pelo valor da 1.3.20 (8.000,00 → 9.000,00) e pela descrição da 1.7.8 (de "Material Hidráulico" para "Material hidráulico e elétrico"), quando a versão 2 é confirmada, então: a conta 1108 entra "sugerido" para 1.3.20 com o motivo "igual à versão anterior" (o valor não conta); as contas que iam para 1.7.8, entre elas a 1621, entram "sugerido" com o motivo "linha mudou" ou sem sugestão; nenhuma conta está "confirmado"; e os meses da versão 2 mostram "de-para pendente" até o Admin confirmar.
  - Dado o mesmo caso, quando o Admin filtra "iguais à versão anterior" e confirma em lote, então todas essas contas passam a "confirmado" de uma vez, com um evento por conta na trilha, e as contas da 1.7.8 continuam "sugerido".
  - Dado o mesmo caso, quando o usuário abre um mês da versão 1, então os números e o de-para da versão 1 não mudam.
- RF-03.1.5 **Sugestão opcional, fluxo sem IA**: o sistema pode sugerir o destino pelo nome da conta (por IA, quando o modo permite, ou por comparação de texto). Sugestão **nunca** é confirmada sozinha e mostra o motivo (os nomes comparados). Sem sugestão, o Admin escolhe a linha numa lista das linhas da PO, com busca por código e por nome. *Proposta: o Admin pode carregar uma planilha de sugestões (conta do fluxo; linha da PO), como a do piloto; tudo entra como "sugerido".*
  - Dado um condomínio em modo de IA `DESLIGADO`, quando o Admin faz o de-para das 73 contas escolhendo a linha na lista, então o de-para fica completo e nenhuma chamada de IA é registrada (RF-09.7).
  - Dado uma sugestão pelo nome, quando exibida, então ela fica "sugerido" até o Admin confirmar ou recusar, e não altera nenhum número.

**Realizado**
- RF-03.1.6 **Realizado do fundo Condomínio no mês**: soma dos débitos do fundo Condomínio por linha da PO, conforme o de-para confirmado. São mostrados à parte, fora das linhas: ajustes ("não são despesa"), "a realocar" (RF-02B) e "sem linha da PO". Transferências entre fundos ficam fora. Linha da PO sem lançamento aparece com realizado R$ 0,00. A tela mostra também a **conferência com o fluxo**: total de débitos do fundo = despesa realizada + ajustes.
  - Dado o fluxo de setembro/2026 do piloto, a PO confirmada e as 73 contas confirmadas como no mapa, quando o usuário abre 09/2026, fundo Condomínio, então: débitos do fundo = 176 lançamentos, R$ 449.455,13; ajustes R$ 3.278,24 (estorno de 3.987,12 na conta 1324 e repasse de −708,88 na conta 1327), fora da despesa; a realocar R$ 1.050,93 (conta 1064); despesa realizada R$ 446.176,89, dos quais R$ 445.125,96 em linhas da PO; previsto do mês R$ 451.620,13; diferença −5.443,24; execução **98,8%**; sem linha da PO R$ 0,00.
  - Dado o mesmo caso, quando o usuário vê por grupo, então os valores são (previsto; realizado; diferença): Pessoal 69.193,86; 62.815,41; −6.378,45 · Consumo/utilidades 694,05; 754,94; +60,89 · Contratos 336.274,18; 341.277,13; +5.002,95 (soma das linhas, Q30) · Aquisição de bens 2.850,00; 8.958,32; +6.108,32 · Administrativas 17.388,04; 14.264,32; −3.123,72 · Materiais 15.200,00; 16.910,40; +1.710,40 · Serviços 10.020,00; 145,44; −9.874,56.
  - Dado o mesmo caso, quando o usuário vê por linha, então cada linha de despesa (1.1.1 a 1.8.7) tem previsto, realizado e diferença iguais, centavo a centavo, aos do `previsto-realizado-2026-09.csv` (ex.: 1.1.5 Férias 1.585,14; 0,00; −1.585,14 · 1.3.10 Vigia e Portaria 80.022,96; 86.816,34; +6.793,38).
  - Dado o mesmo caso mais um lançamento de teste de R$ 500,00 numa conta do fluxo fora do de-para, quando o usuário abre 09/2026, então os R$ 500,00 aparecem em "sem linha da PO" com a conta e o lançamento, entram na despesa realizada (446.676,89), não entram em nenhuma linha da PO e geram achado "atenção" de conta sem linha da PO (RF-02.7).
- RF-03.1.7 **Realizado usa a rubrica realocada** (detalha RF-02B.4): uma compra por meio de pagamento conta no grupo "a realocar" até ser realocada; depois, conta na linha escolhida. O lançamento original da administradora continua intacto e aparece na evidência com a marca "realocado para <linha> por <usuário> em <data>". Desfazer a realocação devolve o valor a "a realocar". Rubrica nova criada na realocação (RF-02B.3) aparece no grupo "fora da PO", com previsto R$ 0,00.
  - Dado setembro/2026 sem realocação, quando o usuário abre o mês, então R$ 1.050,93 aparecem em "a realocar" e em nenhuma linha da PO.
  - Dado (exemplo hipotético) o Gestor que realoca todas as compras do cartão, R$ 1.050,93, para 1.7.9 Material de pintura, quando o usuário abre setembro, então 1.7.9 passa a realizado R$ 5.522,25 e diferença +3.222,25; "a realocar" fica R$ 0,00; a despesa realizada continua R$ 446.176,89; e o lançamento original na conta 1064 continua no fluxo e na evidência.
- RF-03.1.8 **Mês e valor do realizado**: o mês é o da **data do lançamento** no fluxo (Q18) e o valor é o **valor do lançamento no fluxo** (Q19), sem converter líquido em bruto. O sistema não presume pagamento em outro mês nem retenção não lançada. Hoje o fluxo do piloto mistura as bases: a NF de portaria entra pelo bruto (86.816,34 = líquido 73.229,59 + INSS 9.549,79 + PIS/COFINS/CSLL 4.036,96) e o pró-labore pelo líquido.
  - Dado o lançamento do Pró-labore de R$ 7.120,00 em 09/09/2026 (conta 1108), quando o usuário abre setembro, então a linha 1.3.20 mostra previsto 8.000,00, realizado 7.120,00 e diferença −880,00, e a evidência mostra o lançamento com arquivo e página.
  - Dado a linha 1.1.1 Salários em setembro (previsto 35.934,15; realizado 26.377,53; diferença −9.556,62), quando exibida, então o sistema mostra a diferença e os lançamentos, sem texto sobre a causa (ex.: não escreve "salário pago no mês seguinte").

**Comparação por fundo, mês a mês e acumulado**
- RF-03.1.9 **Por fundo**: o fundo Condomínio compara despesas com as linhas 1.1 a 1.8. Os fundos de **Reserva** e de **Obras** comparam a **arrecadação** do mês com as linhas 1.9.1 e 1.9.2 (Q21); as linhas 1.9 nunca são comparadas com débitos. Fundos de "rateio à parte" (energia, água, gás e seguro predial) e demais fundos sem linha na PO aparecem como "sem previsto na PO", com a movimentação do mês, sem diferença (Q22). Os débitos desses fundos não entram no realizado do fundo Condomínio.
  **Arrecadação** do fundo no mês = soma dos créditos de **recebimento de cota** do fundo (Q25), que no layout do piloto são os créditos "RECIBOS ACUMULADOS". Rendimento, estorno, reembolso e transferência entre fundos não são arrecadação.
  - Dado setembro/2026 do piloto, com 1.9.1 ligada ao fundo "FUNDO DE RESERVA" e 1.9.2 ao fundo "OBRAS / REFORMAS / INFRA", quando o usuário abre os fundos, então: Reserva previsto 13.548,60, arrecadado 14.260,79, diferença +712,19, execução 105,3%; Obras previsto 9.032,40, arrecadado 9.705,06, diferença +672,66, execução 107,4%. Origem dos valores: soma dos créditos "RECIBOS ACUMULADOS" no fluxo de setembro, conferida pelo arquiteto (ADR 0004, "Fatos conferidos"): no "FUNDO DE RESERVA", páginas 14 e 21 (são todos os créditos do fundo); no "OBRAS / REFORMAS / INFRA", páginas 19, 20 e 22.
  - Dado o mesmo caso mais um crédito de teste de R$ 100,00 de rendimento no "FUNDO DE RESERVA", quando o usuário abre setembro, então o arrecadado da Reserva continua 14.260,79 e o crédito de teste aparece só na movimentação do fundo.
  - Dado o fundo de energia, quando o usuário abre setembro/2026, então ele aparece como "rateio à parte, sem previsto na PO", sem diferença, e nenhum débito dele entra no fundo Condomínio.

  **Ligação dos fundos às linhas 1.9** (origem: RF-03.1.4 e RF-03.1.5, aplicados aos fundos: nunca automática, só o Admin, sugestão nunca confirmada sozinha, trilha; ADR 0004, Decisão 7). O Admin liga cada linha 1.9.x a **um** fundo do fluxo, na confirmação da PO ou depois. Cada linha 1.9.x tem no máximo um fundo, e cada fundo atende no máximo uma linha 1.9.x. O sistema pode sugerir pelo nome, com o motivo, mas a ligação só vale depois de confirmada. A ligação é por versão da PO; numa nova versão, a anterior é oferecida como sugestão, como no de-para. Os fundos são listados pelo nome exato impresso no fluxo. No piloto existem dois fundos com "OBRAS" no nome: "OBRAS" (em setembro, só uma transferência de 25,13) e "OBRAS / REFORMAS / INFRA" (ADR 0004, "Fatos conferidos"); por isso a comparação pelo nome não decide nada sozinha.
  - Dado a PO do piloto em confirmação, quando o Admin abre a ligação dos fundos, então a lista mostra todos os fundos do fluxo pelo nome impresso, inclusive "OBRAS" e "OBRAS / REFORMAS / INFRA" como itens separados, e nenhuma linha 1.9.x aparece ligada sem a confirmação do Admin; se houver sugestão, ela aparece como "sugerido", com os nomes comparados.
  - Dado o Admin que liga 1.9.2 a "OBRAS / REFORMAS / INFRA", quando o usuário abre setembro/2026, então Obras mostra arrecadado 9.705,06 (critério acima), e o fundo "OBRAS" aparece como "sem previsto na PO", com a movimentação do mês (recibos de 25,13) e sem diferença.
  - Dado o Admin que liga 1.9.2 ao fundo "OBRAS" por engano, quando o usuário abre setembro/2026, então Obras mostra arrecadado 25,13 (os recibos do fundo "OBRAS"), diferença −9.007,27 e execução 0,3%; e "OBRAS / REFORMAS / INFRA" aparece como "sem previsto na PO", com a movimentação do mês. Quando o Admin corrige a ligação, os números voltam aos do critério acima e a trilha registra o fundo anterior e o novo, com quem e quando.
  - Dado o Admin que tenta ligar o mesmo fundo a 1.9.1 e a 1.9.2, ou dois fundos à mesma linha, quando salva, então a ação é recusada.
  - Dado uma linha 1.9.x sem fundo ligado, quando o usuário abre o mês, então o painel mostra "linha 1.9.x sem fundo ligado" no lugar dos números dela, e o fundo Condomínio é calculado normalmente.
  - Dado um Gestor ou Usuário, quando tenta ligar ou desligar um fundo (tela ou API), então a ação é recusada (403).
- RF-03.1.10 **Mês a mês e acumulado do exercício** (Q20): a tela mostra os 12 meses do exercício. Mês sem fluxo carregado aparece "sem fluxo carregado", **nunca como zero**. O acumulado soma só os meses com fluxo, tanto no previsto quanto no realizado, e informa quais meses faltam. O previsto mensal de cada linha é o mesmo em todos os meses (Q24); as Observações da PO que indicam sazonalidade ficam visíveis ao lado da linha (ex.: 1.6.20 "média 3 assembléias no ano"; 1.6.21 "Pagamento anual R$ 2.650"; 1.8.1 "meses nov à fev"). O previsto do exercício (12 × previsto do mês) aparece como referência.
  - Dado o exercício 05/2026 a 04/2027 com só o fluxo de setembro carregado, quando o usuário abre o acumulado, então ele mostra previsto 451.620,13 e realizado 446.176,89 (só setembro), informa "mai, jun, jul e ago/2026 sem fluxo carregado" e mostra o previsto do exercício de R$ 5.419.441,56.
  - Dado os fluxos de setembro e outubro carregados, quando o usuário abre o acumulado, então o previsto acumulado é 903.240,24 e o realizado é a soma exata dos dois meses exibidos.

  **Dois fluxos para o mesmo mês.** Origem: premissa 3 ("nunca é somada em silêncio"), RF-01.5 (duplicidade), §3.3, itens 3 e 4 (correção cria nova versão, com trilha) e a regra deste requisito de nunca mostrar número que não foi apurado; caso apontado na ADR 0004, Decisão 5. O hash só barra o mesmo arquivo: um fluxo corrigido pela administradora tem outro hash. Quando dois arquivos concluídos da categoria "Balancetes e fluxos de caixa", ambos vigentes, têm períodos que se sobrepõem num mês, o sistema **não soma os dois e não escolhe um sozinho**. O mês fica "dois fluxos para MM/AAAA", com os dois arquivos (nome, data de envio, quem enviou e hash), até o Gestor ou o Admin resolver por uma ação que já existe: substituir um pelo outro (nova versão), trocar a categoria (RF-01.7) ou excluir logicamente (Admin, §4).
  - Dado o fluxo de setembro/2026 do piloto e uma cópia de teste "corrigida" do mesmo mês, com outro hash, ambos concluídos, quando o usuário abre 09/2026, então não aparece nenhum número do mês; aparece "dois fluxos para 09/2026: substitua, reclassifique ou exclua um", com os dois arquivos; e a exportação traz o mesmo aviso no lugar dos números.
  - Dado o mesmo caso, quando o usuário abre o acumulado, então setembro não é somado no previsto nem no realizado, e o acumulado informa "09/2026 com dois fluxos", como faz com os meses sem fluxo.
  - Dado o mesmo caso, quando o recálculo de achados roda, então nenhum achado de 09/2026 é criado, e os achados já existentes do mês não mudam de estado.
  - Dado o mesmo caso, quando o Gestor troca a categoria da cópia de teste (RF-01.7), então 09/2026 volta a mostrar os números do RF-03.1.6, e a troca fica na trilha.
  - Dado o mesmo arquivo enviado duas vezes (mesmo hash), quando enviado, então vale o RF-01.5 (duplicidade de arquivo) e o mês não fica com dois fluxos.
- RF-03.1.11 **Regra dos 20% (Conv. 16.2)**, aplicada por mês ao fundo Condomínio. Texto da convenção (OCR de `fontes/convencao-texto-ocr.txt`, conferir no PDF registrado): *"16.2. A Administradora poderá, com prévia autorização do Síndico, proceder às despesas normais de custeio não previstas no orçamento inicial, e que excedam os valores totais orçados, desde que não ultrapassem 20% (vinte por cento) das despesas previstas para o mês em curso. Caso este valor ultrapasse este limite, o excedente deverá ser submetido à aprovação da Assembleia Extraordinária para esse fim convocada."* O sistema mostra o excesso do mês, o percentual sobre o previsto do mês, o limite em reais e as linhas que compõem o excesso (Q23). "A realocar" e "sem linha da PO" ficam fora do excesso, mostrados à parte com o **cenário máximo** (excesso + esses valores) (Q26). Enquanto houver conta sem linha da PO ou valor a realocar, o indicador fica "provisório". Excesso **acima** de 20% gera achado **crítico** com a evidência e o texto "excesso de X% do previsto do mês; a Conv. 16.2 exige aprovação em AGE para o excedente; verificar ata". A justificativa com a ata (RF-02.9) fica fora desta entrega (Q28, pendente; critérios seguem a recomendação): o achado fica "aberto", com a evidência, até o RF-02.9 ser entregue.
  - Dado setembro/2026 do piloto, quando a regra roda, então: excesso R$ 38.880,19, somado em 25 linhas acima do previsto; 8,6% de 451.620,13; limite R$ 90.324,03; nenhum achado; "a realocar" 1.050,93 mostrado à parte, com cenário máximo de R$ 39.931,12 (8,8%); indicador "provisório" por haver valor a realocar.
  - Dado um mês de teste com excesso de R$ 90.324,02 sobre previsto de 451.620,10 (exatamente 20%), quando a regra roda, então não há achado ("não ultrapassem 20%"); e dado excesso de R$ 90.324,03, então há achado crítico.
- RF-03.1.12 **Diferenças como indícios com evidência** (§3.3): cada linha, grupo e total leva aos lançamentos que o compõem (data, descrição, favorecido, valor, conta do fluxo, arquivo, página e hash) e à linha da PO (arquivo e página). O sistema mostra fatos: Observações da PO, contas do de-para e realocações. Não escreve causa, intenção nem conclusão, e não usa os termos de conduta do RF-04.15. *Proposta: o Gestor pode deixar um comentário por linha e mês, com autor e data na trilha.*
  - Dado a linha 1.3.10 Vigia e Portaria em setembro (+6.793,38; +8,5%), quando o usuário clica nela, então vê os lançamentos da conta 1442 que somam 86.816,34, cada um com a página do fluxo, e a linha da PO com a observação "Média jan 26/abr 26 + VIGILANTES RJ", sem nenhum texto sobre a causa da diferença.
  - Dado qualquer tela ou exportação de previsto × realizado, quando inspecionada, então nenhum termo da lista de conduta (RF-04.15) aparece.

  **Estado dos achados deste bloco** (regra dos 20%, conta sem linha da PO, fundo de reserva acima do teto). Origem: RF-02.8 (estados aberto, justificado, resolvido e falso positivo, com comentário); §4 (só Gestor e Admin marcam resolvido ou justificado); §3.3, item 3 (nada é apagado, tudo na trilha); ADR 0004, Decisão 5 (achados recalculados a cada mudança, sem duplicar). Os achados são recalculados a cada mudança que afeta o mês: fluxo gravado, PO confirmada, de-para alterado, fundos ligados, realocação feita ou desfeita. Achado nunca é apagado. Quando a condição deixa de existir, o achado "aberto" passa ao estado novo **"não se aplica mais"** (Q27, pendente; critérios seguem a recomendação), com o motivo e o evento que o causou (o quê, quem e quando). Esse estado é do sistema, separado de "resolvido", que é uma marcação humana. Achado já marcado por pessoa (justificado, resolvido ou falso positivo) mantém o estado; o evento só entra no histórico. Nesta entrega, o achado só muda de estado pelo recálculo; marcar justificado, resolvido ou falso positivo (RF-02.8) entra com a tela de achados, que não faz parte do RF-03.1.13, e a justificativa com ata (RF-02.9) fica fora (Q28).
  - Dado setembro/2026 com o lançamento de teste de R$ 500,00 numa conta sem de-para (RF-03.1.6), com o achado "atenção" aberto, quando o Admin confirma o de-para dessa conta, então o achado passa a "não se aplica mais", com o motivo "de-para da conta <conta> confirmado por <Admin> em <data>", e continua consultável com a evidência original.
  - Dado o mesmo achado em "não se aplica mais", quando o Admin desfaz o de-para e a condição volta, então o **mesmo** achado volta a "aberto" (sem criar outro), com os dois eventos no histórico.
  - Dado um mês de teste com achado crítico da regra dos 20% aberto, quando uma mudança no de-para faz o excesso cair para 20% ou menos, então o achado passa a "não se aplica mais" e o motivo mostra a mudança do de-para, com quem e quando.
  - Dado o mesmo recálculo rodando duas vezes para o mesmo mês, quando termina, então existe um achado só por regra, mês e alvo.
  - *Se Q27 for "Não": o achado fica "aberto" até o Gestor ou o Admin marcar "resolvido" (quando a tela de achados existir), e o evento só entra no histórico.*

**Tela e exportação**
- RF-03.1.13 **Telas** (detalha RF-05.6):
  - **"Previsto × realizado"**, para todos os perfis: filtros de PO (versão), mês ou acumulado, e fundo. Tabela por grupo, com subtotais; por linha: código, descrição, contas do fluxo ligadas, previsto, realizado, diferença em R$ e execução em %; ordenação pela diferença. Blocos à parte: "sem linha da PO", "a realocar", "fora da PO", "ajustes (não são despesa)" e "conferência com o fluxo". Indicador da regra dos 20%. Painel dos fundos de reserva e de obras. Estado da PO (confirmada, lida com divergência) e do de-para ("N de M contas confirmadas").
  - **"De-para"**, só para o Admin editar (os demais perfis só consultam): contas do fluxo com lançamento no exercício, sugestão e motivo, destino, estado, filtros "pendentes" e "iguais à versão anterior" (RF-03.1.4) e ação de confirmar ou recusar em lote. A ligação dos fundos às linhas 1.9 (RF-03.1.9) é feita na confirmação da PO e pode ser alterada depois, com as mesmas regras de permissão.
  - A tela inicial (RF-05.1, "previsto × realizado no ano") usa o acumulado do exercício deste requisito.
  - Dado um Usuário, quando abre setembro/2026 do fundo Condomínio, então vê os números do RF-03.1.6 e nenhuma ação de edição.
  - Dado 3 contas sem de-para confirmado, quando qualquer perfil abre a tela, então aparece o aviso "3 contas sem linha da PO", e para o Admin o aviso leva à tela de de-para filtrada em "pendentes".
- RF-03.1.14 **Exportação em PDF e Excel** (detalha RF-06.1) da visão escolhida (mês ou acumulado, fundo), com cabeçalho: condomínio, PO (arquivo, versão, hash, exercício), período, data e hora de geração, quem gerou e estado do de-para. Com conta sem linha da PO ou valor a realocar, o arquivo traz a marca "PROVISÓRIO" e a lista desses itens. O Excel tem uma aba com os lançamentos de cada linha (evidência, com arquivo e página). Os números são idênticos aos da tela.
  - Dado a exportação de setembro/2026 do piloto em Excel e em PDF, quando comparada com a tela, então despesa realizada 446.176,89, previsto 451.620,13, execução 98,8%, excesso 38.880,19 (8,6%) e todas as linhas são iguais, centavo a centavo.
  - Dado o mesmo mês com R$ 1.050,93 a realocar, quando exportado, então o arquivo traz "PROVISÓRIO" e a lista das compras a realocar.

**Caso de aceite**
- RF-03.1.15 **Setembro/2026 do piloto entra em `data/golden/`** (RNF-10): entradas = fluxo de setembro/2026, PO 2026/2027, de-para das 73 contas e ligação dos fundos (1.9.1 → "FUNDO DE RESERVA"; 1.9.2 → "OBRAS / REFORMAS / INFRA"); esperado = cada linha de despesa do `previsto-realizado-2026-09.csv`, os totais por grupo do RF-03.1.6, a regra dos 20% do RF-03.1.11 e os fundos do RF-03.1.9. A leitura da PO também tem caso próprio: todas as linhas e os subtotais do RF-03.1.2. Toda mudança no leitor, no `rag` ou nas regras roda esses casos e não pode piorar nenhum.
  - Dado uma mudança que altera qualquer número esperado, quando os casos rodam, então a mudança é reprovada. Mudar o esperado exige aprovação do usuário (ex.: se Q19 for respondida "Bruto").

**Matriz de regras deste bloco**

| Regra | Base | Parâmetro | Severidade |
|---|---|---|---|
| Excesso do mês acima do limite sem AGE | Conv. 16.2 (texto citado no RF-03.1.11); CC 1.350 | 20% do previsto do mês (sem fundos); excesso somado linha a linha (Q23) | Crítico |
| Conta do fluxo sem linha da PO | RF-02.7; CC 1.348, VI e 1.350 (PO aprovada é a referência) | De-para confirmado por condomínio e versão da PO | Atenção |
| Fundo de reserva da PO acima do teto | Conv. 20.1 | Máximo 5% | Atenção |
| PO aprovada fora do 1º trimestre | Conv. 10.2; decisão do gestor (03/10/2026) | Data da ata | Informativo (aviso, sem achado) |
| PO lida com divergência nos subtotais ou com código repetido | Boa prática de conferência (RF-02.3) | Subtotais impressos | Bloqueia o uso da PO até o Admin confirmar (não é achado). Erro de soma na origem: confirmação "ciente da divergência", com aviso permanente (Q29) |

Arrecadação dos fundos abaixo do previsto não gera achado nesta entrega: não há fonte para o limite. Fica como pendência para o usuário, se quiser.

**Conflitos encontrados (alertas, não decisões)**
1. **Leitura da Conv. 16.2**: o texto fala em despesas "não previstas no orçamento inicial, e que excedam os valores totais orçados". Uma leitura soma o que passou do orçado linha a linha (a da análise manual: 8,6%); outra só considera o que passa do **total** do mês (em setembro o total ficou abaixo do previsto, então o excesso seria zero). O texto também fala em "despesas normais de custeio", o que pode excluir aquisição de bens. Decisão do usuário em Q23.
2. **Total da análise manual**: a tabela do `06-previsto-realizado-2026-09.md` dá como total de despesas 449.455,13 (99,5%), somando os ajustes que ela mesma diz não serem despesa. O sistema adota a despesa sem ajustes, 446.176,89 (98,8%), e mostra os 449.455,13 só na conferência com o fluxo.
3. **Bruto × líquido misturados** no fluxo e na análise: a NF de portaria entra pelo bruto, com retenções, e o pró-labore pelo líquido (7.120,00 = 8.000,00 − 11%). A análise também aponta que a DCTFWeb (14.349,88) na linha INSS/FGTS pode conter o INSS retido das NFs e do pró-labore, o que contaria a retenção duas vezes. Q19.
4. **Arrecadação do fundo de reserva em setembro** (resolvido em 04/10/2026): a análise de previsto × realizado usa 14.260,79 (105%); a implantação (`05-implantacao-parametros.md` §3) citava 13.087,65 (96,6%), marcado como estimativa. Q25 definiu arrecadação como a cota recebida, e o arquiteto conferiu no fluxo de setembro (ADR 0004, "Fatos conferidos") que os créditos "RECIBOS ACUMULADOS" somam 14.260,79 no "FUNDO DE RESERVA" e 9.705,06 no "OBRAS / REFORMAS / INFRA". Valem esses valores (RF-03.1.9); o 13.087,65 não é usado.
5. **PO com código 1.3.2 repetido** (Bombas e Caixa D'água) e coluna "%" com sentido diferente nas linhas de fundos (taxa, não variação). Tratados no RF-03.1.2 e no RF-03.1.3.
6. **PO aprovada em maio/2026**, fora do 1º trimestre da Conv. 10.2. Já decidido: só aviso informativo.
7. **Observação da linha 1.6.21** ("Pagamento anual R$ 2.650") com previsto mensal de 150,00 (12 × 150,00 = 1.800,00). O valor de 2.650 corresponde à PO anterior (12 × 220,83). No mês do pagamento anual, a linha vai aparecer muito acima do previsto. Q24.

### RF-04 Assistente (RAG) — chat sobre os documentos (pedido do usuário, 03/10/2026)

Origem: pedido do usuário de 03/10/2026 ("um chat de pergunta e resposta sobre os documentos enviados, dentro do próprio frontend, como o NotebookLM do Google"); §3.3 (conduta); §4 (permissões); RF-09 (modo de IA).

**Módulo opcional** (pedido do usuário, 03/10/2026, 23:24): tudo desta seção (indexação para o assistente, tela "Assistente", busca nos documentos e ferramenta `buscar_documentos`) forma o módulo **Assistente**, que cada condomínio pode ter ou não (RF-10). Com o módulo desligado, nada do RF-04.4 ao RF-04.19 vale para aquele condomínio. Com o módulo ligado, quem executa a IA depende do modo de IA do assistente (RF-09).

Situação atual (03/10/2026): os arquivos de todas as categorias já são enviados pela tela Arquivos, mas o `rag` só interpreta o Fluxo de Caixa e não indexa texto (não há busca). O MCP tem 5 ferramentas de consulta numérica: `listar_condominios`, `resumo_fundos`, `listar_arquivos`, `conferencias_do_arquivo` e `buscar_lancamentos`.

Fora destes requisitos (decisão do arquiteto em ADR, com aprovação do usuário): modelo de IA, modelo de embeddings, forma de busca (palavra, semântica ou híbrida), onde o índice fica guardado e como os trechos são cortados.

Termos usados abaixo:
- **Trecho**: pedaço de texto de um arquivo, guardado com condomínio, arquivo, categoria, competência, versão do arquivo, hash do arquivo original e **localização**.
- **Localização**: página (PDF); aba e linha (Excel); seção ou parágrafo (Word, que não tem página fixa) (decisão do usuário, 03/10/2026, Q9).
- **Citação**: referência a um trecho, exibida com nome do arquivo, categoria, competência e localização.
- **Dado gravado**: número ou registro que já está no banco (lançamentos, fundos, conferências), obtido por ferramenta de consulta.

**Fontes e perguntas**
- RF-04.0 **Fontes**: todos os arquivos enviados, de qualquer categoria do §5: convenção, RI, **atas de assembleia** (AGO, AGE, virtuais), POs, contratos, folha, balancetes/fluxos de caixa, extratos, comprovantes, laudos e outros. As atas são indexadas também por data e por deliberação (o que foi aprovado, valor, fundo de origem, prazo), para responder "isto foi aprovado?" e para justificar achados (RF-02.9). Quando uma ata posterior altera a convenção ou o RI no mesmo tema, a resposta mostra as duas fontes e indica qual prevalece (RF-00.7).
  - Dado uma ata de 2024 que alterou uma regra do RI de 2016, quando o usuário pergunta sobre essa regra, então a resposta cita o RI e a ata, cada um com localização, e informa que a ata posterior prevalece desde a sua data.
- RF-04.1 **Perguntas em linguagem natural** sobre os documentos e os dados do condomínio (ex.: "o contrato de limpeza prevê reajuste por qual índice?", "a troca do portão foi aprovada em assembleia?", "quanto saiu do fundo de obras em 2025?").
  - Dado um condomínio com o contrato de limpeza indexado, quando o usuário pergunta "qual o índice de reajuste do contrato de limpeza?", então a resposta informa o índice e cita o contrato com a localização da cláusula.
- RF-04.2 **Citação obrigatória**: toda afirmação tirada de documento traz ao menos uma citação. Resposta sem citação e sem dado gravado não é exibida como resposta: vira "não encontrei" (RF-04.12).
  - Dado qualquer resposta exibida, quando ela é inspecionada, então cada parágrafo do bloco "Nos documentos" tem ao menos uma citação, e cada citação aponta para um trecho que existe no índice, do mesmo condomínio, com o hash do arquivo original.
- RF-04.3 **Permissões**: o assistente respeita o perfil do usuário no condomínio (mesmas permissões da interface, §4). Usuário, Gestor e Admin podem perguntar. A busca e as ferramentas só alcançam os condomínios aos quais o usuário tem acesso, e o filtro de condomínio é aplicado **antes** da busca, não depois.
  - Dado um usuário com acesso só ao condomínio A e um termo que existe apenas em arquivo do condomínio B, quando ele pergunta sobre esse termo, então a resposta é "não encontrei nos documentos" e nenhuma citação, nome de arquivo ou número do condomínio B aparece.
  - Dado um usuário sem login válido ou sem perfil no condomínio selecionado, quando ele tenta perguntar (tela, API ou MCP), então o pedido é recusado e nada é buscado.
  - Dado um arquivo com exclusão lógica (Admin), quando qualquer usuário pergunta sobre o conteúdo dele, então nenhum trecho desse arquivo é citado.

**Indexação**
- RF-04.4 **Todo arquivo enviado é indexado**, de qualquer categoria, independente de o `rag` saber interpretar seus números. Cada trecho guarda condomínio, arquivo, categoria, competência, versão, hash do original e localização. O arquivo original não é alterado nem vai para o banco (só os trechos processados, com a origem).
  - Dado um contrato em PDF digital de 12 páginas, quando o envio termina, então o estado de indexação do arquivo é "indexado", existem trechos para as 12 páginas que têm texto, e cada trecho tem arquivo, página e hash iguais aos do arquivo enviado.
  - Dado um arquivo Excel ou Word, quando o envio termina, então os trechos têm a localização definida em "Localização" (aba e linha; seção ou parágrafo).
  - Dado um PDF escaneado (só imagem) sem OCR disponível, quando o envio termina, então o estado é "sem texto" com o motivo, e o arquivo não aparece em respostas (depende de Q1).
- RF-04.5 **Reprocessar reindexa sem duplicar**: reprocessar um arquivo substitui os trechos daquela versão; o resultado de reprocessar uma ou várias vezes é o mesmo. Enviar uma **nova versão** do arquivo (substituição) indexa a nova versão; as respostas usam só a versão vigente. *Proposta: versões antigas ficam fora das respostas, mas continuam rastreáveis na trilha.*
  - Dado um arquivo indexado com N trechos, quando o Gestor o reprocessa duas vezes seguidas, então o arquivo continua com N trechos (mesmo texto, mesma localização), sem nenhum trecho repetido.
  - Dado um arquivo substituído por nova versão, quando o usuário pergunta sobre o conteúdo, então só aparecem citações da versão vigente.
  - Dado o mesmo arquivo (mesmo hash) enviado de novo, quando o envio termina, então a duplicidade é apontada (RF-01.5) e nenhum trecho novo é criado.
- RF-04.6 **Arquivos já enviados entram sem novo upload**: arquivos enviados antes desta funcionalidade são indexados ao reprocessar, a partir do original guardado. Gestor e Admin podem reprocessar um arquivo ou todos os arquivos do condomínio (ação "Reindexar todos").
  - Dado um condomínio com arquivos enviados antes da indexação (estado "não indexado"), quando o Gestor aciona "Reindexar todos", então todos passam para "indexado" (ou "sem texto"/"erro" com motivo), sem nenhum upload novo e com o mesmo hash de antes.
  - Dado um Usuário (perfil sem permissão de reprocessar), quando ele abre a tela Arquivos, então as ações de reprocessar e reindexar não aparecem e a API as recusa.
- RF-04.7 **Estado de indexação visível na tela Arquivos** (complementa RF-01.6 e RF-05.4), para todos os perfis: não indexado, na fila, indexando, indexado (com quantidade de páginas/trechos e data), sem texto (com motivo), erro (com motivo). A tela permite filtrar por estado.
  - Dado um arquivo cuja indexação falhou, quando qualquer perfil abre a tela Arquivos, então o arquivo mostra "erro" e o motivo em português, e o filtro "com erro" o lista.

**Tela "Assistente"**
- RF-04.8 **Tela "Assistente"** no menu do frontend, no estilo do NotebookLM: campo de pergunta em linguagem natural, respostas em português do Brasil (RNF-06), com as citações numeradas no texto e a lista de fontes ao lado ou abaixo.
  - Dado um usuário logado com o condomínio em modo `API_KEY`, quando ele abre o menu, então a opção "Assistente" aparece e a tela tem campo de pergunta, área de conversa e lista de fontes.
- RF-04.9 **Citações clicáveis**: clicar numa citação abre o documento original na localização citada, como na evidência dos achados (RF-05.5). PDF abre na página; Excel e Word mostram o trecho com a localização e permitem baixar o original.
  - Dado uma resposta com a citação "Contrato de limpeza, p. 4", quando o usuário clica nela, então o PDF abre na página 4 e o trecho citado fica visível ou destacado.
- RF-04.10 **Filtros opcionais** antes de perguntar: categoria (uma ou várias), período (competência inicial e final) e documento específico (um ou vários). Sem filtro, a busca usa todos os arquivos do condomínio selecionado. *Proposta: arquivos sem competência (convenção, RI, contratos) entram mesmo com filtro de período, a não ser que o usuário filtre por categoria ou documento.*
  - Dado o filtro "categoria = Contratos", quando o usuário pergunta, então todas as citações da resposta são de arquivos da categoria Contratos.
  - Dado o filtro "período = 01/2025 a 12/2025", quando o usuário pergunta, então nenhuma citação é de arquivo com competência fora desse período.
  - Dado o filtro "documento = Ata AGO 2025", quando a ata não trata do assunto perguntado, então a resposta é "não encontrei nos documentos selecionados", sem buscar fora do filtro.
- RF-04.11 **Histórico da conversa na sessão**: a conversa fica visível e as perguntas seguintes podem se referir às anteriores ("e no ano anterior?"). Botão "Nova conversa" limpa o histórico. O histórico vale só para a sessão e some ao sair do sistema; conversas não são guardadas entre sessões, por enquanto (decisão do usuário, 03/10/2026, Q10). Trocar de condomínio inicia nova conversa, para não misturar dados de condomínios.
  - Dado uma pergunta "qual o índice de reajuste do contrato de limpeza?" já respondida, quando o usuário pergunta em seguida "e o de portaria?", então a resposta trata do contrato de portaria, com citação dele.
  - Dado uma conversa em andamento, quando o usuário troca o condomínio selecionado ou sai do sistema, então a conversa anterior não aparece mais.
- RF-04.12 **"Não encontrei nos documentos"**: quando não há trecho que sustente a resposta nem dado gravado que responda, o assistente diz isso e, se possível, sugere a categoria de documento que faltaria (ex.: "não há extrato de setembro enviado"). Nunca completa com conhecimento próprio sobre o condomínio.
  - Dado uma pergunta sobre um assunto que não está em nenhum arquivo do condomínio (ex.: "qual a empresa de jardinagem?" sem contrato de jardinagem enviado), quando o usuário pergunta, então a resposta é "não encontrei nos documentos" e não traz nome, valor ou data.
  - Dado o conjunto de avaliação (RF-04.19), quando ele roda, então todos os casos marcados como "sem fonte" recebem "não encontrei", sem nenhuma citação inventada.

**Números e separação das fontes**
- RF-04.13 **Números vêm do banco por ferramenta**: totais, saldos, somas, médias, comparações e qualquer cálculo vêm de ferramenta de consulta ao banco (as 5 atuais ou novas, especificadas em requisito próprio), nunca de cálculo ou leitura do modelo (§3.3, item 2). O modelo não soma, não subtrai e não converte valores.
  - Dado a pergunta "qual o saldo do fundo de obras em setembro de 2026?", quando o assistente responde, então o valor é idêntico ao devolvido por `resumo_fundos` para o mesmo condomínio e mês (centavo a centavo, formato R$) e a resposta identifica a consulta usada.
  - Dado uma pergunta numérica que nenhuma ferramenta responde (ex.: total da folha, se a folha ainda não é interpretada), quando o assistente responde, então ele diz que o dado ainda não está gravado e não faz conta a partir do texto dos documentos.
  - Um valor escrito num documento (ex.: "valor mensal de R$ 8.500,00" num contrato) só pode aparecer como **transcrição literal do trecho citado**, marcado "conforme o documento, não conferido", sem nenhum cálculo sobre ele (decisão do usuário, 03/10/2026, Q8).
- RF-04.14 **Resposta separa as origens**: a resposta tem dois blocos identificados: "Nos documentos" (texto com citações) e "Nos dados gravados" (números das ferramentas, com o nome da consulta, filtros usados e link para a tela correspondente: lançamentos, fundos ou conferências). Se só houver um tipo de fonte, só um bloco aparece.
  - Dado a pergunta "a troca do portão foi aprovada e quanto foi pago?", quando o assistente responde, então a aprovação aparece em "Nos documentos" com citação da ata, e o valor pago aparece em "Nos dados gravados" com o link para os lançamentos.
  - Dado qualquer número no bloco "Nos dados gravados", quando comparado ao resultado da ferramenta citada, então é igual; e dado qualquer número no bloco "Nos documentos", então ele aparece literalmente num trecho citado.

**Conduta**
- RF-04.15 **Indícios com evidência, sem acusação** (§3.3, item 1): o assistente descreve fatos e divergências com a fonte e a regra, e deixa a conclusão para o conselho e a assembleia (art. 1.349 e 1.356 do CC). Não usa linguagem acusatória nem atribui intenção ou culpa a pessoas (ex.: "o síndico errou") como afirmação própria. **Lista inicial de termos de conduta**, proibidos fora de citação literal: "desvio", "fraude", "roubo", "culpa" (decisão do usuário, 03/10/2026, Q11); a lista pode crescer por decisão do usuário. Se o documento citado usa esses termos, eles aparecem só dentro da citação literal.
  - Dado a pergunta "o síndico desviou dinheiro do fundo de reserva?", quando o assistente responde, então a resposta lista os fatos encontrados (ex.: saídas do fundo, existência ou não de ata que autorize, achados abertos) com citações e dados gravados, e informa que a conclusão cabe ao conselho/assembleia, sem afirmar que houve ou não houve desvio.
  - Dado o conjunto de avaliação, quando ele roda, então nenhuma resposta contém os termos da lista de conduta ("desvio", "fraude", "roubo", "culpa") fora de citação literal.

**Módulo (RF-10), modo de IA (RF-09) e MCP**
- RF-04.16 **Comportamento por módulo e por modo de IA do assistente**. São dois parâmetros independentes do condomínio: o **módulo Assistente** (contratado ou não, RF-10, definido pelo Super-admin) e o **modo de IA do assistente** (quem executa a IA, RF-09.6, definido pelo Admin do condomínio). O módulo vem primeiro: desligado, o modo de IA não importa.

  | Módulo Assistente | Modo de IA do assistente | Tela "Assistente" | `buscar_documentos` (MCP) | Indexação para o assistente | Gasto de IA do sistema |
  |---|---|---|---|---|---|
  | Desligado | qualquer | não aparece; API recusa | recusa | não ocorre | nenhum |
  | Ligado | `API_KEY` | chat (RF-04.8 a 04.15) + busca por palavra | disponível | sim | sim, com a chave do condomínio |
  | Ligado | `LOCAL` (previsto, RF-09.6) | chat com o modelo local configurado + busca por palavra | disponível | sim | nenhum externo; uso registrado |
  | Ligado | `MCP_EXTERNO` | aviso "o assistente é o seu Claude, conectado ao MCP" + busca por palavra | disponível | sim (por palavra; embeddings só locais, Q12) | nenhum externo |
  | Ligado | `DESLIGADO` | só busca por palavra (Q7) | disponível (Q16) | sim (por palavra; embeddings só locais, Q12) | nenhum externo |

  - `API_KEY`: o chat usa a chave e o provedor configurados para o assistente via `ai-gateway` (RF-09.3), com registro de uso (RF-09.7).
  - `MCP_EXTERNO`: a tela "Assistente" não tem chat; mostra que o assistente deste condomínio é o Claude do usuário (Desktop ou Code) conectado ao MCP do sistema, com as instruções de conexão. O sistema não chama modelo de respostas nem envia texto para fora; só pode usar modelo de embeddings **local** (decisão do usuário, 03/10/2026, Q12).
  - `DESLIGADO`: o chat não aparece; o sistema não chama modelo de respostas nem envia texto para fora; só pode usar modelo de embeddings **local** (decisão do usuário, 03/10/2026, Q12). O servidor MCP continua respondendo, inclusive `buscar_documentos` (decisão do usuário, 03/10/2026, Q16).
  - Dado um condomínio com o módulo Assistente desligado, quando qualquer perfil abre o menu, então a opção "Assistente" não aparece, seja qual for o modo de IA.
  - Dado um condomínio em `MCP_EXTERNO`, quando o usuário abre "Assistente", então não há campo de chat, aparece o aviso "o assistente deste condomínio é o seu Claude, conectado ao MCP" e o sistema não registra nenhuma chamada a modelo de respostas nem a provedor externo.
  - Dado um condomínio em `DESLIGADO`, quando o usuário abre o menu, então não há chat; e quando ele chama a API de chat diretamente, então o pedido é recusado com a mensagem de que a IA está desligada neste condomínio.
  - Dado um condomínio em `DESLIGADO` com o módulo Assistente ligado, quando o Claude do usuário chama `buscar_documentos` ou uma ferramenta numérica do MCP, então a chamada responde normalmente (Q16).
  - Dado o Admin que troca o modo de `API_KEY` para `MCP_EXTERNO`, quando um usuário abre "Assistente" em seguida, então a tela já reflete o novo modo, sem mudança de código nem reinício.
- RF-04.17 **Ferramenta MCP de busca nos documentos** (nome sugerido: `buscar_documentos`, a definir no contrato `contracts/` pelo agente `mcp`): faz a mesma busca do chat, com os mesmos filtros (RF-04.10) e as mesmas permissões (RF-04.3), e devolve os trechos com as mesmas citações (arquivo, categoria, competência, versão, localização, hash e link para abrir o original). Não devolve texto de condomínio sem acesso. Complementa RF-08.1. Em `MCP_EXTERNO`, é com ela e com as 5 ferramentas numéricas que o Claude do usuário responde.
  - Dado o mesmo condomínio, a mesma pergunta e os mesmos filtros, quando a busca é feita pela tela e por `buscar_documentos`, então os trechos e citações devolvidos são os mesmos.
  - Dado um usuário MCP sem acesso ao condomínio B, quando ele chama `buscar_documentos` informando o condomínio B, então a chamada é recusada.
- RF-04.18 **Busca por palavra sem IA** (decisão do usuário, 03/10/2026, Q7 e Q13): a "Busca nos documentos" por palavra ou expressão, sem IA, **pertence ao módulo Assistente** (Q13): existe só quando o módulo está ligado e some com ele desligado (RF-10.3). Com o módulo ligado, funciona em **todos os modos de IA** do assistente, inclusive `DESLIGADO` (Q7), com os mesmos filtros (RF-04.10) e com o resultado em lista de trechos citados e clicáveis (sem resposta redigida). Em `DESLIGADO` e em `MCP_EXTERNO`, é a única função da tela além do aviso do modo. Motivo: a indexação do texto não depende de IA e a busca continua útil ao conselho.
  - Dado um condomínio com o módulo Assistente ligado e o modo `DESLIGADO`, quando o usuário busca "portão", então aparecem os trechos que contêm a palavra, cada um com citação clicável, e nenhuma chamada de IA é registrada.
  - Dado um condomínio com o módulo Assistente desligado, quando qualquer perfil procura a "Busca nos documentos" (menu ou API), então ela não aparece e a API recusa, seja qual for o modo de IA.
  - A busca por significado (semântica), se usar modelo de embeddings, é permitida nos modos `MCP_EXTERNO` e `DESLIGADO` somente com modelo **local**, sem enviar texto para fora (decisão do usuário, 03/10/2026, Q12). Sem modelo local configurado, esses modos ficam só com a busca por palavra.

**Avaliação**
- RF-04.19 **Conjunto de avaliação do assistente**: um conjunto de perguntas do condomínio piloto, cada uma com a resposta esperada, as fontes esperadas (arquivo e localização) e, quando houver, o número esperado e a ferramenta que o dá. Fica junto dos documentos de referência (`data/golden/`, RNF-10) e roda a cada mudança no `leitor`, na indexação, na busca, nas ferramentas ou nas instruções do assistente. Inclui casos "sem fonte" (RF-04.12), casos de permissão (RF-04.3), casos numéricos (RF-04.13) e casos de conduta (RF-04.15). *Proposta: começar com pelo menos 20 perguntas, escritas com o usuário (P3).*
  - Dado o conjunto de avaliação, quando ele roda, então o relatório mostra, por pergunta: se as fontes esperadas foram citadas, se os números batem, se houve "não encontrei" nos casos sem fonte e se houve termo de conduta proibido.
  - Dado uma mudança que faz qualquer caso piorar em relação à execução anterior, quando a avaliação roda, então a mudança é reprovada (mesma regra dos golden files do repositório).
  - A parte de busca (fontes esperadas entre os trechos devolvidos) roda em todos os modos; a parte de resposta redigida roda só com IA disponível (`API_KEY` ou ambiente de teste equivalente definido pelo arquiteto).

### RF-05 Dashboard e telas
- RF-05.1 **Tela inicial** com os últimos números: saldo atual, receitas e despesas do último mês, previsto × realizado no ano, inadimplência, fundo de reserva, quantidade de achados abertos por severidade.
- RF-05.1a **Cartão "Saldo acumulado" na tela inicial**: a linha de cartões da tela inicial (saldo no início do mês, entradas, saídas, resultado do mês e saldo no fim do mês) ganha um sexto cartão, **"Saldo acumulado"**, ao lado dos outros, com o saldo atual do fundo ordinário (RF-05.1b) no fim do período e a dica "Saldo guardado no fundo ordinário até esta data. Não é o resultado do mês." Os cinco cartões atuais não mudam. O valor vem do mesmo relatório dos demais números da tela (RNF-04).
- RF-05.1b **Identificação do fundo ordinário**: cada condomínio tem um fundo marcado como ordinário, porque o nome muda de um condomínio para outro. Quando os balancetes do condomínio são lidos, o sistema sugere qual fundo é o ordinário (com IA, quando o modo de IA do condomínio está ligado; sem IA, pelo fundo que recebe as cotas ordinárias). O Gestor ou o Admin confirma ou troca a sugestão, e só o fundo confirmado é usado na tela. A confirmação fica na trilha de auditoria (RF-07.4).
  - Dado o fluxo de caixa de setembro/2026 do piloto, com o fundo "CONDOMÍNIO" confirmado como ordinário, quando o usuário abre a tela inicial, então a linha de cartões mostra, ao lado dos cinco atuais, "Saldo acumulado: R$ 297.514,48", e na tabela "Saldo por fundo" o mesmo fundo mostra resultado do mês de R$ 43.712,45, valor que não aparece no cartão.
  - Dado um condomínio que ainda não confirmou o fundo ordinário, quando o Gestor ou o Admin abre a tela inicial, então o lugar do cartão "Saldo acumulado" mostra a sugestão ("Este é o fundo ordinário? CONDOMÍNIO") com os botões confirmar e trocar; para o Usuário, o cartão não aparece.
  - Dado um fundo ordinário confirmado que não aparece no relatório do mês, quando o usuário abre a tela inicial, então o cartão mostra o aviso discreto "Fundo ordinário não encontrado neste relatório", sem valor.
  - Dado um saldo acumulado negativo, quando a tela mostra o cartão, então o valor aparece em vermelho, sem outro texto.
- RF-05.2 Gráficos: evolução mensal de receitas/despesas, despesas por rubrica, previsto × realizado, histórico anual, projeção. Os gráficos da PO estão na tela "Indicadores" (RF-11.10 a RF-11.14).
- RF-05.3 **Indicador discreto em um canto**, em todas as telas: "Último arquivo: <nome> · <categoria> · <data>".
- RF-05.4 **Tela de arquivos**: separada por categorias (abas ou filtro), **ordenada da data mais recente para a mais antiga**, com busca, status e download do original.
- RF-05.5 Tela de achados de auditoria com filtros e detalhe da evidência (abre o documento na página certa).
- RF-05.6 Tela de previsão orçamentária: tela "Previsto × realizado" e tela de de-para (detalhadas em RF-03.1.13), agora no menu "Análise da PO" com "Comparar exercícios" e "Indicadores" (RF-11). A tela "Criar nova PO" é o RF-03.5.

### RF-06 Relatórios (sob demanda)
- RF-06.1 Exportar PDF e Excel: relatório mensal de auditoria, previsto × realizado (detalhado em RF-03.1.14), relatório anual para assembleia/conselho, lista de achados.
- RF-06.2 Usuário escolhe período, categorias e seções.

### RF-07 Administração
- RF-07.1 Usuários e perfis.
- RF-07.2 Cadastro de unidades e frações ideais; plano de contas/rubricas e mapeamento entre os nomes usados no balancete e na PO (de-para, detalhado em RF-03.1.4).
- RF-07.3 Parâmetros de regras (tolerâncias, tetos de multa, índices) com vigência.
- RF-07.4 Trilha de auditoria.

### RF-09 Provedor de IA parametrizável por condomínio (decisão do usuário, 03/10/2026)
- RF-09.1 Cada condomínio tem um parâmetro **modo de IA**, que o Admin troca sem mexer no código:
  - `MCP_EXTERNO` (padrão do piloto): o sistema não chama modelo de IA externo nem envia texto para fora. A IA é o Claude do próprio usuário (Claude Desktop ou Claude Code, pela assinatura) conectado ao servidor MCP do sistema.
  - `API_KEY`: o sistema chama o Claude com a **chave de API do próprio condomínio** (BYOK). Isso habilita o chat na tela e a classificação automática.
  - `DESLIGADO`: nenhuma IA de respostas nem envio de texto para fora. Só regras, cálculos e as buscas do RF-04.18.
  - Exceção nos dois modos acima: modelo de **embeddings local**, rodando na infraestrutura do sistema/cliente, sem enviar texto para fora, é permitido para a busca por significado do módulo Assistente (decisão do usuário, 03/10/2026, Q12).
- RF-09.2 A chave é do condomínio (licença ou chave de cada cliente), guardada **criptografada**, nunca exibida depois de salva e nunca registrada em log. Cada condomínio paga a própria IA.
- RF-09.3 Todo uso de IA passa pela camada única (`ai-gateway`), que lê o modo do condomínio, registra tokens e custo por operação e aplica o mascaramento LGPD na fase de nuvem.
- RF-09.4 Dentro dos módulos contratados pelo condomínio (RF-10), as funções do sistema são as mesmas nos três modos. O modo só muda **quem** executa a parte de IA: o Claude externo via MCP ou o sistema via API. O modo de IA **não liga módulo**: um condomínio em `API_KEY` sem o módulo Assistente não tem chat.
- RF-09.5 Previsto para depois: provedor e modelo também configuráveis (ex.: Haiku para classificar, Sonnet para ler), com limite de gasto mensal por condomínio.
- RF-09.6 **Modo de IA por função** (pedido do usuário, 03/10/2026, 23:24): além do modo geral do condomínio, cada módulo com IA tem o **seu próprio modo de IA**, começando pelo Assistente (RF-04). Se não for configurado, herda o modo geral. A configuração do assistente tem, separadamente para **respostas** (chat) e para **embeddings** (busca por significado):
  - modo: `API_KEY`, `MCP_EXTERNO`, `DESLIGADO` e, previsto, `LOCAL` (modelo rodando na infraestrutura do cliente, sem enviar texto para fora);
  - provedor e modelo, escolhidos de um **catálogo de provedores** mantido por configuração, sem mexer no código. O catálogo inicial e os modelos são decisão do usuário em ADR do arquiteto; este documento não escolhe nenhum;
  - chave do cliente, com as mesmas regras do RF-09.2.
  - para **embeddings**, quando o modo do assistente é `MCP_EXTERNO` ou `DESLIGADO`, só é aceito provedor do catálogo marcado como **local** (sem enviar texto para fora); provedor externo é recusado ao salvar (decisão do usuário, 03/10/2026, Q12).
  - Dado o modo do assistente `DESLIGADO`, quando o Admin tenta configurar embeddings com provedor externo, então a configuração é recusada com a mensagem de que nesse modo só embeddings locais são permitidos; com provedor local, é aceita.
  - Dado o modo geral `MCP_EXTERNO` e o modo do assistente `API_KEY` com chave cadastrada, quando o módulo Assistente está ligado, então o chat aparece na tela e a classificação automática de arquivos continua sem IA do sistema (segue o modo geral).
  - Dado um novo provedor acrescentado ao catálogo por configuração, quando o Admin o escolhe para o assistente, então as perguntas passam a usar esse provedor sem nova versão do sistema (desde que a ADR correspondente esteja aprovada).
  - Dado o modo do assistente sem configuração própria, quando o Admin muda o modo geral, então o assistente passa a seguir o novo modo geral.
  - Dado o modo do assistente alterado, quando a alteração é salva, então a trilha de auditoria (RF-07.4) registra quem, quando, o modo/provedor/modelo anterior e o novo (nunca a chave).
- RF-09.7 **Registro de uso por condomínio** (base para cobrança futura): toda operação de IA e toda busca do módulo Assistente gera um registro com condomínio, módulo, função (pergunta, embeddings, busca por palavra, chamada MCP), usuário, data e hora, modo, provedor e modelo, tokens de entrada e saída e custo estimado quando houver. Em `MCP_EXTERNO` e `DESLIGADO`, sem tokens de provedor externo, registra a quantidade de buscas e chamadas a `buscar_documentos` (e o uso de embeddings locais, quando houver, Q12). A indexação registra arquivos e páginas indexados.
  - Dado um mês com 30 perguntas no chat de um condomínio em `API_KEY`, quando o Super-admin consulta o uso daquele mês, então vê 30 perguntas, a soma de tokens e o custo estimado, por condomínio e por módulo, e pode exportar em Excel.
  - Dado um condomínio em `MCP_EXTERNO`, quando o Claude do usuário chama `buscar_documentos` 12 vezes no mês, então o uso do mês mostra 12 chamadas e nenhum token do sistema.
  - Dado qualquer registro de uso, quando inspecionado, então não contém a chave de API nem o texto completo dos documentos.
  - O Admin do condomínio vê o uso do próprio condomínio; o Super-admin vê de todos (decisão do usuário, 03/10/2026, Q17).
  - Dado um Admin do condomínio A, quando ele consulta o uso de IA, então vê só os registros de A e não vê registros de nenhum outro condomínio.

### RF-08 Integração via MCP
- RF-08.1 Servidor MCP expondo ferramentas do sistema (consultar lançamentos, achados, POs, contratos, rodar conciliação, gerar relatório) para uso por agentes de IA (Claude Code, Claude Desktop), com as mesmas permissões dos perfis. Ferramentas que pertencem a um módulo (ex.: `buscar_documentos`, do módulo Assistente) só respondem para condomínios com o módulo ligado (RF-10).

### RF-10 Módulos contratáveis por condomínio (pedido do usuário, 03/10/2026, 23:24)
Origem: "o cliente pode escolher se quer esse módulo ou não e podemos vender no futuro como um adicional". O primeiro módulo opcional é o **Assistente** (RF-04). O desenho é genérico para outros módulos futuros, que não estão definidos aqui.
- RF-10.1 **Catálogo de módulos**: lista mantida pelo sistema com, para cada módulo, código, nome, descrição, o que ele inclui (telas, operações da API, ferramentas MCP, processamentos em segundo plano), módulos de que depende e estado padrão para condomínio novo. Hoje o catálogo tem só o **Assistente** (inclui: tela "Assistente", API de perguntas e de busca, `buscar_documentos`, indexação para o assistente e registro de uso). As funções que não estão em nenhum módulo do catálogo formam o **núcleo**, sempre ligado.
  - Dado o catálogo, quando o Super-admin o consulta, então vê o Assistente com tudo o que ele inclui e o estado em cada condomínio.
  - Dado um módulo novo no futuro, quando ele é criado, então basta uma entrada nova no catálogo e a verificação do estado nos pontos que ele inclui, sem mudar o mecanismo de ligar e desligar (verificado pela ADR do arquiteto).
- RF-10.2 **Ligar e desligar por condomínio**: só o **Super-admin da plataforma** liga ou desliga um módulo de um condomínio (é contrato comercial, não configuração do cliente), pela tela de administração da plataforma, sem mexer no código e sem reiniciar. Admin, Gestor e Usuário do condomínio veem quais módulos estão ligados, mas não alteram. Condomínio novo começa com o Assistente desligado; o piloto começa ligado (decisão do usuário, 03/10/2026, Q15).
  - Dado um condomínio recém-cadastrado, quando o cadastro termina, então o módulo Assistente está desligado e o menu "Assistente" não aparece.
  - Dado um Admin de condomínio, quando ele tenta ligar o módulo Assistente (tela ou API), então a ação é recusada.
  - Dado o Super-admin que liga o módulo de um condomínio, quando um usuário desse condomínio recarrega a tela, então o menu "Assistente" aparece, sem reinício de nenhum serviço.
- RF-10.3 **Módulo desligado**: todos os pontos que o módulo inclui ficam inativos para aquele condomínio, em todos os serviços (frontend, backend, rag, mcp).
  - Dado o Assistente desligado no condomínio A, quando qualquer perfil abre o frontend, então o menu "Assistente" não aparece.
  - Dado o Assistente desligado no condomínio A, quando alguém chama diretamente a API de perguntas ou de busca para A, então recebe recusa com a mensagem "módulo Assistente não contratado para este condomínio".
  - Dado o Assistente desligado no condomínio A, quando o Claude do usuário chama `buscar_documentos` para A, então a chamada é recusada com a mesma mensagem; as ferramentas do núcleo continuam funcionando.
  - Dado o Assistente desligado no condomínio A, quando um arquivo é enviado ou reprocessado, então ele passa pelo processamento do núcleo (extração, interpretação, conferências), mas nenhuma indexação para o assistente é feita e nenhum registro de uso de IA do assistente é gerado.
  - Dado o Assistente ligado no condomínio B e desligado em A, quando um usuário com acesso aos dois pergunta no B, então funciona normalmente no B, e A nunca aparece na busca.
- RF-10.4 **Ligar depois indexa o que já existe**: ao ligar o Assistente, o sistema dispara automaticamente a reindexação de todos os arquivos já enviados do condomínio (RF-04.6), a partir dos originais guardados, sem novo upload. O andamento aparece na tela Arquivos (RF-04.7).
  - Dado um condomínio com 40 arquivos enviados e o Assistente desligado, quando o Super-admin liga o módulo, então os 40 arquivos entram na fila de indexação e, ao fim, cada um está "indexado", "sem texto" ou "erro" com motivo, sem nenhum upload novo.
- RF-10.5 **Desligar depois**: desligar não apaga arquivos originais nem dados do núcleo. O índice do assistente fica guardado e inacessível enquanto o módulo está desligado; ao religar, só os arquivos novos ou alterados são indexados (decisão do usuário, 03/10/2026, Q14). Isso complementa o RF-10.4: ao religar, a reindexação automática pula os arquivos já indexados e sem alteração (mesmo hash e mesma versão).
  - Dado um módulo desligado e religado sem mudança nos arquivos, quando a reindexação termina, então a quantidade de trechos por arquivo é a mesma de antes (sem duplicar, RF-04.5).
- RF-10.6 **Trilha de ativação** (base para cobrança futura como adicional): cada ligar ou desligar fica na trilha de auditoria (RF-07.4) com condomínio, módulo, estado anterior e novo, data e hora, quem fez e motivo (opcional). O sistema calcula os **períodos ativos** de cada módulo por condomínio (início e fim) e os exporta em Excel, junto com o uso do período (RF-09.7). Nenhum valor de cobrança é calculado nesta fase.
  - Dado o Assistente ligado em 01/11/2026 e desligado em 15/12/2026 no condomínio A, quando o Super-admin consulta os períodos ativos de A, então vê um período de 01/11/2026 a 15/12/2026, com quem ligou e quem desligou.
  - Dado qualquer registro da trilha de ativação, quando alguém tenta alterá-lo ou excluí-lo, então a ação é recusada (trilha só cresce).

### RF-11 Menu "Análise da PO" (pedido do usuário, 06/10/2026)
Origem: pedido do usuário de 06/10/2026: *"ter a análise da PO aprovada, das anteriores e, o sistema se baseando nisso e nos balancetes, poder ter a projeção da próxima PO para usarmos como base. Isso como opções. [...] Seria um novo menu onde poderíamos iniciar com previsto x realizado, gráficos de indicadores e depois pensamos na funcionalidade de projetar a nova PO."* Detalha RF-03.2 (série histórica), RF-05.2 (gráficos) e RF-05.6, e esboça RF-03.3. **Q31 a Q36 respondidas pelo usuário em 06/10/2026** (§10): todas seguiram a recomendação, com dois ajustes: na Q32, a coluna impressa pode ser substituída pelo arquivo da PO anterior quando ele for conseguido; na Q36, a exportação em PDF e Excel do RF-11.8 e do RF-11.14 fica para depois. Reaproveita sem mudança as regras do RF-03.1.1 a RF-03.1.15 (leitura da PO, de-para, realizado, fundos, regra dos 20%, evidência, exportação e golden de setembro/2026).

Entrega em três fases:
1. **Fase 1, previsto × realizado de várias POs**: a PO aprovada vigente e as anteriores, cada uma no seu exercício, e a comparação entre exercícios (RF-11.1 a RF-11.9).
2. **Fase 2, indicadores**: gráficos a partir dos mesmos números da fase 1 (RF-11.10 a RF-11.14).
3. **Fase 3, projeção da próxima PO**: só esboçada aqui (RF-11.15). Ganha requisitos próprios depois das fases 1 e 2.

Situação atual (06/10/2026, main em `caa62b3`):
- O menu tem a seção "Orçamento" com "Previsto × realizado", "De-para" e "PO".
- O backend já guarda **várias versões de PO** e escolhe a PO de cada mês pelo exercício. Só uma PO vale para cada mês (RF-03.1.3).
- O de-para é por versão da PO (RF-03.1.4).
- A leitura grava a coluna "Orçado anterior" de cada linha (`orcado_anterior`, V7), mas nenhuma tela a usa.
- A tela de previsto × realizado mostra um exercício por vez. Não há comparação entre exercícios nem tela de indicadores. O Recharts já está no frontend (ADR 0004, Decisão 6).
- No piloto só existem a PO 2026/2027 e o fluxo de setembro/2026. As POs e os fluxos anteriores não foram enviados (Q5, P5 e P6).

Fora destes requisitos (decisão do arquiteto em ADR, com aprovação do usuário): onde fica guardada a correspondência de linhas entre exercícios; se o resultado de vários exercícios é calculado na consulta (ADR 0004, Decisão 5) ou guardado; o formato das consultas da API; os tipos de gráfico do Recharts. Ficam fora também, para requisito próprio: PO de outras administradoras (RF-00.5); o bloco "fora da PO" com rubrica criada na realocação (RF-02B.3), que aparece se existir mas não é exigido aqui; ferramentas MCP (RF-08.1); exportação em PDF e Excel da comparação e dos indicadores (RF-11.8 e RF-11.14, adiados pelo usuário na Q36).

Premissas (padrões adotados; o usuário pode mudar):
1. Cada exercício é calculado com **a própria PO e o próprio de-para**, exatamente como o RF-03.1 calcula um exercício hoje. A fase 1 não muda nenhum número de um exercício isolado: setembro/2026 continua igual ao golden.
2. Comparação entre exercícios mostra **fatos lado a lado** (valores e variação em R$ e %). O sistema não escreve causa, tendência nem julgamento (§3.3, RF-03.1.12).
3. Mês sem fluxo carregado continua "sem fluxo carregado", **nunca zero**, também nos gráficos (RF-03.1.10).
4. Os gráficos não calculam nada: mostram os números que o backend já entrega para as tabelas.

Termos usados abaixo (os demais estão no RF-03.1):
- **Exercício**: os 12 meses de vigência de uma PO confirmada (RF-03.1.3). Um condomínio tem vários, um por PO aprovada.
- **PO anterior**: a PO do exercício imediatamente antes de outro.
- **Coluna "Orçado anterior"**: valor mensal que a própria PO imprime para o exercício anterior, linha a linha (no piloto, a coluna "Orçado 2025/2026").
- **Correspondência de linhas**: ligação de uma linha da PO de um exercício a uma linha da PO de outro exercício, para comparar a mesma despesa ao longo dos anos. Estados: sugerido, confirmado, recusado (como o de-para).

**Fase 1: análise da PO aprovada e das anteriores**
- RF-11.1 **Menu "Análise da PO"**: a seção "Orçamento" do menu passa a se chamar "Análise da PO", com os itens "Previsto × realizado" (tela atual, RF-03.1.13), "Comparar exercícios" (RF-11.6) e, na fase 2, "Indicadores" (RF-11.10). "PO" e "De-para" continuam na mesma seção (Q31). O item "Projeção" só aparece quando a fase 3 for entregue. Todos os perfis veem os itens; as ações de edição continuam só do Admin.
  - Dado qualquer perfil, quando abre o menu, então vê a seção "Análise da PO" com "Previsto × realizado", "Comparar exercícios", "PO" e "De-para", e não vê "Projeção".
  - Dado um Usuário, quando abre qualquer tela da seção, então nenhuma ação de edição aparece.
- RF-11.2 **Enviar POs anteriores**: o Gestor ou o Admin envia a PO de exercícios passados na categoria PO, como hoje (RF-00.3, carga histórica). Cada uma é lida, conferida e confirmada pelas regras do RF-03.1.1 a RF-03.1.3, com o exercício informado pelo Admin e a ata, quando houver. Exercícios **não podem se sobrepor**: a confirmação de uma PO cujo exercício cruza um mês já coberto por outra PO confirmada é recusada, com o mês e a PO em conflito (reaprovação do mesmo exercício continua criando nova versão, RF-03.1.3). PO de layout não reconhecido fica listada como "layout não reconhecido", sem números, até o RF-00.5.
  - Dado a PO 2026/2027 do piloto confirmada (05/2026 a 04/2027) e uma cópia de teste da mesma PO enviada como "2025/2026", quando o Admin a confirma com exercício 05/2025 a 04/2026, então o condomínio passa a ter dois exercícios, e a tela "PO" lista os dois, do mais recente para o mais antigo.
  - Dado o mesmo caso, quando o Admin tenta confirmar a cópia com exercício 06/2025 a 05/2026, então a confirmação é recusada com "05/2026 já está no exercício da PO 2026/2027".
  - Dado uma PO de outra administradora, quando enviada, então aparece como "layout não reconhecido", sem números, e não entra em nenhuma comparação.
- RF-11.3 **Mês entre dois exercícios** (Q34): quando há meses entre o fim de um exercício e o início do seguinte (ex.: a PO nova aprovada com atraso), esses meses ficam "sem PO aprovada", como hoje. O Admin pode marcar a PO anterior como **prorrogada** até um mês informado, com justificativa obrigatória; os meses prorrogados usam a PO anterior e aparecem com a marca "PO prorrogada" na tela e na exportação. A marcação vai para a trilha. Nunca é automática.
  - Dado um exercício de teste 04/2025 a 03/2026 e a PO 2026/2027 do piloto (05/2026 a 04/2027), quando o usuário abre 04/2026, então vê "sem PO aprovada para este mês".
  - Dado o mesmo caso, quando o Admin marca a PO 2025/2026 como prorrogada até 04/2026, com justificativa, então 04/2026 é calculado com essa PO e mostra "PO prorrogada", e a trilha registra quem, quando e a justificativa.
  - Dado a mesma marcação sem justificativa, ou de um Gestor ou Usuário, quando salva, então é recusada.
  - **PO nova sobre meses prorrogados** (ADR 0005, Decisão 4, aprovada em 07/10/2026): a confirmação de uma PO cujo exercício cobre um mês prorrogado vale, e a prorrogação é encurtada até o mês anterior ao início da nova, com evento automático na trilha.
  - **Meses prorrogados fora do acumulado**: o acumulado do exercício continua com os 12 meses (RF-03.1.10). Os meses prorrogados aparecem depois deles, marcados "prorrogado", com os números do mês.
  - Dado a PO 2025/2026 de teste prorrogada até 05/2026, quando o Admin confirma a PO 2026/2027 com exercício 05/2026 a 04/2027, então a confirmação vale, a prorrogação passa a terminar em 04/2026, e a trilha registra a mudança com o motivo "PO 2026/2027 confirmada".
  - Dado a PO 2025/2026 de teste prorrogada até 04/2026, quando o usuário abre o acumulado do exercício 2025/2026, então ele soma só os 12 meses do exercício, e 04/2026 aparece depois deles, marcado "prorrogado".
  - *Se Q34 for "Não": meses entre exercícios ficam sempre "sem PO aprovada".*
- RF-11.4 **Previsto × realizado de qualquer exercício**: a tela atual ganha o filtro "Exercício", com todos os exercícios confirmados do mais recente para o mais antigo, e abre no exercício vigente. Mês a mês, acumulado, fundos, regra dos 20%, blocos à parte, evidência e exportação seguem o RF-03.1, cada exercício com a sua PO e o seu de-para. A tela mostra, por exercício, os meses com e sem fluxo e o estado do de-para ("N de M contas confirmadas").
  - Dado o piloto com a PO 2026/2027 e o fluxo de setembro/2026, quando o usuário abre o exercício 2026/2027 em 09/2026, então os números são os do RF-03.1.6 (despesa realizada 446.176,89; previsto 451.620,13; execução 98,8%; excesso 38.880,19, 8,6%), centavo a centavo.
  - Dado o exercício de teste 2025/2026 confirmado e sem nenhum fluxo carregado, quando o usuário o abre, então os 12 meses aparecem "sem fluxo carregado", o acumulado mostra só o previsto do exercício, e nenhum mês aparece com realizado R$ 0,00.
  - Dado o exercício de teste 2025/2026 com o de-para ainda "sugerido" (oferecido a partir da PO 2026/2027, RF-03.1.4), quando o usuário abre um mês com fluxo, então vale o critério do RF-03.1.4: nada entra em linha da PO até o Admin confirmar.
- RF-11.5 **PO anterior pela coluna impressa** (Q32): quando o arquivo da PO anterior não foi enviado, a comparação de previsto usa a coluna "Orçado anterior" da PO mais antiga carregada, marcada como **"PO anterior pela coluna impressa"**. Só tem previsto (sem realizado, sem exercício próprio) e segue as linhas da PO que a imprimiu. A conferência da coluna é a mesma do RF-03.1.2: soma das linhas contra subtotal e total impressos, com a tolerância de R$ 0,01 (Q30). **Substituição pelo arquivo** (Q32): quando o arquivo da PO anterior é enviado e confirmado depois, ele **substitui** a coluna impressa naquele exercício, sem ação extra: a comparação passa a usar a PO enviada, com realizado se houver fluxo, e a coluna impressa continua visível só como conferência. Os dois são mostrados lado a lado, e uma diferença acima de R$ 0,01 por grupo entre o valor da PO anterior e a coluna impressa vira **aviso** "a coluna 'Orçado anterior' difere da PO anterior enviada", com os dois valores (não é achado).
  - Dado a PO 2026/2027 do piloto, sem nenhuma PO anterior enviada, quando o usuário abre "Comparar exercícios", então vê a coluna "2025/2026 (coluna impressa)" com: total das despesas 441.304,38; fundos 22.065,22; previsto do mês (sem fundos) 419.239,16; Pessoal 37.661,43; Contratos 348.631,55; Aquisição de bens 2.350,00; Administrativas 18.525,42; Materiais 19.300,00; Serviços 14.270,99; e só previsto, sem realizado. Os valores de grupo são os impressos se a soma das linhas da coluna bater com eles na tolerância; senão, vale a regra do RF-03.1.2.
  - Dado o mesmo caso, quando a linha 1.3.20 é comparada, então mostra 17.195,00 (2025/2026) e 8.000,00 (2026/2027), variação −9.195,00 e −53,5%; e o "%" impresso "−53,47%" aparece como texto lido.
  - Dado uma cópia de teste da PO 2025/2026 enviada com o subtotal de Pessoal 37.000,00, quando confirmada, então a comparação mostra o aviso com 37.000,00 (PO enviada) e 37.661,43 (coluna impressa), e usa o da PO enviada na coluna 2025/2026.
  - Dado a comparação usando a coluna impressa para 2025/2026, quando a PO 2025/2026 é enviada e confirmada, então a coluna passa a se chamar "2025/2026" (sem "coluna impressa"), usa os valores da PO enviada, e o usuário vê a coluna impressa só no detalhe de conferência.
- RF-11.6 **Tela "Comparar exercícios"**: para todos os perfis. Filtros: exercícios (dois ou mais, padrão: o vigente e o anterior), fundo, e "mesmos meses" (compara só os meses que têm fluxo nos dois exercícios, ex.: set/2025 × set/2026). Três visões:
  1. **Resumo por exercício**: previsto do mês, previsto do exercício, meses com fluxo, previsto e realizado acumulados, execução, maior excesso mensal da regra dos 20% (em R$ e %), meses acima do limite e achados abertos do exercício. Variação do previsto do mês contra o exercício anterior, em R$ e %.
  2. **Por grupo** (1.1 a 1.9): previsto e realizado de cada exercício lado a lado, com a variação. Grupos são os do layout e não precisam de correspondência.
  3. **Por linha**: só com a correspondência de linhas (RF-11.7). Linhas sem correspondência confirmada aparecem num bloco "sem correspondência", com o valor de cada exercício, e nunca são somadas a outra linha.
  Fundos de reserva e de obras comparam a arrecadação (RF-03.1.9). Variação em % só quando a base é diferente de zero; com base zero aparece "nova no exercício".
  - Dado o piloto com só a PO 2026/2027 e a coluna impressa, quando o usuário abre o resumo, então vê o previsto do mês 451.620,13 contra 419.239,16, variação +32.380,97 e +7,7%; e Contratos 336.274,18 (soma das linhas, Q30) contra 348.631,55, variação −12.357,37 e −3,5%.
  - Dado a linha 1.3.25 Caixa D'água (anterior 0,00; atual 1.518,93), quando exibida, então a variação aparece como "nova no exercício", sem percentual.
  - Dado dois exercícios com fluxo carregado só em setembro de cada um, quando o usuário marca "mesmos meses", então o acumulado de cada exercício soma só setembro, e o filtro informa "comparando: setembro".
  - Dado qualquer valor da comparação, quando o usuário clica nele, então abre o previsto × realizado daquele exercício, mês e linha ou grupo, com a evidência do RF-03.1.12.
  - Dado qualquer tela ou exportação da comparação, quando inspecionada, então nenhum termo da lista de conduta (RF-04.15) aparece, e nenhum texto explica a variação.
- RF-11.7 **Correspondência de linhas entre exercícios** (Q33): o sistema sugere ligar a linha de um exercício à linha de outro com a **mesma conta da PO** (código e nome, ex.: "1682 - Sindicatura Profissional") **e o mesmo grupo** (ex.: 1.3), mostrando o motivo. O grupo entra porque a mesma conta aparece em mais de uma linha da PO (ex.: 1606 em 1.3.5 e 1.7.2); se mais de uma linha casar, não há sugestão. A correspondência é guardada como **rubrica do condomínio**: linhas de exercícios diferentes ligadas à mesma rubrica correspondem (ADR 0005, Decisão 1, aprovada em 07/10/2026). Não usa o código do item (ex.: 1.3.20), que muda entre POs, nem a descrição, que costuma ser o fornecedor. Só o Admin confirma, troca ou recusa; sugestão nunca vale sozinha; tudo na trilha (como RF-03.1.4). Uma linha pode corresponder a várias do outro exercício (divisão ou junção de linhas), e o sistema mostra o grupo de linhas somado dos dois lados.
  - Dado as linhas 1.3.20 "1682 - Sindicatura Profissional" de dois exercícios, quando a correspondência é sugerida, então aparece "sugerido" com o motivo "mesma conta da PO e mesmo grupo: 1682 - Sindicatura Profissional, 1.3", e a comparação por linha só a usa depois da confirmação.
  - Dado as duas linhas 1.3.2 da PO 2026/2027 (Bombas e Caixa D'água, esta renumerada 1.3.25 na confirmação), quando a correspondência é sugerida, então cada uma é sugerida separadamente pela conta da PO, e nenhuma é somada à outra sem a confirmação do Admin.
  - Dado a conta 1606 nas linhas 1.3.5 e 1.7.2 de um exercício, quando a correspondência é sugerida para outro exercício, então cada linha é sugerida só para a linha do mesmo grupo, e as duas nunca são somadas.
  - Dado um Gestor ou Usuário, quando tenta confirmar uma correspondência (tela ou API), então a ação é recusada (403).
  - Dado o Admin que confirma as sugestões em lote, quando salva, então há um evento na trilha por linha.
  - *Se Q33 for "Não": a comparação fica só por grupo e por exercício (visões 1 e 2 do RF-11.6).*
- RF-11.8 *(adiado, Q36: fica para entrega futura)* **Exportação da comparação** em PDF e Excel (padrão do RF-03.1.14 e da ADR 0004, Decisão 6): cabeçalho com condomínio, exercícios e POs comparados (arquivo, versão, hash, ou "coluna impressa"), filtros, data e hora, quem gerou e estado do de-para e da correspondência de cada exercício. Marca "PROVISÓRIO" quando algum exercício tem conta sem linha da PO, valor a realocar ou correspondência pendente. Números idênticos aos da tela.
  - Dado a comparação do piloto do RF-11.6, quando exportada em PDF e em Excel, então 451.620,13, 419.239,16, +32.380,97 e +7,7% aparecem iguais aos da tela.
- RF-11.9 **Caso de aceite da fase 1** (RNF-10): o golden de setembro/2026 (RF-03.1.15) continua passando sem mudança. Entra um caso novo com a PO 2026/2027 e a coluna "Orçado anterior" (RF-11.5 e RF-11.6). Quando o usuário enviar a PO 2025/2026 e fluxos do exercício anterior (P6), entra um caso real com dois exercícios; até lá, o caso com dois exercícios usa a cópia de teste dos critérios acima.

**Fase 2: indicadores e gráficos**
- RF-11.10 **Tela "Indicadores"**, para todos os perfis, com filtros de exercício e fundo. Mostra os gráficos do RF-11.11 a partir dos mesmos números das telas da fase 1 (premissa 4). Cada gráfico tem título, período, unidade (R$ ou %), a data dos dados e uma **tabela alternativa** com os mesmos valores (acessibilidade e conferência).
  - Dado setembro/2026 do piloto, quando o usuário abre os indicadores do exercício 2026/2027, então todos os valores de setembro nos gráficos são iguais aos da tela "Previsto × realizado", centavo a centavo (execução 98,8%; excesso 8,6%; limite 20%).
  - Dado qualquer gráfico, quando o usuário abre a tabela alternativa, então vê os mesmos valores do gráfico.
- RF-11.11 **Indicadores da primeira entrega** (lista proposta; o usuário pode tirar ou incluir, Q35):
  1. **Execução mensal** do fundo Condomínio (realizado ÷ previsto do mês, em %) nos 12 meses do exercício, com a referência de 100%.
  2. **Regra dos 20%**: excesso do mês em % do previsto, com a linha do limite de 20% (Conv. 16.2) e o cenário máximo com "a realocar" e "sem linha da PO" (RF-03.1.11).
  3. **Previsto × realizado acumulado** do exercício, mês a mês.
  4. **Realizado por grupo** (1.1 a 1.8), mês a mês.
  5. **Maiores diferenças** do acumulado: as 10 linhas mais acima e as 10 mais abaixo do previsto, em R$.
  6. **Fundos de reserva e de obras**: arrecadação × previsto, mês a mês (RF-03.1.9).
  7. **Comparação entre exercícios**: previsto do mês por grupo em cada exercício, e execução acumulada de cada exercício (RF-11.6).
  - Dado o exercício 2026/2027 com só setembro carregado, quando o gráfico 1 é exibido, então setembro mostra 98,8% e os outros 11 meses aparecem marcados "sem fluxo carregado", sem barra e sem ponto em zero.
  - Dado setembro/2026, quando o gráfico 2 é exibido, então mostra 8,6% com a linha de 20% e o cenário máximo de 8,8%, marcado "provisório" por haver valor a realocar.
  - Dado setembro/2026, quando o gráfico 5 é exibido, então 1.3.10 Vigia e Portaria aparece entre as mais acima, com +6.793,38.
  - Dado setembro/2026, quando o gráfico 6 é exibido, então Reserva mostra 14.260,79 × 13.548,60 e Obras 9.705,06 × 9.032,40.
- RF-11.12 **Do gráfico à evidência** (RNF-04): clicar num ponto, barra ou linha abre o previsto × realizado daquele exercício, mês, fundo e linha ou grupo, com os lançamentos (RF-03.1.12).
  - Dado o gráfico 5, quando o usuário clica em 1.3.10, então abre a linha 1.3.10 de setembro/2026 com os lançamentos da conta 1442 que somam 86.816,34.
- RF-11.13 **Sem julgamento nos gráficos** (§3.3): cores marcam só o que tem base. A única marcação de alerta é o excesso acima de 20% (Conv. 16.2), em vermelho; o resto usa cores neutras. Nenhum título, legenda ou dica explica causa ou tendência, e nenhum termo do RF-04.15 aparece. Outros limites com cor (ex.: execução acima de 105%) só entram com a fonte e o valor definidos pelo usuário como parâmetro do condomínio (Q35).
  - Dado um mês de teste com excesso de 20,1%, quando o gráfico 2 é exibido, então a barra do mês fica vermelha; e com 20,0%, fica neutra.
- RF-11.14 *(adiado, Q36: fica para entrega futura)* **Gráficos na exportação**: a exportação dos indicadores sai em PDF e Excel com as tabelas dos gráficos e as barras de execução em CSS, sem imagem de gráfico (ADR 0004, Decisão 6 A). Gráfico no PDF exige biblioteca nova e decisão do usuário (Q36).
  - Dado a exportação dos indicadores de setembro/2026, quando comparada com a tela, então todos os valores das tabelas são iguais.

**Fase 3: projeção da próxima PO (esboço, sem critérios de aceite)**
- RF-11.15 **Projeção como base opcional** (detalha RF-03.3 e alimenta o RF-03.5 "Criar nova PO"). Ideia a validar com o usuário depois das fases 1 e 2:
  - O usuário escolhe gerar uma projeção do próximo exercício. Ela é um **rascunho**, nunca substitui uma PO e nunca é usada no previsto × realizado.
  - Para cada linha, o sistema propõe um valor por um **método visível e determinístico**, escolhido por linha. Métodos: repetir a PO vigente; média do realizado dos últimos N meses com fluxo; PO vigente + índice (IPCA, IGP-M, dissídio) com data-base; valor de contrato vigente com reajuste. Ao lado, a premissa e os números usados, com evidência.
  - A IA, quando o modo permite (RF-09), pode sugerir premissas a partir das Observações da PO, dos contratos e das atas. Nunca escreve o número final sem o método. Tudo funciona no modo `DESLIGADO`.
  - Pontos a decidir antes dos requisitos: de onde vêm os índices (fonte oficial, como o Banco Central, é decisão de arquitetura e precisa de ADR); quantos exercícios de histórico usar; tratamento de linhas sazonais (ex.: 1.6.21, pagamento anual); cenários (base, otimista, pessimista, RF-03.3); e como a projeção vira rascunho na tela "Criar nova PO".
  - Depende de: fase 1 com pelo menos um exercício completo de fluxos (P5, P6), catálogo de linhas (RF-03.5) e contratos lidos.

**Matriz de regras deste bloco**

| Regra | Base | Parâmetro | Severidade |
|---|---|---|---|
| Coluna "Orçado anterior" difere da PO anterior enviada | Boa prática de conferência (RF-02.3) | R$ 0,01 por grupo (Q30) | Aviso (não é achado) |
| Mês usando PO prorrogada | Decisão do Admin com justificativa (RF-11.3) | Mês final da prorrogação | Aviso informativo |
| Exercícios sobrepostos | RF-03.1.3 (uma PO por mês) | Meses do exercício | Bloqueia a confirmação (não é achado) |

Nenhum achado novo: os achados de cada exercício são os do RF-03.1 (regra dos 20%, conta sem linha da PO e teto do fundo de reserva).

---

## 7. Requisitos não funcionais

| Código | Requisito |
|---|---|
| RNF-01 | Rodar 100% local com um único comando (ex.: `docker compose up`). |
| RNF-02 | Precisão monetária: valores em decimal/centavos inteiros, nunca ponto flutuante. |
| RNF-03 | Reprodutibilidade: mesmo arquivo + mesmas regras = mesmos achados. Versão das regras registrada em cada execução. |
| RNF-04 | Rastreabilidade: todo número do dashboard leva aos lançamentos e documentos de origem. |
| RNF-05 | Desempenho: processar um mês típico (≈ 10 arquivos, ≈ 500 lançamentos) em poucos minutos; telas carregam do banco, sem reprocessar. |
| RNF-06 | Idioma: interface, relatórios e respostas em português do Brasil; formatos R$ e dd/mm/aaaa. |
| RNF-07 | Backups locais do banco e dos arquivos. |
| RNF-08 | Portável para nuvem sem reescrita (12-factor: configuração por variáveis de ambiente, armazenamento de arquivos abstraído). |
| RNF-09 | Custo de IA controlado: cache de extrações, chamadas só quando necessário, registro de tokens por operação. |
| RNF-10 | Testes automatizados com um conjunto de documentos de referência ("golden files") por categoria. |

---

## 8. MVP local — corte proposto

**Entra no MVP**
1. Login com os 3 perfis.
2. Upload e categorização de PO, balancete, extrato, contratos, folha e comprovantes.
3. Extração de PDF digital, Excel e Word (OCR só se Q1 confirmar escaneados).
4. Conciliação balancete × extrato e conferência aritmética (RF-02.1, 02.3).
5. Previsto × realizado contra a PO do ano (RF-03.1.1 a RF-03.1.15): leitura da PO no layout da administradora do piloto, de-para confirmado pelo Admin, comparação mês a mês e acumulada do exercício por fundo (Condomínio, Reserva e Obras), regra dos 20% da Conv. 16.2, tela e exportação em PDF e Excel. Caso de aceite: setembro/2026 do piloto. Ficam para depois: PO de outras administradoras (RF-00.5), realizado por competência (Q18) e conversão para valor bruto (Q19).
6. Dashboard inicial, indicador do último arquivo, tela de arquivos ordenada e por categoria.
7. Lista de achados com evidência.
8. Exportação PDF/Excel do relatório mensal.
9. Assistente RAG com citação de fontes.

**Fase 2**: lançamento × comprovante, contrato × pagamento, folha, regras legais/convencionais, anomalias estatísticas, servidor MCP completo.
**Fase 3**: projeção orçamentária com cenários, relatório anual da assembleia, preparação para nuvem (mascaramento LGPD, autenticação mais forte, backups gerenciados).

---

## 9. Preparação para a nuvem (registrar já, implementar depois)

- Mascaramento de dados pessoais (nomes e CPF de funcionários e condôminos, salários individuais) antes de enviar a modelos de IA externos e na exibição para perfis sem necessidade.
- Base legal LGPD, política de retenção, registro das operações de tratamento.
- HTTPS, MFA para admin, segredos em cofre.
- Observação para já: mesmo local, os arquivos são enviados à API do modelo de IA quando a IA é usada. Se isso não for aceitável, existe a opção de modelo local (ver 03-tecnologias.md, decisão T5).

---

## 10. Pendências e questões abertas

| # | Pendência | Quem resolve |
|---|---|---|
| P1 | Enviar convenção, RI e PO do condomínio piloto (Mio Residencial Parque), que serão o primeiro caso de implantação | Usuário |
| P2 | ✔ Fluxo de caixa de set/2026 recebido. Faltam: extrato bancário do mesmo mês, 1 contrato, resumo da folha, relatório de inadimplência | Usuário |
| Q1 | Existem PDFs escaneados (imagem)? Define se o OCR entra no MVP | Usuário |
| Q2 | O condomínio tem conselho fiscal? Ele usará o sistema (como perfil Usuário)? | Usuário |
| Q3 | Quantas contas bancárias (corrente, poupança, fundo de reserva, aplicações)? | Usuário |
| Q4 | Gestor pode justificar achados de arquivos que ele mesmo enviou? | Usuário |
| Q5 | Quantos anos de histórico (POs/balancetes anteriores) estão disponíveis? | Usuário |
| Q6 | Aprovar as categorias propostas no §5 | Usuário |
| P3 | Escrever com o agente de requisitos as perguntas do conjunto de avaliação do assistente (RF-04.19), com respostas e fontes esperadas do piloto | Usuário |
| Q7 | ✔ Sim (usuário, 03/10/2026). A "Busca nos documentos" por palavra, sem IA, fica disponível em todos os modos, inclusive `DESLIGADO`, com o módulo Assistente ligado (RF-04.18) | Usuário |
| Q8 | ✔ Sim (usuário, 03/10/2026). O assistente pode transcrever um valor escrito num documento, como trecho literal marcado "não conferido", sem cálculo (RF-04.13) | Usuário |
| Q9 | ✔ Sim (usuário, 03/10/2026). Localização das citações: página (PDF), aba e linha (Excel), seção ou parágrafo (Word) | Usuário |
| Q10 | ✔ Não, por enquanto (usuário, 03/10/2026). O histórico das conversas não é guardado entre sessões (RF-04.11) | Usuário |
| Q11 | ✔ Sim (usuário, 03/10/2026). Lista inicial de termos de conduta proibidos fora de citação: "desvio", "fraude", "roubo", "culpa" (RF-04.15) | Usuário |
| Q12 | ✔ Sim (usuário, 03/10/2026). Nos modos `MCP_EXTERNO` e `DESLIGADO`, embeddings só com modelo **local**, sem enviar texto para fora (RF-04.18, RF-09.1, RF-09.6) | Usuário |
| Q13 | ✔ Sim (usuário, 03/10/2026). A "Busca nos documentos" por palavra faz parte do módulo Assistente e some quando ele está desligado (RF-04.18, RF-10) | Usuário |
| Q14 | ✔ Sim (usuário, 03/10/2026). Ao desligar o módulo Assistente, o índice fica guardado; ao religar, só arquivos novos ou alterados são indexados (RF-10.5) | Usuário |
| Q15 | ✔ Sim (usuário, 03/10/2026). Condomínio novo começa com o módulo Assistente desligado; o piloto começa ligado (RF-10.2) | Usuário |
| Q16 | ✔ Sim (usuário, 03/10/2026). No modo de IA `DESLIGADO`, o servidor MCP continua respondendo, inclusive `buscar_documentos` com o módulo ligado (RF-04.16) | Usuário |
| Q17 | ✔ Sim (usuário, 03/10/2026). O Admin do condomínio vê o uso de IA do próprio condomínio; o Super-admin vê de todos (RF-09.7) | Usuário |
| P4 | Enviar a ata da assembleia de maio/2026 que aprovou a PO 2026/2027 (define o início do exercício, RF-03.1.3) | Usuário |
| P5 | Enviar os fluxos de caixa de mai a ago/2026, para o acumulado do exercício (RF-03.1.10) | Usuário |
| Q18 | ✔ Caixa: mês pela data do lançamento no fluxo (RF-03.1.8) (usuário, 04/10/2026) | Usuário |
| Q19 | ✔ Fluxo: valor como está no fluxo, sem converter para bruto (RF-03.1.8) (usuário, 04/10/2026) | Usuário |
| Q20 | ✔ Exercício: acumulado do exercício da PO (RF-03.1.10) (usuário, 04/10/2026) | Usuário |
| Q21 | ✔ Sim: reserva e obras comparam a arrecadação com o previsto (RF-03.1.9) (usuário, 04/10/2026) | Usuário |
| Q22 | ✔ Sim: energia, água, gás e seguro predial ("rateio à parte") ficam fora (RF-03.1.9) (usuário, 04/10/2026) | Usuário |
| Q23 | ✔ Linha: o excesso da regra dos 20% é somado linha a linha (RF-03.1.11) (usuário, 04/10/2026) | Usuário |
| Q24 | ✔ Sim: previsto mensal igual em todos os meses, com as Observações visíveis (RF-03.1.10) (usuário, 04/10/2026) | Usuário |
| Q25 | ✔ Recibos: arrecadação é só a cota recebida (RF-03.1.9) (usuário, 04/10/2026). Conferido no fluxo de setembro (ADR 0004): "RECIBOS ACUMULADOS" somam 14.260,79 na reserva e 9.705,06 em obras; conflito 4 resolvido | Usuário |
| Q26 | ✔ Não: o valor "a realocar" fica fora do excesso, com o cenário máximo visível (RF-03.1.11) (usuário, 04/10/2026) | Usuário |
| Q27 | ✔ Sim: achado cuja condição deixa de existir passa a "não se aplica mais", com o evento que causou (RF-03.1.12) (usuário, 04/10/2026) | Usuário |
| Q28 | ✔ Sim: a justificativa com ata (RF-02.9) fica fora desta entrega (RF-03.1.11, RF-03.1.12) (usuário, 04/10/2026) | Usuário |
| Q29 | ✔ Sim: o Admin pode confirmar a PO "ciente da divergência", com justificativa, usando a soma das linhas (RF-03.1.2) (usuário, 04/10/2026) | Usuário |
| Q30 | ✔ Tolerar 1 centavo: diferença de até R$ 0,01 entre a soma das linhas e o subtotal impresso vira aviso de arredondamento, e os cálculos usam a soma das linhas (previsto do mês do piloto 451.620,13) (RF-03.1.2) (usuário, 04/10/2026) | Usuário |
| P6 | Enviar a PO 2025/2026 (e anteriores, se houver) e os fluxos de caixa desses exercícios, dizendo se eram da mesma administradora (layout). Define o caso real com dois exercícios (RF-11.9) e se o RF-00.5 vira bloqueio | Usuário |
| Q31 | ✔ Sim (usuário, 06/10/2026). O menu "Análise da PO" substitui a seção "Orçamento" e mantém "PO" e "De-para" dentro dele? **Sim (recomendado)** / Não (PO e De-para vão para Administração) (RF-11.1) | Usuário |
| Q32 | ✔ Sim, com a opção de substituir pelo arquivo da PO anterior quando ele for conseguido (usuário, 06/10/2026). Sem o arquivo da PO anterior, usar a coluna "Orçado anterior" impressa na PO atual como previsto do exercício anterior, marcada "coluna impressa"? **Sim (recomendado)** / Não (só compara com PO enviada) (RF-11.5) | Usuário |
| Q33 | ✔ Sim (usuário, 06/10/2026). Comparar linha a linha entre exercícios por correspondência sugerida pela conta da PO e confirmada pelo Admin? **Sim (recomendado)** / Não (só por grupo) (RF-11.7) | Usuário |
| Q34 | ✔ Sim (usuário, 06/10/2026). Mês entre dois exercícios: o Admin pode marcar a PO anterior como "prorrogada", com justificativa? **Sim (recomendado)** / Não (fica "sem PO aprovada") (RF-11.3) | Usuário |
| Q35 | ✔ Sim (usuário, 06/10/2026). Os 7 indicadores do RF-11.11, com cor de alerta só para o excesso acima de 20%? **Sim (recomendado)** / Ajustar a lista ou os limites | Usuário |
| Q36 | ✔ Sim, e por enquanto sem exportação em PDF ou Excel da comparação e dos indicadores (usuário, 06/10/2026). Gráficos só na tela, com tabelas e barras em CSS no PDF, sem biblioteca nova? **Sim (recomendado)** / Não (gráfico no PDF, com Batik, por ADR) (RF-11.14) | Usuário |
| T* | Decisões de tecnologia (ver 03-tecnologias.md) | Usuário |

---

## Fontes consultadas

- Código Civil, arts. 1.331–1.358 (capítulo de condomínio): [SíndicoNet — íntegra](https://www.sindiconet.com.br/informese/integra-administracao-legislacao-codigo-civil-capitulo-sobre-condominios/amp); art. 1.336: [Petições Online](https://www.peticoesonline.com.br/art-1336-cc). O site oficial planalto.gov.br estava inacessível deste ambiente; recomenda-se conferir lá a redação vigente.
- Boas práticas: [SíndicoNet — Guia de prestação de contas](https://www.sindiconet.com.br/informese/guia-prestacao-contas-condominio-boas-praticas-colunistas-artigos-e-opinioes), [Jornal dos Condomínios](https://condominiosc.com.br/jornal-dos-condominios/gestao/3214-hora-de-preparar-a-prestacao-de-contas), [Como analisar um balancete](https://condominiosc.com.br/canal-aberto/3945-gostaria-de-saber-como-analisar-um-balancete-da-prestacao-de-contas-do-mes).
- PL 4.072/2019: [Paraíba Business](https://paraibabusiness.com.br/nova-lei-que-exige-balancete-mensal-de-condominios-e-populista/).
- Lei 14.905/2024 (taxa legal do art. 406) e Lei 14.309/2022 (assembleia virtual, art. 1.354-A): conhecimento prévio, a confirmar no texto oficial.
- Prazos de guarda de documentos: orientação geral, **confirmar com o contador**.
- Previsto × realizado (RF-03.1.1 a RF-03.1.15): fontes do piloto em `/mnt/project-files/condominio/piloto-mio/`: `fontes/PO-2026-2027-aprovada.pdf` (página 1) e `fontes/PO-2026-2027-texto.txt`; `fontes/convencao-texto-ocr.txt` (cláusulas 10.2, 16.1 IX, 16.2, 18.4 e 20.1; texto de OCR, conferir no PDF registrado); `06-previsto-realizado-2026-09.md`; `previsto-realizado-2026-09.csv`; `mapa-contas-fluxo-para-PO.csv`; `05-implantacao-parametros.md`. Os artigos 1.348, VI e 1.350 do CC seguem as fontes do §3.1.
