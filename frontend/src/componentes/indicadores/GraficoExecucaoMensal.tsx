import { Bar, BarChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { PontoExecucao } from "../../api/tipos";
import { formatarMes } from "../../formato";
import { CartaoIndicador } from "./CartaoIndicador";
import { abrirPonto, type ContextoGrafico } from "./contexto";
import { ALTURA_EIXO_MES, EixoMes } from "./EixoMes";
import { formatarEixoPercentual, moedaDoMes, percentualDoMes, rotuloEixoMes } from "./formatacao";
import { dicaDaTabela, TabelaAlternativa, type ModeloTabela } from "./TabelaAlternativa";

interface Props {
  pontos: PontoExecucao[];
  contexto: ContextoGrafico;
}

/**
 * Gráfico 1 (RF-11.11): execução do fundo Condomínio mês a mês (realizado ÷ previsto, em %, já calculada pela API),
 * com a referência de 100%. Mês sem fluxo fica sem barra e com a nota no eixo.
 */
export function GraficoExecucaoMensal({ pontos, contexto }: Props) {
  const acoes = pontos.map((p) => abrirPonto(contexto, p));
  const tabela: ModeloTabela = {
    legenda: "Execução mensal do fundo Condomínio",
    colunaRotulo: "Mês",
    colunas: ["Previsto (R$)", "Despesa realizada (R$)", "Execução (%)"],
    linhas: pontos.map((p, i) => ({
      chave: p.mes,
      rotulo: formatarMes(p.mes),
      celulas: [
        { texto: moedaDoMes(p.previsto, p.situacao), aoAbrir: acoes[i] },
        { texto: moedaDoMes(p.realizado, p.situacao), aoAbrir: acoes[i] },
        { texto: percentualDoMes(p.execucao, p.situacao), aoAbrir: acoes[i] },
      ],
    })),
  };
  // Valor nulo continua nulo: sem barra, nunca zero
  const dados = pontos.map((p, indice) => ({ indice, eixo: rotuloEixoMes(p.mes, p.situacao), execucao: p.execucao ?? null }));

  return (
    <CartaoIndicador
      numero={1}
      titulo="Execução mensal do fundo Condomínio"
      periodo={contexto.periodo}
      unidade="%"
      dadosDe={contexto.dadosDe}
      dica="Barra = despesa realizada ÷ previsto do mês. Linha tracejada = 100%. Clique numa barra para abrir o mês no previsto × realizado."
      tabela={<TabelaAlternativa modelo={tabela} />}
    >
      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={dados} margin={{ top: 16, left: 4, right: 24, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="eixo" tick={<EixoMes />} interval={0} height={ALTURA_EIXO_MES} />
          <YAxis tickFormatter={formatarEixoPercentual} width={56} />
          <Tooltip content={dicaDaTabela(tabela)} />
          <ReferenceLine
            y={100}
            ifOverflow="extendDomain"
            stroke="var(--referencia)"
            strokeDasharray="6 4"
            label={{ value: "100%", position: "insideTopRight", fill: "var(--texto-discreto)", fontSize: 12 }}
          />
          <Bar
            dataKey="execucao"
            name="Execução"
            fill="var(--cor-neutra)"
            isAnimationActive={false}
            cursor="pointer"
            onClick={(item) => acoes[(item.payload as { indice: number }).indice]?.()}
          />
        </BarChart>
      </ResponsiveContainer>
    </CartaoIndicador>
  );
}
