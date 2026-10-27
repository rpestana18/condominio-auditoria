import { useState } from "react";
import { useCorrectFiscalYearEnd } from "../../api/cashFlowQueries";
import type { BudgetDetail } from "../../api/types";
import { useSession } from "../../context";
import { formatMonth } from "../../format";

/**
 * "Corrigir o fim do exercício" (RF-13.17; ADR 0009, Decision 6), Admin only, for a confirmed budget, with a
 * justification that goes to the trail. The API validates (start, other budgets) and recalculates nothing stored.
 */
export function FiscalYearEndCorrection({ detail }: { detail: BudgetDetail }) {
  const { condominiumId } = useSession();
  const correct = useCorrectFiscalYearEnd(condominiumId, detail.budget.id);
  const [open, setOpen] = useState(false);
  const [end, setEnd] = useState(detail.suggestedFiscalYearEnd ?? detail.budget.fiscalYearEnd ?? "");
  const [justification, setJustification] = useState("");
  const current = detail.budget.fiscalYearEnd;
  const suggestionDiffers = !!detail.suggestedFiscalYearEnd && detail.suggestedFiscalYearEnd !== current;

  if (!open) {
    return (
      <p className="muted">
        {suggestionDiffers && detail.suggestedFiscalYearEnd && (
          <>Sugestão de fim do exercício: {formatMonth(detail.suggestedFiscalYearEnd)} (março a março, D2). </>
        )}
        <button className="link-button" onClick={() => setOpen(true)}>
          Corrigir o fim do exercício
        </button>
      </p>
    );
  }
  return (
    <form
      className="form"
      onSubmit={(e) => {
        e.preventDefault();
        correct.mutate({ fiscalYearEnd: end, justification }, { onSuccess: () => setOpen(false) });
      }}
    >
      <div className="filters">
        <label className="field">
          Novo mês final
          <input type="month" required value={end} onChange={(e) => setEnd(e.target.value)} />
        </label>
        <label className="field">
          Justificativa (vai para a trilha)
          <input required value={justification} onChange={(e) => setJustification(e.target.value)} />
        </label>
      </div>
      <button className="button" disabled={correct.isPending}>
        Corrigir {current ? `(hoje: ${formatMonth(current)})` : ""}
      </button>{" "}
      <button type="button" className="link-button" onClick={() => setOpen(false)}>
        Cancelar
      </button>
      {correct.error && <p className="notice error">{correct.error.message}</p>}
    </form>
  );
}
