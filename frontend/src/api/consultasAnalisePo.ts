// Consultas e ações da "Análise da PO" (ADR 0005): exercícios, comparação entre exercícios,
// conferência da coluna impressa e rubricas. Todo número vem pronto do backend; aqui só se busca e se envia.
import { useMutation, useQuery } from "@tanstack/react-query";
import { atualizar, enviarJson, obter } from "./cliente";
import { base, consulta, useRecarregarOrcamento } from "./consultasOrcamento";
import type {
  ComparacaoExercicios,
  ConferenciaColuna,
  EventoRubrica,
  Exercicio,
  FiltroRubrica,
  Indicadores,
  LinhaComRubrica,
  PedidoLoteRubrica,
  PedidoRubricaLinha,
  ResultadoLoteRubrica,
  ResultadoSugestoesRubrica,
  Rubrica,
  RubricasDaPo,
} from "./tipos";

// ---------- Exercícios (RF-11.4 e RF-11.5) ----------

/** Exercícios do mais recente para o mais antigo: um por PO confirmada e, quando couber, a coluna impressa. */
export function useExercicios(condominioId: string) {
  return useQuery({
    queryKey: ["exercicios", condominioId],
    queryFn: async () => (await obter<Exercicio[]>(`${base(condominioId)}/fiscal-years`)) ?? [],
  });
}

// ---------- Comparar exercícios (RF-11.6) ----------

export interface FiltroComparacao {
  /** Ids da lista de exercícios ("budget:<uuid>" ou "column:<uuid>"). Nulo = o backend escolhe os dois mais recentes. */
  exercicios: string[] | null;
  fundoId: string | null;
  mesmosMeses: boolean;
}

export function useComparacaoExercicios(condominioId: string, filtro: FiltroComparacao) {
  const { exercicios, fundoId, mesmosMeses } = filtro;
  return useQuery({
    queryKey: ["comparacao-exercicios", condominioId, exercicios?.join(",") ?? "padrao", fundoId ?? "todos", mesmosMeses],
    queryFn: () =>
      obter<ComparacaoExercicios>(
        `${base(condominioId)}/fiscal-year-comparison${consulta({
          // O contrato pede a lista separada por vírgula (style: form, explode: false)
          fiscalYears: exercicios?.join(","),
          fund: fundoId,
          sameMonths: mesmosMeses ? "true" : null,
        })}`,
      ),
  });
}

/** Conferência da coluna "Orçado anterior" impressa na PO `poId` (RF-11.5). Só busca quando `poId` existe. */
export function useColunaImpressa(condominioId: string, poId: string | null) {
  return useQuery({
    queryKey: ["coluna-impressa", condominioId, poId],
    queryFn: () => obter<ConferenciaColuna>(`${base(condominioId)}/budgets/${poId}/printed-column`),
    enabled: !!poId,
  });
}

// ---------- Indicadores (RF-11.10 a RF-11.13) ----------

/**
 * Séries dos 7 gráficos do exercício da PO `poId` (vazio = o mais recente). Com o fundo Condomínio, a série de
 * fundos vem nula; com outro fundo, as séries 1 a 5 vêm nulas. Nada é calculado aqui.
 */
export function useIndicadores(condominioId: string, poId: string | null, fundoId: string | null) {
  return useQuery({
    queryKey: ["indicadores", condominioId, poId ?? "vigente", fundoId ?? "todos"],
    queryFn: () => obter<Indicadores>(`${base(condominioId)}/indicators${consulta({ budget: poId, fund: fundoId })}`),
  });
}

// ---------- Rubricas (RF-11.7) ----------

/** Catálogo de rubricas do condomínio, em ordem de nome. */
export function useRubricas(condominioId: string, ativo = true) {
  return useQuery({
    queryKey: ["rubricas", condominioId],
    queryFn: async () => (await obter<Rubrica[]>(`${base(condominioId)}/budget-items`)) ?? [],
    enabled: ativo,
  });
}

export function useRubricasDaPo(condominioId: string, poId: string | undefined, filtro: FiltroRubrica) {
  return useQuery({
    queryKey: ["rubricas-po", condominioId, poId, filtro],
    queryFn: () =>
      obter<RubricasDaPo>(
        `${base(condominioId)}/budgets/${poId}/budget-items${consulta({ filter: filtro === "ALL" ? null : filtro })}`,
      ),
    enabled: !!poId,
  });
}

export function useEventosRubricas(condominioId: string, poId: string, ativo: boolean) {
  return useQuery({
    queryKey: ["rubricas-eventos", condominioId, poId],
    queryFn: async () => (await obter<EventoRubrica[]>(`${base(condominioId)}/budgets/${poId}/budget-items/events`)) ?? [],
    enabled: ativo,
  });
}

/** Só Admin (o backend responde 403 para Gestor e Usuário). Rubrica do catálogo ou nova, nunca as duas. */
export function useDefinirRubrica(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: ({ linhaId, pedido }: { linhaId: string; pedido: PedidoRubricaLinha }) =>
      atualizar<LinhaComRubrica>(`${base(condominioId)}/budgets/${poId}/budget-items/${linhaId}`, pedido),
    onSuccess: recarregar,
  });
}

/** Só Admin: confirma ou recusa várias linhas; o backend grava um evento por linha. */
export function useLoteRubricas(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: (pedido: PedidoLoteRubrica) =>
      enviarJson<ResultadoLoteRubrica>(`${base(condominioId)}/budgets/${poId}/budget-items/batch`, pedido),
    onSuccess: recarregar,
  });
}

/** Só Admin: pede as sugestões (mesma conta da PO e mesmo grupo), sem IA. Tudo entra como sugerido. */
export function useSugerirRubricas(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: () => enviarJson<ResultadoSugestoesRubrica>(`${base(condominioId)}/budgets/${poId}/budget-items/suggestions`),
    onSuccess: recarregar,
  });
}
