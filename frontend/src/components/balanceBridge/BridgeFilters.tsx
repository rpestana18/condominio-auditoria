import type { BridgeFilter } from "../../api/budgetTrackingQueries";
import type { Fund } from "../../api/types";

interface Props {
  funds: Fund[];
  filter: BridgeFilter;
  onChange: (filter: BridgeFilter) => void;
}

/**
 * Fund and period of the bridge (RF-13.6). Default: the operating fund (empty value, the API picks it) and the fiscal
 * year up to the last month loaded (empty months). "Todos os fundos" goes to the API as fund=all.
 */
export function BridgeFilters({ funds, filter, onChange }: Props) {
  const others = funds.filter((f) => !f.operating);
  const operating = funds.find((f) => f.operating);
  const change = (changes: Partial<BridgeFilter>) => onChange({ ...filter, ...changes });
  return (
    <div className="filters">
      <label>
        Fundo
        <select value={filter.fund ?? ""} onChange={(e) => change({ fund: e.target.value || null })}>
          <option value="">{operating ? `Fundo ordinário (${operating.name})` : "Fundo ordinário"}</option>
          <option value="all">Todos os fundos</option>
          {others.map((f) => (
            <option key={f.id} value={f.id}>
              {f.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        De
        <input type="month" value={filter.from ?? ""} onChange={(e) => change({ from: e.target.value || null })} />
      </label>
      <label>
        Até
        <input type="month" value={filter.to ?? ""} onChange={(e) => change({ to: e.target.value || null })} />
      </label>
      {(filter.from || filter.to) && (
        <button className="link-button" onClick={() => change({ from: null, to: null })}>
          Voltar ao exercício
        </button>
      )}
    </div>
  );
}
