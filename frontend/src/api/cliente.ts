import { config } from "../config";
import { tokenValido } from "../autenticacao/keycloak";
import type { Problema } from "./tipos";

/** Erro da API com a mensagem em português que o backend mandou (formato RFC 9457). */
export class ErroApi extends Error {
  constructor(
    readonly status: number,
    readonly problema: Problema | null,
  ) {
    super(problema?.detail ?? `Erro ${status} ao falar com o servidor`);
  }
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

/** Baixa um arquivo autenticado e abre numa nova aba (o navegador não manda o token sozinho num link). */
export async function abrirArquivo(caminho: string): Promise<void> {
  const resposta = await chamar(caminho);
  const url = URL.createObjectURL(await resposta.blob());
  window.open(url, "_blank", "noopener");
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
