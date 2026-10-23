import { useState } from "react";
import { useAccountMappingBatch, useAccountMappingSheet } from "../../api/budgetQueries";
import type { AccountMappingList, SkippedAccount } from "../../api/types";
import { useSession } from "../../context";
import { formatDateTime } from "../../format";
import { ConfirmAllSuggestions } from "./ConfirmAllSuggestions";

interface Props {
  budgetId: string;
  list: AccountMappingList | null | undefined;
  selected: string[];
  onBatchDone: () => void;
}

/**
 * Admin action bar (RF-03.1.5, Q51): the suggestions already come ready from the backend (no "generate" button);
 * here the Admin confirms all of them at once, confirms or rejects the selected ones and loads the spreadsheet.
 */
export function AccountMappingActions({ budgetId, list, selected, onBatchDone }: Props) {
  const { condominiumId } = useSession();
  const batch = useAccountMappingBatch(condominiumId, budgetId);
  const sheet = useAccountMappingSheet(condominiumId, budgetId);
  const [message, setMessage] = useState<{ text: string; skipped: SkippedAccount[] } | null>(null);
  const busy = batch.isPending || sheet.isPending;
  const error = batch.error ?? sheet.error;

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
    <>
      {list && (
        <ConfirmAllSuggestions
          budgetId={budgetId}
          suggested={list.summary.suggested}
          bySource={list.suggestedBySource}
          onDone={(text, skipped) => {
            setMessage({ text, skipped });
            onBatchDone();
          }}
        />
      )}
      <div className="upload">
        <button className="button secondary" disabled={busy || selected.length === 0} onClick={() => runBatch("CONFIRM")}>
          Confirmar selecionadas ({selected.length})
        </button>
        <button className="button secondary" disabled={busy || selected.length === 0} onClick={() => runBatch("REJECT")}>
          Recusar selecionadas
        </button>
        <label
          className="button secondary file-picker"
          title="A última planilha carregada fica guardada e vale também para contas novas e para as próximas versões da PO, sempre como sugerido."
        >
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
                    text: `${r.accepted} linha(s) da planilha entraram como sugerido. ${r.rejected.length} recusada(s). A planilha fica guardada para as contas novas e as próximas versões da PO.`,
                    skipped: [
                      ...r.skipped,
                      ...r.rejected.map((x) => ({ account: `linha ${x.line}`, reason: `${x.reason}: ${x.content}` })),
                    ],
                  }),
              });
            }}
          />
        </label>
        {list?.sheet && (
          <small className="muted">
            Planilha guardada: {list.sheet.fileName ?? "sem nome"} · {list.sheet.lines} linha(s) · carregada por {list.sheet.loadedBy} em{" "}
            {formatDateTime(list.sheet.loadedAt)}
          </small>
        )}
      </div>
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
    </>
  );
}
