import { Link } from "react-router";
import { usePrevistoRealizado } from "../api/consultasOrcamento";
import { useSessao } from "../contexto";
import { formatarMes, formatarMoeda, formatarPercentual } from "../formato";

/**
 * Cartão "Previsto × realizado no exercício" da tela inicial (RF-03.1.13 e RF-05.1).
 * Usa o acumulado do exercício da PO, calculado pelo backend, e leva à tela completa.
 */
export function CartaoPrevistoAno() {
  const { condominioId } = useSessao();
  const { data: resultado } = usePrevistoRealizado(condominioId, "cumulative");
  if (!resultado) return null;

  const totais = resultado.status === "CALCULATED" ? resultado.totals : null;
  return (
    <Link to="/previsto-realizado?periodo=acumulado" className="cartao-numero cartao-link" title="Abrir o previsto × realizado">
      <span className="cartao-titulo">Previsto × realizado no exercício</span>
      {totais ? (
        <>
          <strong>{formatarPercentual(totais.execution)}</strong>
          <span className="discreto">
            {formatarMoeda(totais.actualExpense)} de {formatarMoeda(totais.planned)}
          </span>
          <span className="discreto">
            {resultado.summedMonths.length} {resultado.summedMonths.length === 1 ? "mês" : "meses"} com fluxo
            {resultado.monthsWithoutCashFlow.length > 0 && ` · faltam ${resultado.monthsWithoutCashFlow.map(formatarMes).join(", ")}`}
            {resultado.provisional && " · provisório"}
          </span>
        </>
      ) : (
        <span className="discreto">{resultado.message ?? "Sem números para o exercício"}</span>
      )}
    </Link>
  );
}
