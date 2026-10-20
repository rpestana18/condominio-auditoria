import type { BudgetSummary, FiscalYear, SpendingProfile, TrackingGroup, TrackingMonth } from "../../api/types";
import { formatMonth } from "../../format";
import { FiscalYearSelector } from "../budgetVsActual/FiscalYearSelector";
import { spendingProfileLabel } from "../budgetVsActual/labels";
import type { TrackingFilter } from "./trackingFilter";

interface Props {
  fiscalYears: FiscalYear[];
  budgets: BudgetSummary[];
  budgetId: string | null;
  onChangeBudget: (budgetId: string) => void;
  groups: TrackingGroup[];
  months: TrackingMonth[];
  filter: TrackingFilter;
  onChange: (filter: TrackingFilter) => void;
}

const profiles = Object.keys(spendingProfileLabel) as SpendingProfile[];

/** Filters of RF-13.4: fiscal year, group, "só estouro no mês", "só saldo negativo" and profile. */
export function TrackingFilters({ fiscalYears, budgets, budgetId, onChangeBudget, groups, months, filter, onChange }: Props) {
  const withNumbers = months.filter((m) => m.status === "WITH_CASH_FLOW");
  const defaultMonth = withNumbers.at(-1)?.month ?? null;
  const change = (changes: Partial<TrackingFilter>) => onChange({ ...filter, ...changes });

  return (
    <div className="filters">
      <FiscalYearSelector fiscalYears={fiscalYears} budgets={budgets} budgetId={budgetId} onChange={onChangeBudget} />
      <label>
        Grupo
        <select value={filter.groupId ?? ""} onChange={(e) => change({ groupId: e.target.value || null })}>
          <option value="">Todos os grupos</option>
          {groups.map((g) => (
            <option key={g.lineId} value={g.lineId}>
              {g.code} {g.description}
            </option>
          ))}
        </select>
      </label>
      <label>
        Tipo da linha
        <select value={filter.profile ?? ""} onChange={(e) => change({ profile: (e.target.value || null) as SpendingProfile | null })}>
          <option value="">Todos os tipos</option>
          {profiles.map((p) => (
            <option key={p} value={p}>
              {spendingProfileLabel[p]}
            </option>
          ))}
        </select>
      </label>
      <div className="filter-group">
        <label className="inline-field">
          <input
            type="checkbox"
            checked={filter.overrunMonth !== null}
            disabled={!defaultMonth}
            onChange={(e) => change({ overrunMonth: e.target.checked ? defaultMonth : null })}
          />
          Só linhas com estouro no mês
        </label>
        {filter.overrunMonth !== null && (
          <select aria-label="Mês do estouro" value={filter.overrunMonth} onChange={(e) => change({ overrunMonth: e.target.value })}>
            {withNumbers.map((m) => (
              <option key={m.month} value={m.month}>
                {formatMonth(m.month)}
              </option>
            ))}
          </select>
        )}
      </div>
      <label className="inline-field">
        <input type="checkbox" checked={filter.negativeBalance} onChange={(e) => change({ negativeBalance: e.target.checked })} />
        Só linhas com saldo negativo no exercício
      </label>
    </div>
  );
}
