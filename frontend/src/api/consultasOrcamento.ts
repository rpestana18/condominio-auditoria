// Consultas e ações do previsto × realizado, da PO e do de-para (ADR 0004).
// Todo número vem calculado do backend; aqui só se busca e se envia.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { atualizar, enviar, enviarJson, excluir, obter } from "./cliente";
import type {
  Achado,
  ContaDepara,
  EventoPrevisao,
  FundoFluxo,
  DeparaLista,
  EventoDepara,
  EvidenciaLancamento,
  FiltroDepara,
  PedidoConfirmacao,
  PedidoDestino,
  PedidoLote,
  PrevisaoDetalhe,
  PrevisaoResumo,
  PedidoRealocacao,
  PrevistoRealizado,
  Realocacao,
  ResultadoLote,
  ResultadoPlanilha,
  ResultadoSugestoes,
} from "./tipos";

/** "2026-09" (um mês) ou "cumulative" (exercício da PO), como pede a API. */
export type Periodo = string;

export type FormatoExportacao = "pdf" | "xlsx";

/** Monta "?a=1&b=2" ignorando os valores vazios. */
export function consulta(parametros: Record<string, string | null | undefined>): string {
  const busca = new URLSearchParams();
  for (const [nome, valor] of Object.entries(parametros)) if (valor) busca.set(nome, valor);
  const texto = busca.toString();
  return texto ? `?${texto}` : "";
}

export const base = (condominioId: string) => `/condominiums/${condominioId}`;

// ---------- PO ----------

export function usePrevisoes(condominioId: string) {
  return useQuery({
    queryKey: ["previsoes", condominioId],
    queryFn: async () => (await obter<PrevisaoResumo[]>(`${base(condominioId)}/budgets`)) ?? [],
  });
}

export function usePrevisao(condominioId: string, poId: string | undefined) {
  return useQuery({
    queryKey: ["previsao", condominioId, poId],
    queryFn: () => obter<PrevisaoDetalhe>(`${base(condominioId)}/budgets/${poId}`),
    enabled: !!poId,
  });
}

// ---------- Previsto × realizado ----------

/** `fundoId` filtra no backend: o ordinário traz só o fundo Condomínio; outro fundo, só o painel dele; sem ele, tudo. */
export function usePrevistoRealizado(condominioId: string, periodo: Periodo | null, poId?: string | null, fundoId?: string | null) {
  return useQuery({
    queryKey: ["previsto-realizado", condominioId, periodo, poId ?? "vigente", fundoId ?? "todos"],
    queryFn: () =>
      obter<PrevistoRealizado>(`${base(condominioId)}/budget-vs-actual${consulta({ period: periodo, budget: poId, fund: fundoId })}`),
    enabled: periodo !== null,
  });
}

/**
 * Lançamentos de um número da tela. Alvo: "line:<id>", "group:<id>", "total", "fund:<id>", ADJUSTMENTS,
 * TO_REALLOCATE, NO_BUDGET_LINE ou TRANSFERS (contrato da API).
 */
export function useEvidencia(condominioId: string, periodo: Periodo, poId: string | null | undefined, alvo: string | null) {
  return useQuery({
    queryKey: ["evidencia", condominioId, periodo, poId ?? "vigente", alvo],
    queryFn: async () =>
      (await obter<EvidenciaLancamento[]>(
        `${base(condominioId)}/budget-vs-actual/evidence${consulta({ period: periodo, budget: poId, target: alvo })}`,
      )) ?? [],
    enabled: alvo !== null,
  });
}

/** Exportação da mesma visão da tela (mesmos filtros), gerada no backend (RF-03.1.14). */
export function caminhoExportacao(
  condominioId: string,
  formato: FormatoExportacao,
  periodo: Periodo,
  poId?: string | null,
  fundoId?: string | null,
) {
  return `${base(condominioId)}/budget-vs-actual/export${consulta({ format: formato, period: periodo, budget: poId, fund: fundoId })}`;
}

/** Fundos do fluxo pelo nome impresso (filtro de fundo e ligação das linhas 1.9). */
export function useFundos(condominioId: string) {
  return useQuery({
    queryKey: ["fundos", condominioId],
    queryFn: async () => (await obter<FundoFluxo[]>(`${base(condominioId)}/funds`)) ?? [],
  });
}

/** Achados de um mês (ou de todos, sem competência), só leitura. */
export function useAchados(condominioId: string, competencia: string | null) {
  return useQuery({
    queryKey: ["achados", condominioId, competencia ?? "todos"],
    queryFn: async () => (await obter<Achado[]>(`${base(condominioId)}/findings${consulta({ referenceMonth: competencia })}`)) ?? [],
  });
}

export function useEventosPrevisao(condominioId: string, poId: string | undefined) {
  return useQuery({
    queryKey: ["previsao-eventos", condominioId, poId],
    queryFn: async () => (await obter<EventoPrevisao[]>(`${base(condominioId)}/budgets/${poId}/events`)) ?? [],
    enabled: !!poId,
  });
}

// ---------- Realocação (RF-03.1.7) ----------

export function useRealocacoes(condominioId: string, poId: string | undefined) {
  return useQuery({
    queryKey: ["realocacoes", condominioId, poId],
    queryFn: async () => (await obter<Realocacao[]>(`${base(condominioId)}/reallocations${consulta({ budget: poId })}`)) ?? [],
    enabled: !!poId,
  });
}

// ---------- De-para ----------

export function useDepara(condominioId: string, poId: string | undefined, filtro: FiltroDepara) {
  return useQuery({
    queryKey: ["depara", condominioId, poId, filtro],
    queryFn: () =>
      obter<DeparaLista>(
        `${base(condominioId)}/budgets/${poId}/account-mappings${consulta({ filter: filtro === "ALL" ? null : filtro })}`,
      ),
    enabled: !!poId,
  });
}

export function useEventosDepara(condominioId: string, poId: string | undefined, ativo: boolean) {
  return useQuery({
    queryKey: ["depara-eventos", condominioId, poId],
    queryFn: async () => (await obter<EventoDepara[]>(`${base(condominioId)}/budgets/${poId}/account-mappings/events`)) ?? [],
    enabled: !!poId && ativo,
  });
}

/**
 * Mudou PO, de-para ou rubrica: o previsto × realizado, a análise da PO, a tela inicial e as listas são recarregados.
 * Exportada para as consultas da análise da PO (consultasAnalisePo.ts).
 */
export function useRecarregarOrcamento() {
  const cliente = useQueryClient();
  return () => {
    const chaves = [
      "previsoes", "previsao", "previsao-eventos", "depara", "depara-eventos", "previsto-realizado", "evidencia", "painel",
      "realocacoes", "achados", "exercicios", "comparacao-exercicios", "coluna-impressa", "rubricas", "rubricas-po", "rubricas-eventos", "indicadores",
    ];
    for (const chave of chaves) {
      void cliente.invalidateQueries({ queryKey: [chave] });
    }
  };
}

/** Só Admin (o backend responde 403 para os demais). */
export function useConfirmarPrevisao(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: (pedido: PedidoConfirmacao) =>
      enviarJson<PrevisaoDetalhe>(`${base(condominioId)}/budgets/${poId}/confirmation`, pedido),
    onSuccess: recarregar,
  });
}

export function useDefinirDepara(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: ({ conta, pedido }: { conta: string; pedido: PedidoDestino }) =>
      atualizar<ContaDepara>(`${base(condominioId)}/budgets/${poId}/account-mappings/${encodeURIComponent(conta)}`, pedido),
    onSuccess: recarregar,
  });
}

export function useLoteDepara(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: (pedido: PedidoLote) => enviarJson<ResultadoLote>(`${base(condominioId)}/budgets/${poId}/account-mappings/batch`, pedido),
    onSuccess: recarregar,
  });
}

export function useSugerirDepara(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: () => enviarJson<ResultadoSugestoes>(`${base(condominioId)}/budgets/${poId}/account-mappings/suggestions`),
    onSuccess: recarregar,
  });
}

export function usePlanilhaDepara(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: (arquivo: File) => {
      const corpo = new FormData();
      corpo.append("file", arquivo);
      return enviar<ResultadoPlanilha>(`${base(condominioId)}/budgets/${poId}/account-mappings/sheet`, corpo);
    },
    onSuccess: recarregar,
  });
}

/** Só Gestor e Admin (o backend responde 403 para o Usuário). */
export function useRealocar(condominioId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: (pedido: PedidoRealocacao) => enviarJson<Realocacao>(`${base(condominioId)}/reallocations`, pedido),
    onSuccess: recarregar,
  });
}

/** Desfazer não apaga: a realocação fica registrada como desfeita e o valor volta a "a realocar". */
export function useDesfazerRealocacao(condominioId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: (realocacaoId: string) => excluir<Realocacao>(`${base(condominioId)}/reallocations/${realocacaoId}`),
    onSuccess: recarregar,
  });
}

/** Só Admin: troca a ligação das linhas 1.9 aos fundos depois da confirmação (lista completa). */
export function useAlterarFundosPo(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: (fundos: { linhaId: string; fundoId: string | null }[]) =>
      atualizar<PrevisaoDetalhe>(`${base(condominioId)}/budgets/${poId}/funds`, { funds: fundos.map((f) => ({ lineId: f.linhaId, fundId: f.fundoId })) }),
    onSuccess: recarregar,
  });
}
