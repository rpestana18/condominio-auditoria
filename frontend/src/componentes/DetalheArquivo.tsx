import { abrirArquivo } from "../api/cliente";
import { useDetalheArquivo, useReprocessar } from "../api/consultas";
import { useSessao } from "../contexto";
import { formatarDataHora, formatarMoeda, formatarPeriodo, formatarTamanho } from "../formato";
import { IndexacaoArquivo } from "./IndexacaoArquivo";
import { StatusArquivo } from "./StatusArquivo";

export function DetalheArquivo({ id, aoFechar }: { id: string; aoFechar: () => void }) {
  const { condominioId, pode, moduloLigado } = useSessao();
  const { data: detalhe } = useDetalheArquivo(condominioId, id);
  const reprocessar = useReprocessar(condominioId);
  if (!detalhe) return null;
  const { file: arquivo } = detalhe;
  const ocupado = arquivo.status === "PENDING" || arquivo.status === "PROCESSING";

  return (
    <aside className="detalhe" aria-label="Detalhe do arquivo">
      <header>
        <h2>{arquivo.name}</h2>
        <button className="botao-link" onClick={aoFechar} aria-label="Fechar">
          ✕
        </button>
      </header>
      <dl>
        <dt>Situação</dt>
        <dd>
          <StatusArquivo status={arquivo.status} /> {arquivo.message}
        </dd>
        {(moduloLigado("ASSISTANT") || arquivo.indexing) && (
          <>
            <dt title="Indexação para a busca nos documentos e o assistente">Busca</dt>
            <dd>
              <IndexacaoArquivo indexacao={arquivo.indexing} completo />
            </dd>
          </>
        )}
        <dt>Categoria</dt>
        <dd>{arquivo.categoryLabel}</dd>
        {arquivo.periodStart && (
          <>
            <dt>Período</dt>
            <dd>
              {formatarPeriodo(arquivo.periodStart, arquivo.periodEnd)} · {arquivo.entryCount} lançamentos
            </dd>
          </>
        )}
        <dt>Enviado</dt>
        <dd>
          {formatarDataHora(arquivo.uploadedAt)} por {arquivo.uploadedBy} · {formatarTamanho(arquivo.sizeBytes)}
        </dd>
        <dt title="Impressão digital do arquivo: identifica o conteúdo exato">SHA-256</dt>
        <dd className="hash">{detalhe.sha256}</dd>
      </dl>
      <div className="acoes">
        <button className="botao secundario" onClick={() => abrirArquivo(`/condominiums/${condominioId}/files/${id}/content`)}>
          Abrir original
        </button>
        {pode("MANAGER", "ADMIN") && (
          <button className="botao secundario" disabled={ocupado || reprocessar.isPending} onClick={() => reprocessar.mutate(id)}>
            Reprocessar
          </button>
        )}
      </div>

      {detalhe.totalsChecks.length > 0 && (
        <section>
          <h3>Conferências</h3>
          <ul className="conferencias">
            {detalhe.totalsChecks.map((c) => (
              <li key={c.code} className={c.ok ? "ok" : "falha"}>
                <span>{c.ok ? "✔" : "✖"}</span>
                <div>
                  <strong>{c.description}</strong>
                  <small>{c.detail}</small>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {detalhe.funds.length > 0 && (
        <section>
          <h3>Posição por fundo</h3>
          <table className="tabela compacta">
            <thead>
              <tr>
                <th>Fundo</th>
                <th className="numero">Resultado</th>
                <th className="numero">Saldo</th>
              </tr>
            </thead>
            <tbody>
              {detalhe.funds.map((f) => (
                <tr key={f.fund}>
                  <td>{f.fund}</td>
                  <td className={`numero ${f.credits - f.debits < 0 ? "negativo" : ""}`}>
                    {formatarMoeda(f.credits - f.debits)}
                  </td>
                  <td className={`numero ${f.closingBalance < 0 ? "negativo" : ""}`}>{formatarMoeda(f.closingBalance)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </aside>
  );
}
