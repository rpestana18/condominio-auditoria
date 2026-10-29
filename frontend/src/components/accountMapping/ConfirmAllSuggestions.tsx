import { useState } from "react";
import { useConfirmAllSuggestions } from "../../api/budgetQueries";
import type { AutomaticBySource, SkippedAccount } from "../../api/types";
import { useSession } from "../../context";

interface Props {
  budgetId: string;
  /** How many accounts are "automático" now (summary.automatic). */
  automatic: number;
  bySource: AutomaticBySource;
  onDone: (text: string, skipped: SkippedAccount[]) => void;
}

/** "12 da versão anterior · 30 da planilha guardada · 7 pelo nome", only the sources that have links. */
function sourcesText(bySource: AutomaticBySource): string {
  const parts: [string, number][] = [
    ["da versão anterior", bySource.previousVersion],
    ["da planilha guardada", bySource.spreadsheet],
    ["da planilha de apoio da PO", bySource.budgetSheet],
    ["pelo nome", bySource.name],
    ["escolhidas pelo Admin sem marcar como conferidas", bySource.admin],
  ];
  return parts
    .filter(([, n]) => n > 0)
    .map(([label, n]) => `${n} ${label}`)
    .join(" · ");
}

/**
 * "Marcar todas como conferidas", Admin only and optional since Q54: the automatic links already count. Two steps: the
 * first click shows how many links there are and where they came from; only the second one confirms. The count shown
 * goes to the backend, which confirms nothing if it changed in the meantime.
 */
export function ConfirmAllSuggestions({ budgetId, automatic, bySource, onDone }: Props) {
  const { condominiumId } = useSession();
  const confirmAll = useConfirmAllSuggestions(condominiumId, budgetId);
  const [asking, setAsking] = useState(false);

  if (automatic === 0 && !confirmAll.error) return null;

  return (
    <div className="confirm-all">
      {!asking ? (
        <button className="button secondary" disabled={automatic === 0 || confirmAll.isPending} onClick={() => setAsking(true)}>
          Marcar todas como conferidas ({automatic}, opcional)
        </button>
      ) : (
        <div className="confirmation" role="group" aria-label="Marcar todas como conferidas">
          <p>
            <strong>
              {automatic} {automatic === 1 ? "ligação automática" : "ligações automáticas"}: {sourcesText(bySource)}.
            </strong>
          </p>
          <p className="muted">
            Elas já valem no previsto × realizado; marcar só registra que você conferiu, e o sistema não as troca mais. Cada conta vai
            para a trilha com o seu nome e a data. Contas sem linha encontrada e recusadas não mudam.
          </p>
          <div className="upload">
            <button
              className="button"
              disabled={confirmAll.isPending}
              onClick={() =>
                confirmAll.mutate(
                  { expected: automatic },
                  {
                    onSuccess: (r) => {
                      setAsking(false);
                      onDone(`${r.changed} ligação(ões) marcadas como conferidas.`, r.skipped);
                    },
                    onError: () => setAsking(false),
                  },
                )
              }
            >
              Marcar as {automatic}
            </button>
            <button className="button secondary" disabled={confirmAll.isPending} onClick={() => setAsking(false)}>
              Cancelar
            </button>
          </div>
        </div>
      )}
      {confirmAll.error && <p className="notice error">{confirmAll.error.message}</p>}
    </div>
  );
}
