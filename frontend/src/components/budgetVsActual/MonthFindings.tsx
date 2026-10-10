import { openFile } from "../../api/client";
import { useFindings } from "../../api/budgetQueries";
import type { Finding } from "../../api/types";
import { useSession } from "../../context";
import { formatDateTime, formatMonth } from "../../format";
import { findingStatusLabel, severityLabel } from "./labels";

const severityClass: Record<Finding["severity"], string> = { INFO: "neutral", WARNING: "warning", CRITICAL: "critical" };

/**
 * Findings of the month (read only in this delivery). Each one brings the original evidence, which opens the document
 * on the page, and the status history. Marking as justified or resolved is left for the findings screen.
 */
export function MonthFindings({ referenceMonth }: { referenceMonth: string }) {
  const { condominiumId } = useSession();
  const { data: findings = [] } = useFindings(condominiumId, referenceMonth);
  if (findings.length === 0) return null;

  return (
    <section className="block">
      <h2>Achados de {formatMonth(referenceMonth)}</h2>
      <ul className="finding-list">
        {findings.map((f) => (
          <li key={f.id}>
            <div className="evidence-header">
              <span>
                <span className={`badge ${severityClass[f.severity]}`}>{severityLabel[f.severity]}</span>{" "}
                <span className="badge neutral">{findingStatusLabel[f.status]}</span>
              </span>
              <span className="muted">criado em {formatDateTime(f.createdAt)}</span>
            </div>
            <span>{f.description}</span>
            {f.statusReason && <span className="muted">{f.statusReason}</span>}
            <span className="muted">
              Evidência:{" "}
              {f.evidence.map((e, i) => (
                <span key={e.position}>
                  {i > 0 && " · "}
                  <button
                    className="link-button"
                    title={`SHA-256 ${e.sha256}`}
                    onClick={() =>
                      void openFile(`/condominiums/${condominiumId}/files/${e.fileId}/content`, e.page ?? undefined)
                    }
                  >
                    {e.reference}
                    {e.page ? `, pág. ${e.page}` : ""}
                  </button>
                </span>
              ))}
            </span>
            {f.history.length > 1 && (
              <details>
                <summary className="muted">Histórico ({f.history.length})</summary>
                <ul className="simple-list">
                  {f.history.map((h, i) => (
                    <li key={i}>
                      {formatDateTime(h.occurredAt)} · {h.username}: {h.previousStatus ? `${findingStatusLabel[h.previousStatus]} → ` : ""}
                      {findingStatusLabel[h.newStatus]} ({h.reason})
                    </li>
                  ))}
                </ul>
              </details>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
