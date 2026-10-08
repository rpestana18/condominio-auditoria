import { Bar, BarChart, CartesianGrid, Legend, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { ComparacaoIndicador, Exercicio } from "../../api/tipos";
import { formatarMoedaCurta, formatarMoedaOuTraco, formatarPercentual } from "../../formato";
import { CartaoIndicador } from "./CartaoIndicador";
import type { ContextoGrafico } from "./contexto";
import { corDaSerie } from "./cores";
import { formatarEixoPercentual } from "./formatacao";
import { dicaDaTabela, TabelaAlternativa, type ModeloTabela } from "./TabelaAlternativa";

interface Props {
  comparacao: ComparacaoIndicador;
  /** Lista do GET /exercicios: dá a PO de cada exercício da comparação, para abrir o previsto × realizado. */
  exercicios: Exercicio[];
  contexto: ContextoGrafico;
}

/**
 * Gráfico 7 (RF-11.11 e RF-11.6): previsto do mês de cada grupo em cada exercício (barras agrupadas) e a
 * execução acumulada de cada exercício. Valores na ordem de `comparacao.exercicios`, prontos da API.
 *
 * Lacuna de contrato: os grupos do gráfico 7 não trazem `alvo` (nem o id da linha do grupo), e os exercícios não
 * trazem o `poId`. O clique abre o exercício no período dele, sem a evidência do grupo; a PO vem do GET /exercicios.
 */
export function GraficoEntreExercicios({ comparacao, exercicios, contexto }: Props) {
  const { grupos } = comparacao;
  // Ação de cada exercício: só os de PO, com período (a coluna impressa não tem realizado nem período)
  const acoes = comparacao.exercicios.map((ex) => {
    const daLista = exercicios.find((e) => e.id === ex.id);
    if (!daLista || daLista.tipo !== "PO" || !ex.periodo) return undefined;
    return contexto.abrir({ poId: daLista.poId, periodo: ex.periodo, fundoId: contexto.fundoId });
  });

  const tabelaGrupos: ModeloTabela = {
    legenda: "Previsto do mês por grupo da PO em cada exercício",
    colunaRotulo: "Grupo",
    colunas: comparacao.exercicios.map((ex) => `${ex.rotulo} (R$)`),
    linhas: grupos.map((g) => ({
      chave: g.codigo,
      rotulo: `${g.codigo} ${g.descricao}`,
      celulas: comparacao.exercicios.map((_, i) => ({ texto: formatarMoedaOuTraco(g.previstoMes[i]), aoAbrir: acoes[i] })),
    })),
  };
  const tabelaExecucao: ModeloTabela = {
    legenda: "Execução acumulada de cada exercício",
    colunaRotulo: "Exercício",
    colunas: ["Execução acumulada (%)"],
    linhas: comparacao.exercicios.map((ex, i) => ({
      chave: ex.id,
      rotulo: ex.rotulo,
      celulas: [{ texto: formatarPercentual(ex.execucao), aoAbrir: acoes[i] }],
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
      dica="Clique numa barra para abrir aquele exercício no previsto × realizado."
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
              cursor={acoes[i] ? "pointer" : undefined}
              onClick={() => acoes[i]?.()}
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
            cursor="pointer"
            onClick={(item) => acoes[(item.payload as { indice: number }).indice]?.()}
          />
        </BarChart>
      </ResponsiveContainer>
    </CartaoIndicador>
  );
}
