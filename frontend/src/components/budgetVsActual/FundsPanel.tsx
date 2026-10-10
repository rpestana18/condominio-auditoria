import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { BudgetVsActualFund } from "../../api/types";
import { differenceClass, formatDifference, formatMoney, formatMoneyShort, formatPercentage } from "../../format";
import type { OpenEvidence } from "./evidence";
import { fundStatusLabel } from "./labels";
import { SourcedValue } from "./SourcedValue";

interface Props {
  funds: BudgetVsActualFund[];
  onOpenEvidence: OpenEvidence;
}

/**
 * Reserve and works funds: collection × planned of the 1.9 lines (RF-03.1.9). The other funds
 * show as "sem previsto na PO", only with their movement, without a difference.
 */
export function FundsPanel({ funds, onOpenEvidence }: Props) {
  const compared = funds.filter((f) => f.status === "COMPARED");
  const others = funds.filter((f) => f.status !== "COMPARED");
  const name = (f: BudgetVsActualFund) => [f.lineCode, f.fund].filter(Boolean).join(" · ") || "—";

  return (
    <>
      <section className="block">
        <h2>Fundos com previsto na PO</h2>
        {compared.length === 0 ? (
          <p className="notice">Nenhum fundo ligado às linhas de fundos da PO.</p>
        ) : (
          <>
            <ResponsiveContainer width="100%" height={Math.max(160, compared.length * 60)}>
              <BarChart
                data={compared.map((f) => ({ name: name(f), planned: f.planned, collected: f.collected }))}
                layout="vertical"
                margin={{ left: 24, right: 24 }}
              >
                <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" tickFormatter={formatMoneyShort} />
                <YAxis type="category" dataKey="name" width={230} tick={{ fontSize: 12 }} />
                <Tooltip formatter={(value) => formatMoney(Number(value))} />
                <Legend />
                <Bar dataKey="planned" name="Previsto" fill="var(--color-planned)" isAnimationActive={false} />
                <Bar dataKey="collected" name="Arrecadado" fill="var(--color-inflow)" isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
            <table className="table">
              <thead>
                <tr>
                  <th>Linha · fundo do fluxo</th>
                  <th className="number">Previsto</th>
                  <th className="number">Arrecadado</th>
                  <th className="number">Diferença</th>
                  <th className="number">Execução</th>
                </tr>
              </thead>
              <tbody>
                {compared.map((f) => (
                  <tr key={f.lineId ?? f.fundId}>
                    <td>{name(f)}</td>
                    <td className="number">{formatMoney(f.planned ?? 0)}</td>
                    <td className="number">
                      {f.fundId && f.collected !== null && f.collected !== undefined ? (
                        <SourcedValue
                          value={f.collected}
                          onOpen={() => onOpenEvidence({ target: `fund:${f.fundId}`, title: `Arrecadação: ${f.fund}` })}
                        />
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className={`number ${differenceClass(f.difference ?? 0)}`}>
                      {f.difference === null || f.difference === undefined ? "—" : formatDifference(f.difference)}
                    </td>
                    <td className="number">{formatPercentage(f.execution)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </section>

      {others.length > 0 && (
        <section className="block">
          <h2>Demais fundos</h2>
          <table className="table">
            <thead>
              <tr>
                <th>Fundo</th>
                <th>Situação</th>
                <th className="number">Créditos</th>
                <th className="number">Débitos</th>
              </tr>
            </thead>
            <tbody>
              {others.map((f, i) => (
                <tr key={f.fundId ?? f.lineId ?? i}>
                  <td>{name(f)}</td>
                  <td className="muted">
                    {f.status === "LINE_WITHOUT_FUND" ? `Linha ${f.lineCode} sem fundo ligado` : fundStatusLabel[f.status]}
                  </td>
                  <td className="number">{f.credits === null || f.credits === undefined ? "—" : formatMoney(f.credits)}</td>
                  <td className="number">{f.debits === null || f.debits === undefined ? "—" : formatMoney(f.debits)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </>
  );
}
