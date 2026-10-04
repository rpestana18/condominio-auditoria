// Consultas e ações do previsto × realizado, da PO e do de-para (ADR 0004).
// Todo número vem calculado do backend; aqui só se busca e se envia.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { enviar, enviarJson, atualizar, obter } from "./cliente";
import type {
  ContaDepara,
  DeparaLista,
  EventoDepara,
  EvidenciaLancamento,
  FiltroDepara,
  PedidoConfirmacao,
  PedidoDestino,
  PedidoLote,
  PrevisaoDetalhe,
  PrevisaoResumo,
  PrevistoRealizado,
  ResultadoLote,
  ResultadoPlanilha,
  ResultadoSugestoes,
} from "./tipos";

/** "2026-09" (um mês) ou "acumulado" (exercício da PO), como pede a API. */
export type Periodo = string;

export type FormatoExportacao = "pdf" | "xlsx";

/** Monta "?a=1&b=2" ignorando os valores vazios. */
function consulta(parametros: Record<string, string | null | undefined>): string {
  const busca = new URLSearchParams();
  for (const [nome, valor] of Object.entries(parametros)) if (valor) busca.set(nome, valor);
  const texto = busca.toString();
  return texto ? `?${texto}` : "";
}

const base = (condominioId: string) => `/condominios/${condominioId}`;

// ---------- PO ----------

export function usePrevisoes(condominioId: string) {
  return useQuery({
    queryKey: ["previsoes", condominioId],
    queryFn: async () => (await obter<PrevisaoResumo[]>(`${base(condominioId)}/previsoes`)) ?? [],
  });
}

export function usePrevisao(condominioId: string, poId: string | undefined) {
  return useQuery({
    queryKey: ["previsao", condominioId, poId],
    queryFn: () => obter<PrevisaoDetalhe>(`${base(condominioId)}/previsoes/${poId}`),
    enabled: !!poId,
  });
}

// ---------- Previsto × realizado ----------

export function usePrevistoRealizado(condominioId: string, periodo: Periodo | null, poId?: string | null) {
  return useQuery({
    queryKey: ["previsto-realizado", condominioId, periodo, poId ?? "vigente"],
    queryFn: () =>
      obter<PrevistoRealizado>(`${base(condominioId)}/previsto-realizado${consulta({ periodo, po: poId })}`),
    enabled: periodo !== null,
  });
}

/**
 * Lançamentos de um número da tela. Alvo: "linha:<id>", "fundo:<id>", AJUSTES, A_REALOCAR,
 * SEM_LINHA_PO ou TRANSFERENCIAS (contrato da API).
 */
export function useEvidencia(condominioId: string, periodo: Periodo, poId: string | null | undefined, alvo: string | null) {
  return useQuery({
    queryKey: ["evidencia", condominioId, periodo, poId ?? "vigente", alvo],
    queryFn: async () =>
      (await obter<EvidenciaLancamento[]>(
        `${base(condominioId)}/previsto-realizado/evidencia${consulta({ periodo, po: poId, alvo })}`,
      )) ?? [],
    enabled: alvo !== null,
  });
}

/**
 * Caminho da exportação (passo 9 da ADR 0004, ainda em construção no backend).
 * Usa o caminho previsto na ADR: GET /previsto-realizado/exportacao?formato=pdf|xlsx, com os filtros da tela.
 */
export function caminhoExportacao(condominioId: string, formato: FormatoExportacao, periodo: Periodo, poId?: string | null) {
  return `${base(condominioId)}/previsto-realizado/exportacao${consulta({ formato, periodo, po: poId })}`;
}

// ---------- De-para ----------

export function useDepara(condominioId: string, poId: string | undefined, filtro: FiltroDepara) {
  return useQuery({
    queryKey: ["depara", condominioId, poId, filtro],
    queryFn: () =>
      obter<DeparaLista>(
        `${base(condominioId)}/previsoes/${poId}/depara${consulta({ filtro: filtro === "TODAS" ? null : filtro })}`,
      ),
    enabled: !!poId,
  });
}

export function useEventosDepara(condominioId: string, poId: string | undefined, ativo: boolean) {
  return useQuery({
    queryKey: ["depara-eventos", condominioId, poId],
    queryFn: async () => (await obter<EventoDepara[]>(`${base(condominioId)}/previsoes/${poId}/depara/eventos`)) ?? [],
    enabled: !!poId && ativo,
  });
}

/** Mudou PO ou de-para: o previsto × realizado, a tela inicial e as listas são recarregados. */
function useRecarregarOrcamento() {
  const cliente = useQueryClient();
  return () => {
    for (const chave of ["previsoes", "previsao", "depara", "depara-eventos", "previsto-realizado", "evidencia", "painel"]) {
      void cliente.invalidateQueries({ queryKey: [chave] });
    }
  };
}

/** Só Admin (o backend responde 403 para os demais). */
export function useConfirmarPrevisao(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: (pedido: PedidoConfirmacao) =>
      enviarJson<PrevisaoDetalhe>(`${base(condominioId)}/previsoes/${poId}/confirmacao`, pedido),
    onSuccess: recarregar,
  });
}

export function useDefinirDepara(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: ({ conta, pedido }: { conta: string; pedido: PedidoDestino }) =>
      atualizar<ContaDepara>(`${base(condominioId)}/previsoes/${poId}/depara/${encodeURIComponent(conta)}`, pedido),
    onSuccess: recarregar,
  });
}

export function useLoteDepara(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: (pedido: PedidoLote) => enviarJson<ResultadoLote>(`${base(condominioId)}/previsoes/${poId}/depara/lote`, pedido),
    onSuccess: recarregar,
  });
}

export function useSugerirDepara(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: () => enviarJson<ResultadoSugestoes>(`${base(condominioId)}/previsoes/${poId}/depara/sugestoes`),
    onSuccess: recarregar,
  });
}

export function usePlanilhaDepara(condominioId: string, poId: string) {
  const recarregar = useRecarregarOrcamento();
  return useMutation({
    mutationFn: (arquivo: File) => {
      const corpo = new FormData();
      corpo.append("arquivo", arquivo);
      return enviar<ResultadoPlanilha>(`${base(condominioId)}/previsoes/${poId}/depara/planilha`, corpo);
    },
    onSuccess: recarregar,
  });
}
