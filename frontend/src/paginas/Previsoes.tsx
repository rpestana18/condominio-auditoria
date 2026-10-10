import { useNavigate } from "react-router";
import { usePrevisoes } from "../api/consultasOrcamento";
import { rotuloEstadoPo } from "../componentes/previsto/rotulos";
import { useSessao } from "../contexto";
import { formatarDataHora, formatarMes, formatarMoeda } from "../formato";

/** POs lidas do condomínio, da mais recente para a mais antiga (como vêm da API). */
export function Previsoes() {
  const { condominioId } = useSessao();
  const navegar = useNavigate();
  const { data: previsoes = [], isLoading, error } = usePrevisoes(condominioId);

  return (
    <>
      <header className="titulo-pagina">
        <h1>Previsões orçamentárias (PO)</h1>
      </header>
      {isLoading && <p className="aviso">Carregando…</p>}
      {error && <p className="aviso erro">{error.message}</p>}
      {!isLoading && previsoes.length === 0 && (
        <p className="aviso">Nenhuma PO lida ainda. Envie a PO aprovada na categoria PO, em Arquivos.</p>
      )}
      {previsoes.length > 0 && (
        <table className="tabela clicavel">
          <thead>
            <tr>
              <th>Arquivo</th>
              <th>Versão</th>
              <th>Exercício</th>
              <th className="numero">Previsto do mês</th>
              <th>Situação</th>
              <th>Lida em</th>
            </tr>
          </thead>
          <tbody>
            {previsoes.map((p) => (
              <tr key={p.id} onClick={() => navegar(`/previsoes/${p.id}`)}>
                <td>{p.fileName ?? p.title}</td>
                <td>{p.version ?? "—"}</td>
                <td>
                  {p.fiscalYearStart && p.fiscalYearEnd
                    ? `${formatarMes(p.fiscalYearStart)} a ${formatarMes(p.fiscalYearEnd)}`
                    : (p.printedFiscalYear ?? "—")}
                </td>
                <td className="numero">{formatarMoeda(p.monthlyPlanned)}</td>
                <td>
                  <span className={p.status === "CONFIRMED" ? "selo ok" : "selo alerta"}>{rotuloEstadoPo[p.status]}</span>
                </td>
                <td>{formatarDataHora(p.readAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}
