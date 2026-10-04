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
  const comparados = fundos.filter((f) => f.situacao === "COMPARADO");
  const demais = fundos.filter((f) => f.situacao !== "COMPARADO");
  const nome = (f: FundoPrevistoRealizado) => [f.linhaCodigo, f.fundo].filter(Boolean).join(" · ") || "—";

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
                data={comparados.map((f) => ({ nome: nome(f), previsto: f.previsto, arrecadado: f.arrecadado }))}
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
                  <tr key={f.linhaId ?? f.fundoId}>
                    <td>{nome(f)}</td>
                    <td className="numero">{formatarMoeda(f.previsto ?? 0)}</td>
                    <td className="numero">
                      {f.fundoId && f.arrecadado !== null && f.arrecadado !== undefined ? (
                        <ValorComFonte
                          valor={f.arrecadado}
                          aoAbrir={() => aoAbrirEvidencia({ alvo: `fundo:${f.fundoId}`, titulo: `Arrecadação: ${f.fundo}` })}
                        />
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className={`numero ${classeDiferenca(f.diferenca ?? 0)}`}>
                      {f.diferenca === null || f.diferenca === undefined ? "—" : formatarDiferenca(f.diferenca)}
                    </td>
                    <td className="numero">{formatarPercentual(f.execucao)}</td>
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
                <tr key={f.fundoId ?? f.linhaId ?? i}>
                  <td>{nome(f)}</td>
                  <td className="discreto">
                    {f.situacao === "LINHA_SEM_FUNDO" ? `Linha ${f.linhaCodigo} sem fundo ligado` : rotuloSituacaoFundo[f.situacao]}
                  </td>
                  <td className="numero">{f.creditos === null || f.creditos === undefined ? "—" : formatarMoeda(f.creditos)}</td>
                  <td className="numero">{f.debitos === null || f.debitos === undefined ? "—" : formatarMoeda(f.debitos)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </>
  );
}
