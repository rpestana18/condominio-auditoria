import { Bar, CartesianGrid, Cell, ComposedChart, Legend, Line, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { PontoRegra20 } from "../../api/tipos";
import { formatarMes, formatarPercentual } from "../../formato";
import { CartaoIndicador } from "./CartaoIndicador";
import { abrirPonto, type ContextoGrafico } from "./contexto";
import { ALTURA_EIXO_MES, EixoMes } from "./EixoMes";
import { formatarEixoPercentual, moedaDoMes, percentualDoMes, rotuloEixoMes, textoSituacao } from "./formatacao";
import { dicaDaTabela, TabelaAlternativa, type ModeloTabela } from "./TabelaAlternativa";

interface Props {
  pontos: PontoRegra20[];
  /** Limite da regra (Conv. 16.2), do campo `limitePercentual` da resposta. Nulo quando nenhum mês tem números. */
  limitePercentual: number | null | undefined;
  contexto: ContextoGrafico;
}

/**
 * Gráfico 2 (RF-11.11): excesso do mês em % do previsto, com a linha do limite e a marca do cenário máximo
 * (com "a realocar" e "sem linha da PO", RF-03.1.11). A única cor de alerta da tela (RF-11.13): a barra fica
 * vermelha só quando o ponto vem com `acimaDoLimite` true. O frontend não compara valores.
 */
export function GraficoRegra20({ pontos, limitePercentual, contexto }: Props) {
  const acoes = pontos.map((p) => abrirPonto(contexto, p));
  const tabela: ModeloTabela = {
    legenda: "Regra dos 20%: excesso do mês e cenário máximo",
    colunaRotulo: "Mês",
    colunas: ["Excesso (R$)", "Excesso (%)", "Limite (%)", "Cenário máximo (R$)", "Cenário máximo (%)", "Marcas"],
    linhas: pontos.map((p, i) => {
      const acima = p.aboveLimit === true;
      return {
        chave: p.month,
        rotulo: formatarMes(p.month),
        celulas: [
          { texto: moedaDoMes(p.overrun, p.status), aoAbrir: acoes[i] },
          { texto: percentualDoMes(p.percentage, p.status), aoAbrir: acoes[i], alerta: acima },
          { texto: percentualDoMes(p.limitPercentage, p.status) },
          { texto: moedaDoMes(p.maxScenario, p.status) },
          { texto: percentualDoMes(p.maxScenarioPercentage, p.status) },
          { texto: marcas(p), alerta: acima },
        ],
      };
    }),
  };
  const dados = pontos.map((p, indice) => ({
    indice,
    eixo: rotuloEixoMes(p.month, p.status, p.provisional ? "provisório" : null),
    percentual: p.percentage ?? null,
    cenario: p.maxScenarioPercentage ?? null,
    acima: p.aboveLimit === true,
  }));
  const textoLimite = limitePercentual === null || limitePercentual === undefined ? null : formatarPercentual(limitePercentual);

  return (
    <CartaoIndicador
      numero={2}
      titulo="Regra dos 20% (Conv. 16.2)"
      periodo={contexto.periodo}
      unidade="%"
      dadosDe={contexto.dadosDe}
      dica={
        `Barra = excesso do mês em % do previsto. Círculo = cenário máximo. ` +
        (textoLimite ? `Linha tracejada = limite de ${textoLimite}; barra em vermelho = acima do limite. ` : "") +
        `"provisório" no eixo: o mês tem valor a realocar ou conta sem linha da PO. Clique numa barra para abrir o mês.`
      }
      tabela={<TabelaAlternativa modelo={tabela} />}
    >
      <ResponsiveContainer width="100%" height={300}>
        <ComposedChart data={dados} margin={{ top: 16, left: 4, right: 24, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="eixo" tick={<EixoMes />} interval={0} height={ALTURA_EIXO_MES} />
          <YAxis tickFormatter={formatarEixoPercentual} width={56} />
          <Tooltip content={dicaDaTabela(tabela)} />
          <Legend />
          {limitePercentual !== null && limitePercentual !== undefined && (
            <ReferenceLine
              y={limitePercentual}
              ifOverflow="extendDomain"
              stroke="var(--referencia)"
              strokeDasharray="6 4"
              label={{ value: `limite ${textoLimite}`, position: "insideTopRight", fill: "var(--texto-discreto)", fontSize: 12 }}
            />
          )}
          <Bar
            dataKey="percentual"
            name="Excesso do mês"
            fill="var(--cor-neutra)"
            isAnimationActive={false}
            cursor="pointer"
            onClick={(item) => acoes[(item.payload as { indice: number }).indice]?.()}
          >
            {/* Vermelho só onde a API marcou acimaDoLimite (RF-11.13) */}
            {dados.map((d) => (
              <Cell key={d.indice} fill={d.acima ? "var(--critico)" : "var(--cor-neutra)"} />
            ))}
          </Bar>
          <Line
            dataKey="cenario"
            name="Cenário máximo"
            stroke="none"
            legendType="circle"
            dot={{ r: 5, fill: "var(--superficie)", stroke: "var(--texto)", strokeWidth: 2 }}
            activeDot={false}
            isAnimationActive={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </CartaoIndicador>
  );
}

/** Marcas do mês, só com o que veio da API: "acima do limite", "provisório" ou a situação do mês. */
function marcas(p: PontoRegra20): string {
  if (p.status !== "WITH_CASH_FLOW") return textoSituacao[p.status];
  const lista = [p.aboveLimit ? "acima do limite" : null, p.provisional ? "provisório" : null].filter(Boolean);
  return lista.length ? lista.join(" · ") : "—";
}
