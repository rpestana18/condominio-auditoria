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
  const { data: resultado } = usePrevistoRealizado(condominioId, "acumulado");
  if (!resultado) return null;

  const totais = resultado.situacao === "CALCULADO" ? resultado.totais : null;
  return (
    <Link to="/previsto-realizado?periodo=acumulado" className="cartao-numero cartao-link" title="Abrir o previsto × realizado">
      <span className="cartao-titulo">Previsto × realizado no exercício</span>
      {totais ? (
        <>
          <strong>{formatarPercentual(totais.execucao)}</strong>
          <span className="discreto">
            {formatarMoeda(totais.despesaRealizada)} de {formatarMoeda(totais.previsto)}
          </span>
          <span className="discreto">
            {resultado.mesesSomados.length} {resultado.mesesSomados.length === 1 ? "mês" : "meses"} com fluxo
            {resultado.mesesSemFluxo.length > 0 && ` · faltam ${resultado.mesesSemFluxo.map(formatarMes).join(", ")}`}
            {resultado.provisorio && " · provisório"}
          </span>
        </>
      ) : (
        <span className="discreto">{resultado.mensagem ?? "Sem números para o exercício"}</span>
      )}
    </Link>
  );
}
