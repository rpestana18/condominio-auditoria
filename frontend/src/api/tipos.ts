// Tipos gerados a partir de contracts/openapi.yaml (pnpm gerar-api). Não edite esquema.ts à mão.
import type { components } from "./esquema";

type Esquemas = components["schemas"];

export type Categoria = Esquemas["Categoria"];
export type StatusArquivo = Esquemas["StatusArquivo"];
export type CategoriaDto = Esquemas["CategoriaDto"];
export type UsuarioLogado = Esquemas["UsuarioLogado"];
export type ArquivoResumo = Esquemas["ArquivoResumo"];
export type ArquivoDetalhe = Esquemas["ArquivoDetalhe"];
export type Painel = Esquemas["Painel"];
export type Problema = Esquemas["Problema"];
export type Perfil = UsuarioLogado["perfis"][number];
