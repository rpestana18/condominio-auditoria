-- Módulos contratáveis por condomínio e registro de uso (RF-10, RF-09.7; ADR 0003, Decisão 4).
-- O catálogo de módulos (código, nome, o que inclui, padrão) fica no arquivo catalogo-modulos.yml do backend, não
-- no banco: um módulo novo é uma entrada no catálogo. Por isso o código do módulo não tem restrição de valores aqui.

-- Estado atual de cada módulo no condomínio. Sem linha = vale o padrão do catálogo (ASSISTENTE: desligado, Q15).
create table modulo_condominio (
    condominio_id  uuid not null references condominio (id),
    modulo         varchar(40) not null,
    ligado         boolean not null,
    desde          timestamptz not null,
    alterado_por   varchar(120) not null,
    primary key (condominio_id, modulo)
);

-- Trilha de ativação (RF-10.6): cada ligar ou desligar, com quem, quando e motivo (opcional). Só cresce.
create table evento_modulo (
    id              uuid primary key,
    condominio_id   uuid not null references condominio (id),
    modulo          varchar(40) not null,
    ligado_antes    boolean not null,
    ligado_depois   boolean not null,
    usuario         varchar(120) not null,
    quando          timestamptz not null,
    motivo          varchar(500),
    constraint ck_evento_modulo_mudanca check (ligado_antes <> ligado_depois),
    -- Sem motivo = nulo (nunca texto em branco)
    constraint ck_evento_modulo_motivo check (motivo is null or length(btrim(motivo)) > 0)
);
create index ix_evento_modulo on evento_modulo (condominio_id, modulo, quando);

-- Registro de uso (RF-09.7): uma linha por operação. Nunca guarda texto de documento, pergunta ou chave de API.
-- usuario nulo = processamento em segundo plano (ex.: indexação). Tokens nulos = sem modelo de respostas.
-- O custo não é gravado: sai no relatório do período (tokens × preço do catálogo), a partir da entrega 3.
create table uso_modulo (
    id               uuid primary key,
    condominio_id    uuid not null references condominio (id),
    modulo           varchar(40) not null,
    funcao           varchar(30) not null,
    usuario          varchar(120),
    quando           timestamptz not null,
    modo             varchar(20),
    provedor         varchar(60),
    modelo           varchar(120),
    tokens_entrada   bigint,
    tokens_saida     bigint,
    arquivos         integer,
    paginas          integer,
    versao_prompt    varchar(40),
    constraint ck_uso_modulo_funcao
        check (funcao in ('busca_documentos', 'chamada_mcp', 'indexacao', 'embeddings', 'pergunta')),
    constraint ck_uso_modulo_modo check (modo in ('API_KEY', 'MCP_EXTERNO', 'LOCAL', 'DESLIGADO')),
    constraint ck_uso_modulo_contagens check ((tokens_entrada is null or tokens_entrada >= 0)
        and (tokens_saida is null or tokens_saida >= 0)
        and (arquivos is null or arquivos >= 0) and (paginas is null or paginas >= 0))
);
create index ix_uso_modulo_periodo on uso_modulo (condominio_id, quando);

-- Trilha e uso são só de inclusão: o banco recusa update, delete e truncate (RF-10.6).
create function recusar_alteracao_so_inclusao() returns trigger
    language plpgsql as
$$
begin
    raise exception 'A tabela % só aceita inclusão: % recusado', tg_table_name, tg_op
        using errcode = 'restrict_violation';
end;
$$;

create trigger tg_evento_modulo_so_inclusao before update or delete on evento_modulo
    for each row execute function recusar_alteracao_so_inclusao();
create trigger tg_evento_modulo_sem_truncate before truncate on evento_modulo
    for each statement execute function recusar_alteracao_so_inclusao();
create trigger tg_uso_modulo_so_inclusao before update or delete on uso_modulo
    for each row execute function recusar_alteracao_so_inclusao();
create trigger tg_uso_modulo_sem_truncate before truncate on uso_modulo
    for each statement execute function recusar_alteracao_so_inclusao();

-- O piloto começa com o Assistente ligado (Q15), com o evento na trilha.
insert into modulo_condominio (condominio_id, modulo, ligado, desde, alterado_por)
select id, 'ASSISTENTE', true, now(), 'sistema (migração V10)'
from condominio where id = '6f1d2c1e-3b4a-4c8e-9a51-2815a0000001';

insert into evento_modulo (id, condominio_id, modulo, ligado_antes, ligado_depois, usuario, quando, motivo)
select gen_random_uuid(), id, 'ASSISTENTE', false, true, 'sistema (migração V10)', now(), 'Implantação do piloto'
from condominio where id = '6f1d2c1e-3b4a-4c8e-9a51-2815a0000001';
