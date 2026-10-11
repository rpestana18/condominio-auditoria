import { openFile } from "../api/client";
import { useFileDetail, useReprocess } from "../api/queries";
import { useSession } from "../context";
import { formatDateTime, formatMoney, formatPeriod, formatSize } from "../format";
import { FileIndexingBadge } from "./FileIndexingBadge";
import { FileStatusBadge } from "./FileStatusBadge";

export function FileDetail({ id, onClose }: { id: string; onClose: () => void }) {
  const { condominiumId, can, isFeatureEnabled } = useSession();
  const { data: detail } = useFileDetail(condominiumId, id);
  const reprocess = useReprocess(condominiumId);
  if (!detail) return null;
  const { file } = detail;
  const busy = file.status === "PENDING" || file.status === "PROCESSING";

  return (
    <aside className="detail" aria-label="Detalhe do arquivo">
      <header>
        <h2>{file.name}</h2>
        <button className="link-button" onClick={onClose} aria-label="Fechar">
          ✕
        </button>
      </header>
      <dl>
        <dt>Situação</dt>
        <dd>
          <FileStatusBadge status={file.status} /> {file.message}
        </dd>
        {(isFeatureEnabled("ASSISTANT") || file.indexing) && (
          <>
            <dt title="Indexação para a busca nos documentos e o assistente">Busca</dt>
            <dd>
              <FileIndexingBadge indexing={file.indexing} full />
            </dd>
          </>
        )}
        <dt>Categoria</dt>
        <dd>{file.categoryLabel}</dd>
        {file.periodStart && (
          <>
            <dt>Período</dt>
            <dd>
              {formatPeriod(file.periodStart, file.periodEnd)} · {file.entryCount} lançamentos
            </dd>
          </>
        )}
        <dt>Enviado</dt>
        <dd>
          {formatDateTime(file.uploadedAt)} por {file.uploadedBy} · {formatSize(file.sizeBytes)}
        </dd>
        <dt title="Impressão digital do arquivo: identifica o conteúdo exato">SHA-256</dt>
        <dd className="hash">{detail.sha256}</dd>
      </dl>
      <div className="actions">
        <button className="button secondary" onClick={() => openFile(`/condominiums/${condominiumId}/files/${id}/content`)}>
          Abrir original
        </button>
        {can("MANAGER", "ADMIN") && (
          <button className="button secondary" disabled={busy || reprocess.isPending} onClick={() => reprocess.mutate(id)}>
            Reprocessar
          </button>
        )}
      </div>

      {detail.totalsChecks.length > 0 && (
        <section>
          <h3>Conferências</h3>
          <ul className="checks">
            {detail.totalsChecks.map((c) => (
              <li key={c.code} className={c.ok ? "ok" : "fail"}>
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

      {detail.funds.length > 0 && (
        <section>
          <h3>Posição por fundo</h3>
          <table className="table compact">
            <thead>
              <tr>
                <th>Fundo</th>
                <th className="number">Resultado</th>
                <th className="number">Saldo</th>
              </tr>
            </thead>
            <tbody>
              {detail.funds.map((f) => (
                <tr key={f.fund}>
                  <td>{f.fund}</td>
                  <td className={`number ${f.credits - f.debits < 0 ? "negative" : ""}`}>
                    {formatMoney(f.credits - f.debits)}
                  </td>
                  <td className={`number ${f.closingBalance < 0 ? "negative" : ""}`}>{formatMoney(f.closingBalance)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </aside>
  );
}
