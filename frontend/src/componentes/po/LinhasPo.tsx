import { abrirArquivo } from "../../api/cliente";
import type { LinhaPo } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarMoeda } from "../../formato";
import { rotuloMarca } from "../previsto/rotulos";

interface Props {
  linhas: LinhaPo[];
  colunaOrcadoAnterior?: string | null;
  colunaOrcado?: string | null;
}

/** As linhas como foram lidas (RF-03.1.1). Valores lidos nunca são editados aqui. */
export function LinhasPo({ linhas, colunaOrcadoAnterior, colunaOrcado }: Props) {
  const { condominioId } = useSessao();
  return (
    <section className="bloco">
      <h2>Linhas lidas</h2>
      <div className="rolagem">
        <table className="tabela compacta previsto">
          <thead>
            <tr>
              <th>Código</th>
              <th>Conta da PO</th>
              <th>Descrição</th>
              <th className="numero">Orçado {colunaOrcadoAnterior ?? "anterior"}</th>
              <th className="numero">Orçado {colunaOrcado ?? ""}</th>
              <th className="numero">%</th>
              <th>Observações</th>
              <th>Origem</th>
            </tr>
          </thead>
          <tbody>
            {linhas.map((l) => (
              <tr key={l.id} className={l.tipo === "LINHA" ? undefined : "linha-grupo"}>
                <td>
                  {l.codigoEfetivo}
                  {l.codigoEfetivo !== l.codigoImpresso && <small className="discreto"> (impresso {l.codigoImpresso})</small>}
                </td>
                <td>{l.marca ? <span className="selo neutro">{rotuloMarca[l.marca]}</span> : (l.conta ?? l.contaTexto ?? "")}</td>
                <td>{l.descricao}</td>
                <td className="numero">{formatarMoeda(l.orcadoAnterior)}</td>
                <td className="numero">{formatarMoeda(l.orcado)}</td>
                <td className="numero">{l.percentualTexto}</td>
                <td className="discreto">{l.observacoes}</td>
                <td>
                  <button
                    className="botao-link"
                    title={`SHA-256 ${l.sha256}`}
                    onClick={() => void abrirArquivo(`/condominios/${condominioId}/arquivos/${l.arquivoId}/conteudo`, l.pagina)}
                  >
                    pág. {l.pagina}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
