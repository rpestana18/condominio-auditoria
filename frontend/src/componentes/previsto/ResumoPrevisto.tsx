import type { PrevistoRealizado, TotaisPrevistoRealizado } from "../../api/tipos";
import { classeDiferenca, formatarDiferenca, formatarMes, formatarMoeda, formatarPercentual } from "../../formato";

interface Props {
  resultado: PrevistoRealizado;
  totais: TotaisPrevistoRealizado;
}

/** Cartões do topo: previsto, despesa realizada, diferença e execução (todos calculados pelo backend). */
export function ResumoPrevisto({ resultado, totais }: Props) {
  const acumulado = resultado.periodo === "acumulado";
  return (
    <>
      <div className="cartoes">
        <Cartao titulo={acumulado ? "Previsto dos meses com fluxo" : "Previsto do mês"} valor={formatarMoeda(totais.previsto)} />
        <Cartao
          titulo="Despesa realizada"
          valor={formatarMoeda(totais.despesaRealizada)}
          dica={`Em linhas da PO: ${formatarMoeda(totais.emLinhas)}; o restante está em "a realocar" e "sem linha da PO"`}
        />
        <Cartao titulo="Diferença" valor={formatarDiferenca(totais.diferenca)} classe={classeDiferenca(totais.diferenca)} />
        <Cartao titulo="Execução" valor={formatarPercentual(totais.execucao)} dica="Realizado ÷ previsto" />
        {acumulado && (
          <Cartao
            titulo="Previsto do exercício (referência)"
            valor={formatarMoeda(totais.previstoExercicio)}
            dica={`Previsto do mês (${formatarMoeda(totais.previstoMes)}) × meses do exercício`}
          />
        )}
      </div>
      {acumulado && <MesesFaltando resultado={resultado} />}
    </>
  );
}

function Cartao({ titulo, valor, classe, dica }: { titulo: string; valor: string; classe?: string; dica?: string }) {
  return (
    <div className="cartao-numero" title={dica}>
      <span className="cartao-titulo">{titulo}</span>
      <strong className={classe}>{valor}</strong>
    </div>
  );
}

/** RF-03.1.10: o acumulado diz quais meses não entraram (nunca os trata como zero). */
function MesesFaltando({ resultado }: { resultado: PrevistoRealizado }) {
  const { mesesSomados, mesesSemFluxo, mesesComDoisFluxos } = resultado;
  return (
    <p className="discreto">
      Somados: {mesesSomados.map(formatarMes).join(", ") || "nenhum mês"}.
      {mesesSemFluxo.length > 0 && ` Sem fluxo carregado: ${mesesSemFluxo.map(formatarMes).join(", ")}.`}
      {mesesComDoisFluxos.length > 0 && ` Com dois fluxos (não somados): ${mesesComDoisFluxos.map(formatarMes).join(", ")}.`}
    </p>
  );
}
