import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { atualizar, enviar, gravar, obter } from "./cliente";
import type { ArquivoDetalhe, ArquivoResumo, Categoria, CategoriaDto, NovaCategoria, Painel, UsuarioLogado } from "./tipos";

const emAndamento = (a: ArquivoResumo) => a.status === "PENDENTE" || a.status === "PROCESSANDO";

export function useUsuario() {
  return useQuery({ queryKey: ["eu"], queryFn: () => obter<UsuarioLogado>("/eu") });
}

export function useCategorias() {
  return useQuery({ queryKey: ["categorias"], queryFn: async () => (await obter<CategoriaDto[]>("/categorias")) ?? [], staleTime: Infinity });
}

export function useArquivos(condominioId: string, categoria?: Categoria) {
  const filtro = categoria ? `?categoria=${categoria}` : "";
  return useQuery({
    queryKey: ["arquivos", condominioId, categoria ?? "todas"],
    queryFn: async () => (await obter<ArquivoResumo[]>(`/condominios/${condominioId}/arquivos${filtro}`)) ?? [],
    // Enquanto algum arquivo estiver na fila ou processando, atualiza a lista a cada 3 segundos
    refetchInterval: (consulta) => (consulta.state.data?.some(emAndamento) ? 3000 : false),
  });
}

export function useUltimoArquivo(condominioId: string) {
  return useQuery({
    queryKey: ["arquivos", condominioId, "ultimo"],
    queryFn: () => obter<ArquivoResumo>(`/condominios/${condominioId}/arquivos/ultimo`),
    refetchInterval: (consulta) => (consulta.state.data && emAndamento(consulta.state.data) ? 3000 : false),
  });
}

export function useDetalheArquivo(condominioId: string, id: string | null) {
  return useQuery({
    queryKey: ["arquivo", condominioId, id],
    queryFn: () => obter<ArquivoDetalhe>(`/condominios/${condominioId}/arquivos/${id}`),
    enabled: id !== null,
    refetchInterval: (consulta) => (consulta.state.data && emAndamento(consulta.state.data.arquivo) ? 3000 : false),
  });
}

export function usePainel(condominioId: string) {
  return useQuery({
    queryKey: ["painel", condominioId],
    queryFn: () => obter<Painel>(`/condominios/${condominioId}/painel`),
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
      corpo.append("arquivo", arquivo);
      return enviar<ArquivoResumo>(`/condominios/${condominioId}/arquivos?categoria=${categoria}`, corpo);
    },
    onSuccess: recarregar,
  });
}

export function useReprocessar(condominioId: string) {
  const recarregar = useRecarregarArquivos();
  return useMutation({
    mutationFn: (id: string) => enviar<ArquivoResumo>(`/condominios/${condominioId}/arquivos/${id}/reprocessar`),
    onSuccess: recarregar,
  });
}

/** Troca a categoria; o backend reprocessa o arquivo com a nova (RF-01.7). */
export function useAlterarCategoria(condominioId: string) {
  const recarregar = useRecarregarArquivos();
  return useMutation({
    mutationFn: ({ id, categoria }: { id: string; categoria: Categoria }) =>
      atualizar<ArquivoResumo>(`/condominios/${condominioId}/arquivos/${id}/categoria`, { categoria } satisfies NovaCategoria),
    onSuccess: recarregar,
  });
}

export function useConfirmarFundoOrdinario(condominioId: string) {
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: (fundoId: string) => gravar(`/condominios/${condominioId}/fundo-ordinario`, { fundoId }),
    onSuccess: () => void cliente.invalidateQueries({ queryKey: ["painel", condominioId] }),
  });
}
