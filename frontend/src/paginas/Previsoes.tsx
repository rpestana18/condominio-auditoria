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
                <td>{p.arquivoNome ?? p.titulo}</td>
                <td>{p.versao ?? "—"}</td>
                <td>
                  {p.exercicioInicio && p.exercicioFim
                    ? `${formatarMes(p.exercicioInicio)} a ${formatarMes(p.exercicioFim)}`
                    : (p.exercicioImpresso ?? "—")}
                </td>
                <td className="numero">{formatarMoeda(p.previstoMes)}</td>
                <td>
                  <span className={p.estado === "CONFIRMADA" ? "selo ok" : "selo alerta"}>{rotuloEstadoPo[p.estado]}</span>
                </td>
                <td>{formatarDataHora(p.lidaEm)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}
