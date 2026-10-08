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
    const linha = linhas.find((l) => l.linhaId === linhaId);
    return linha ? `${linha.codigo} ${linha.descricao}` : linhaId;
  };

  const executarLote = (acao: "CONFIRMAR" | "RECUSAR") =>
    lote.mutate(
      { acao, linhas: selecionadas },
      {
        onSuccess: (r) => {
          setMensagem({
            texto: `${r.alteradas} linha(s) ${acao === "CONFIRMAR" ? "confirmadas" : "recusadas"}.`,
            naoAlteradas: r.ignoradas.map((i) => ({ linha: descrever(i.linhaId), motivo: i.motivo })),
          });
          aoConcluirLote();
        },
      },
    );

  const pedirSugestoes = () =>
    sugerir.mutate(undefined, {
      onSuccess: (r) =>
        setMensagem({
          texto: r.primeiraPo
            ? `Primeira PO do condomínio: ${r.rubricasCriadas} rubrica(s) criadas, já confirmadas.`
            : `${r.sugeridas} sugestão(ões): ${r.daVersaoAnterior} da versão anterior, ${r.pelaConta} pela conta da PO e grupo. ${r.semSugestao.length} sem sugestão.`,
          naoAlteradas: r.semSugestao.map((s) => ({ linha: `${s.codigo} ${s.conta}`, motivo: s.motivo })),
        }),
    });

  return (
    <div className="envio">
      <button className="botao" disabled={ocupado || selecionadas.length === 0} onClick={() => executarLote("CONFIRMAR")}>
        Confirmar selecionadas ({selecionadas.length})
      </button>
      <button className="botao secundario" disabled={ocupado || selecionadas.length === 0} onClick={() => executarLote("RECUSAR")}>
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
