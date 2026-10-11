import { openFile } from "../../api/client";
import { useEvidence, type Period } from "../../api/budgetQueries";
import type { BudgetVsActual } from "../../api/types";
import { useSession } from "../../context";
import { formatDate, formatMoney, shortHash } from "../../format";
import type { EvidenceTarget } from "./evidence";
import { UndoReallocation, ReallocateEntry } from "./Reallocation";

interface Props {
  period: Period;
  budget: NonNullable<BudgetVsActual["budget"]>;
  target: EvidenceTarget;
  onClose: () => void;
}

/**
 * Entries that make up a number (RF-03.1.12). Shows only facts: date, memo, payee, amount,
 * account, file, page and hash. No text about the cause of the difference.
 */
export function EvidencePanel({ period, budget, target, onClose }: Props) {
  const { condominiumId, can } = useSession();
  const canReallocate = can("MANAGER", "ADMIN");
  const { data: entries = [], isLoading, error } = useEvidence(condominiumId, period, budget.id, target.target);
  const openOnPage = (fileId: string, page: number) =>
    void openFile(`/condominiums/${condominiumId}/files/${fileId}/content`, page);

  return (
    <aside className="detail" aria-label="Evidência">
      <header>
        <h2>{target.title}</h2>
        <button className="link-button" onClick={onClose} aria-label="Fechar">
          ✕
        </button>
      </header>

      {target.budgetLine && (
        <section className="budget-line-source">
          <h3>Linha da PO</h3>
          {target.budgetLine.notes && <p>Observações da PO: “{target.budgetLine.notes}”</p>}
          <button className="link-button" onClick={() => openOnPage(budget.fileId, target.budgetLine!.page)}>
            Abrir a PO na página {target.budgetLine.page}
          </button>
          <p className="muted">
            {budget.fileName} · <span title={budget.sha256}>SHA-256 {shortHash(budget.sha256)}…</span>
          </p>
        </section>
      )}

      <h3>Lançamentos ({entries.length})</h3>
      {isLoading && <p className="notice">Carregando…</p>}
      {error && <p className="notice error">{error.message}</p>}
      {!isLoading && entries.length === 0 && <p className="notice">Nenhum lançamento neste número.</p>}
      <ul className="evidence-list">
        {entries.map((e) => (
          <li key={e.entryId}>
            <div className="evidence-header">
              <strong>{formatMoney(e.amount)}</strong>
              <span className="muted">{formatDate(e.date)}</span>
            </div>
            <span>{e.memo}</span>
            {e.supplier && <span className="muted">Favorecido: {e.supplier}</span>}
            <span className="muted">
              {e.account && `Conta ${e.account}${e.accountName ? ` ${e.accountName}` : ""}`}
              {e.document && ` · doc. ${e.document}`}
              {e.fund && ` · ${e.fund}`}
            </span>
            {e.reallocation && <span className="badge warning">{e.reallocation}</span>}
            {canReallocate && e.reallocationId && <UndoReallocation reallocationId={e.reallocationId} />}
            {canReallocate && target.target === "TO_REALLOCATE" && !e.reallocationId && <ReallocateEntry budgetId={budget.id} entry={e} />}
            <span className="muted">
              <button className="link-button" onClick={() => openOnPage(e.fileId, e.page)}>
                {e.fileName ?? "Fluxo"}, pág. {e.page}
              </button>{" "}
              · <span title={e.sha256}>SHA-256 {shortHash(e.sha256)}…</span>
            </span>
          </li>
        ))}
      </ul>
    </aside>
  );
}
