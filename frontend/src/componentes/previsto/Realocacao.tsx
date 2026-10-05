import { useState } from "react";
import { useDesfazerRealocacao, usePrevisao, useRealocacoes, useRealocar } from "../../api/consultasOrcamento";
import type { EvidenciaLancamento } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarData, formatarDataHora, formatarMoeda } from "../../formato";

/** Linhas de despesa (1.1 a 1.8) da PO, únicas que podem receber uma realocação. */
function useLinhasDestino(poId: string) {
  const { condominioId } = useSessao();
  const { data } = usePrevisao(condominioId, poId);
  return data?.linhas.filter((l) => l.tipo === "LINHA" && !l.linhaDeFundo) ?? [];
}

/**
 * Escolha da linha para um lançamento "a realocar" (RF-03.1.7). Só Gestor e Admin chegam aqui.
 * O lançamento original da administradora não muda: a realocação é uma camada do sistema.
 */
export function RealocarLancamento({ poId, lancamento }: { poId: string; lancamento: EvidenciaLancamento }) {
  const { condominioId } = useSessao();
  const linhas = useLinhasDestino(poId);
  const realocar = useRealocar(condominioId);
  const [linhaId, setLinhaId] = useState("");
  return (
    <div className="realocar">
      <select value={linhaId} onChange={(e) => setLinhaId(e.target.value)} aria-label="Linha da PO">
        <option value="">Realocar para a linha…</option>
        {linhas.map((l) => (
          <option key={l.id} value={l.id}>
            {l.codigoEfetivo} {l.descricao}
          </option>
        ))}
      </select>
      <button
        className="botao secundario"
        disabled={!linhaId || realocar.isPending}
        onClick={() => realocar.mutate({ lancamentoId: lancamento.lancamentoId, linhaId })}
      >
        Realocar
      </button>
      {realocar.isError && <span className="aviso erro">{realocar.error.message}</span>}
    </div>
  );
}

/** Botão de desfazer: o valor volta a "a realocar" e a realocação fica no histórico como desfeita. */
export function DesfazerRealocacao({ realocacaoId }: { realocacaoId: string }) {
  const { condominioId } = useSessao();
  const desfazer = useDesfazerRealocacao(condominioId);
  return (
    <>
      <button className="botao-link" disabled={desfazer.isPending} onClick={() => desfazer.mutate(realocacaoId)}>
        Desfazer realocação
      </button>
      {desfazer.isError && <span className="aviso erro"> {desfazer.error.message}</span>}
    </>
  );
}

/** Realocações desta versão da PO, ativas e desfeitas (todos consultam; Gestor e Admin desfazem). */
export function ListaRealocacoes({ poId }: { poId: string }) {
  const { condominioId, pode } = useSessao();
  const { data: realocacoes = [] } = useRealocacoes(condominioId, poId);
  if (realocacoes.length === 0) return null;
  return (
    <details>
      <summary>Realocações feitas ({realocacoes.filter((r) => r.ativa).length} ativas)</summary>
      <table className="tabela compacta">
        <thead>
          <tr>
            <th>Lançamento</th>
            <th className="numero">Valor</th>
            <th>Linha</th>
            <th>Quem e quando</th>
            <th aria-label="Ações" />
          </tr>
        </thead>
        <tbody>
          {realocacoes.map((r) => (
            <tr key={r.id} className={r.ativa ? undefined : "desfeita"}>
              <td>
                {formatarData(r.data)} · {r.conta} {r.historico} <span className="discreto">pág. {r.pagina}</span>
              </td>
              <td className="numero">{formatarMoeda(r.valor)}</td>
              <td>
                {r.linhaCodigo} {r.linhaDescricao}
              </td>
              <td className="discreto">
                {r.realocadaPor} em {formatarDataHora(r.realocadaEm)}
                {!r.ativa && r.desfeitaEm && ` · desfeita por ${r.desfeitaPor} em ${formatarDataHora(r.desfeitaEm)}`}
              </td>
              <td>{r.ativa && pode("GESTOR", "ADMIN") && <DesfazerRealocacao realocacaoId={r.id} />}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  );
}
