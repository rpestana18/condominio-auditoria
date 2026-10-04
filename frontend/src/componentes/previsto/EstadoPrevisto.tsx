import { Link } from "react-router";
import type { PrevistoRealizado } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { rotuloEstadoPo } from "./rotulos";

/**
 * Estado da PO e do de-para (RF-03.1.13). O aviso "N contas sem linha da PO" aparece para todos;
 * para o Admin ele leva à tela de de-para já filtrada em "pendentes".
 */
export function EstadoPrevisto({ resultado }: { resultado: PrevistoRealizado }) {
  const { pode } = useSessao();
  const { po, depara } = resultado;
  const pendentes = depara?.semDeparaConfirmado ?? 0;
  const linkPendentes = po ? `/depara?po=${po.id}&filtro=PENDENTES` : "/depara?filtro=PENDENTES";

  return (
    <div className="estado-previsto">
      {po && (
        <Link to={`/previsoes/${po.id}`} className={po.estado === "CONFIRMADA" ? "selo ok" : "selo alerta"}>
          PO {po.versao ? `versão ${po.versao}` : ""} · {rotuloEstadoPo[po.estado]}
        </Link>
      )}
      {depara && (
        <span className={pendentes === 0 ? "selo ok" : "selo alerta"}>
          De-para: {depara.confirmadas} de {depara.contas} contas confirmadas
        </span>
      )}
      {resultado.provisorio && (
        <span className="selo alerta" title="Há valor a realocar ou conta sem linha da PO">
          Provisório
        </span>
      )}
      {pendentes > 0 &&
        (pode("ADMIN") ? (
          <Link className="aviso alerta" to={linkPendentes}>
            {pendentes} {pendentes === 1 ? "conta" : "contas"} sem linha da PO · revisar o de-para
          </Link>
        ) : (
          <span className="aviso alerta">
            {pendentes} {pendentes === 1 ? "conta" : "contas"} sem linha da PO
          </span>
        ))}
    </div>
  );
}
