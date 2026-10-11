import { useState } from "react";
import { useBudgetItemBatch, useSuggestBudgetItems } from "../../api/budgetAnalysisQueries";
import type { BudgetLineItem } from "../../api/types";
import { useSession } from "../../context";

interface Props {
  budgetId: string;
  /** Lines of the list, to show the code instead of the id when the batch skips some. */
  lines: BudgetLineItem[];
  selected: string[];
  onBatchDone: () => void;
}

interface Message {
  text: string;
  /** Lines not changed, with the reason the backend sent. */
  unchanged: { line: string; reason: string }[];
}

/** Admin action bar: confirm or reject in batch and ask for suggestions (RF-11.7). */
export function BudgetItemActions({ budgetId, lines, selected, onBatchDone }: Props) {
  const { condominiumId } = useSession();
  const batch = useBudgetItemBatch(condominiumId, budgetId);
  const suggest = useSuggestBudgetItems(condominiumId, budgetId);
  const [message, setMessage] = useState<Message | null>(null);
  const busy = batch.isPending || suggest.isPending;
  const error = batch.error ?? suggest.error;
  const describe = (lineId: string) => {
    const line = lines.find((l) => l.lineId === lineId);
    return line ? `${line.code} ${line.description}` : lineId;
  };

  const runBatch = (action: "CONFIRM" | "REJECT") =>
    batch.mutate(
      { action, lines: selected },
      {
        onSuccess: (r) => {
          setMessage({
            text: `${r.changed} linha(s) ${action === "CONFIRM" ? "confirmadas" : "recusadas"}.`,
            unchanged: r.skipped.map((s) => ({ line: describe(s.lineId), reason: s.reason })),
          });
          onBatchDone();
        },
      },
    );

  const askSuggestions = () =>
    suggest.mutate(undefined, {
      onSuccess: (r) =>
        setMessage({
          text: r.firstBudget
            ? `Primeira PO do condomínio: ${r.createdItems} rubrica(s) criadas, já confirmadas.`
            : `${r.suggested} sugestão(ões): ${r.fromPreviousVersion} da versão anterior, ${r.byAccount} pela conta da PO e grupo. ${r.withoutSuggestion.length} sem sugestão.`,
          unchanged: r.withoutSuggestion.map((s) => ({ line: `${s.code} ${s.account}`, reason: s.reason })),
        }),
    });

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
        title="Mesma conta da PO e mesmo grupo, sem IA. Tudo entra como sugerido; nenhum número muda."
        onClick={askSuggestions}
      >
        Pedir sugestões
      </button>
      {error && <p className="notice error">{error.message}</p>}
      {message && (
        <div className="notice ok">
          {message.text}
          {message.unchanged.length > 0 && (
            <details>
              <summary>Sem alteração ({message.unchanged.length})</summary>
              <ul className="simple-list">
                {message.unchanged.map((u, i) => (
                  <li key={i}>
                    {u.line}: {u.reason}
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
