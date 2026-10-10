import type { ContaDepara } from "../../api/tipos";
import { formatarMoeda } from "../../formato";
import { rotuloOrigem } from "../previsto/rotulos";
import { SeloEstadoDepara } from "./SeloEstadoDepara";

interface Props {
  contas: ContaDepara[];
  /** Só o Admin recebe seleção e edição; para os demais a tabela é só consulta. */
  edicao?: {
    selecionadas: Set<string>;
    aoSelecionar: (conta: string, marcada: boolean) => void;
    aoSelecionarTodas: (marcadas: boolean) => void;
    aoEditar: (conta: ContaDepara) => void;
  };
}

/** Contas do fluxo com o destino, o estado, a origem e o motivo da sugestão (RF-03.1.13). */
export function TabelaDepara({ contas, edicao }: Props) {
  const todas = edicao && contas.length > 0 && contas.every((c) => edicao.selecionadas.has(c.account));
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
            <th>Conta do fluxo</th>
            <th className="numero">Lançamentos</th>
            <th className="numero">Débitos no exercício</th>
            <th>Destino</th>
            <th>Estado</th>
            <th>Origem e motivo</th>
            {edicao && <th aria-label="Ações" />}
          </tr>
        </thead>
        <tbody>
          {contas.map((c) => (
            <tr key={c.account} className={edicao?.selecionadas.has(c.account) ? "selecionado" : undefined}>
              {edicao && (
                <td>
                  <input
                    type="checkbox"
                    aria-label={`Selecionar a conta ${c.account}`}
                    checked={edicao.selecionadas.has(c.account)}
                    onChange={(e) => edicao.aoSelecionar(c.account, e.target.checked)}
                  />
                </td>
              )}
              <td>
                <strong>{c.account}</strong> {c.name}
              </td>
              <td className="numero">{c.ledgerEntries}</td>
              <td className="numero">{formatarMoeda(c.debits)}</td>
              <td>{c.target?.text ?? <span className="discreto">—</span>}</td>
              <td>
                <SeloEstadoDepara estado={c.status} />
                {c.sameAsPreviousVersion && <span className="selo neutro">igual à versão anterior</span>}
              </td>
              <td className="discreto">
                {c.source && <span>{rotuloOrigem[c.source]}</span>}
                {c.reason && <small className="observacao">{c.reason}</small>}
              </td>
              {edicao && (
                <td>
                  <button className="botao-link" onClick={() => edicao.aoEditar(c)}>
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
