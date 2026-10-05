import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { GrupoPrevistoRealizado } from "../../api/tipos";
import { formatarMoeda, formatarMoedaCurta } from "../../formato";

/** Barras previsto × realizado por grupo da PO (ADR 0004, Decisão 6). Os valores vêm prontos da API. */
export function GraficoGrupos({ grupos }: { grupos: GrupoPrevistoRealizado[] }) {
  const dados = grupos.map((g) => ({ nome: `${g.codigo} ${g.descricao}`, previsto: g.previsto, realizado: g.realizado }));
  return (
    <section className="bloco grafico">
      <h2>Previsto × realizado por grupo</h2>
      <ResponsiveContainer width="100%" height={Math.max(260, dados.length * 48)}>
        <BarChart data={dados} layout="vertical" margin={{ left: 24, right: 24 }}>
          <CartesianGrid strokeDasharray="3 3" horizontal={false} />
          <XAxis type="number" tickFormatter={formatarMoedaCurta} />
          <YAxis type="category" dataKey="nome" width={230} tick={{ fontSize: 12 }} />
          <Tooltip formatter={(valor) => formatarMoeda(Number(valor))} />
          <Legend />
          <Bar dataKey="previsto" name="Previsto" fill="var(--cor-previsto)" isAnimationActive={false} />
          <Bar dataKey="realizado" name="Realizado" fill="var(--cor-realizado)" isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </section>
  );
}
