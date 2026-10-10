import { abrirArquivo } from "../../api/cliente";
import { useEvidencia, type Periodo } from "../../api/consultasOrcamento";
import type { PrevistoRealizado } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarData, formatarMoeda, hashCurto } from "../../formato";
import type { AlvoEvidencia } from "./evidencia";
import { DesfazerRealocacao, RealocarLancamento } from "./Realocacao";

interface Props {
  periodo: Periodo;
  po: NonNullable<PrevistoRealizado["budget"]>;
  alvo: AlvoEvidencia;
  aoFechar: () => void;
}

/**
 * Lançamentos que compõem um número (RF-03.1.12). Mostra só fatos: data, histórico, favorecido, valor,
 * conta, arquivo, página e hash. Nenhum texto sobre a causa da diferença.
 */
export function PainelEvidencia({ periodo, po, alvo, aoFechar }: Props) {
  const { condominioId, pode } = useSessao();
  const podeRealocar = pode("GESTOR", "ADMIN");
  const { data: lancamentos = [], isLoading, error } = useEvidencia(condominioId, periodo, po.id, alvo.alvo);
  const abrirNaPagina = (arquivoId: string, pagina: number) =>
    void abrirArquivo(`/condominiums/${condominioId}/files/${arquivoId}/content`, pagina);

  return (
    <aside className="detalhe" aria-label="Evidência">
      <header>
        <h2>{alvo.titulo}</h2>
        <button className="botao-link" onClick={aoFechar} aria-label="Fechar">
          ✕
        </button>
      </header>

      {alvo.linhaPo && (
        <section className="linha-po-fonte">
          <h3>Linha da PO</h3>
          {alvo.linhaPo.observacoes && <p>Observações da PO: “{alvo.linhaPo.observacoes}”</p>}
          <button className="botao-link" onClick={() => abrirNaPagina(po.fileId, alvo.linhaPo!.pagina)}>
            Abrir a PO na página {alvo.linhaPo.pagina}
          </button>
          <p className="discreto">
            {po.fileName} · <span title={po.sha256}>SHA-256 {hashCurto(po.sha256)}…</span>
          </p>
        </section>
      )}

      <h3>Lançamentos ({lancamentos.length})</h3>
      {isLoading && <p className="aviso">Carregando…</p>}
      {error && <p className="aviso erro">{error.message}</p>}
      {!isLoading && lancamentos.length === 0 && <p className="aviso">Nenhum lançamento neste número.</p>}
      <ul className="evidencias">
        {lancamentos.map((l) => (
          <li key={l.entryId}>
            <div className="evidencia-topo">
              <strong>{formatarMoeda(l.amount)}</strong>
              <span className="discreto">{formatarData(l.date)}</span>
            </div>
            <span>{l.memo}</span>
            {l.supplier && <span className="discreto">Favorecido: {l.supplier}</span>}
            <span className="discreto">
              {l.account && `Conta ${l.account}${l.accountName ? ` ${l.accountName}` : ""}`}
              {l.document && ` · doc. ${l.document}`}
              {l.fund && ` · ${l.fund}`}
            </span>
            {l.reallocation && <span className="selo alerta">{l.reallocation}</span>}
            {podeRealocar && l.reallocationId && <DesfazerRealocacao realocacaoId={l.reallocationId} />}
            {podeRealocar && alvo.alvo === "TO_REALLOCATE" && !l.reallocationId && <RealocarLancamento poId={po.id} lancamento={l} />}
            <span className="discreto">
              <button className="botao-link" onClick={() => abrirNaPagina(l.fileId, l.page)}>
                {l.fileName ?? "Fluxo"}, pág. {l.page}
              </button>{" "}
              · <span title={l.sha256}>SHA-256 {hashCurto(l.sha256)}…</span>
            </span>
          </li>
        ))}
      </ul>
    </aside>
  );
}
