import type { AvisoPo, ConferenciaPo } from "../../api/tipos";

const simbolo: Record<ConferenciaPo["classificacao"], string> = {
  OK: "✔",
  ARREDONDAMENTO: "≈",
  DIVERGENCIA: "✖",
  CODIGO_REPETIDO: "!",
};

/** Conferência da leitura contra o próprio documento (RF-03.1.2) e avisos da PO. */
export function ConferenciasPo({ conferencias, avisos }: { conferencias: ConferenciaPo[]; avisos: AvisoPo[] }) {
  return (
    <section className="bloco">
      <h2>Conferência da leitura</h2>
      {avisos.map((a) => (
        <p key={a.codigo} className="aviso alerta">
          {a.texto}
        </p>
      ))}
      <ul className="conferencias">
        {conferencias.map((c, i) => (
          <li key={`${c.codigo}-${i}`} className={c.classificacao === "OK" || c.classificacao === "ARREDONDAMENTO" ? "ok" : "falha"}>
            <span title={c.classificacao}>{simbolo[c.classificacao]}</span>
            <div>
              <strong>{c.descricao}</strong>
              {c.detalhe && <small>{c.detalhe}</small>}
              {c.explicacao && <small>{c.explicacao}</small>}
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
