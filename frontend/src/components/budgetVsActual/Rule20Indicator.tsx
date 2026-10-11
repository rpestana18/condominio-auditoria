import type { Rule20 } from "../../api/types";
import { formatMoney, formatPercentage } from "../../format";

/**
 * 20% rule of Conv. 16.2 (RF-03.1.11). Shows the overrun, the percentage, the limit and the lines
 * that make up the overrun. "A realocar" and "sem linha da PO" stay apart, in the maximum scenario.
 */
export function Rule20Indicator({ rule }: { rule: Rule20 }) {
  // Bar width: the overrun percentage relative to the limit (drawing only, never beyond 100%)
  const width = Math.min(100, (rule.percentage / rule.limitPercentage) * 100);
  return (
    <section className="block">
      <header className="block-title">
        <h2>
          Regra dos {rule.limitPercentage}% (Conv. 16.2)
        </h2>
        <span className={rule.aboveLimit ? "badge critical" : "badge ok"}>
          {rule.aboveLimit ? "Acima do limite: verificar ata de AGE" : "Dentro do limite"}
        </span>
        {rule.provisional && <span className="badge warning">Provisório</span>}
      </header>
      <div className="limit-bar" aria-hidden>
        <div className={rule.aboveLimit ? "filled critical" : "filled"} style={{ width: `${width}%` }} />
      </div>
      <dl className="number-list">
        <dt>Excesso do mês</dt>
        <dd>
          {formatMoney(rule.overrun)} ({formatPercentage(rule.percentage)} do previsto do mês)
        </dd>
        <dt>Limite</dt>
        <dd>
          {formatMoney(rule.limit)} ({rule.limitPercentage}% de {formatMoney(rule.monthlyPlanned)})
        </dd>
        <dt>Linhas acima do previsto</dt>
        <dd>{rule.linesAbove}</dd>
        {(rule.toReallocate > 0 || rule.withoutBudgetLine > 0) && (
          <>
            <dt>Fora do excesso</dt>
            <dd>
              a realocar {formatMoney(rule.toReallocate)} · sem linha da PO {formatMoney(rule.withoutBudgetLine)}
            </dd>
            <dt>Cenário máximo</dt>
            <dd>
              {formatMoney(rule.maxScenario)} ({formatPercentage(rule.maxScenarioPercentage)})
            </dd>
          </>
        )}
      </dl>
      {rule.lines.length > 0 && (
        <details>
          <summary>Linhas que compõem o excesso ({rule.lines.length})</summary>
          <table className="table compact">
            <tbody>
              {rule.lines.map((l) => (
                <tr key={l.lineId}>
                  <td>{l.code}</td>
                  <td>{l.description}</td>
                  <td className="number">{formatMoney(l.overrun)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      )}
    </section>
  );
}
