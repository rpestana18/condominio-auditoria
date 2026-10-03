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

### RF-04 Assistente (RAG) — chat sobre os documentos (pedido do usuário, 03/10/2026)

Origem: pedido do usuário de 03/10/2026 ("um chat de pergunta e resposta sobre os documentos enviados, dentro do próprio frontend, como o NotebookLM do Google"); §3.3 (conduta); §4 (permissões); RF-09 (modo de IA).

Situação atual (03/10/2026): os arquivos de todas as categorias já são enviados pela tela Arquivos, mas o `rag` só interpreta o Fluxo de Caixa e não indexa texto (não há busca). O MCP tem 5 ferramentas de consulta numérica: `listar_condominios`, `resumo_fundos`, `listar_arquivos`, `conferencias_do_arquivo` e `buscar_lancamentos`.

Fora destes requisitos (decisão do arquiteto em ADR, com aprovação do usuário): modelo de IA, modelo de embeddings, forma de busca (palavra, semântica ou híbrida), onde o índice fica guardado e como os trechos são cortados.

Termos usados abaixo:
- **Trecho**: pedaço de texto de um arquivo, guardado com condomínio, arquivo, categoria, competência, versão do arquivo, hash do arquivo original e **localização**.
- **Localização**: página (PDF); aba e linha (Excel); seção ou parágrafo (Word, que não tem página fixa). *Proposta, a confirmar pelo usuário (Q9).*
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
  - Dado um arquivo Excel ou Word, quando o envio termina, então os trechos têm a localização definida na proposta de "Localização" (aba e linha; seção ou parágrafo).
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
- RF-04.11 **Histórico da conversa na sessão**: a conversa fica visível e as perguntas seguintes podem se referir às anteriores ("e no ano anterior?"). Botão "Nova conversa" limpa o histórico. *Proposta: o histórico some ao sair do sistema; guardar conversas entre sessões é pendência (Q10).* Trocar de condomínio inicia nova conversa, para não misturar dados de condomínios.
  - Dado uma pergunta "qual o índice de reajuste do contrato de limpeza?" já respondida, quando o usuário pergunta em seguida "e o de portaria?", então a resposta trata do contrato de portaria, com citação dele.
  - Dado uma conversa em andamento, quando o usuário troca o condomínio selecionado ou sai do sistema, então a conversa anterior não aparece mais.
- RF-04.12 **"Não encontrei nos documentos"**: quando não há trecho que sustente a resposta nem dado gravado que responda, o assistente diz isso e, se possível, sugere a categoria de documento que faltaria (ex.: "não há extrato de setembro enviado"). Nunca completa com conhecimento próprio sobre o condomínio.
  - Dado uma pergunta sobre um assunto que não está em nenhum arquivo do condomínio (ex.: "qual a empresa de jardinagem?" sem contrato de jardinagem enviado), quando o usuário pergunta, então a resposta é "não encontrei nos documentos" e não traz nome, valor ou data.
  - Dado o conjunto de avaliação (RF-04.18), quando ele roda, então todos os casos marcados como "sem fonte" recebem "não encontrei", sem nenhuma citação inventada.

**Números e separação das fontes**
- RF-04.13 **Números vêm do banco por ferramenta**: totais, saldos, somas, médias, comparações e qualquer cálculo vêm de ferramenta de consulta ao banco (as 5 atuais ou novas, especificadas em requisito próprio), nunca de cálculo ou leitura do modelo (§3.3, item 2). O modelo não soma, não subtrai e não converte valores.
  - Dado a pergunta "qual o saldo do fundo de obras em setembro de 2026?", quando o assistente responde, então o valor é idêntico ao devolvido por `resumo_fundos` para o mesmo condomínio e mês (centavo a centavo, formato R$) e a resposta identifica a consulta usada.
  - Dado uma pergunta numérica que nenhuma ferramenta responde (ex.: total da folha, se a folha ainda não é interpretada), quando o assistente responde, então ele diz que o dado ainda não está gravado e não faz conta a partir do texto dos documentos.
  - *Proposta, a confirmar (Q8)*: um valor escrito num documento (ex.: "valor mensal de R$ 8.500,00" num contrato) só pode aparecer como **transcrição literal do trecho citado**, marcado "conforme o documento, não conferido", sem nenhum cálculo sobre ele.
- RF-04.14 **Resposta separa as origens**: a resposta tem dois blocos identificados: "Nos documentos" (texto com citações) e "Nos dados gravados" (números das ferramentas, com o nome da consulta, filtros usados e link para a tela correspondente: lançamentos, fundos ou conferências). Se só houver um tipo de fonte, só um bloco aparece.
  - Dado a pergunta "a troca do portão foi aprovada e quanto foi pago?", quando o assistente responde, então a aprovação aparece em "Nos documentos" com citação da ata, e o valor pago aparece em "Nos dados gravados" com o link para os lançamentos.
  - Dado qualquer número no bloco "Nos dados gravados", quando comparado ao resultado da ferramenta citada, então é igual; e dado qualquer número no bloco "Nos documentos", então ele aparece literalmente num trecho citado.

**Conduta**
- RF-04.15 **Indícios com evidência, sem acusação** (§3.3, item 1): o assistente descreve fatos e divergências com a fonte e a regra, e deixa a conclusão para o conselho e a assembleia (art. 1.349 e 1.356 do CC). Não usa linguagem acusatória nem atribui intenção ou culpa a pessoas (ex.: "desvio", "fraude", "roubo", "o síndico errou") como afirmação própria. Se o documento citado usa esses termos, eles aparecem só dentro da citação literal.
  - Dado a pergunta "o síndico desviou dinheiro do fundo de reserva?", quando o assistente responde, então a resposta lista os fatos encontrados (ex.: saídas do fundo, existência ou não de ata que autorize, achados abertos) com citações e dados gravados, e informa que a conclusão cabe ao conselho/assembleia, sem afirmar que houve ou não houve desvio.
  - Dado o conjunto de avaliação, quando ele roda, então nenhuma resposta contém os termos da lista de conduta fora de citação literal. *A lista de termos é proposta, a confirmar pelo usuário (Q11).*

**Modo de IA (RF-09) e MCP**
- RF-04.16 **Comportamento por modo de IA do condomínio**:
  - `API_KEY`: o chat aparece na tela "Assistente" (RF-04.8 a RF-04.15), usando a chave do condomínio via `ai-gateway` (RF-09.3), com registro de tokens e custo por pergunta.
  - `MCP_EXTERNO`: a tela "Assistente" não tem chat; mostra que o assistente deste condomínio é o Claude do usuário (Desktop ou Code) conectado ao MCP do sistema, com as instruções de conexão. O sistema não chama nenhuma IA nesse modo.
  - `DESLIGADO`: o chat não aparece e o sistema não chama nenhuma IA.
  - Dado um condomínio em `MCP_EXTERNO`, quando o usuário abre "Assistente", então não há campo de chat, aparece o aviso "o assistente deste condomínio é o seu Claude, conectado ao MCP" e o sistema não registra nenhuma chamada de IA.
  - Dado um condomínio em `DESLIGADO`, quando o usuário abre o menu, então não há chat; e quando ele chama a API de chat diretamente, então o pedido é recusado com a mensagem de que a IA está desligada neste condomínio.
  - Dado o Admin que troca o modo de `API_KEY` para `MCP_EXTERNO`, quando um usuário abre "Assistente" em seguida, então a tela já reflete o novo modo, sem mudança de código nem reinício.
- RF-04.17 **Ferramenta MCP de busca nos documentos** (nome sugerido: `buscar_documentos`, a definir no contrato `contracts/` pelo agente `mcp`): faz a mesma busca do chat, com os mesmos filtros (RF-04.10) e as mesmas permissões (RF-04.3), e devolve os trechos com as mesmas citações (arquivo, categoria, competência, versão, localização, hash e link para abrir o original). Não devolve texto de condomínio sem acesso. Complementa RF-08.1. Em `MCP_EXTERNO`, é com ela e com as 5 ferramentas numéricas que o Claude do usuário responde.
  - Dado o mesmo condomínio, a mesma pergunta e os mesmos filtros, quando a busca é feita pela tela e por `buscar_documentos`, então os trechos e citações devolvidos são os mesmos.
  - Dado um usuário MCP sem acesso ao condomínio B, quando ele chama `buscar_documentos` informando o condomínio B, então a chamada é recusada.
- RF-04.18 **Busca por palavra sem IA** — *proposta do agente de requisitos, a confirmar pelo usuário (Q7)*: a tela "Assistente" oferece, em **todos os modos**, uma "Busca nos documentos" por palavra ou expressão, sem IA, com os mesmos filtros e com o resultado em lista de trechos citados e clicáveis (sem resposta redigida). Em `DESLIGADO` e em `MCP_EXTERNO`, é a única função da tela além do aviso do modo. Motivo: a indexação do texto não depende de IA e a busca continua útil ao conselho.
  - Dado um condomínio em `DESLIGADO`, quando o usuário busca "portão", então aparecem os trechos que contêm a palavra, cada um com citação clicável, e nenhuma chamada de IA é registrada.
  - Se a busca por significado (semântica) exigir modelo de embeddings, o uso dele nos modos `MCP_EXTERNO` e `DESLIGADO` depende de Q12.

**Avaliação**
- RF-04.19 **Conjunto de avaliação do assistente**: um conjunto de perguntas do condomínio piloto, cada uma com a resposta esperada, as fontes esperadas (arquivo e localização) e, quando houver, o número esperado e a ferramenta que o dá. Fica junto dos documentos de referência (`data/golden/`, RNF-10) e roda a cada mudança no `leitor`, na indexação, na busca, nas ferramentas ou nas instruções do assistente. Inclui casos "sem fonte" (RF-04.12), casos de permissão (RF-04.3), casos numéricos (RF-04.13) e casos de conduta (RF-04.15). *Proposta: começar com pelo menos 20 perguntas, escritas com o usuário (P3).*
  - Dado o conjunto de avaliação, quando ele roda, então o relatório mostra, por pergunta: se as fontes esperadas foram citadas, se os números batem, se houve "não encontrei" nos casos sem fonte e se houve termo de conduta proibido.
  - Dado uma mudança que faz qualquer caso piorar em relação à execução anterior, quando a avaliação roda, então a mudança é reprovada (mesma regra dos golden files do repositório).
  - A parte de busca (fontes esperadas entre os trechos devolvidos) roda em todos os modos; a parte de resposta redigida roda só com IA disponível (`API_KEY` ou ambiente de teste equivalente definido pelo arquiteto).

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
| P3 | Escrever com o agente de requisitos as perguntas do conjunto de avaliação do assistente (RF-04.19), com respostas e fontes esperadas do piloto | Usuário |
| Q7 | A "Busca nos documentos" por palavra, sem IA, fica disponível em todos os modos, inclusive `DESLIGADO` (RF-04.18)? Sim/Não | Usuário |
| Q8 | O assistente pode transcrever um valor escrito num documento, como trecho literal marcado "não conferido", sem cálculo (RF-04.13)? Sim/Não | Usuário |
| Q9 | Aprovar a localização das citações: página (PDF), aba e linha (Excel), seção ou parágrafo (Word)? Sim/Não | Usuário |
| Q10 | Guardar o histórico das conversas entre sessões (além da sessão atual, RF-04.11)? Sim/Não | Usuário |
| Q11 | Aprovar a lista inicial de termos de conduta proibidos fora de citação ("desvio", "fraude", "roubo", "culpa")? Sim/Não | Usuário |
| Q12 | Nos modos `MCP_EXTERNO` e `DESLIGADO`, permitir modelo de embeddings **local** (sem enviar texto para fora) para a busca por significado? Sim/Não | Usuário |
| T* | Decisões de tecnologia (ver 03-tecnologias.md) | Usuário |

---

## Fontes consultadas

- Código Civil, arts. 1.331–1.358 (capítulo de condomínio): [SíndicoNet — íntegra](https://www.sindiconet.com.br/informese/integra-administracao-legislacao-codigo-civil-capitulo-sobre-condominios/amp); art. 1.336: [Petições Online](https://www.peticoesonline.com.br/art-1336-cc). O site oficial planalto.gov.br estava inacessível deste ambiente; recomenda-se conferir lá a redação vigente.
- Boas práticas: [SíndicoNet — Guia de prestação de contas](https://www.sindiconet.com.br/informese/guia-prestacao-contas-condominio-boas-praticas-colunistas-artigos-e-opinioes), [Jornal dos Condomínios](https://condominiosc.com.br/jornal-dos-condominios/gestao/3214-hora-de-preparar-a-prestacao-de-contas), [Como analisar um balancete](https://condominiosc.com.br/canal-aberto/3945-gostaria-de-saber-como-analisar-um-balancete-da-prestacao-de-contas-do-mes).
- PL 4.072/2019: [Paraíba Business](https://paraibabusiness.com.br/nova-lei-que-exige-balancete-mensal-de-condominios-e-populista/).
- Lei 14.905/2024 (taxa legal do art. 406) e Lei 14.309/2022 (assembleia virtual, art. 1.354-A): conhecimento prévio, a confirmar no texto oficial.
- Prazos de guarda de documentos: orientação geral, **confirmar com o contador**.
