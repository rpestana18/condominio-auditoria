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

  /** Código do módulo quando a recusa é "Módulo não contratado" (403 com o campo `feature`). */
  get moduloNaoContratado(): string | undefined {
    return this.status === 403 ? (this.problema?.feature ?? undefined) : undefined;
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

/** POST com corpo JSON (ex.: confirmação da PO, ações em lote do de-para). */
export async function enviarJson<T>(caminho: string, corpo?: unknown): Promise<T> {
  const resposta = await chamar(caminho, {
    method: "POST",
    headers: corpo === undefined ? undefined : { "Content-Type": "application/json" },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  });
  return (await resposta.json()) as T;
}

/** DELETE que devolve JSON (ex.: desfazer a realocação devolve o registro desfeito). */
export async function excluir<T>(caminho: string): Promise<T> {
  const resposta = await chamar(caminho, { method: "DELETE" });
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

/**
 * Baixa um arquivo autenticado e abre numa nova aba (o navegador não manda o token sozinho num link).
 * Com `pagina`, o leitor de PDF do navegador abre direto nela (#page=N), para mostrar a evidência.
 */
export async function abrirArquivo(caminho: string, pagina?: number): Promise<void> {
  const resposta = await chamar(caminho);
  const url = URL.createObjectURL(await resposta.blob());
  window.open(pagina ? `${url}#page=${pagina}` : url, "_blank", "noopener");
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/**
 * Abre o original numa nova aba, opcionalmente numa página (PDF: `#page=N`, RF-04.9).
 * A aba é aberta antes de baixar, ainda dentro do clique, para o navegador não bloquear como pop-up;
 * depois recebe o arquivo (blob local, já autenticado). `tipo` força o tipo do conteúdo (ex.: "application/pdf"),
 * porque o servidor manda o original como application/octet-stream e o navegador baixaria em vez de mostrar.
 */
export async function abrirOriginal(caminho: string, opcoes: { pagina?: number | null; tipo?: string } = {}): Promise<void> {
  const janela = window.open("", "_blank");
  try {
    const resposta = await chamar(caminho);
    const blob = await resposta.blob();
    const url = URL.createObjectURL(opcoes.tipo ? new Blob([blob], { type: opcoes.tipo }) : blob);
    const endereco = opcoes.pagina ? `${url}#page=${opcoes.pagina}` : url;
    if (janela) {
      janela.opener = null;
      janela.location.href = endereco;
    } else {
      window.open(endereco, "_blank", "noopener");
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (erro) {
    janela?.close();
    throw erro;
  }
}

/**
 * Baixa um arquivo autenticado e salva com o nome que o servidor mandou no Content-Disposition.
 * Não assumimos o tipo (PDF, Excel, CSV): o blob vai como veio.
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
