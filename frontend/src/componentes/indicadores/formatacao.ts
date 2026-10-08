// Textos dos gráficos de indicadores. Só formatação: todo número já vem pronto do GET /indicadores.
import type { SituacaoMesIndicador } from "../../api/tipos";
import { formatarMesCurto, formatarMoeda, formatarPercentual } from "../../formato";

/** O que aparece no lugar do número quando o mês não tem números (RF-11.11: nunca zero). */
export const textoSituacao: Record<SituacaoMesIndicador, string> = {
  COM_FLUXO: "",
  SEM_FLUXO: "sem fluxo carregado",
  DOIS_FLUXOS: "dois fluxos no mês",
};

/**
 * Valor de um mês para a tabela e a dica do gráfico. Fora de COM_FLUXO, a situação do mês ("sem fluxo carregado");
 * com fluxo mas sem valor (ex.: previsto zero, sem percentual), "—".
 */
export function textoDoMes(
  valor: number | null | undefined,
  situacao: SituacaoMesIndicador,
  formatar: (valor: number) => string,
): string {
  if (situacao !== "COM_FLUXO") return textoSituacao[situacao];
  return valor === null || valor === undefined ? "—" : formatar(valor);
}

export const moedaDoMes = (valor: number | null | undefined, situacao: SituacaoMesIndicador) =>
  textoDoMes(valor, situacao, formatarMoeda);

export const percentualDoMes = (valor: number | null | undefined, situacao: SituacaoMesIndicador) =>
  textoDoMes(valor, situacao, formatarPercentual);

/**
 * Rótulo do eixo X: "set/26" e, abaixo, as notas do mês (uma por linha). Mês sem fluxo ganha
 * "sem fluxo / carregado" no eixo, já que não tem barra nem ponto.
 */
export function rotuloEixoMes(mes: string, situacao: SituacaoMesIndicador, ...outrasNotas: (string | null | undefined)[]): string {
  const notas = situacao === "SEM_FLUXO" ? ["sem fluxo", "carregado"] : situacao === "DOIS_FLUXOS" ? ["dois fluxos"] : [];
  return [formatarMesCurto(mes), ...notas, ...outrasNotas.filter(Boolean)].join("\n");
}

/** Eixo em %: 100 vira "100%". Só formata o número que o Recharts escolheu para a marca do eixo. */
export const formatarEixoPercentual = (valor: number) => `${valor.toLocaleString("pt-BR")}%`;
