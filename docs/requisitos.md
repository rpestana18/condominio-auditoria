# Documento de Requisitos — Sistema de Auditoria e Contabilidade do Condomínio

Versão 0.2 · 03/10/2026 (multi-condomínio) · Status: **rascunho para validação do usuário**
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

Os perfis valem **por condomínio**: a mesma pessoa pode ser Gestor em um condomínio e Usuário em outro. Acima deles existe o **Super-admin da plataforma**, que cadastra condomínios e o primeiro Admin de cada um.


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
- RF-00.8 **De-para de contas**: a PO e o fluxo da administradora podem usar planos de contas diferentes (no piloto, o código 1621 é Interfones na PO e Material Hidráulico no fluxo). O casamento é feito por uma tabela de-para por condomínio, sugerida pela IA pelo nome e confirmada pelo Admin, nunca pelo número.
- RF-00.9 **Fundo em recomposição planejada**: o gestor pode marcar um fundo negativo como investimento deliberado. O sistema passa a acompanhar a tendência em vez de alertar pelo prazo de rateio.


### RF-01 Ingestão de documentos
- RF-01.1 Upload de PDF, XLSX/XLS e DOCX, individual ou em lote, com escolha de categoria e competência.
- RF-01.2 Sugerir categoria e competência automaticamente (IA), com confirmação do gestor.
- RF-01.3 Extrair texto e tabelas. PDF digital por extração direta; **PDF escaneado por OCR** (questão aberta Q1: existem escaneados?).
- RF-01.4 Normalizar em lançamentos estruturados: data, descrição, fornecedor/favorecido, CNPJ/CPF, conta contábil/rubrica, valor, documento de origem, página/linha.
- RF-01.5 Detectar duplicidade de arquivo (hash) e de lançamento.
- RF-01.6 Mostrar o status de cada arquivo: recebido → extraído → validado → indexado (ou erro com motivo).

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
- RF-02B.4 A realocação é uma **camada do sistema**: não altera o lançamento original da administradora, que continua rastreável. O previsto × realizado usa a rubrica realocada.
- RF-02B.5 Realocações aprendidas viram regras (ex.: "tinta" vai para Material de Pintura) e são aplicadas sozinhas nos meses seguintes, com revisão do gestor.

### RF-03 Orçamento e previsão
- RF-03.1 Previsto (PO) × realizado por rubrica, mês a mês e acumulado no ano.
- RF-03.2 Série histórica com as POs e balancetes de anos anteriores.
- RF-03.3 Projeção do próximo ano por rubrica considerando histórico, contratos vigentes (reajustes), dissídio dos funcionários e índices (IPCA/IGP-M) — com cenários (base, otimista, pessimista) e a premissa de cada número visível.
- RF-03.4 Simulação da cota condominial por unidade resultante da projeção.
- RF-03.5 **Tela "Criar nova PO"** (a ser desenhada junto com o usuário):
  - parte da PO aprovada vigente, com o realizado acumulado e a projeção de cada linha ao lado;
  - mostra o **catálogo de linhas (rubricas) do condomínio**, inclusive as que não estão na PO atual, para incluir, excluir ou renomear;
  - sugere o valor de cada linha com base em histórico, contratos e reajustes (índice e data-base), dissídio e as premissas da coluna "Observações" da PO atual (ex.: "média jan/abr + IPCA");
  - permite editar o valor e a premissa de cada linha e simular a cota por unidade (fração ideal) e os fundos (reserva e obras em %);
  - compara com a PO anterior (coluna "%"), como no documento aprovado;
  - exporta no formato de apresentação para a AGO e guarda versões (rascunho, apresentada, aprovada com a ata).
  - Pendente: o usuário vai enviar o catálogo completo de linhas, além das que estão na PO.

### RF-04 Assistente (RAG)
- RF-04.0 Fontes do RAG: convenção, RI, **atas de assembleia** (AGO, AGE, virtuais), POs, contratos, folha, balancetes e demais documentos. As atas são indexadas por data e por deliberação (o que foi aprovado, valor, fundo de origem, prazo), para responder "isto foi aprovado?" e para justificar achados (RF-02.9).
- RF-04.1 Perguntas em linguagem natural sobre os documentos ("quanto gastamos com elevador em 2025?", "o contrato de limpeza prevê reajuste por qual índice?").
- RF-04.2 Toda resposta cita as fontes (documento, página) e, quando houver número, ele vem de consulta ao banco (ferramenta), não de memória do modelo.
- RF-04.3 Respeitar o perfil do usuário (mesmas permissões da interface).

### RF-05 Dashboard e telas
- RF-05.1 **Tela inicial** com os últimos números: saldo atual, receitas e despesas do último mês, previsto × realizado no ano, inadimplência, fundo de reserva, quantidade de achados abertos por severidade.
- RF-05.2 Gráficos: evolução mensal de receitas/despesas, despesas por rubrica, previsto × realizado, histórico anual, projeção.
- RF-05.3 **Indicador discreto em um canto**, em todas as telas: "Último arquivo: <nome> · <categoria> · <data>".
- RF-05.4 **Tela de arquivos**: separada por categorias (abas ou filtro), **ordenada da data mais recente para a mais antiga**, com busca, status e download do original.
- RF-05.5 Tela de achados de auditoria com filtros e detalhe da evidência (abre o documento na página certa).
- RF-05.6 Tela de previsão orçamentária.

### RF-06 Relatórios (sob demanda)
- RF-06.1 Exportar PDF e Excel: relatório mensal de auditoria, previsto × realizado, relatório anual para assembleia/conselho, lista de achados.
- RF-06.2 Usuário escolhe período, categorias e seções.

### RF-07 Administração
- RF-07.1 Usuários e perfis.
- RF-07.2 Cadastro de unidades e frações ideais; plano de contas/rubricas e mapeamento entre os nomes usados no balancete e na PO.
- RF-07.3 Parâmetros de regras (tolerâncias, tetos de multa, índices) com vigência.
- RF-07.4 Trilha de auditoria.

### RF-09 Provedor de IA parametrizável por condomínio (decisão do usuário, 03/10/2026)
- RF-09.1 Cada condomínio tem um parâmetro **modo de IA**, que o Admin troca sem mexer no código:
  - `MCP_EXTERNO` (padrão do piloto): o sistema não chama nenhuma IA. A IA é o Claude do próprio usuário (Claude Desktop ou Claude Code, pela assinatura) conectado ao servidor MCP do sistema.
  - `API_KEY`: o sistema chama o Claude com a **chave de API do próprio condomínio** (BYOK). Isso habilita o chat na tela e a classificação automática.
  - `DESLIGADO`: nenhuma IA. Só regras e cálculos.
- RF-09.2 A chave é do condomínio (licença ou chave de cada cliente), guardada **criptografada**, nunca exibida depois de salva e nunca registrada em log. Cada condomínio paga a própria IA.
- RF-09.3 Todo uso de IA passa pela camada única (`ai-gateway`), que lê o modo do condomínio, registra tokens e custo por operação e aplica o mascaramento LGPD na fase de nuvem.
- RF-09.4 As funções do sistema são as mesmas nos três modos. O modo só muda **quem** executa a parte de IA: o Claude externo via MCP ou o sistema via API.
- RF-09.5 Previsto para depois: provedor e modelo também configuráveis (ex.: Haiku para classificar, Sonnet para ler), com limite de gasto mensal por condomínio.

### RF-08 Integração via MCP
- RF-08.1 Servidor MCP expondo ferramentas do sistema (consultar lançamentos, achados, POs, contratos, rodar conciliação, gerar relatório) para uso por agentes de IA (Claude Code, Claude Desktop), com as mesmas permissões dos perfis.

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
5. Previsto × realizado contra a PO do ano (RF-03.1).
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
| T* | Decisões de tecnologia (ver 03-tecnologias.md) | Usuário |

---

## Fontes consultadas

- Código Civil, arts. 1.331–1.358 (capítulo de condomínio): [SíndicoNet — íntegra](https://www.sindiconet.com.br/informese/integra-administracao-legislacao-codigo-civil-capitulo-sobre-condominios/amp); art. 1.336: [Petições Online](https://www.peticoesonline.com.br/art-1336-cc). O site oficial planalto.gov.br estava inacessível deste ambiente; recomenda-se conferir lá a redação vigente.
- Boas práticas: [SíndicoNet — Guia de prestação de contas](https://www.sindiconet.com.br/informese/guia-prestacao-contas-condominio-boas-praticas-colunistas-artigos-e-opinioes), [Jornal dos Condomínios](https://condominiosc.com.br/jornal-dos-condominios/gestao/3214-hora-de-preparar-a-prestacao-de-contas), [Como analisar um balancete](https://condominiosc.com.br/canal-aberto/3945-gostaria-de-saber-como-analisar-um-balancete-da-prestacao-de-contas-do-mes).
- PL 4.072/2019: [Paraíba Business](https://paraibabusiness.com.br/nova-lei-que-exige-balancete-mensal-de-condominios-e-populista/).
- Lei 14.905/2024 (taxa legal do art. 406) e Lei 14.309/2022 (assembleia virtual, art. 1.354-A): conhecimento prévio, a confirmar no texto oficial.
- Prazos de guarda de documentos: orientação geral, **confirmar com o contador**.
