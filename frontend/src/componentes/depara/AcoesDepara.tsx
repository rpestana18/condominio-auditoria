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

  const executarLote = (acao: "CONFIRM" | "REJECT") =>
    lote.mutate(
      { action: acao, accounts: selecionadas },
      {
        onSuccess: (r) => {
          setMensagem({ texto: `${r.changed} conta(s) ${acao === "CONFIRM" ? "confirmadas" : "recusadas"}.`, ignoradas: r.skipped });
          aoConcluirLote();
        },
      },
    );

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
        title="Compara os nomes, sem IA. Tudo entra como sugerido; nenhum número muda."
        onClick={() =>
          sugerir.mutate(undefined, {
            onSuccess: (r) =>
              setMensagem({
                texto: `${r.created} sugestão(ões): ${r.fromPreviousVersion} da versão anterior, ${r.byName} pelo nome. ${r.withoutSuggestion.length} sem sugestão.`,
                ignoradas: r.withoutSuggestion.map((s) => ({ account: s.account, reason: s.reason })),
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
                  texto: `${r.accepted} linha(s) da planilha entraram como sugerido. ${r.rejected.length} recusada(s).`,
                  ignoradas: [
                    ...r.skipped,
                    ...r.rejected.map((x) => ({ account: `linha ${x.line}`, reason: `${x.reason}: ${x.content}` })),
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
                    {i.account}: {i.reason}
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
