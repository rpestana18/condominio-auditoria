import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { FundoPrevistoRealizado } from "../../api/tipos";
import { classeDiferenca, formatarDiferenca, formatarMoeda, formatarMoedaCurta, formatarPercentual } from "../../formato";
import type { AbrirEvidencia } from "./evidencia";
import { rotuloSituacaoFundo } from "./rotulos";
import { ValorComFonte } from "./ValorComFonte";

interface Props {
  fundos: FundoPrevistoRealizado[];
  aoAbrirEvidencia: AbrirEvidencia;
}

/**
 * Fundos de reserva e de obras: arrecadação × previsto das linhas 1.9 (RF-03.1.9). Os demais fundos
 * aparecem como "sem previsto na PO", só com a movimentação, sem diferença.
 */
export function PainelFundos({ fundos, aoAbrirEvidencia }: Props) {
  const comparados = fundos.filter((f) => f.status === "COMPARED");
  const demais = fundos.filter((f) => f.status !== "COMPARED");
  const nome = (f: FundoPrevistoRealizado) => [f.lineCode, f.fund].filter(Boolean).join(" · ") || "—";

  return (
    <>
      <section className="bloco">
        <h2>Fundos com previsto na PO</h2>
        {comparados.length === 0 ? (
          <p className="aviso">Nenhum fundo ligado às linhas de fundos da PO.</p>
        ) : (
          <>
            <ResponsiveContainer width="100%" height={Math.max(160, comparados.length * 60)}>
              <BarChart
                data={comparados.map((f) => ({ nome: nome(f), previsto: f.planned, arrecadado: f.collected }))}
                layout="vertical"
                margin={{ left: 24, right: 24 }}
              >
                <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" tickFormatter={formatarMoedaCurta} />
                <YAxis type="category" dataKey="nome" width={230} tick={{ fontSize: 12 }} />
                <Tooltip formatter={(valor) => formatarMoeda(Number(valor))} />
                <Legend />
                <Bar dataKey="previsto" name="Previsto" fill="var(--cor-previsto)" isAnimationActive={false} />
                <Bar dataKey="arrecadado" name="Arrecadado" fill="var(--cor-entrada)" isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
            <table className="tabela">
              <thead>
                <tr>
                  <th>Linha · fundo do fluxo</th>
                  <th className="numero">Previsto</th>
                  <th className="numero">Arrecadado</th>
                  <th className="numero">Diferença</th>
                  <th className="numero">Execução</th>
                </tr>
              </thead>
              <tbody>
                {comparados.map((f) => (
                  <tr key={f.lineId ?? f.fundId}>
                    <td>{nome(f)}</td>
                    <td className="numero">{formatarMoeda(f.planned ?? 0)}</td>
                    <td className="numero">
                      {f.fundId && f.collected !== null && f.collected !== undefined ? (
                        <ValorComFonte
                          valor={f.collected}
                          aoAbrir={() => aoAbrirEvidencia({ alvo: `fund:${f.fundId}`, titulo: `Arrecadação: ${f.fund}` })}
                        />
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className={`numero ${classeDiferenca(f.difference ?? 0)}`}>
                      {f.difference === null || f.difference === undefined ? "—" : formatarDiferenca(f.difference)}
                    </td>
                    <td className="numero">{formatarPercentual(f.execution)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </section>

      {demais.length > 0 && (
        <section className="bloco">
          <h2>Demais fundos</h2>
          <table className="tabela">
            <thead>
              <tr>
                <th>Fundo</th>
                <th>Situação</th>
                <th className="numero">Créditos</th>
                <th className="numero">Débitos</th>
              </tr>
            </thead>
            <tbody>
              {demais.map((f, i) => (
                <tr key={f.fundId ?? f.lineId ?? i}>
                  <td>{nome(f)}</td>
                  <td className="discreto">
                    {f.status === "LINE_WITHOUT_FUND" ? `Linha ${f.lineCode} sem fundo ligado` : rotuloSituacaoFundo[f.status]}
                  </td>
                  <td className="numero">{f.credits === null || f.credits === undefined ? "—" : formatarMoeda(f.credits)}</td>
                  <td className="numero">{f.debits === null || f.debits === undefined ? "—" : formatarMoeda(f.debits)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </>
  );
}
