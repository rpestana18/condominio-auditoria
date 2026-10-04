import { Link, useParams } from "react-router";
import { abrirArquivo } from "../api/cliente";
import { usePrevisao } from "../api/consultasOrcamento";
import type { PrevisaoDetalhe } from "../api/tipos";
import { ConferenciasPo } from "../componentes/po/ConferenciasPo";
import { FormConfirmacaoPo } from "../componentes/po/FormConfirmacaoPo";
import { LinhasPo } from "../componentes/po/LinhasPo";
import { rotuloEstadoPo } from "../componentes/previsto/rotulos";
import { useSessao } from "../contexto";
import { formatarData, formatarDataHora, formatarMes, formatarMoeda } from "../formato";

/**
 * Detalhe da PO lida, com conferências e linhas. O Admin confirma aqui (RF-03.1.3);
 * os demais perfis só consultam.
 */
export function PrevisaoPo() {
  const { poId } = useParams();
  const { condominioId, pode } = useSessao();
  const { data: detalhe, isLoading, error } = usePrevisao(condominioId, poId);

  if (isLoading) return <p className="aviso">Carregando…</p>;
  if (error) return <p className="aviso erro">{error.message}</p>;
  if (!detalhe) return null;
  const { previsao } = detalhe;
  const aguardando = previsao.estado === "LIDA" || previsao.estado === "LIDA_COM_DIVERGENCIA";

  return (
    <>
      <header className="titulo-pagina">
        <h1>{previsao.titulo ?? "Previsão orçamentária"}</h1>
        <span className={previsao.estado === "CONFIRMADA" ? "selo ok" : "selo alerta"}>{rotuloEstadoPo[previsao.estado]}</span>
        <Link className="botao-link" to="/previsoes">
          Todas as POs
        </Link>
      </header>

      <Resumo detalhe={detalhe} condominioId={condominioId} />
      {aguardando && !pode("ADMIN") && <p className="aviso alerta">Aguardando a confirmação do Admin.</p>}
      {aguardando && pode("ADMIN") && <FormConfirmacaoPo detalhe={detalhe} />}
      <ConferenciasPo conferencias={detalhe.conferencias} avisos={detalhe.avisos} />
      {detalhe.fundos.length > 0 && <FundosLigados detalhe={detalhe} />}
      {detalhe.achados.length > 0 && <Achados detalhe={detalhe} />}
      <LinhasPo linhas={detalhe.linhas} colunaOrcadoAnterior={detalhe.colunaOrcadoAnterior} colunaOrcado={detalhe.colunaOrcado} />
    </>
  );
}

function Resumo({ detalhe, condominioId }: { detalhe: PrevisaoDetalhe; condominioId: string }) {
  const { previsao, confirmacao } = detalhe;
  return (
    <section className="bloco">
      <dl className="lista-numeros">
        <dt>Arquivo</dt>
        <dd>
          <button className="botao-link" onClick={() => void abrirArquivo(`/condominios/${condominioId}/arquivos/${previsao.arquivoId}/conteudo`)}>
            {previsao.arquivoNome ?? "Abrir original"}
          </button>{" "}
          <span className="hash">{previsao.sha256}</span>
        </dd>
        <dt>Exercício</dt>
        <dd>
          {previsao.exercicioInicio && previsao.exercicioFim
            ? `${formatarMes(previsao.exercicioInicio)} a ${formatarMes(previsao.exercicioFim)}`
            : `impresso: ${previsao.exercicioImpresso ?? "—"}`}
        </dd>
        <dt>Previsto do mês</dt>
        <dd>
          {formatarMoeda(previsao.previstoMes)} <span className="discreto">(soma das linhas de despesa)</span>
          {detalhe.previstoMesImpresso !== null && detalhe.previstoMesImpresso !== undefined && (
            <span className="discreto"> · impresso: {formatarMoeda(detalhe.previstoMesImpresso)}</span>
          )}
        </dd>
        <dt>Lida em</dt>
        <dd>{formatarDataHora(previsao.lidaEm)}</dd>
        {previsao.confirmadaEm && (
          <>
            <dt>Confirmada</dt>
            <dd>
              {formatarDataHora(previsao.confirmadaEm)} por {previsao.confirmadaPor}
              {confirmacao?.semAta ? " · sem ata" : confirmacao?.dataAprovacao ? ` · ata de ${formatarData(confirmacao.dataAprovacao)}` : ""}
            </dd>
          </>
        )}
        {detalhe.substituidaDesde && (
          <>
            <dt>Substituída desde</dt>
            <dd>{formatarMes(detalhe.substituidaDesde)}</dd>
          </>
        )}
      </dl>
    </section>
  );
}

function FundosLigados({ detalhe }: { detalhe: PrevisaoDetalhe }) {
  return (
    <section className="bloco">
      <h2>Fundos ligados às linhas de fundos</h2>
      <table className="tabela compacta">
        <tbody>
          {detalhe.fundos.map((f) => (
            <tr key={f.linhaId}>
              <td>
                {f.codigoEfetivo} {f.descricao}
              </td>
              <td className="numero">{formatarMoeda(f.orcado)}</td>
              <td>{f.fundo}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

const rotuloSeveridade = { INFORMATIVO: "informativo", ATENCAO: "atenção", CRITICO: "crítico" } as const;

function Achados({ detalhe }: { detalhe: PrevisaoDetalhe }) {
  return (
    <section className="bloco">
      <h2>Achados desta PO</h2>
      <ul className="lista-simples">
        {detalhe.achados.map((a) => (
          <li key={a.id}>
            <span className={a.severidade === "CRITICO" ? "selo critico" : "selo alerta"}>{rotuloSeveridade[a.severidade]}</span>{" "}
            {formatarMes(a.competencia)} · {a.descricao}
          </li>
        ))}
      </ul>
    </section>
  );
}
