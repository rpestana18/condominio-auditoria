import { ApiError } from "../../api/client";

/**
 * Portuguese message for an assistant error (RF-04.16 and the contract errors). Uses the text the backend
 * sent in the Problem when there is one; otherwise, a default text per status code. Never breaks the conversation.
 */
export function assistantErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return "Não foi possível falar com o servidor. Confira a conexão e tente de novo.";
  const fromBackend = error.problem?.detail ?? error.problem?.title;
  if (error.featureNotEnabled) return "O módulo Assistente não está ligado neste condomínio.";
  if (fromBackend) return fromBackend;
  switch (error.status) {
    case 400:
      return "A pergunta ou os filtros não foram aceitos. Confira o texto (até 2000 caracteres) e as datas.";
    case 409:
      return "O chat não está disponível no modo de IA atual deste condomínio.";
    case 422:
      return "A chave de IA do condomínio foi recusada pelo provedor. Peça ao administrador para conferir a chave.";
    case 429:
      return "O limite de uso do provedor de IA foi atingido. Tente de novo em alguns minutos.";
    case 503:
      return "O serviço de IA está fora do ar no momento. Tente de novo mais tarde.";
    case 504:
      return "A resposta demorou mais que o prazo. Tente uma pergunta mais específica ou use filtros.";
    default:
      return error.message;
  }
}
