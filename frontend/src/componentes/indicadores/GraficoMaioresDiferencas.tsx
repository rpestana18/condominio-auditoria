import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { DiferencaIndicador, MaioresDiferencas } from "../../api/tipos";
import { formatarDiferenca, formatarMoeda, formatarMoedaCurta } from "../../formato";
import { CartaoIndicador } from "./CartaoIndicador";
import type { ContextoGrafico } from "./contexto";
import { dicaDaTabela, TabelaAlternativa, type ModeloTabela } from "./TabelaAlternativa";

interface Props {
  diferencas: MaioresDiferencas;
  contexto: ContextoGrafico;
}

type Lado = "acima" | "abaixo";

const rotuloLado: Record<Lado, string> = { acima: "acima do previsto", abaixo: "abaixo do previsto" };

/**
 * Gráfico 5 (RF-11.11): as linhas da PO mais acima e mais abaixo do previsto no acumulado (até 10 de cada lado,
 * já escolhidas e ordenadas pela API), em R$. Clicar numa barra abre a linha no acumulado, com os lançamentos.
 */
export function GraficoMaioresDiferencas({ diferencas, contexto }: Props) {
  // As duas listas na ordem da API: primeiro as mais acima, depois as mais abaixo
  const itens: { lado: Lado; d: DiferencaIndicador }[] = [
    ...diferencas.above.map((d) => ({ lado: "acima" as const, d })),
    ...diferencas.below.map((d) => ({ lado: "abaixo" as const, d })),
  ];
  const acoes = itens.map(({ d }) =>
    contexto.abrir({ poId: contexto.poId, periodo: "cumulative", alvo: d.target, fundoId: contexto.fundoId }),
  );
  const tabela: ModeloTabela = {
    legenda: "Maiores diferenças do acumulado por linha da PO (realizado menos previsto)",
    colunaRotulo: "Linha da PO",
    colunas: ["Posição", "Previsto (R$)", "Realizado (R$)", "Diferença (R$)"],
    linhas: itens.map(({ lado, d }, i) => ({
      chave: d.lineId,
      rotulo: `${d.code} ${d.description}`,
      celulas: [
        { texto: rotuloLado[lado] },
        { texto: formatarMoeda(d.planned), aoAbrir: acoes[i] },
        { texto: formatarMoeda(d.actual), aoAbrir: acoes[i] },
        { texto: formatarDiferenca(d.difference), aoAbrir: acoes[i] },
      ],
    })),
  };
  const dados = itens.map(({ lado, d }, indice) => ({ indice, lado, nome: `${d.code} ${d.description}`, diferenca: d.difference }));

  return (
    <CartaoIndicador
      numero={5}
      titulo="Maiores diferenças do acumulado por linha da PO"
      periodo={`${contexto.periodo}, acumulado dos meses com fluxo`}
      unidade="R$"
      dadosDe={contexto.dadosDe}
      dica="Diferença = realizado menos previsto. Até 10 linhas acima e 10 abaixo do previsto. Clique numa barra para abrir a linha com os lançamentos."
      tabela={<TabelaAlternativa modelo={tabela} />}
    >
      {itens.length === 0 ? (
        <p className="discreto">Nenhuma linha com diferença no acumulado.</p>
      ) : (
        <ResponsiveContainer width="100%" height={Math.max(220, dados.length * 30 + 50)}>
          <BarChart data={dados} layout="vertical" margin={{ top: 8, left: 8, right: 32, bottom: 8 }}>
            <CartesianGrid strokeDasharray="3 3" horizontal={false} />
            <XAxis type="number" tickFormatter={formatarMoedaCurta} />
            <YAxis type="category" dataKey="nome" width={280} interval={0} tick={{ fontSize: 12 }} />
            <Tooltip content={dicaDaTabela(tabela)} />
            <ReferenceLine x={0} stroke="var(--referencia)" />
            <Bar
              dataKey="diferenca"
              name="Diferença"
              isAnimationActive={false}
              cursor="pointer"
              onClick={(item) => acoes[(item.payload as { indice: number }).indice]?.()}
            >
              {/* Dois tons neutros só para separar as duas listas da API; nenhum é cor de alerta */}
              {dados.map((d) => (
                <Cell key={d.indice} fill={d.lado === "acima" ? "var(--cor-neutra)" : "var(--cor-previsto)"} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      )}
    </CartaoIndicador>
  );
}
