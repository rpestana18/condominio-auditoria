import type { AvisoPo, ConferenciaPo } from "../../api/tipos";

const simbolo: Record<ConferenciaPo["classification"], string> = {
  OK: "✔",
  ROUNDING: "≈",
  DISCREPANCY: "✖",
  REPEATED_CODE: "!",
};

/** Conferência da leitura contra o próprio documento (RF-03.1.2) e avisos da PO. */
export function ConferenciasPo({ conferencias, avisos }: { conferencias: ConferenciaPo[]; avisos: AvisoPo[] }) {
  return (
    <section className="bloco">
      <h2>Conferência da leitura</h2>
      {avisos.map((a, i) => (
        <p key={`${a.code}-${i}`} className="aviso alerta">
          {a.text}
        </p>
      ))}
      <ul className="conferencias">
        {conferencias.map((c, i) => (
          <li key={`${c.code}-${i}`} className={c.classification === "OK" || c.classification === "ROUNDING" ? "ok" : "falha"}>
            <span title={c.classification}>{simbolo[c.classification]}</span>
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
