import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { MesPrevistoRealizado } from "../../api/tipos";
import { formatarMes, formatarMesCurto, formatarMoeda, formatarMoedaCurta } from "../../formato";

const situacao: Record<MesPrevistoRealizado["situacao"], string> = {
  COM_FLUXO: "",
  SEM_FLUXO: "sem fluxo",
  DOIS_FLUXOS: "dois fluxos",
};

interface Props {
  meses: MesPrevistoRealizado[];
  aoEscolherMes: (mes: string) => void;
}

/**
 * Os 12 meses do exercício (RF-03.1.10). Mês sem fluxo fica sem barra e com o aviso no eixo:
 * nunca aparece como zero.
 */
export function GraficoMeses({ meses, aoEscolherMes }: Props) {
  const dados = meses.map((m) => {
    // Segunda linha do eixo: a situação do mês e, depois dos 12 meses, a marca de prorrogado (RF-11.3)
    const marca = [situacao[m.situacao], m.prorrogado ? "prorrogado" : ""].filter(Boolean).join(" · ");
    return {
      mes: m.mes,
      rotulo: marca ? `${formatarMesCurto(m.mes)}\n${marca}` : formatarMesCurto(m.mes),
      previsto: m.situacao === "COM_FLUXO" ? m.previsto : null,
      realizado: m.situacao === "COM_FLUXO" ? m.despesaRealizada : null,
    };
  });
  return (
    <section className="bloco grafico">
      <h2>Mês a mês no exercício</h2>
      <p className="discreto">Clique num mês para abrir o detalhe.</p>
      <ResponsiveContainer width="100%" height={300}>
        <BarChart
          data={dados}
          margin={{ left: 12, right: 12, bottom: 12 }}
          onClick={(estado) => {
            const indice = Number(estado?.activeTooltipIndex);
            if (Number.isInteger(indice) && dados[indice]) aoEscolherMes(dados[indice].mes);
          }}
        >
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="rotulo" tick={<RotuloMes />} interval={0} height={44} />
          <YAxis tickFormatter={formatarMoedaCurta} width={80} />
          <Tooltip
            labelFormatter={(_, itens) => {
              const mes = itens?.[0]?.payload?.mes as string | undefined;
              return mes ? formatarMes(mes) : "";
            }}
            formatter={(valor) => (valor === null ? "sem fluxo carregado" : formatarMoeda(Number(valor)))}
          />
          <Legend />
          <Bar dataKey="previsto" name="Previsto" fill="var(--cor-previsto)" isAnimationActive={false} cursor="pointer" />
          <Bar dataKey="realizado" name="Despesa realizada" fill="var(--cor-realizado)" isAnimationActive={false} cursor="pointer" />
        </BarChart>
      </ResponsiveContainer>
    </section>
  );
}

/** Rótulo do eixo em duas linhas: "set/26" e, quando for o caso, "sem fluxo". */
function RotuloMes(props: { x?: number; y?: number; payload?: { value: string } }) {
  const [mes, aviso] = (props.payload?.value ?? "").split("\n");
  return (
    <text x={props.x} y={props.y} textAnchor="middle" className="rotulo-eixo">
      <tspan x={props.x} dy={14}>
        {mes}
      </tspan>
      {aviso && (
        <tspan x={props.x} dy={14} className="rotulo-eixo-aviso">
          {aviso}
        </tspan>
      )}
    </text>
  );
}
