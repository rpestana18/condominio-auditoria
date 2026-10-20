import type { BudgetWarning, BudgetCheck } from "../../api/types";

const symbol: Record<BudgetCheck["classification"], string> = {
  OK: "✔",
  ROUNDING: "≈",
  DISCREPANCY: "✖",
  REPEATED_CODE: "i",
};

/**
 * Check of the reading against the document itself (RF-03.1.2) and the budget warnings. A repeated item is only
 * informative and never blocks the confirmation (RF-13.2).
 */
export function BudgetChecks({ checks, warnings }: { checks: BudgetCheck[]; warnings: BudgetWarning[] }) {
  return (
    <section className="block">
      <h2>Conferência da leitura</h2>
      {warnings.map((w, i) => (
        <p key={`${w.code}-${i}`} className="notice warning">
          {w.text}
        </p>
      ))}
      <ul className="checks">
        {checks.map((c, i) => (
          <li key={`${c.code}-${i}`} className={c.classification === "DISCREPANCY" ? "fail" : c.classification === "REPEATED_CODE" ? "info" : "ok"}>
            <span title={c.classification}>{symbol[c.classification]}</span>
            <div>
              <strong>{c.description}</strong>
              {c.detail && <small>{c.detail}</small>}
              {c.explanation && <small>{c.explanation}</small>}
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
