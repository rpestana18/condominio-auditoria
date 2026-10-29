import { Link } from "react-router";
import { accountsText, hasPendingSteps, mappingLink, reviewLink, type PendingSteps } from "./pendingSteps";

interface Props {
  steps: PendingSteps;
  /** Other short notes of the screen (warnings of the API), shown in the same box, once each. */
  notes?: string[];
  /** Smaller version, for the home screen card. */
  compact?: boolean;
}

/**
 * The one place for what to know before reading the numbers: only the exceptions (Q54), with a link to where they are
 * solved, and the other notes of the screen. No mandatory review step. Shows nothing when there is no exception and no
 * note.
 */
export function BeforeReadingBox({ steps, notes = [], compact = false }: Props) {
  const pending = hasPendingSteps(steps);
  if (!pending && notes.length === 0) return null;
  return (
    <section className={`before-reading${compact ? " compact" : ""}`} aria-label="Antes de ler estes números">
      {pending && (
        <>
          <strong className="before-reading-title">Antes de ler estes números</strong>
          <ol>
            {steps.linesToReview && (
              <li>
                <Link to={reviewLink(steps.linesToReview.budgetId)}>
                  Conferir as linhas da PO ({steps.linesToReview.reviewedLines} de {steps.linesToReview.totalLines} conferidas)
                </Link>
                {!compact && <span className="muted"> · até lá, os números por linha da PO não aparecem</span>}
              </li>
            )}
            {steps.unmappedAccounts > 0 && (
              <li>
                <Link to={mappingLink(steps.budgetId)}>
                  {accountsText(steps.unmappedAccounts)} do extrato sem linha encontrada na PO
                </Link>
                {!compact && (
                  <span className="muted">
                    {steps.provisional
                      ? " · ficam em “sem linha da PO”; “Economia nas linhas” e “Fora da PO” são provisórios até o Admin escolher o destino"
                      : " · ficam em “sem linha da PO” até o Admin escolher o destino"}
                  </span>
                )}
              </li>
            )}
          </ol>
        </>
      )}
      {notes.length > 0 && (
        <ul className="before-reading-notes">
          {notes.map((n) => (
            <li key={n}>{n}</li>
          ))}
        </ul>
      )}
    </section>
  );
}
