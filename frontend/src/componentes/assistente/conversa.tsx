import { useQueryClient } from "@tanstack/react-query";
import { createContext, useContext, useState, type ReactNode } from "react";
import { ErroApi, postar } from "../../api/cliente";
import type { FiltrosDocumentos, PedidoPergunta, RespostaAssistente, TrocaHistorico } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { mensagemErroAssistente } from "./erros";

/** Quantas trocas anteriores vão em cada pergunta (o backend também corta nas últimas 6). */
const TROCAS_NO_HISTORICO = 6;

/** Uma pergunta e o que voltou: a resposta, um erro, ou nada ainda (esperando). */
export interface Troca {
  id: number;
  pergunta: string;
  resposta?: RespostaAssistente;
  erro?: string;
}

interface EstadoConversa {
  /** A conversa é de um condomínio só (RF-04.11): trocou, começa outra. */
  condominioId: string;
  /** Muda a cada "Nova conversa": respostas que chegarem de uma conversa antiga são descartadas. */
  conversaId: number;
  trocas: Troca[];
  filtros: FiltrosDocumentos;
}

interface Conversa {
  trocas: Troca[];
  pendente: boolean;
  filtros: FiltrosDocumentos;
  alterarFiltros: (filtros: FiltrosDocumentos) => void;
  perguntar: (pergunta: string) => void;
  novaConversa: () => void;
}

let proximoId = 1;
const novoId = () => proximoId++;

const vazio = (condominioId: string): EstadoConversa => ({ condominioId, conversaId: novoId(), trocas: [], filtros: {} });

/**
 * Texto de uma resposta para o histórico: os parágrafos "Nos documentos" e as linhas "Nos dados gravados",
 * como vieram da API (sem recalcular nada). Serve só para o modelo entender "e no ano anterior?".
 */
function textoDaResposta(resposta: RespostaAssistente): string {
  if (resposta.situacao === "NAO_ENCONTRADA") {
    return ["Não encontrei nos documentos.", resposta.sugestao].filter(Boolean).join(" ");
  }
  const documentos = resposta.nosDocumentos.map((p) => p.texto);
  const dados = resposta.nosDadosGravados.map(
    (d) => `${d.consulta}: ${d.linhas.map((l) => `${l.rotulo} ${l.valor}`).join("; ")}`,
  );
  return [...documentos, ...dados].join("\n");
}

/** Filtros sem campos vazios: a API entende ausente como "todos". */
export function filtrosParaApi(filtros: FiltrosDocumentos): FiltrosDocumentos | undefined {
  const limpo: FiltrosDocumentos = {};
  if (filtros.categorias?.length) limpo.categorias = filtros.categorias;
  if (filtros.arquivoIds?.length) limpo.arquivoIds = filtros.arquivoIds;
  if (filtros.dataInicio) limpo.dataInicio = filtros.dataInicio;
  if (filtros.dataFim) limpo.dataFim = filtros.dataFim;
  return Object.keys(limpo).length > 0 ? limpo : undefined;
}

const ContextoConversa = createContext<Conversa | null>(null);

/**
 * Guarda a conversa do Assistente só na memória (nunca em localStorage/sessionStorage, RF-04.11).
 * Fica acima das rotas para a conversa não sumir ao visitar outra tela; some ao trocar de condomínio,
 * com "Nova conversa" e ao sair (o logout recarrega a página).
 */
export function ProvedorConversa({ children }: { children: ReactNode }) {
  const { condominioId } = useSessao();
  const consultas = useQueryClient();
  const [estado, setEstado] = useState<EstadoConversa>(() => vazio(condominioId));

  // Trocou o condomínio: começa outra conversa (ajuste de estado durante a renderização, padrão do React)
  let atual = estado;
  if (estado.condominioId !== condominioId) {
    atual = vazio(condominioId);
    setEstado(atual);
  }

  const pendente = atual.trocas.some((t) => !t.resposta && !t.erro);

  /** Atualiza uma troca, mas só se ainda for a mesma conversa (senão a resposta chegou tarde e é descartada). */
  function concluir(conversaId: number, trocaId: number, resultado: Pick<Troca, "resposta" | "erro">) {
    setEstado((e) =>
      e.conversaId !== conversaId ? e : { ...e, trocas: e.trocas.map((t) => (t.id === trocaId ? { ...t, ...resultado } : t)) },
    );
  }

  function perguntar(pergunta: string) {
    if (pendente) return;
    const historico: TrocaHistorico[] = atual.trocas
      .filter((t) => t.resposta)
      .slice(-TROCAS_NO_HISTORICO)
      .map((t) => ({ pergunta: t.pergunta, resposta: textoDaResposta(t.resposta!) }));
    const pedido: PedidoPergunta = { pergunta, historico, filtros: filtrosParaApi(atual.filtros) };
    const { conversaId } = atual;
    const trocaId = novoId();
    setEstado((e) => ({ ...e, trocas: [...e.trocas, { id: trocaId, pergunta }] }));

    postar<RespostaAssistente>(`/condominios/${condominioId}/assistente/perguntas`, pedido)
      .then((resposta) => concluir(conversaId, trocaId, { resposta }))
      .catch((erro: unknown) => {
        concluir(conversaId, trocaId, { erro: mensagemErroAssistente(erro) });
        // 409: o modo mudou desde que a tela abriu; recarrega o contexto para a tela se ajustar (RF-04.16)
        if (erro instanceof ErroApi && (erro.status === 409 || erro.moduloNaoContratado)) {
          void consultas.invalidateQueries({ queryKey: ["contexto", condominioId] });
        }
      });
  }

  const conversa: Conversa = {
    trocas: atual.trocas,
    pendente,
    filtros: atual.filtros,
    alterarFiltros: (filtros) => setEstado((e) => ({ ...e, filtros })),
    perguntar,
    // Mantém os filtros: eles são uma escolha da tela, não da conversa
    novaConversa: () => setEstado((e) => ({ ...vazio(e.condominioId), filtros: e.filtros })),
  };
  return <ContextoConversa.Provider value={conversa}>{children}</ContextoConversa.Provider>;
}

export function useConversa(): Conversa {
  const conversa = useContext(ContextoConversa);
  if (!conversa) throw new Error("useConversa fora do ProvedorConversa");
  return conversa;
}
