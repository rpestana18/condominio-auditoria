import type { FiscalYear, Fund } from "../../api/types";
import { formatMonth } from "../../format";
import { FundSelector } from "../budgetVsActual/FundSelector";

interface Props {
  /** List from GET /fiscal-years, from the most recent to the oldest, including the printed column. */
  fiscalYears: FiscalYear[];
  /** Checked ids: the ones the person chose or, with no choice, the ones the backend used (the two most recent). */
  checked: string[];
  /** Receives the new list in the order of the fiscal year list. Never fewer than two. */
  onChangeFiscalYears: (ids: string[]) => void;
  /** There is a choice of the person in the address (shows "back to default"). */
  ownChoice: boolean;
  onResetToDefault: () => void;
  funds: Fund[];
  fundId: string | null;
  onChangeFund: (fundId: string | null) => void;
  sameMonths: boolean;
  onChangeSameMonths: (checked: boolean) => void;
  /** Ready-made text from the API (e.g. "comparando: setembro"), only with "mesmos meses". */
  comparing: string | null | undefined;
}

/** Filters of RF-11.6: fiscal years (two or more), fund and "mesmos meses". */
export function ComparisonFilters(props: Props) {
  const { fiscalYears, checked, onChangeFiscalYears, ownChoice, onResetToDefault } = props;
  const checkedSet = new Set(checked);

  const toggle = (id: string) => {
    const next = fiscalYears.filter((f) => (f.id === id ? !checkedSet.has(id) : checkedSet.has(f.id))).map((f) => f.id);
    if (next.length >= 2) onChangeFiscalYears(next);
  };

  return (
    <div className="filters">
      <fieldset className="fiscal-year-filter">
        <legend>Exercícios (dois ou mais)</legend>
        <div className="option-list">
          {fiscalYears.map((f) => {
            const isChecked = checkedSet.has(f.id);
            return (
              <label key={f.id}>
                <input
                  type="checkbox"
                  checked={isChecked}
                  // With only two checked, none can leave: the comparison needs two
                  disabled={isChecked && checked.length <= 2}
                  onChange={() => toggle(f.id)}
                />
                {f.label}
                <span className="muted">
                  {formatMonth(f.start)} a {formatMonth(f.end)}
                  {f.type === "PRINTED_COLUMN" && " · só previsto"}
                </span>
              </label>
            );
          })}
        </div>
        {ownChoice && (
          <button type="button" className="link-button" onClick={onResetToDefault}>
            Voltar aos dois mais recentes
          </button>
        )}
      </fieldset>
      <FundSelector funds={props.funds} fundId={props.fundId} onChange={props.onChangeFund} />
      <div className="same-months">
        <label className="inline-field">
          <input type="checkbox" checked={props.sameMonths} onChange={(e) => props.onChangeSameMonths(e.target.checked)} />
          Mesmos meses (só os meses com fluxo em todos os exercícios)
        </label>
        {props.sameMonths && props.comparing && <span className="badge neutral">{props.comparing}</span>}
      </div>
    </div>
  );
}
