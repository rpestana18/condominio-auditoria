import { useState } from "react";
import { useEventosRubricas } from "../../api/consultasAnalisePo";
import type { EstadoRubrica } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarDataHora } from "../../formato";
import { rotuloAcaoRubrica, rotuloEstadoRubrica, rotuloOrigemRubrica } from "../previsto/rotulos";

/** " (confirmado)" depois do nome da rubrica; nada sem estado. */
const comEstado = (estado: EstadoRubrica | null | undefined) => (estado ? ` (${rotuloEstadoRubrica[estado].toLowerCase()})` : "");

/** Trilha das rubricas desta PO (só de inserção): quem, quando, rubrica e estado anteriores e novos. */
export function TrilhaRubricas({ poId }: { poId: string }) {
  const { condominioId } = useSessao();
  const [aberta, setAberta] = useState(false);
  const { data: eventos = [], isLoading } = useEventosRubricas(condominioId, poId, aberta);

  return (
    <details className="trilha" onToggle={(e) => setAberta(e.currentTarget.open)}>
      <summary>Histórico das rubricas</summary>
      {isLoading && <p className="aviso">Carregando…</p>}
      {aberta && !isLoading && eventos.length === 0 && <p className="aviso">Nenhuma alteração registrada.</p>}
      {eventos.length > 0 && (
        <div className="rolagem">
          <table className="tabela compacta">
            <thead>
              <tr>
                <th>Quando</th>
                <th>Quem</th>
                <th>Linha</th>
                <th>Ação</th>
                <th>Antes</th>
                <th>Depois</th>
                <th>Origem</th>
              </tr>
            </thead>
            <tbody>
              {eventos.map((e) => (
                <tr key={e.id}>
                  <td>{formatarDataHora(e.em)}</td>
                  <td>{e.usuario}</td>
                  <td>{e.codigo ? `${e.codigo} ${e.descricao ?? ""}` : <span className="discreto">—</span>}</td>
                  <td>{rotuloAcaoRubrica[e.acao]}</td>
                  <td className="discreto">
                    {e.rubricaAnterior ?? "—"}
                    {comEstado(e.estadoAnterior)}
                  </td>
                  <td>
                    {e.rubricaNova}
                    {comEstado(e.estadoNovo)}
                  </td>
                  <td className="discreto">
                    {e.origem && rotuloOrigemRubrica[e.origem]}
                    {e.motivo && <small className="observacao">{e.motivo}</small>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </details>
  );
}
