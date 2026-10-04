import { abrirArquivo } from "../../api/cliente";
import { useEvidencia, type Periodo } from "../../api/consultasOrcamento";
import type { PrevistoRealizado } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarData, formatarMoeda, hashCurto } from "../../formato";
import type { AlvoEvidencia } from "./evidencia";

interface Props {
  periodo: Periodo;
  po: NonNullable<PrevistoRealizado["po"]>;
  alvo: AlvoEvidencia;
  aoFechar: () => void;
}

/**
 * Lançamentos que compõem um número (RF-03.1.12). Mostra só fatos: data, histórico, favorecido, valor,
 * conta, arquivo, página e hash. Nenhum texto sobre a causa da diferença.
 */
export function PainelEvidencia({ periodo, po, alvo, aoFechar }: Props) {
  const { condominioId } = useSessao();
  const { data: lancamentos = [], isLoading, error } = useEvidencia(condominioId, periodo, po.id, alvo.alvo);
  const abrirNaPagina = (arquivoId: string, pagina: number) =>
    void abrirArquivo(`/condominios/${condominioId}/arquivos/${arquivoId}/conteudo`, pagina);

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
          <button className="botao-link" onClick={() => abrirNaPagina(po.arquivoId, alvo.linhaPo!.pagina)}>
            Abrir a PO na página {alvo.linhaPo.pagina}
          </button>
          <p className="discreto">
            {po.arquivoNome} · <span title={po.sha256}>SHA-256 {hashCurto(po.sha256)}…</span>
          </p>
        </section>
      )}

      <h3>Lançamentos ({lancamentos.length})</h3>
      {isLoading && <p className="aviso">Carregando…</p>}
      {error && <p className="aviso erro">{error.message}</p>}
      {!isLoading && lancamentos.length === 0 && <p className="aviso">Nenhum lançamento neste número.</p>}
      <ul className="evidencias">
        {lancamentos.map((l) => (
          <li key={l.lancamentoId}>
            <div className="evidencia-topo">
              <strong>{formatarMoeda(l.valor)}</strong>
              <span className="discreto">{formatarData(l.data)}</span>
            </div>
            <span>{l.historico}</span>
            {l.fornecedor && <span className="discreto">Favorecido: {l.fornecedor}</span>}
            <span className="discreto">
              {l.conta && `Conta ${l.conta}${l.contaNome ? ` ${l.contaNome}` : ""}`}
              {l.documento && ` · doc. ${l.documento}`}
              {l.fundo && ` · ${l.fundo}`}
            </span>
            {l.realocacao && <span className="selo alerta">{l.realocacao}</span>}
            <span className="discreto">
              <button className="botao-link" onClick={() => abrirNaPagina(l.arquivoId, l.pagina)}>
                {l.arquivoNome ?? "Fluxo"}, pág. {l.pagina}
              </button>{" "}
              · <span title={l.sha256}>SHA-256 {hashCurto(l.sha256)}…</span>
            </span>
          </li>
        ))}
      </ul>
    </aside>
  );
}
