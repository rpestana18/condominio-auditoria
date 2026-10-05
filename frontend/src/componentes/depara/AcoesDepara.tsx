import { useState } from "react";
import { useLoteDepara, usePlanilhaDepara, useSugerirDepara } from "../../api/consultasOrcamento";
import type { ContaIgnorada } from "../../api/tipos";
import { useSessao } from "../../contexto";

interface Props {
  poId: string;
  selecionadas: string[];
  aoConcluirLote: () => void;
}

/** Barra de ações do Admin: confirmar ou recusar em lote, gerar sugestões e carregar a planilha (RF-03.1.5). */
export function AcoesDepara({ poId, selecionadas, aoConcluirLote }: Props) {
  const { condominioId } = useSessao();
  const lote = useLoteDepara(condominioId, poId);
  const sugerir = useSugerirDepara(condominioId, poId);
  const planilha = usePlanilhaDepara(condominioId, poId);
  const [mensagem, setMensagem] = useState<{ texto: string; ignoradas: ContaIgnorada[] } | null>(null);
  const ocupado = lote.isPending || sugerir.isPending || planilha.isPending;
  const erro = lote.error ?? sugerir.error ?? planilha.error;

  const executarLote = (acao: "CONFIRMAR" | "RECUSAR") =>
    lote.mutate(
      { acao, contas: selecionadas },
      {
        onSuccess: (r) => {
          setMensagem({ texto: `${r.alteradas} conta(s) ${acao === "CONFIRMAR" ? "confirmadas" : "recusadas"}.`, ignoradas: r.ignoradas });
          aoConcluirLote();
        },
      },
    );

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
        title="Compara os nomes, sem IA. Tudo entra como sugerido; nenhum número muda."
        onClick={() =>
          sugerir.mutate(undefined, {
            onSuccess: (r) =>
              setMensagem({
                texto: `${r.criadas} sugestão(ões): ${r.daVersaoAnterior} da versão anterior, ${r.peloNome} pelo nome. ${r.semSugestao.length} sem sugestão.`,
                ignoradas: r.semSugestao.map((s) => ({ conta: s.conta, motivo: s.motivo })),
              }),
          })
        }
      >
        Gerar sugestões pelo nome
      </button>
      <label className="botao secundario carregar">
        Carregar planilha (CSV)
        <input
          type="file"
          accept=".csv,text/csv"
          hidden
          disabled={ocupado}
          onChange={(e) => {
            const arquivo = e.target.files?.[0];
            e.target.value = "";
            if (!arquivo) return;
            planilha.mutate(arquivo, {
              onSuccess: (r) =>
                setMensagem({
                  texto: `${r.aceitas} linha(s) da planilha entraram como sugerido. ${r.recusadas.length} recusada(s).`,
                  ignoradas: [
                    ...r.ignoradas,
                    ...r.recusadas.map((x) => ({ conta: `linha ${x.linha}`, motivo: `${x.motivo}: ${x.conteudo}` })),
                  ],
                }),
            });
          }}
        />
      </label>
      {erro && <p className="aviso erro">{erro.message}</p>}
      {mensagem && (
        <div className="aviso ok">
          {mensagem.texto}
          {mensagem.ignoradas.length > 0 && (
            <details>
              <summary>Não alteradas ({mensagem.ignoradas.length})</summary>
              <ul className="lista-simples">
                {mensagem.ignoradas.map((i, n) => (
                  <li key={n}>
                    {i.conta}: {i.motivo}
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
