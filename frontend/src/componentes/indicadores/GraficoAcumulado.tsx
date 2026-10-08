import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { PontoAcumulado } from "../../api/tipos";
import { formatarMes, formatarMoedaCurta } from "../../formato";
import { CartaoIndicador } from "./CartaoIndicador";
import { abrirPonto, type ContextoGrafico } from "./contexto";
import { ALTURA_EIXO_MES, EixoMes } from "./EixoMes";
import { moedaDoMes, rotuloEixoMes } from "./formatacao";
import { dicaDaTabela, TabelaAlternativa, type ModeloTabela } from "./TabelaAlternativa";

interface Props {
  pontos: PontoAcumulado[];
  contexto: ContextoGrafico;
}

/**
 * Gráfico 3 (RF-11.11): previsto e realizado somados até cada mês, já somados pela API (só os meses com fluxo
 * entram na soma). Mês sem fluxo fica sem ponto: a linha é interrompida ali, nunca desce a zero.
 */
export function GraficoAcumulado({ pontos, contexto }: Props) {
  const acoes = pontos.map((p) => abrirPonto(contexto, p));
  const tabela: ModeloTabela = {
    legenda: "Previsto × realizado acumulado do fundo Condomínio",
    colunaRotulo: "Mês",
    colunas: ["Previsto acumulado (R$)", "Realizado acumulado (R$)"],
    linhas: pontos.map((p, i) => ({
      chave: p.mes,
      rotulo: formatarMes(p.mes),
      celulas: [
        { texto: moedaDoMes(p.previstoAcumulado, p.situacao), aoAbrir: acoes[i] },
        { texto: moedaDoMes(p.realizadoAcumulado, p.situacao), aoAbrir: acoes[i] },
      ],
    })),
  };
  const dados = pontos.map((p, indice) => ({
    indice,
    eixo: rotuloEixoMes(p.mes, p.situacao),
    previsto: p.previstoAcumulado ?? null,
    realizado: p.realizadoAcumulado ?? null,
  }));

  return (
    <CartaoIndicador
      numero={3}
      titulo="Previsto × realizado acumulado do fundo Condomínio"
      periodo={contexto.periodo}
      unidade="R$"
      dadosDe={contexto.dadosDe}
      dica="Soma dos meses com fluxo até cada mês. Clique num ponto para abrir aquele mês no previsto × realizado."
      tabela={<TabelaAlternativa modelo={tabela} />}
    >
      <ResponsiveContainer width="100%" height={300}>
        <LineChart
          data={dados}
          margin={{ top: 16, left: 4, right: 24, bottom: 4 }}
          onClick={(estado) => {
            const indice = Number(estado?.activeTooltipIndex);
            if (Number.isInteger(indice)) acoes[indice]?.();
          }}
        >
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="eixo" tick={<EixoMes />} interval={0} height={ALTURA_EIXO_MES} />
          <YAxis tickFormatter={formatarMoedaCurta} width={80} />
          <Tooltip content={dicaDaTabela(tabela)} />
          <Legend />
          <Line
            dataKey="previsto"
            name="Previsto acumulado"
            stroke="var(--cor-previsto)"
            strokeWidth={2}
            strokeDasharray="6 4"
            dot={{ r: 4 }}
            isAnimationActive={false}
            cursor="pointer"
          />
          <Line
            dataKey="realizado"
            name="Realizado acumulado"
            stroke="var(--cor-neutra)"
            strokeWidth={2}
            dot={{ r: 4 }}
            isAnimationActive={false}
            cursor="pointer"
          />
        </LineChart>
      </ResponsiveContainer>
    </CartaoIndicador>
  );
}
