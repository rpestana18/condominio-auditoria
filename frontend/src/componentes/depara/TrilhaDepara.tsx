import { useState } from "react";
import { useEventosDepara } from "../../api/consultasOrcamento";
import { useSessao } from "../../contexto";
import { formatarDataHora } from "../../formato";
import { rotuloEstadoDepara, rotuloOrigem } from "../previsto/rotulos";

/** Trilha do de-para (só de inserção): quem, quando, destino e estado anteriores e novos. */
export function TrilhaDepara({ poId }: { poId: string }) {
  const { condominioId } = useSessao();
  const [aberta, setAberta] = useState(false);
  const { data: eventos = [], isLoading } = useEventosDepara(condominioId, poId, aberta);

  return (
    <details className="bloco" onToggle={(e) => setAberta(e.currentTarget.open)}>
      <summary>Histórico de alterações</summary>
      {isLoading && <p className="aviso">Carregando…</p>}
      {aberta && !isLoading && eventos.length === 0 && <p className="aviso">Nenhuma alteração registrada.</p>}
      {eventos.length > 0 && (
        <table className="tabela compacta">
          <thead>
            <tr>
              <th>Quando</th>
              <th>Quem</th>
              <th>Conta</th>
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
                <td>
                  {e.conta} {e.nome}
                </td>
                <td className="discreto">
                  {e.destinoAnterior ?? "—"}
                  {e.estadoAnterior && ` (${rotuloEstadoDepara[e.estadoAnterior].toLowerCase()})`}
                </td>
                <td>
                  {e.destinoNovo} ({rotuloEstadoDepara[e.estadoNovo].toLowerCase()})
                </td>
                <td className="discreto">
                  {rotuloOrigem[e.origem]}
                  {e.motivo && ` · ${e.motivo}`}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </details>
  );
}
