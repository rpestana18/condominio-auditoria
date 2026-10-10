import type { Fund } from "../../api/types";

interface Props {
  /** Cash flow funds (GET /funds). The filter goes to the API; the screen does not split anything by itself. */
  funds: Fund[];
  fundId: string | null;
  onChange: (fundId: string | null) => void;
}

/** Fund filter used in budget vs actual and in "Comparar exercícios". */
export function FundSelector({ funds, fundId, onChange }: Props) {
  // The operating fund (Condomínio fund) comes first; the others, in the API's name order
  const sorted = [...funds.filter((f) => f.operating), ...funds.filter((f) => !f.operating)];
  return (
    <label>
      Fundo
      <select value={fundId ?? ""} onChange={(e) => onChange(e.target.value || null)}>
        <option value="">Todos os fundos</option>
        {sorted.map((f) => (
          <option key={f.id} value={f.id}>
            {f.operating ? `Fundo Condomínio (${f.name})` : f.name}
          </option>
        ))}
      </select>
    </label>
  );
}
