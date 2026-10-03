import { abrirArquivo } from "../api/cliente";
import { useDetalheArquivo, useReprocessar } from "../api/consultas";
import { useSessao } from "../contexto";
import { formatarDataHora, formatarMoeda, formatarPeriodo, formatarTamanho } from "../formato";
import { StatusArquivo } from "./StatusArquivo";

export function DetalheArquivo({ id, aoFechar }: { id: string; aoFechar: () => void }) {
  const { condominioId, pode } = useSessao();
  const { data: detalhe } = useDetalheArquivo(condominioId, id);
  const reprocessar = useReprocessar(condominioId);
  if (!detalhe) return null;
  const { arquivo } = detalhe;
  const ocupado = arquivo.status === "PENDENTE" || arquivo.status === "PROCESSANDO";

  return (
    <aside className="detalhe" aria-label="Detalhe do arquivo">
      <header>
        <h2>{arquivo.nome}</h2>
        <button className="botao-link" onClick={aoFechar} aria-label="Fechar">
          ✕
        </button>
      </header>
      <dl>
        <dt>Situação</dt>
        <dd>
          <StatusArquivo status={arquivo.status} /> {arquivo.mensagem}
        </dd>
        <dt>Categoria</dt>
        <dd>{arquivo.categoriaRotulo}</dd>
        {arquivo.periodoInicio && (
          <>
            <dt>Período</dt>
            <dd>
              {formatarPeriodo(arquivo.periodoInicio, arquivo.periodoFim)} · {arquivo.totalLancamentos} lançamentos
            </dd>
          </>
        )}
        <dt>Enviado</dt>
        <dd>
          {formatarDataHora(arquivo.enviadoEm)} por {arquivo.enviadoPor} · {formatarTamanho(arquivo.tamanhoBytes)}
        </dd>
        <dt title="Impressão digital do arquivo: identifica o conteúdo exato">SHA-256</dt>
        <dd className="hash">{detalhe.sha256}</dd>
      </dl>
      <div className="acoes">
        <button className="botao secundario" onClick={() => abrirArquivo(`/condominios/${condominioId}/arquivos/${id}/conteudo`)}>
          Abrir original
        </button>
        {pode("GESTOR", "ADMIN") && (
          <button className="botao secundario" disabled={ocupado || reprocessar.isPending} onClick={() => reprocessar.mutate(id)}>
            Reprocessar
          </button>
        )}
      </div>

      {detalhe.conferencias.length > 0 && (
        <section>
          <h3>Conferências</h3>
          <ul className="conferencias">
            {detalhe.conferencias.map((c) => (
              <li key={c.codigo} className={c.ok ? "ok" : "falha"}>
                <span>{c.ok ? "✔" : "✖"}</span>
                <div>
                  <strong>{c.descricao}</strong>
                  <small>{c.detalhe}</small>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {detalhe.fundos.length > 0 && (
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
              {detalhe.fundos.map((f) => (
                <tr key={f.fundo}>
                  <td>{f.fundo}</td>
                  <td className={`numero ${f.creditos - f.debitos < 0 ? "negativo" : ""}`}>
                    {formatarMoeda(f.creditos - f.debitos)}
                  </td>
                  <td className={`numero ${f.saldoAtual < 0 ? "negativo" : ""}`}>{formatarMoeda(f.saldoAtual)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </aside>
  );
}
