import { useState } from "react";
import { useConfirmAllSuggestions } from "../../api/budgetQueries";
import type { SkippedAccount, SuggestedBySource } from "../../api/types";
import { useSession } from "../../context";

interface Props {
  budgetId: string;
  /** How many accounts are "sugerido" now (summary.suggested). */
  suggested: number;
  bySource: SuggestedBySource;
  onDone: (text: string, skipped: SkippedAccount[]) => void;
}

/** "versão anterior: 12 · planilha: 30 · pelo nome: 7", only the sources that have suggestions. */
function sourcesText(bySource: SuggestedBySource): string {
  const parts: [string, number][] = [
    ["da versão anterior", bySource.previousVersion],
    ["da planilha", bySource.spreadsheet],
    ["pelo nome", bySource.name],
    ["escolhidas pelo Admin sem confirmar", bySource.admin],
  ];
  return parts
    .filter(([, n]) => n > 0)
    .map(([label, n]) => `${n} ${label}`)
    .join(" · ");
}

/**
 * "Confirmar todas as sugestões" (Q51), Admin only. Two steps: the first click shows how many suggestions there are and
 * where they came from; only the second one confirms. The count shown goes to the backend, which confirms nothing if it
 * changed in the meantime.
 */
export function ConfirmAllSuggestions({ budgetId, suggested, bySource, onDone }: Props) {
  const { condominiumId } = useSession();
  const confirmAll = useConfirmAllSuggestions(condominiumId, budgetId);
  const [asking, setAsking] = useState(false);

  if (suggested === 0 && !confirmAll.error) return null;

  return (
    <div className="confirm-all">
      {!asking ? (
        <button className="button" disabled={suggested === 0 || confirmAll.isPending} onClick={() => setAsking(true)}>
          Confirmar todas as sugestões ({suggested})
        </button>
      ) : (
        <div className="confirmation" role="group" aria-label="Confirmar todas as sugestões">
          <p>
            <strong>
              {suggested} {suggested === 1 ? "sugestão" : "sugestões"}: {sourcesText(bySource)}.
            </strong>
          </p>
          <p className="muted">
            Todas passam a "confirmado" e entram no previsto × realizado. Cada conta vai para a trilha com o seu nome e a data. Contas sem
            sugestão e recusadas não mudam. Confira a lista antes, se quiser: o filtro "Sugeridas" mostra só elas.
          </p>
          <div className="upload">
            <button
              className="button"
              disabled={confirmAll.isPending}
              onClick={() =>
                confirmAll.mutate(
                  { expected: suggested },
                  {
                    onSuccess: (r) => {
                      setAsking(false);
                      onDone(`${r.changed} sugestão(ões) confirmadas.`, r.skipped);
                    },
                    onError: () => setAsking(false),
                  },
                )
              }
            >
              Confirmar as {suggested}
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
