import { Bar, BarChart, CartesianGrid, Legend, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { ComparacaoIndicador } from "../../api/tipos";
import { formatarMoedaCurta, formatarMoedaOuTraco, formatarPercentual } from "../../formato";
import { CartaoIndicador } from "./CartaoIndicador";
import type { ContextoGrafico } from "./contexto";
import { corDaSerie } from "./cores";
import { formatarEixoPercentual } from "./formatacao";
import { dicaDaTabela, TabelaAlternativa, type ModeloTabela } from "./TabelaAlternativa";

interface Props {
  comparacao: ComparacaoIndicador;
  contexto: ContextoGrafico;
}

/**
 * Gráfico 7 (RF-11.11 e RF-11.6): previsto do mês de cada grupo em cada exercício (barras agrupadas) e a
 * execução acumulada de cada exercício. Valores na ordem de `comparacao.exercicios`, prontos da API.
 *
 * Clique: cada exercício traz o `poId` e o período; cada grupo traz o `alvo` em cada exercício (mesma ordem).
 * A barra de um grupo abre o previsto × realizado daquele exercício já com a evidência do grupo; a barra da
 * execução abre o exercício no período dele. A coluna impressa (`poId` nulo) e grupo sem alvo ficam sem link.
 */
export function GraficoEntreExercicios({ comparacao, contexto }: Props) {
  const { grupos } = comparacao;

  /** Ação que abre o exercício `i` no previsto × realizado (com o alvo, se houver); `undefined` sem PO ou período. */
  const abrirExercicio = (i: number, alvo?: string | null): (() => void) | undefined => {
    const { poId, periodo } = comparacao.exercicios[i];
    if (!poId || !periodo) return undefined;
    return contexto.abrir({ poId, periodo, alvo, fundoId: contexto.fundoId });
  };
  /** Ação da barra do grupo `g` no exercício `i`: só com alvo (sem alvo não há evidência para abrir). */
  const abrirGrupo = (g: number, i: number) => {
    const alvo = grupos[g].alvos[i];
    return alvo ? abrirExercicio(i, alvo) : undefined;
  };
  const acoesExecucao = comparacao.exercicios.map((_, i) => abrirExercicio(i));

  const tabelaGrupos: ModeloTabela = {
    legenda: "Previsto do mês por grupo da PO em cada exercício",
    colunaRotulo: "Grupo",
    colunas: comparacao.exercicios.map((ex) => `${ex.rotulo} (R$)`),
    linhas: grupos.map((g, indiceGrupo) => ({
      chave: g.codigo,
      rotulo: `${g.codigo} ${g.descricao}`,
      celulas: comparacao.exercicios.map((_, i) => ({
        texto: formatarMoedaOuTraco(g.previstoMes[i]),
        aoAbrir: abrirGrupo(indiceGrupo, i),
      })),
    })),
  };
  const tabelaExecucao: ModeloTabela = {
    legenda: "Execução acumulada de cada exercício",
    colunaRotulo: "Exercício",
    colunas: ["Execução acumulada (%)"],
    linhas: comparacao.exercicios.map((ex, i) => ({
      chave: ex.id,
      rotulo: ex.rotulo,
      celulas: [{ texto: formatarPercentual(ex.execucao), aoAbrir: acoesExecucao[i] }],
    })),
  };

  // Chaves "e0", "e1"... na ordem dos exercícios; o eixo mostra só o código do grupo (o nome vai na dica)
  const dadosGrupos = grupos.map((g, indice) => {
    const linha: Record<string, number | string | null> = { indice, codigo: g.codigo };
    comparacao.exercicios.forEach((_, i) => (linha[`e${i}`] = g.previstoMes[i] ?? null));
    return linha;
  });
  const dadosExecucao = comparacao.exercicios.map((ex, indice) => ({ indice, rotulo: ex.rotulo, execucao: ex.execucao ?? null }));
  const periodo = comparacao.exercicios.map((ex) => ex.rotulo).join(" × ");

  return (
    <CartaoIndicador
      numero={7}
      titulo="Comparação entre exercícios"
      periodo={periodo}
      unidade="R$ (previsto do mês) e % (execução acumulada)"
      dadosDe={contexto.dadosDe}
      dica="Clique numa barra para abrir o grupo (ou o exercício) no previsto × realizado, com a evidência."
      tabela={
        <>
          <TabelaAlternativa modelo={tabelaGrupos} />
          <TabelaAlternativa modelo={tabelaExecucao} />
        </>
      }
    >
      <h3>Previsto do mês por grupo</h3>
      <ResponsiveContainer width="100%" height={320}>
        <BarChart data={dadosGrupos} margin={{ top: 8, left: 4, right: 24, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="codigo" interval={0} />
          <YAxis tickFormatter={formatarMoedaCurta} width={80} />
          <Tooltip content={dicaDaTabela(tabelaGrupos)} />
          <Legend />
          {comparacao.exercicios.map((ex, i) => (
            <Bar
              key={ex.id}
              dataKey={`e${i}`}
              name={ex.rotulo}
              fill={corDaSerie(i)}
              isAnimationActive={false}
              cursor={acoesExecucao[i] ? "pointer" : undefined}
              onClick={(item) => abrirGrupo((item.payload as { indice: number }).indice, i)?.()}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>

      <h3>Execução acumulada de cada exercício</h3>
      <ResponsiveContainer width="100%" height={Math.max(140, dadosExecucao.length * 40 + 50)}>
        <BarChart data={dadosExecucao} layout="vertical" margin={{ top: 8, left: 8, right: 32, bottom: 8 }}>
          <CartesianGrid strokeDasharray="3 3" horizontal={false} />
          <XAxis type="number" tickFormatter={formatarEixoPercentual} />
          <YAxis type="category" dataKey="rotulo" width={220} interval={0} tick={{ fontSize: 12 }} />
          <Tooltip content={dicaDaTabela(tabelaExecucao)} />
          <ReferenceLine
            x={100}
            ifOverflow="extendDomain"
            stroke="var(--referencia)"
            strokeDasharray="6 4"
            label={{ value: "100%", position: "insideTopRight", fill: "var(--texto-discreto)", fontSize: 12 }}
          />
          <Bar
            dataKey="execucao"
            name="Execução acumulada"
            fill="var(--cor-neutra)"
            isAnimationActive={false}
            cursor={acoesExecucao.some(Boolean) ? "pointer" : undefined}
            onClick={(item) => acoesExecucao[(item.payload as { indice: number }).indice]?.()}
          />
        </BarChart>
      </ResponsiveContainer>
    </CartaoIndicador>
  );
}
