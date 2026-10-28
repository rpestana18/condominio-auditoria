import { config } from "../config";
import { validToken } from "../auth/keycloak";
import type { Problem } from "./types";

/** Header of the request id (ADR 0008): the same id is in the log lines of the api, rag and reader. */
const REQUEST_ID_HEADER = "X-Request-Id";

/**
 * API error carrying the Portuguese message the backend sent (RFC 9457 format) and the request id. A server error
 * (5xx) shows the id in the message, for the user to tell support; a 4xx shows only the reason.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: Problem | null,
    readonly requestId: string | null = null,
  ) {
    super(message(status, problem, requestId));
  }

  /** Feature code when the refusal is "feature not contracted" (403 with the `feature` field). */
  get featureNotEnabled(): string | undefined {
    return this.status === 403 ? (this.problem?.feature ?? undefined) : undefined;
  }
}

function message(status: number, problem: Problem | null, requestId: string | null): string {
  const text = problem?.detail ?? problem?.title ?? defaultMessage(status);
  return status >= 500 && requestId && !text.includes(requestId) ? `${text} (código ${requestId})` : text;
}

/** New request id; crypto.randomUUID only exists on https or localhost. */
function newRequestId(): string {
  return typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

function defaultMessage(status: number): string {
  if (status === 403) return "Seu perfil não tem permissão para esta ação.";
  return `Erro ${status} ao falar com o servidor`;
}

async function call(path: string, options: RequestInit = {}): Promise<Response> {
  const token = await validToken();
  const requestId = newRequestId();
  const response = await fetch(config.api + path, {
    ...options,
    headers: { ...options.headers, Authorization: `Bearer ${token}`, [REQUEST_ID_HEADER]: requestId },
  });
  if (!response.ok) {
    const problem: Problem | null = response.headers.get("content-type")?.includes("json") ? await response.json() : null;
    throw new ApiError(response.status, problem, problem?.requestId ?? response.headers.get(REQUEST_ID_HEADER) ?? requestId);
  }
  return response;
}

/** GET returning JSON; null when the server answers 204 (e.g. no data yet). */
export async function getJson<T>(path: string): Promise<T | null> {
  const response = await call(path);
  return response.status === 204 ? null : ((await response.json()) as T);
}

export async function postForm<T>(path: string, body?: FormData): Promise<T> {
  const response = await call(path, { method: "POST", body });
  return (await response.json()) as T;
}

/** POST with a JSON body (e.g. budget confirmation, account mapping batch actions). */
export async function postJson<T>(path: string, body?: unknown): Promise<T> {
  const response = await call(path, {
    method: "POST",
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return (await response.json()) as T;
}

/** DELETE returning JSON (e.g. undoing a reallocation returns the undone record). */
export async function deleteJson<T>(path: string): Promise<T> {
  const response = await call(path, { method: "DELETE" });
  return (await response.json()) as T;
}

/** PUT with a JSON body and no response (204). */
export async function put(path: string, body: unknown): Promise<void> {
  await call(path, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
}

/** PUT with a JSON body. */
export async function putJson<T>(path: string, body: unknown): Promise<T> {
  const response = await call(path, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return (await response.json()) as T;
}

/**
 * Downloads an authenticated file and opens it in a new tab (the browser does not send the token by itself on a link).
 * With `page`, the browser's PDF viewer opens directly on it (#page=N), to show the evidence.
 */
export async function openFile(path: string, page?: number): Promise<void> {
  const response = await call(path);
  const url = URL.createObjectURL(await response.blob());
  window.open(page ? `${url}#page=${page}` : url, "_blank", "noopener");
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/**
 * Opens the original in a new tab, optionally on a page (PDF: `#page=N`, RF-04.9).
 * The tab is opened before downloading, still inside the click, so the browser does not block it as a pop-up;
 * then it receives the file (local blob, already authenticated). `type` forces the content type (e.g. "application/pdf"),
 * because the server sends the original as application/octet-stream and the browser would download instead of showing it.
 */
export async function openOriginal(path: string, options: { page?: number | null; type?: string } = {}): Promise<void> {
  const tab = window.open("", "_blank");
  try {
    const response = await call(path);
    const blob = await response.blob();
    const url = URL.createObjectURL(options.type ? new Blob([blob], { type: options.type }) : blob);
    const address = options.page ? `${url}#page=${options.page}` : url;
    if (tab) {
      tab.opener = null;
      tab.location.href = address;
    } else {
      window.open(address, "_blank", "noopener");
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (error) {
    tab?.close();
    throw error;
  }
}

/**
 * Downloads an authenticated file and saves it with the name the server sent in Content-Disposition.
 * We do not assume the type (PDF, Excel, CSV): the blob goes as it came.
 */
export async function downloadFile(path: string, defaultName: string): Promise<void> {
  const response = await call(path);
  const name = fileNameFromHeader(response.headers.get("content-disposition")) ?? defaultName;
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** Reads the name from `attachment; filename*=UTF-8''uso%20out.xlsx` or `filename="uso.csv"`. */
function fileNameFromHeader(header: string | null): string | null {
  if (!header) return null;
  const encoded = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/.exec(header);
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1].trim().replace(/^"|"$/g, ""));
    } catch {
      // badly encoded name: try the plain filename below
    }
  }
  const plain = /filename\s*=\s*"?([^";]+)"?/.exec(header);
  return plain ? plain[1].trim() : null;
}
