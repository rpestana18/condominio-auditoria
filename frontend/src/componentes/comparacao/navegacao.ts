import { useNavigate } from "react-router";
import type { ExercicioComparado } from "../../api/tipos";

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
    if (!exercicio?.periodo || !alvo) return undefined;
    const parametros = new URLSearchParams({ po: exercicio.poId, periodo: exercicio.periodo, alvo });
    if (fundoId) parametros.set("fundo", fundoId);
    return () => navegar(`/previsto-realizado?${parametros.toString()}`);
  };
}
