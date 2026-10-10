import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { atualizar, enviar, gravar, obter, enviarJson } from "./cliente";
import type {
  AlteracaoModulo,
  ArquivoDetalhe,
  ArquivoResumo,
  Categoria,
  CategoriaDto,
  ConfiguracaoIa,
  ContextoCondominio,
  EventoModulo,
  ModuloDoCondominio,
  NovaCategoria,
  Painel,
  PedidoBuscaDocumentos,
  PedidoConfiguracaoIa,
  PeriodoAtivo,
  ProvedorIa,
  TrechoDocumento,
  UsoDoPeriodo,
  UsuarioLogado,
} from "./tipos";

/** Processamento ou indexação ainda em curso: a tela continua perguntando a cada 3 segundos. */
const emAndamento = (a: ArquivoResumo) =>
  a.status === "PENDING" ||
  a.status === "PROCESSING" ||
  a.indexing?.status === "QUEUED" ||
  a.indexing?.status === "INDEXING";

export function useUsuario() {
  return useQuery({ queryKey: ["eu"], queryFn: () => obter<UsuarioLogado>("/me") });
}

export function useCategorias() {
  return useQuery({ queryKey: ["categorias"], queryFn: async () => (await obter<CategoriaDto[]>("/categories")) ?? [], staleTime: Infinity });
}

export function useArquivos(condominioId: string, categoria?: Categoria) {
  const filtro = categoria ? `?category=${categoria}` : "";
  return useQuery({
    queryKey: ["arquivos", condominioId, categoria ?? "todas"],
    queryFn: async () => (await obter<ArquivoResumo[]>(`/condominiums/${condominioId}/files${filtro}`)) ?? [],
    // Enquanto algum arquivo estiver na fila ou processando, atualiza a lista a cada 3 segundos
    refetchInterval: (consulta) => (consulta.state.data?.some(emAndamento) ? 3000 : false),
  });
}

export function useUltimoArquivo(condominioId: string) {
  return useQuery({
    queryKey: ["arquivos", condominioId, "ultimo"],
    queryFn: () => obter<ArquivoResumo>(`/condominiums/${condominioId}/files/latest`),
    refetchInterval: (consulta) => (consulta.state.data && emAndamento(consulta.state.data) ? 3000 : false),
  });
}

export function useDetalheArquivo(condominioId: string, id: string | null) {
  return useQuery({
    queryKey: ["arquivo", condominioId, id],
    queryFn: () => obter<ArquivoDetalhe>(`/condominiums/${condominioId}/files/${id}`),
    enabled: id !== null,
    refetchInterval: (consulta) => (consulta.state.data && emAndamento(consulta.state.data.file) ? 3000 : false),
  });
}

export function usePainel(condominioId: string) {
  return useQuery({
    queryKey: ["painel", condominioId],
    queryFn: () => obter<Painel>(`/condominiums/${condominioId}/dashboard`),
  });
}

/** Depois de enviar, reprocessar ou trocar a categoria, tudo o que depende de arquivos é recarregado. */
function useRecarregarArquivos() {
  const cliente = useQueryClient();
  return () => {
    void cliente.invalidateQueries({ queryKey: ["arquivos"] });
    void cliente.invalidateQueries({ queryKey: ["arquivo"] });
    void cliente.invalidateQueries({ queryKey: ["painel"] });
  };
}

export function useEnviarArquivo(condominioId: string) {
  const recarregar = useRecarregarArquivos();
  return useMutation({
    mutationFn: ({ categoria, arquivo }: { categoria: Categoria; arquivo: File }) => {
      const corpo = new FormData();
      corpo.append("file", arquivo);
      return enviar<ArquivoResumo>(`/condominiums/${condominioId}/files?category=${categoria}`, corpo);
    },
    onSuccess: recarregar,
  });
}

export function useReprocessar(condominioId: string) {
  const recarregar = useRecarregarArquivos();
  return useMutation({
    mutationFn: (id: string) => enviar<ArquivoResumo>(`/condominiums/${condominioId}/files/${id}/reprocess`),
    onSuccess: recarregar,
  });
}

/** Troca a categoria; o backend reprocessa o arquivo com a nova (RF-01.7). */
export function useAlterarCategoria(condominioId: string) {
  const recarregar = useRecarregarArquivos();
  return useMutation({
    mutationFn: ({ id, categoria }: { id: string; categoria: Categoria }) =>
      atualizar<ArquivoResumo>(`/condominiums/${condominioId}/files/${id}/category`, { category: categoria } satisfies NovaCategoria),
    onSuccess: recarregar,
  });
}

export function useConfirmarFundoOrdinario(condominioId: string) {
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: (fundoId: string) => gravar(`/condominiums/${condominioId}/operating-fund`, { fundId: fundoId }),
    onSuccess: () => void cliente.invalidateQueries({ queryKey: ["painel", condominioId] }),
  });
}

// ---- Módulos contratáveis (RF-10) e uso (RF-09.7) ----

/** Módulos ligados no condomínio. Serve para mostrar ou esconder menus (o backend é quem barra). */
export function useContexto(condominioId: string) {
  return useQuery({
    queryKey: ["contexto", condominioId],
    queryFn: () => obter<ContextoCondominio>(`/condominiums/${condominioId}/context`),
  });
}

export function useModulos(condominioId: string) {
  return useQuery({
    queryKey: ["modulos", condominioId],
    queryFn: async () => (await obter<ModuloDoCondominio[]>(`/condominiums/${condominioId}/features`)) ?? [],
  });
}

/** Trilha de ligar/desligar de um módulo (só ADMIN). */
export function useEventosModulo(condominioId: string, codigo: string) {
  return useQuery({
    queryKey: ["modulos", condominioId, codigo, "eventos"],
    queryFn: async () => (await obter<EventoModulo[]>(`/condominiums/${condominioId}/features/${codigo}/events`)) ?? [],
  });
}

/** Períodos ativos, calculados pelo backend a partir da trilha (só ADMIN). */
export function usePeriodosModulo(condominioId: string, codigo: string) {
  return useQuery({
    queryKey: ["modulos", condominioId, codigo, "periodos"],
    queryFn: async () => (await obter<PeriodoAtivo[]>(`/condominiums/${condominioId}/features/${codigo}/periods`)) ?? [],
  });
}

/** Liga ou desliga um módulo (só ADMIN). Ligar o Assistente coloca os arquivos na fila de indexação. */
export function useAlterarModulo(condominioId: string) {
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: ({ codigo, alteracao }: { codigo: string; alteracao: AlteracaoModulo }) =>
      atualizar<ModuloDoCondominio>(`/condominiums/${condominioId}/features/${codigo}`, alteracao),
    onSuccess: () => {
      void cliente.invalidateQueries({ queryKey: ["modulos", condominioId] });
      void cliente.invalidateQueries({ queryKey: ["contexto", condominioId] });
      void cliente.invalidateQueries({ queryKey: ["arquivos", condominioId] });
      void cliente.invalidateQueries({ queryKey: ["arquivo", condominioId] });
    },
  });
}

/** Caminho do uso no período; a exportação usa o mesmo filtro. Datas em AAAA-MM-DD. */
export const caminhoUso = (condominioId: string, inicio: string, fim: string, exportacao = false) =>
  `/condominiums/${condominioId}/usage${exportacao ? "/export" : ""}?start=${inicio}&end=${fim}`;

/** Só pergunta à API com as duas datas e o início antes do fim (AAAA-MM-DD compara como texto). */
export const periodoPreenchido = (inicio: string, fim: string) => inicio !== "" && fim !== "" && inicio <= fim;

export function useUso(condominioId: string, inicio: string, fim: string) {
  return useQuery({
    queryKey: ["uso", condominioId, inicio, fim],
    queryFn: () => obter<UsoDoPeriodo>(caminhoUso(condominioId, inicio, fim)),
    enabled: periodoPreenchido(inicio, fim),
  });
}

// ---- Configuração de IA (RF-09.6) e Assistente (RF-04) ----

/** Configuração de IA do condomínio (só ADMIN). A chave nunca volta: só "cadastrada" e os 4 últimos caracteres. */
export function useConfiguracaoIa(condominioId: string) {
  return useQuery({
    queryKey: ["ia", condominioId],
    queryFn: () => obter<ConfiguracaoIa>(`/condominiums/${condominioId}/ai`),
  });
}

/** Catálogo de provedores e modelos, lido do rag pelo backend (só ADMIN). */
export function useProvedoresIa() {
  return useQuery({
    queryKey: ["ia", "provedores"],
    queryFn: async () => (await obter<ProvedorIa[]>("/ai/providers")) ?? [],
    staleTime: 5 * 60_000,
  });
}

/**
 * Grava a configuração de IA. Depois recarrega o contexto: o menu e a tela "Assistente" passam a refletir
 * o novo modo sem recarregar a página (RF-04.16, último critério).
 */
export function useGravarConfiguracaoIa(condominioId: string) {
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: (pedido: PedidoConfiguracaoIa) => atualizar<ConfiguracaoIa>(`/condominiums/${condominioId}/ai`, pedido),
    onSuccess: (configuracao) => {
      cliente.setQueryData(["ia", condominioId], configuracao);
      void cliente.invalidateQueries({ queryKey: ["contexto", condominioId] });
    },
  });
}

/** Busca por palavra nos documentos, sem IA (RF-04.18). É uma ação do usuário, por isso mutação e não consulta. */
export function useBuscarDocumentos(condominioId: string) {
  return useMutation({
    mutationFn: async (pedido: PedidoBuscaDocumentos) =>
      (await enviarJson<TrechoDocumento[]>(`/condominiums/${condominioId}/assistant/search`, pedido)) ?? [],
  });
}
