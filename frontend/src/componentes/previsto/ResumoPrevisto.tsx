import type { PrevistoRealizado, TotaisPrevistoRealizado } from "../../api/tipos";
import { classeDiferenca, formatarDiferenca, formatarMes, formatarMoeda, formatarPercentual } from "../../formato";
import type { AbrirEvidencia } from "./evidencia";
import { ValorComFonte } from "./ValorComFonte";

interface Props {
  resultado: PrevistoRealizado;
  totais: TotaisPrevistoRealizado;
  aoAbrirEvidencia: AbrirEvidencia;
}

/** Cartões do topo: previsto, despesa realizada, diferença e execução (todos calculados pelo backend). */
export function ResumoPrevisto({ resultado, totais, aoAbrirEvidencia }: Props) {
  const acumulado = resultado.period === "cumulative";
  return (
    <>
      <div className="cartoes">
        <Cartao titulo={acumulado ? "Previsto dos meses com fluxo" : "Previsto do mês"} valor={formatarMoeda(totais.planned)} />
        <div
          className="cartao-numero"
          title={`Em linhas da PO: ${formatarMoeda(totais.inLines)}; o restante está em "a realocar" e "sem linha da PO"`}
        >
          <span className="cartao-titulo">Despesa realizada</span>
          <strong>
            <ValorComFonte
              valor={totais.actualExpense}
              aoAbrir={() => aoAbrirEvidencia({ alvo: "total", titulo: "Despesa realizada" })}
            />
          </strong>
        </div>
        <Cartao titulo="Diferença" valor={formatarDiferenca(totais.difference)} classe={classeDiferenca(totais.difference)} />
        <Cartao titulo="Execução" valor={formatarPercentual(totais.execution)} dica="Realizado ÷ previsto" />
        {acumulado && (
          <Cartao
            titulo="Previsto do exercício (referência)"
            valor={formatarMoeda(totais.fiscalYearPlanned)}
            dica={`Previsto do mês (${formatarMoeda(totais.monthlyPlanned)}) × meses do exercício`}
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
  const { summedMonths: mesesSomados, monthsWithoutCashFlow: mesesSemFluxo, monthsWithTwoCashFlows: mesesComDoisFluxos } = resultado;
  return (
    <p className="discreto">
      Somados: {mesesSomados.map(formatarMes).join(", ") || "nenhum mês"}.
      {mesesSemFluxo.length > 0 && ` Sem fluxo carregado: ${mesesSemFluxo.map(formatarMes).join(", ")}.`}
      {mesesComDoisFluxos.length > 0 && ` Com dois fluxos (não somados): ${mesesComDoisFluxos.map(formatarMes).join(", ")}.`}
    </p>
  );
}
