import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { SerieFundoIndicador } from "../../api/tipos";
import { formatarMes, formatarMoedaCurta } from "../../formato";
import { CartaoIndicador } from "./CartaoIndicador";
import { abrirPonto, type ContextoGrafico } from "./contexto";
import { ALTURA_EIXO_MES, EixoMes } from "./EixoMes";
import { moedaDoMes, rotuloEixoMes } from "./formatacao";
import { dicaDaTabela, TabelaAlternativa, type ModeloTabela } from "./TabelaAlternativa";

interface Props {
  series: SerieFundoIndicador[];
  contexto: ContextoGrafico;
}

const nomeDoFundo = (s: SerieFundoIndicador) => (s.lineCode ? `${s.lineCode} ${s.fund}` : s.fund);

/**
 * Gráfico 6 (RF-11.11 e RF-03.1.9): para cada fundo ligado a uma linha 1.9, a arrecadação e o previsto do mês,
 * em barras agrupadas. Clicar abre o fundo naquele mês no previsto × realizado.
 */
export function GraficoFundos({ series, contexto }: Props) {
  const modelos = series.map((s) => modeloDoFundo(s, contexto));
  return (
    <CartaoIndicador
      numero={6}
      titulo="Fundos: arrecadação × previsto"
      periodo={contexto.periodo}
      unidade="R$"
      dadosDe={contexto.dadosDe}
      dica="Um gráfico por fundo ligado às linhas 1.9 da PO. Clique numa barra para abrir o fundo no mês."
      tabela={modelos.map(({ tabela }) => (
        <TabelaAlternativa key={tabela.legenda} modelo={tabela} />
      ))}
    >
      {series.length === 0 ? (
        <p className="discreto">Nenhum fundo ligado às linhas 1.9 da PO deste exercício.</p>
      ) : (
        series.map((s, i) => <GraficoDeUmFundo key={s.fundId} serie={s} {...modelos[i]} />)
      )}
    </CartaoIndicador>
  );
}

function modeloDoFundo(serie: SerieFundoIndicador, contexto: ContextoGrafico) {
  // O fundo do ponto vai como filtro: o previsto × realizado abre só o painel dele
  const acoes = serie.points.map((p) => abrirPonto(contexto, p, serie.fundId));
  const tabela: ModeloTabela = {
    legenda: `${nomeDoFundo(serie)}: arrecadação × previsto`,
    colunaRotulo: "Mês",
    colunas: ["Previsto (R$)", "Arrecadado (R$)"],
    linhas: serie.points.map((p, i) => ({
      chave: p.month,
      rotulo: formatarMes(p.month),
      celulas: [
        { texto: moedaDoMes(p.planned, p.status), aoAbrir: acoes[i] },
        { texto: moedaDoMes(p.collected, p.status), aoAbrir: acoes[i] },
      ],
    })),
  };
  return { acoes, tabela };
}

interface PropsFundo {
  serie: SerieFundoIndicador;
  acoes: ((() => void) | undefined)[];
  tabela: ModeloTabela;
}

function GraficoDeUmFundo({ serie, acoes, tabela }: PropsFundo) {
  const dados = serie.points.map((p, indice) => ({
    indice,
    eixo: rotuloEixoMes(p.month, p.status),
    previsto: p.planned ?? null,
    arrecadado: p.collected ?? null,
  }));
  const aoClicar = (item: { payload?: unknown }) => acoes[(item.payload as { indice: number }).indice]?.();
  return (
    <>
      <h3>{nomeDoFundo(serie)}</h3>
      <ResponsiveContainer width="100%" height={280}>
        <BarChart data={dados} margin={{ top: 8, left: 4, right: 24, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="eixo" tick={<EixoMes />} interval={0} height={ALTURA_EIXO_MES} />
          <YAxis tickFormatter={formatarMoedaCurta} width={80} />
          <Tooltip content={dicaDaTabela(tabela)} />
          <Legend />
          <Bar dataKey="previsto" name="Previsto" fill="var(--cor-previsto)" isAnimationActive={false} cursor="pointer" onClick={aoClicar} />
          <Bar dataKey="arrecadado" name="Arrecadado" fill="var(--cor-neutra)" isAnimationActive={false} cursor="pointer" onClick={aoClicar} />
        </BarChart>
      </ResponsiveContainer>
    </>
  );
}
