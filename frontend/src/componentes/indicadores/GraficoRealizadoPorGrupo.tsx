import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { SerieGrupoIndicador } from "../../api/tipos";
import { formatarMes, formatarMoedaCurta } from "../../formato";
import { CartaoIndicador } from "./CartaoIndicador";
import { abrirPonto, type ContextoGrafico } from "./contexto";
import { corDaSerie } from "./cores";
import { ALTURA_EIXO_MES, EixoMes } from "./EixoMes";
import { moedaDoMes, rotuloEixoMes } from "./formatacao";
import { dicaDaTabela, TabelaAlternativa, type ModeloTabela } from "./TabelaAlternativa";

interface Props {
  series: SerieGrupoIndicador[];
  contexto: ContextoGrafico;
}

/**
 * Gráfico 4 (RF-11.11): realizado de cada grupo da PO (1.1 a 1.8) mês a mês, em barras empilhadas.
 * Cada pedaço da barra abre o grupo daquele mês no previsto × realizado.
 */
export function GraficoRealizadoPorGrupo({ series, contexto }: Props) {
  // Todas as séries trazem os mesmos meses do exercício, na mesma ordem
  const meses = series[0]?.points ?? [];
  const acoes = series.map((s) => s.points.map((p) => abrirPonto(contexto, p)));
  const tabela: ModeloTabela = {
    legenda: "Realizado por grupo da PO, mês a mês",
    colunaRotulo: "Mês",
    colunas: series.map((s) => `${s.code} ${s.description} (R$)`),
    linhas: meses.map((m, i) => ({
      chave: m.month,
      rotulo: formatarMes(m.month),
      celulas: series.map((s, g) => {
        const ponto = s.points[i];
        return { texto: ponto ? moedaDoMes(ponto.amount, ponto.status) : "—", aoAbrir: acoes[g][i] };
      }),
    })),
  };
  // Chaves "g0", "g1"...: o Recharts leria "1.1" como caminho de objeto
  const dados = meses.map((m, indice) => {
    const linha: Record<string, number | string | null> = { indice, eixo: rotuloEixoMes(m.month, m.status) };
    series.forEach((s, g) => (linha[`g${g}`] = s.points[indice]?.amount ?? null));
    return linha;
  });

  return (
    <CartaoIndicador
      numero={4}
      titulo="Realizado por grupo da PO"
      periodo={contexto.periodo}
      unidade="R$"
      dadosDe={contexto.dadosDe}
      dica="Cada cor é um grupo da PO. Clique num pedaço da barra para abrir aquele grupo no mês."
      tabela={<TabelaAlternativa modelo={tabela} />}
    >
      {series.length === 0 ? (
        <p className="discreto">Nenhum grupo com números neste exercício.</p>
      ) : (
        <ResponsiveContainer width="100%" height={360}>
          <BarChart data={dados} margin={{ top: 16, left: 4, right: 24, bottom: 4 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="eixo" tick={<EixoMes />} interval={0} height={ALTURA_EIXO_MES} />
            <YAxis tickFormatter={formatarMoedaCurta} width={80} />
            <Tooltip content={dicaDaTabela(tabela)} />
            <Legend />
            {series.map((s, g) => (
              <Bar
                key={s.code}
                dataKey={`g${g}`}
                name={`${s.code} ${s.description}`}
                stackId="grupos"
                fill={corDaSerie(g)}
                isAnimationActive={false}
                cursor="pointer"
                onClick={(item) => acoes[g][(item.payload as { indice: number }).indice]?.()}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      )}
    </CartaoIndicador>
  );
}
