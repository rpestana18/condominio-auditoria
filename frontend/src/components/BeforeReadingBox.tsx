import { Link } from "react-router";
import { accountsText, hasPendingSteps, mappingLink, profilesLink, type PendingSteps } from "./pendingSteps";

interface Props {
  steps: PendingSteps;
  /** Other short notes of the screen (warnings of the API), shown in the same box, once each. */
  notes?: string[];
  /** Smaller version, for the home screen card. */
  compact?: boolean;
}

/**
 * The one place for what to know before reading the numbers: the pending steps, with links to where they are done,
 * and the other notes of the screen. Shows nothing when there is nothing pending and no note.
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
            {steps.unmappedAccounts > 0 && (
              <li>
                <Link to={mappingLink(steps.budgetId)}>
                  Confirmar o de-para ({accountsText(steps.unmappedAccounts)} {steps.unmappedAccounts === 1 ? "pendente" : "pendentes"})
                </Link>
                {!compact && (
                  <span className="muted">
                    {steps.provisional
                      ? " · até lá, “Economia nas linhas” e “Fora da PO” são provisórios"
                      : " · até lá, essas despesas ficam fora das linhas da PO"}
                  </span>
                )}
              </li>
            )}
            {steps.profilesNotConfirmed && (
              <li>
                <Link to={profilesLink(steps.budgetId)}>Confirmar os tipos das linhas</Link>
                {!compact && <span className="muted"> · até lá, as linhas valem “mensal”</span>}
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
