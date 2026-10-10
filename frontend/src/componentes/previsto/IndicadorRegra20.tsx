import type { Regra20 } from "../../api/tipos";
import { formatarMoeda, formatarPercentual } from "../../formato";

/**
 * Regra dos 20% da Conv. 16.2 (RF-03.1.11). Mostra o excesso, o percentual, o limite e as linhas
 * que compõem o excesso. "A realocar" e "sem linha da PO" ficam à parte, no cenário máximo.
 */
export function IndicadorRegra20({ regra }: { regra: Regra20 }) {
  // Largura da barra: o percentual do excesso em relação ao limite (só desenho, sem passar de 100%)
  const largura = Math.min(100, (regra.percentage / regra.limitPercentage) * 100);
  return (
    <section className="bloco">
      <header className="titulo-bloco">
        <h2>
          Regra dos {regra.limitPercentage}% (Conv. 16.2)
        </h2>
        <span className={regra.aboveLimit ? "selo critico" : "selo ok"}>
          {regra.aboveLimit ? "Acima do limite: verificar ata de AGE" : "Dentro do limite"}
        </span>
        {regra.provisional && <span className="selo alerta">Provisório</span>}
      </header>
      <div className="barra-limite" aria-hidden>
        <div className={regra.aboveLimit ? "preenchido critico" : "preenchido"} style={{ width: `${largura}%` }} />
      </div>
      <dl className="lista-numeros">
        <dt>Excesso do mês</dt>
        <dd>
          {formatarMoeda(regra.overrun)} ({formatarPercentual(regra.percentage)} do previsto do mês)
        </dd>
        <dt>Limite</dt>
        <dd>
          {formatarMoeda(regra.limit)} ({regra.limitPercentage}% de {formatarMoeda(regra.monthlyPlanned)})
        </dd>
        <dt>Linhas acima do previsto</dt>
        <dd>{regra.linesAbove}</dd>
        {(regra.toReallocate > 0 || regra.withoutBudgetLine > 0) && (
          <>
            <dt>Fora do excesso</dt>
            <dd>
              a realocar {formatarMoeda(regra.toReallocate)} · sem linha da PO {formatarMoeda(regra.withoutBudgetLine)}
            </dd>
            <dt>Cenário máximo</dt>
            <dd>
              {formatarMoeda(regra.maxScenario)} ({formatarPercentual(regra.maxScenarioPercentage)})
            </dd>
          </>
        )}
      </dl>
      {regra.lines.length > 0 && (
        <details>
          <summary>Linhas que compõem o excesso ({regra.lines.length})</summary>
          <table className="tabela compacta">
            <tbody>
              {regra.lines.map((l) => (
                <tr key={l.lineId}>
                  <td>{l.code}</td>
                  <td>{l.description}</td>
                  <td className="numero">{formatarMoeda(l.overrun)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      )}
    </section>
  );
}
