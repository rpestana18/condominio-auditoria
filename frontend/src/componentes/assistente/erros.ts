import { ErroApi } from "../../api/cliente";

/**
 * Mensagem em português para um erro do assistente (RF-04.16 e erros do contrato). Usa o texto que o backend
 * mandou no Problema quando houver; senão, um texto padrão por código. Nunca derruba a conversa.
 */
export function mensagemErroAssistente(erro: unknown): string {
  if (!(erro instanceof ErroApi)) return "Não foi possível falar com o servidor. Confira a conexão e tente de novo.";
  const doBackend = erro.problema?.detail ?? erro.problema?.title;
  if (erro.moduloNaoContratado) return "O módulo Assistente não está ligado neste condomínio.";
  if (doBackend) return doBackend;
  switch (erro.status) {
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
      return erro.message;
  }
}
