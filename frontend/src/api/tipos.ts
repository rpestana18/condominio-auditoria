// Tipos gerados a partir de contracts/openapi.yaml (pnpm gerar-api). Não edite esquema.ts à mão.
import type { components } from "./esquema";

type Esquemas = components["schemas"];

export type Categoria = Esquemas["Categoria"];
export type StatusArquivo = Esquemas["StatusArquivo"];
export type CategoriaDto = Esquemas["CategoriaDto"];
export type NovaCategoria = Esquemas["NovaCategoria"];
export type UsuarioLogado = Esquemas["UsuarioLogado"];
export type ArquivoResumo = Esquemas["ArquivoResumo"];
export type ArquivoDetalhe = Esquemas["ArquivoDetalhe"];
export type Painel = Esquemas["Painel"];
export type Problema = Esquemas["Problema"];
export type Perfil = UsuarioLogado["perfis"][number];

// Indexação para a busca (RF-04.7) e módulos contratáveis (RF-10)
export type IndexacaoArquivo = Esquemas["IndexacaoArquivo"];
export type SituacaoIndexacao = Esquemas["SituacaoIndexacao"];
export type ContextoCondominio = Esquemas["ContextoCondominio"];
export type ModuloDoCondominio = Esquemas["ModuloDoCondominio"];
export type AlteracaoModulo = Esquemas["AlteracaoModulo"];
export type EventoModulo = Esquemas["EventoModulo"];
export type PeriodoAtivo = Esquemas["PeriodoAtivo"];
export type FuncaoUso = Esquemas["FuncaoUso"];
export type TotalUso = Esquemas["TotalUso"];
export type UsoDoPeriodo = Esquemas["UsoDoPeriodo"];
