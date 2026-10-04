-- Fundo ordinário de cada condomínio (RF-05.1b): o nome muda de um condomínio para outro, então o Gestor confirma qual é.
alter table condominio add column fundo_ordinario_id uuid references fundo (id);
alter table condominio add column fundo_ordinario_confirmado_por varchar(200);
alter table condominio add column fundo_ordinario_confirmado_em timestamptz;
