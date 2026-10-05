-- Configuração de IA por condomínio (RF-09.1, RF-09.2, RF-09.6; ADR 0003, Decisão 4 e Sub-decisão 4.1 A).
-- (A V12 fica reservada para a realocação do PR #18.)
--
-- Uma linha por condomínio, módulo e função:
--   módulo nulo + RESPOSTAS          = modo geral do condomínio (só o modo; sem provedor nem chave nesta fase);
--   ASSISTENTE + RESPOSTAS           = chat do Assistente: modo nulo = herda o modo geral; provedor, modelo e chave;
--   ASSISTENTE + EMBEDDINGS          = busca por significado e indexação: só LOCAL ou DESLIGADO nesta fase (Q12).
-- Nenhuma linha inicial: sem linha = modo geral MCP_EXTERNO (padrão do piloto), respostas do Assistente herdando o
-- geral e embeddings LOCAL com ollama-local/bge-m3.
--
-- A chave de API do condomínio fica só cifrada com a chave pública do rag (envelope de
-- contracts/grpc/assistente/v1, ConfiguracaoPergunta.chave_cifrada): o backend guarda, mas não consegue ler.
-- chave_final = os 4 últimos caracteres, para a tela mostrar qual chave está cadastrada.
create table configuracao_ia (
    id              uuid primary key,
    condominio_id   uuid not null references condominio (id),
    modulo          varchar(40),
    funcao          varchar(20) not null,
    modo            varchar(20),
    provedor        varchar(60),
    modelo          varchar(120),
    chave_cifrada   bytea,
    chave_final     varchar(4),
    atualizado_por  varchar(120) not null,
    atualizado_em   timestamptz not null,
    constraint uk_configuracao_ia unique nulls not distinct (condominio_id, modulo, funcao),
    constraint ck_configuracao_ia_funcao check (funcao in ('RESPOSTAS', 'EMBEDDINGS')),
    constraint ck_configuracao_ia_modo check (modo in ('API_KEY', 'MCP_EXTERNO', 'LOCAL', 'DESLIGADO')),
    -- O modo geral é só de respostas e nunca herda
    constraint ck_configuracao_ia_geral check (modulo is not null or (funcao = 'RESPOSTAS' and modo is not null
        and provedor is null and modelo is null and chave_cifrada is null)),
    -- Embeddings sempre com modo próprio (sem herança)
    constraint ck_configuracao_ia_embeddings check (funcao <> 'EMBEDDINGS' or modo is not null),
    -- Chave e final andam juntas; só respostas têm chave
    constraint ck_configuracao_ia_chave check ((chave_cifrada is null) = (chave_final is null)
        and (chave_cifrada is null or funcao = 'RESPOSTAS'))
);

-- Trilha da configuração de IA (RF-09.6, último critério; RF-07.4): uma linha por função alterada, com quem,
-- quando, valores anteriores e novos. Nunca a chave: só se ela foi trocada (ou removida) e os 4 últimos caracteres
-- da nova. Modo nulo = herda o modo geral (respostas do Assistente) ou "sem configuração gravada" (anterior).
-- chave_trocada = true com chave_final nulo = chave removida.
create table evento_configuracao_ia (
    id                  uuid primary key,
    condominio_id       uuid not null references condominio (id),
    modulo              varchar(40),
    funcao              varchar(20) not null,
    usuario             varchar(120) not null,
    quando              timestamptz not null,
    modo_anterior       varchar(20),
    modo_novo           varchar(20),
    provedor_anterior   varchar(60),
    provedor_novo       varchar(60),
    modelo_anterior     varchar(120),
    modelo_novo         varchar(120),
    chave_trocada       boolean not null,
    chave_final         varchar(4),
    constraint ck_evento_configuracao_ia_funcao check (funcao in ('RESPOSTAS', 'EMBEDDINGS'))
);
create index ix_evento_configuracao_ia on evento_configuracao_ia (condominio_id, quando);

-- Só de inserção, com a mesma função da trilha da previsão orçamentária (V7)
create trigger tg_evento_configuracao_ia_so_insercao before update or delete on evento_configuracao_ia
    for each row execute function recusar_alteracao_trilha();
create trigger tg_evento_configuracao_ia_sem_truncate before truncate on evento_configuracao_ia
    for each statement execute function recusar_alteracao_trilha();
