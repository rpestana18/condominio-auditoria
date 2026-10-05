import { config } from "../config";
import { tokenValido } from "../autenticacao/keycloak";
import type { Problema } from "./tipos";

/** Erro da API com a mensagem em português que o backend mandou (formato RFC 9457). */
export class ErroApi extends Error {
  constructor(
    readonly status: number,
    readonly problema: Problema | null,
  ) {
    super(problema?.detail ?? problema?.title ?? mensagemPadrao(status));
  }

  /** Código do módulo quando a recusa é "Módulo não contratado" (403 com o campo `modulo`). */
  get moduloNaoContratado(): string | undefined {
    return this.status === 403 ? (this.problema?.modulo ?? undefined) : undefined;
  }
}

function mensagemPadrao(status: number): string {
  if (status === 403) return "Seu perfil não tem permissão para esta ação.";
  return `Erro ${status} ao falar com o servidor`;
}

async function chamar(caminho: string, opcoes: RequestInit = {}): Promise<Response> {
  const token = await tokenValido();
  const resposta = await fetch(config.api + caminho, {
    ...opcoes,
    headers: { ...opcoes.headers, Authorization: `Bearer ${token}` },
  });
  if (!resposta.ok) {
    const problema = resposta.headers.get("content-type")?.includes("json") ? await resposta.json() : null;
    throw new ErroApi(resposta.status, problema);
  }
  return resposta;
}

/** GET que devolve JSON; null quando o servidor responde 204 (ex.: ainda não há dados). */
export async function obter<T>(caminho: string): Promise<T | null> {
  const resposta = await chamar(caminho);
  return resposta.status === 204 ? null : ((await resposta.json()) as T);
}

export async function enviar<T>(caminho: string, corpo?: FormData): Promise<T> {
  const resposta = await chamar(caminho, { method: "POST", body: corpo });
  return (await resposta.json()) as T;
}

/** PUT com corpo JSON e sem resposta (204). */
export async function gravar(caminho: string, corpo: unknown): Promise<void> {
  await chamar(caminho, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(corpo) });
}

/** PUT com corpo JSON. */
export async function atualizar<T>(caminho: string, corpo: unknown): Promise<T> {
  const resposta = await chamar(caminho, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
  return (await resposta.json()) as T;
}

/** Baixa um arquivo autenticado e abre numa nova aba (o navegador não manda o token sozinho num link). */
export async function abrirArquivo(caminho: string): Promise<void> {
  const resposta = await chamar(caminho);
  const url = URL.createObjectURL(await resposta.blob());
  window.open(url, "_blank", "noopener");
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/**
 * Baixa um arquivo autenticado e salva com o nome que o servidor mandou no Content-Disposition.
 * Não assumimos o tipo (CSV hoje, .xlsx depois): o blob vai como veio.
 */
export async function baixarArquivo(caminho: string, nomePadrao: string): Promise<void> {
  const resposta = await chamar(caminho);
  const nome = nomeDoArquivo(resposta.headers.get("content-disposition")) ?? nomePadrao;
  const url = URL.createObjectURL(await resposta.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = nome;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** Lê o nome de `attachment; filename*=UTF-8''uso%20out.xlsx` ou `filename="uso.csv"`. */
function nomeDoArquivo(cabecalho: string | null): string | null {
  if (!cabecalho) return null;
  const codificado = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/.exec(cabecalho);
  if (codificado) {
    try {
      return decodeURIComponent(codificado[1].trim().replace(/^"|"$/g, ""));
    } catch {
      // nome mal codificado: tenta o filename simples abaixo
    }
  }
  const simples = /filename\s*=\s*"?([^";]+)"?/.exec(cabecalho);
  return simples ? simples[1].trim() : null;
}
