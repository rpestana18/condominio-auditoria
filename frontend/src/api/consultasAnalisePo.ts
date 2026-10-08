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
    queryFn: async () => (await obter<Exercicio[]>(`${base(condominioId)}/exercicios`)) ?? [],
  });
}

// ---------- Comparar exercícios (RF-11.6) ----------

export interface FiltroComparacao {
  /** Ids da lista de exercícios ("po:<uuid>" ou "coluna:<uuid>"). Nulo = o backend escolhe os dois mais recentes. */
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
        `${base(condominioId)}/comparacao-exercicios${consulta({
          // O contrato pede a lista separada por vírgula (style: form, explode: false)
          exercicios: exercicios?.join(","),
          fundo: fundoId,
          mesmosMeses: mesmosMeses ? "true" : null,
        })}`,
      ),
  });
}

/** Conferência da coluna "Orçado anterior" impressa na PO `poId` (RF-11.5). Só busca quando `poId` existe. */
export function useColunaImpressa(condominioId: string, poId: string | null) {
  return useQuery({
    queryKey: ["coluna-impressa", condominioId, poId],
    queryFn: () => obter<ConferenciaColuna>(`${base(condominioId)}/previsoes/${poId}/coluna-impressa`),
    enabled: !!poId,
  });
}

// ---------- Rubricas (RF-11.7) ----------

/** Catálogo de rubricas do condomínio, em ordem de nome. */
export function useRubricas(condominioId: string, ativo = true) {
  return useQuery({
    queryKey: ["rubricas", condominioId],
    queryFn: async () => (await obter<Rubrica[]>(`${base(condominioId)}/rubricas`)) ?? [],
    enabled: ativo,
  });
}

export function useRubricasDaPo(condominioId: string, poId: string | undefined, filtro: FiltroRubrica) {
  return useQuery({
    queryKey: ["rubricas-po", condominioId, poId, filtro],
    queryFn: () =>
      obter<RubricasDaPo>(
        `${base(condominioId)}/previsoes/${poId}/rubricas${consulta({ filtro: filtro === "TODAS" ? null : filtro })}`,
      ),
    enabled: !!poId,
  });
}

export function useEventosRubricas(condominioId: string, poId: string, ativo: boolean) {
  return useQuery({
    queryKey: ["rubricas-eventos", condominioId, poId],
    queryFn: async () => (await obter<EventoRubrica[]>(`${base(condominioId)}/previsoes/${poId}/rubricas/eventos`)) ?? [],
    enabled: ativo,
  });
}

/** Só Admin (o backend responde 403 para Gestor e Usuário). Rubrica do catálogo ou nova, nunca as duas. */
export function useDefinirRubrica(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: ({ linhaId, pedido }: { linhaId: string; pedido: PedidoRubricaLinha }) =>
      atualizar<LinhaComRubrica>(`${base(condominioId)}/previsoes/${poId}/rubricas/${linhaId}`, pedido),
    onSuccess: recarregar,
  });
}

/** Só Admin: confirma ou recusa várias linhas; o backend grava um evento por linha. */
export function useLoteRubricas(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: (pedido: PedidoLoteRubrica) =>
      enviarJson<ResultadoLoteRubrica>(`${base(condominioId)}/previsoes/${poId}/rubricas/lote`, pedido),
    onSuccess: recarregar,
  });
}

/** Só Admin: pede as sugestões (mesma conta da PO e mesmo grupo), sem IA. Tudo entra como sugerido. */
export function useSugerirRubricas(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: () => enviarJson<ResultadoSugestoesRubrica>(`${base(condominioId)}/previsoes/${poId}/rubricas/sugestoes`),
    onSuccess: recarregar,
  });
}
