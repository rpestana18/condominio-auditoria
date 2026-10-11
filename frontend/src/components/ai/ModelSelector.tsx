import type { AiModel, AiProvider } from "../../api/types";

/** "2.00" becomes "2,00": only swaps the separator of the exact decimal text that came from the API (no arithmetic). */
const price = (text: string) => text.replace(".", ",");

function modelLabel(m: AiModel): string {
  const prices = `US$ ${price(m.inputPricePerMillionUsd)} entrada / US$ ${price(m.outputPricePerMillionUsd)} saída por milhão de tokens`;
  return `${m.name}${m.isDefault ? " (padrão)" : ""} · ${prices}`;
}

interface Props {
  id: string;
  providers: AiProvider[];
  provider: string;
  model: string;
  onChange: (provider: string, model: string) => void;
  /** Hides prices (local embeddings cost zero). */
  hidePrices?: boolean;
}

/** Provider and model from the catalog. Empty model = the provider's default (the backend picks it). */
export function ModelSelector({ id, providers, provider, model, onChange, hidePrices = false }: Props) {
  const chosen = providers.find((p) => p.code === provider);
  return (
    <div className="filters">
      <label className="field">
        Provedor
        <select id={`${id}-provider`} value={provider} onChange={(e) => onChange(e.target.value, "")}>
          {!chosen && <option value="">Escolha…</option>}
          {providers.map((p) => (
            <option key={p.code} value={p.code}>
              {p.name}
            </option>
          ))}
        </select>
      </label>
      <label className="field">
        Modelo
        <select id={`${id}-model`} value={model} onChange={(e) => onChange(provider, e.target.value)} disabled={!chosen}>
          <option value="">Padrão do provedor</option>
          {chosen?.models.map((m) => (
            <option key={m.id} value={m.id}>
              {hidePrices ? `${m.name}${m.isDefault ? " (padrão)" : ""}` : modelLabel(m)}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
