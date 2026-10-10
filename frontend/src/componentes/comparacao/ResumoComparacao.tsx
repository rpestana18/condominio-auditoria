import type { ReactNode } from "react";
import type { ExercicioComparado, ResumoComparado } from "../../api/tipos";
import { formatarMes, formatarMoeda, formatarPercentual } from "../../formato";
import type { AbridorEvidencia } from "./navegacao";
import { Variacao } from "./Variacao";
import { ValorOuTraco } from "./ValorOuTraco";

interface Props {
  exercicios: ExercicioComparado[];
  resumo: ResumoComparado[];
  abridor: AbridorEvidencia;
}

/** Uma linha da tabela: o nome do número e como mostrar a célula de cada exercício. */
interface Metrica {
  rotulo: string;
  dica?: string;
  celula: (r: ResumoComparado) => ReactNode;
}

/** Número inteiro que pode faltar (ex.: coluna impressa não tem achados): nulo vira "—". */
const inteiroOuTraco = (valor: number | null | undefined) => (valor === null || valor === undefined ? "—" : String(valor));

/**
 * Visão 1 do RF-11.6, resumo por exercício. Os números são os do backend, na ordem da API; as colunas são
 * os exercícios (do mais recente para o mais antigo) e a variação é contra a coluna seguinte.
 */
export function ResumoComparacao({ exercicios, resumo, abridor }: Props) {
  const metricas: Metrica[] = [
    { rotulo: "Previsto do mês", dica: "Soma das linhas sem os fundos", celula: (r) => formatarMoeda(r.monthlyPlanned) },
    { rotulo: "Variação do previsto do mês", celula: (r) => <Variacao variacao={r.monthlyPlannedVariation} /> },
    { rotulo: "Previsto do exercício", celula: (r) => formatarMoeda(r.fiscalYearPlanned) },
    { rotulo: "Meses com fluxo", celula: (r) => String(r.monthsWithCashFlow) },
    { rotulo: "Previsto dos meses comparados", celula: (r) => <ValorOuTraco valor={r.planned} /> },
    {
      rotulo: "Realizado dos meses comparados",
      dica: "Clique para ver os lançamentos no previsto × realizado",
      celula: (r) => <ValorOuTraco valor={r.actual} aoAbrir={abridor(r.fiscalYearId, r.target)} />,
    },
    { rotulo: "Variação do realizado", celula: (r) => <Variacao variacao={r.actualVariation} /> },
    { rotulo: "Execução", dica: "Realizado ÷ previsto", celula: (r) => formatarPercentual(r.execution) },
    {
      rotulo: "Maior excesso mensal (regra dos 20%)",
      celula: (r) =>
        r.largestOverrun ? (
          <>
            {formatarMoeda(r.largestOverrun.amount)}
            <small className="observacao">
              {formatarPercentual(r.largestOverrun.percentage)} em {formatarMes(r.largestOverrun.month)}
            </small>
          </>
        ) : (
          "—"
        ),
    },
    { rotulo: "Meses acima do limite", celula: (r) => inteiroOuTraco(r.monthsAboveLimit) },
    { rotulo: "Achados abertos", celula: (r) => inteiroOuTraco(r.openFindings) },
    {
      rotulo: "Situação",
      dica: "Há valor a realocar ou conta sem linha da PO",
      celula: (r) => (r.provisional ? <span className="selo alerta">Provisório</span> : "—"),
    },
  ];

  return (
    <section className="bloco">
      <h2>Resumo por exercício</h2>
      <div className="rolagem">
        <table className="tabela">
          <thead>
            <tr>
              <th scope="col">Número</th>
              {exercicios.map((e) => (
                <th key={e.id} scope="col" className="numero">
                  {e.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {metricas.map((m) => (
              <tr key={m.rotulo}>
                <th scope="row" title={m.dica}>
                  {m.rotulo}
                </th>
                {exercicios.map((e) => {
                  const r = resumo.find((x) => x.fiscalYearId === e.id);
                  return (
                    <td key={e.id} className="numero">
                      {r ? m.celula(r) : "—"}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
