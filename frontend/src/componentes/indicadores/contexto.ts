import { useNavigate } from "react-router";
import { enderecoPrevisto, type DestinoPrevisto } from "../comparacao/navegacao";

/** Ação que abre um número no previsto × realizado, com a evidência (RF-11.12). */
export type AbrirNoPrevisto = (destino: DestinoPrevisto) => () => void;

/** Mesmo endereço usado por "Comparar exercícios" (?po=&periodo=&fundo=&alvo=). */
export function useAbrirNoPrevisto(): AbrirNoPrevisto {
  const navegar = useNavigate();
  return (destino) => () => navegar(enderecoPrevisto(destino));
}

/** O que todo gráfico precisa além da própria série: de onde vieram os números e como abrir a evidência. */
export interface ContextoGrafico {
  /** PO do exercício mostrado (vem na resposta, mesmo quando o filtro está vazio). */
  poId: string;
  /** Filtro de fundo da tela; vai junto para o previsto × realizado. */
  fundoId: string | null;
  /** Ex.: "09/2026 a 08/2027 (2026/2027)". */
  periodo: string;
  /** Envio mais recente dos fluxos usados (ISO); nulo sem fluxo carregado. */
  dadosDe: string | null | undefined;
  abrir: AbrirNoPrevisto;
}

/** Ponto de uma série mensal com o alvo da evidência que vem da API. */
interface PontoComAlvo {
  mes: string;
  situacao: string;
  alvo?: string | null;
}

/**
 * Ação de um ponto mensal: abre o mês no previsto × realizado com o alvo do ponto. Mês sem números
 * (sem fluxo ou dois fluxos) ou sem alvo não tem o que abrir: devolve `undefined`.
 */
export function abrirPonto(contexto: ContextoGrafico, ponto: PontoComAlvo, fundoId = contexto.fundoId): (() => void) | undefined {
  if (ponto.situacao !== "COM_FLUXO" || !ponto.alvo) return undefined;
  return contexto.abrir({ poId: contexto.poId, periodo: ponto.mes, alvo: ponto.alvo, fundoId });
}
