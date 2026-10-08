import type { LinhaComRubrica } from "../../api/tipos";
import { formatarMoeda } from "../../formato";
import { rotuloOrigemRubrica } from "../previsto/rotulos";
import { SeloEstadoRubrica } from "./SeloEstadoRubrica";

interface Props {
  linhas: LinhaComRubrica[];
  /** Só o Admin recebe seleção e edição; para os demais a tabela é só consulta. */
  edicao?: {
    selecionadas: Set<string>;
    aoSelecionar: (linhaId: string, marcada: boolean) => void;
    aoSelecionarTodas: (marcadas: boolean) => void;
    aoEditar: (linha: LinhaComRubrica) => void;
  };
}

/** Linhas da PO com a rubrica, o estado, a origem e o motivo da sugestão (RF-11.7). */
export function TabelaRubricas({ linhas, edicao }: Props) {
  const todas = edicao && linhas.length > 0 && linhas.every((l) => edicao.selecionadas.has(l.linhaId));
  return (
    <div className="rolagem">
      <table className="tabela">
        <thead>
          <tr>
            {edicao && (
              <th>
                <input type="checkbox" aria-label="Selecionar todas" checked={!!todas} onChange={(e) => edicao.aoSelecionarTodas(e.target.checked)} />
              </th>
            )}
            <th>Linha da PO</th>
            <th>Conta da PO</th>
            <th className="numero">Orçado</th>
            <th>Rubrica</th>
            <th>Estado</th>
            <th>Origem e motivo</th>
            {edicao && <th aria-label="Ações" />}
          </tr>
        </thead>
        <tbody>
          {linhas.map((l) => (
            <tr key={l.linhaId} className={edicao?.selecionadas.has(l.linhaId) ? "selecionado" : undefined}>
              {edicao && (
                <td>
                  <input
                    type="checkbox"
                    aria-label={`Selecionar a linha ${l.codigo}`}
                    checked={edicao.selecionadas.has(l.linhaId)}
                    onChange={(e) => edicao.aoSelecionar(l.linhaId, e.target.checked)}
                  />
                </td>
              )}
              <td>
                <strong>{l.codigo}</strong> {l.descricao}
                {l.grupo && <small className="observacao">grupo {l.grupo}</small>}
              </td>
              <td className="discreto">{l.conta}</td>
              <td className="numero">{formatarMoeda(l.orcado)}</td>
              <td>{l.rubrica?.nome ?? <span className="discreto">—</span>}</td>
              <td>
                <SeloEstadoRubrica estado={l.estado} />
              </td>
              <td className="discreto">
                {l.origem && <span>{rotuloOrigemRubrica[l.origem]}</span>}
                {l.motivo && <small className="observacao">{l.motivo}</small>}
              </td>
              {edicao && (
                <td>
                  <button className="botao-link" onClick={() => edicao.aoEditar(l)}>
                    Alterar
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
