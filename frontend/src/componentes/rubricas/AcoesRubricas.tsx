import { useState } from "react";
import { useLoteRubricas, useSugerirRubricas } from "../../api/consultasAnalisePo";
import type { LinhaComRubrica } from "../../api/tipos";
import { useSessao } from "../../contexto";

interface Props {
  poId: string;
  /** Linhas da lista, para mostrar o código no lugar do id quando o lote ignora alguma. */
  linhas: LinhaComRubrica[];
  selecionadas: string[];
  aoConcluirLote: () => void;
}

interface Mensagem {
  texto: string;
  /** Linhas não alteradas, com o motivo que o backend mandou. */
  naoAlteradas: { linha: string; motivo: string }[];
}

/** Barra de ações do Admin: confirmar ou recusar em lote e pedir as sugestões (RF-11.7). */
export function AcoesRubricas({ poId, linhas, selecionadas, aoConcluirLote }: Props) {
  const { condominioId } = useSessao();
  const lote = useLoteRubricas(condominioId, poId);
  const sugerir = useSugerirRubricas(condominioId, poId);
  const [mensagem, setMensagem] = useState<Mensagem | null>(null);
  const ocupado = lote.isPending || sugerir.isPending;
  const erro = lote.error ?? sugerir.error;
  const descrever = (linhaId: string) => {
    const linha = linhas.find((l) => l.lineId === linhaId);
    return linha ? `${linha.code} ${linha.description}` : linhaId;
  };

  const executarLote = (acao: "CONFIRM" | "REJECT") =>
    lote.mutate(
      { action: acao, lines: selecionadas },
      {
        onSuccess: (r) => {
          setMensagem({
            texto: `${r.changed} linha(s) ${acao === "CONFIRM" ? "confirmadas" : "recusadas"}.`,
            naoAlteradas: r.skipped.map((i) => ({ linha: descrever(i.lineId), motivo: i.reason })),
          });
          aoConcluirLote();
        },
      },
    );

  const pedirSugestoes = () =>
    sugerir.mutate(undefined, {
      onSuccess: (r) =>
        setMensagem({
          texto: r.firstBudget
            ? `Primeira PO do condomínio: ${r.createdItems} rubrica(s) criadas, já confirmadas.`
            : `${r.suggested} sugestão(ões): ${r.fromPreviousVersion} da versão anterior, ${r.byAccount} pela conta da PO e grupo. ${r.withoutSuggestion.length} sem sugestão.`,
          naoAlteradas: r.withoutSuggestion.map((s) => ({ linha: `${s.code} ${s.account}`, motivo: s.reason })),
        }),
    });

  return (
    <div className="envio">
      <button className="botao" disabled={ocupado || selecionadas.length === 0} onClick={() => executarLote("CONFIRM")}>
        Confirmar selecionadas ({selecionadas.length})
      </button>
      <button className="botao secundario" disabled={ocupado || selecionadas.length === 0} onClick={() => executarLote("REJECT")}>
        Recusar selecionadas
      </button>
      <button
        className="botao secundario"
        disabled={ocupado}
        title="Mesma conta da PO e mesmo grupo, sem IA. Tudo entra como sugerido; nenhum número muda."
        onClick={pedirSugestoes}
      >
        Pedir sugestões
      </button>
      {erro && <p className="aviso erro">{erro.message}</p>}
      {mensagem && (
        <div className="aviso ok">
          {mensagem.texto}
          {mensagem.naoAlteradas.length > 0 && (
            <details>
              <summary>Sem alteração ({mensagem.naoAlteradas.length})</summary>
              <ul className="lista-simples">
                {mensagem.naoAlteradas.map((n, i) => (
                  <li key={i}>
                    {n.linha}: {n.motivo}
                  </li>
                ))}
              </ul>
            </details>
          )}
        </div>
      )}
    </div>
  );
}
