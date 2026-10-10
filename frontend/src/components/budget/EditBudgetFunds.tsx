import { useState } from "react";
import { ApiError } from "../../api/client";
import { useChangeBudgetFunds, useFunds } from "../../api/budgetQueries";
import type { BudgetDetail } from "../../api/types";
import { useSession } from "../../context";
import { formatMoney } from "../../format";

/**
 * Admin changes, after confirmation, which cash flow fund serves each 1.9.x line (RF-03.1.9).
 * Sends the full link list; the budget trail records the previous and the new fund. The backend refuses
 * a repeated fund and the operating fund.
 */
export function EditBudgetFunds({ detail }: { detail: BudgetDetail }) {
  const { condominiumId } = useSession();
  const { data: funds = [] } = useFunds(condominiumId);
  const change = useChangeBudgetFunds(condominiumId, detail.budget.id);
  const fundLines = detail.lines.filter((l) => l.fundLine && l.type === "LINE");
  const current = Object.fromEntries(detail.funds.map((f) => [f.lineId, f.fundId]));
  const [chosen, setChosen] = useState<Record<string, string>>(current);
  const changed = fundLines.some((l) => (chosen[l.id] ?? "") !== (current[l.id] ?? ""));
  const reasons = change.error instanceof ApiError ? (change.error.problem?.reasons ?? []) : [];

  return (
    <section className="block form">
      <h2>Fundos ligados às linhas de fundos</h2>
      <p className="muted">Nomes como impressos no fluxo. Cada fundo atende no máximo uma linha.</p>
      <fieldset>
        {fundLines.map((l) => (
          <label key={l.id} className="field">
            {l.effectiveCode} {l.description} · {formatMoney(l.budgeted)}/mês
            <select value={chosen[l.id] ?? ""} onChange={(e) => setChosen((c) => ({ ...c, [l.id]: e.target.value }))}>
              <option value="">Sem fundo ligado</option>
              {funds
                .filter((f) => !f.operating)
                .map((f) => (
                  <option key={f.id} value={f.id}>
                    {f.name}
                  </option>
                ))}
            </select>
          </label>
        ))}
      </fieldset>
      {change.isError && (
        <div className="notice error">
          {change.error.message}
          {reasons.length > 0 && (
            <ul>
              {reasons.map((r) => (
                <li key={r}>{r}</li>
              ))}
            </ul>
          )}
        </div>
      )}
      {change.isSuccess && !changed && <p className="notice ok">Ligação gravada.</p>}
      <button
        className="button"
        disabled={!changed || change.isPending}
        onClick={() => change.mutate(fundLines.map((l) => ({ lineId: l.id, fundId: chosen[l.id] || null })))}
      >
        {change.isPending ? "Salvando…" : "Salvar ligação dos fundos"}
      </button>
    </section>
  );
}
