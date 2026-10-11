import type { AiMode } from "../../api/types";

/** Modes the Admin can choose in this phase. LOCAL for answers is planned, but still without a provider (the backend refuses). */
export const modeDescriptions: Record<Exclude<AiMode, "LOCAL">, { label: string; description: string }> = {
  EXTERNAL_MCP: {
    label: "Claude do usuário (MCP)",
    description: "Cada pessoa usa o próprio Claude conectado ao MCP do sistema. O sistema não chama IA. Padrão do piloto.",
  },
  API_KEY: {
    label: "Chave do condomínio",
    description: "O sistema chama o provedor de IA com a chave cadastrada abaixo. O uso fica registrado.",
  },
  OFF: {
    label: "Desligado",
    description: "Nenhuma IA de respostas; só regras, cálculos e a busca por palavra.",
  },
};

type Option<T extends string> = { value: T; label: string; description: string };

interface Props<T extends string> {
  name: string;
  legend: string;
  options: Option<T>[];
  value: T;
  onChange: (value: T) => void;
}

/** Group of option buttons (radio) with a short description of each choice. */
export function ModeChoice<T extends string>({ name, legend, options, value, onChange }: Props<T>) {
  return (
    <fieldset className="mode-choice">
      <legend>{legend}</legend>
      {options.map((o) => (
        <label key={o.value}>
          <input type="radio" name={name} value={o.value} checked={value === o.value} onChange={() => onChange(o.value)} />
          <span>
            <strong>{o.label}</strong>
            <small className="muted block-text">{o.description}</small>
          </span>
        </label>
      ))}
    </fieldset>
  );
}

/** The three general options, in screen order. */
export const modeOptions = (Object.keys(modeDescriptions) as Exclude<AiMode, "LOCAL">[]).map((value) => ({
  value,
  ...modeDescriptions[value],
}));
