import { useNavigate } from "react-router";
import type { ExercicioComparado } from "../../api/tipos";

/** Onde abrir um número no previsto × realizado: exercício (PO), período, fundo e, quando houver, o alvo da evidência. */
export interface DestinoPrevisto {
  poId: string;
  /** "cumulative" ou AAAA-MM, como pede a API. */
  periodo: string;
  /** "line:<id>", "group:<id>", "fund:<id>", "total"... Sem alvo, a tela abre no período sem o painel de evidência. */
  alvo?: string | null;
  fundoId?: string | null;
}

/**
 * Endereço do previsto × realizado com os filtros no formato que a tela lê (?po=&periodo=&fundo=&alvo=).
 * Usado por "Comparar exercícios" e por "Indicadores", para os dois abrirem a evidência do mesmo jeito.
 */
export function enderecoPrevisto({ poId, periodo, alvo, fundoId }: DestinoPrevisto): string {
  const parametros = new URLSearchParams({ po: poId, periodo });
  if (fundoId) parametros.set("fundo", fundoId);
  if (alvo) parametros.set("alvo", alvo);
  return `/previsto-realizado?${parametros.toString()}`;
}

/**
 * Dado o exercício e o alvo de um valor, devolve a ação que abre esse número no previsto × realizado,
 * já com a evidência aberta (RF-11.6, clique). Sem alvo, ou na coluna impressa (que não tem período nem
 * realizado), devolve `undefined` e o valor aparece como texto simples.
 */
export type AbridorEvidencia = (exercicioId: string, alvo: string | null | undefined) => (() => void) | undefined;

export function useAbridorEvidencia(exercicios: ExercicioComparado[], fundoId: string | null): AbridorEvidencia {
  const navegar = useNavigate();
  return (exercicioId, alvo) => {
    const exercicio = exercicios.find((e) => e.id === exercicioId);
    const periodo = exercicio?.period;
    if (!exercicio || !periodo || !alvo) return undefined;
    return () => navegar(enderecoPrevisto({ poId: exercicio.budgetId, periodo, alvo, fundoId }));
  };
}
