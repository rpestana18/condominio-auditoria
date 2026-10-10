import { useState } from "react";
import { useAccountMappingBatch, useAccountMappingSheet, useSuggestAccountMappings } from "../../api/budgetQueries";
import type { SkippedAccount } from "../../api/types";
import { useSession } from "../../context";

interface Props {
  budgetId: string;
  selected: string[];
  onBatchDone: () => void;
}

/** Admin action bar: confirm or reject in batch, generate suggestions and load the spreadsheet (RF-03.1.5). */
export function AccountMappingActions({ budgetId, selected, onBatchDone }: Props) {
  const { condominiumId } = useSession();
  const batch = useAccountMappingBatch(condominiumId, budgetId);
  const suggest = useSuggestAccountMappings(condominiumId, budgetId);
  const sheet = useAccountMappingSheet(condominiumId, budgetId);
  const [message, setMessage] = useState<{ text: string; skipped: SkippedAccount[] } | null>(null);
  const busy = batch.isPending || suggest.isPending || sheet.isPending;
  const error = batch.error ?? suggest.error ?? sheet.error;

  const runBatch = (action: "CONFIRM" | "REJECT") =>
    batch.mutate(
      { action, accounts: selected },
      {
        onSuccess: (r) => {
          setMessage({ text: `${r.changed} conta(s) ${action === "CONFIRM" ? "confirmadas" : "recusadas"}.`, skipped: r.skipped });
          onBatchDone();
        },
      },
    );

  return (
    <div className="upload">
      <button className="button" disabled={busy || selected.length === 0} onClick={() => runBatch("CONFIRM")}>
        Confirmar selecionadas ({selected.length})
      </button>
      <button className="button secondary" disabled={busy || selected.length === 0} onClick={() => runBatch("REJECT")}>
        Recusar selecionadas
      </button>
      <button
        className="button secondary"
        disabled={busy}
        title="Compara os nomes, sem IA. Tudo entra como sugerido; nenhum número muda."
        onClick={() =>
          suggest.mutate(undefined, {
            onSuccess: (r) =>
              setMessage({
                text: `${r.created} sugestão(ões): ${r.fromPreviousVersion} da versão anterior, ${r.byName} pelo nome. ${r.withoutSuggestion.length} sem sugestão.`,
                skipped: r.withoutSuggestion.map((s) => ({ account: s.account, reason: s.reason })),
              }),
          })
        }
      >
        Gerar sugestões pelo nome
      </button>
      <label className="button secondary file-picker">
        Carregar planilha (CSV)
        <input
          type="file"
          accept=".csv,text/csv"
          hidden
          disabled={busy}
          onChange={(e) => {
            const file = e.target.files?.[0];
            e.target.value = "";
            if (!file) return;
            sheet.mutate(file, {
              onSuccess: (r) =>
                setMessage({
                  text: `${r.accepted} linha(s) da planilha entraram como sugerido. ${r.rejected.length} recusada(s).`,
                  skipped: [
                    ...r.skipped,
                    ...r.rejected.map((x) => ({ account: `linha ${x.line}`, reason: `${x.reason}: ${x.content}` })),
                  ],
                }),
            });
          }}
        />
      </label>
      {error && <p className="notice error">{error.message}</p>}
      {message && (
        <div className="notice ok">
          {message.text}
          {message.skipped.length > 0 && (
            <details>
              <summary>Não alteradas ({message.skipped.length})</summary>
              <ul className="simple-list">
                {message.skipped.map((s, n) => (
                  <li key={n}>
                    {s.account}: {s.reason}
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
