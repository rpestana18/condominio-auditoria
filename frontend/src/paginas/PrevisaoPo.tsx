import { Link, useParams } from "react-router";
import { abrirArquivo } from "../api/cliente";
import { usePrevisao } from "../api/consultasOrcamento";
import type { PrevisaoDetalhe } from "../api/tipos";
import { ConferenciasPo } from "../componentes/po/ConferenciasPo";
import { FormConfirmacaoPo } from "../componentes/po/FormConfirmacaoPo";
import { LinhasPo } from "../componentes/po/LinhasPo";
import { EditarFundosPo } from "../componentes/po/EditarFundosPo";
import { EventosPo } from "../componentes/po/EventosPo";
import { RubricasPo } from "../componentes/rubricas/RubricasPo";
import { rotuloEstadoAchado, rotuloEstadoPo, rotuloSeveridade } from "../componentes/previsto/rotulos";
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
  const { budget: previsao } = detalhe;
  const aguardando = previsao.status === "READ" || previsao.status === "READ_WITH_DISCREPANCY";

  return (
    <>
      <header className="titulo-pagina">
        <h1>{previsao.title ?? "Previsão orçamentária"}</h1>
        <span className={previsao.status === "CONFIRMED" ? "selo ok" : "selo alerta"}>{rotuloEstadoPo[previsao.status]}</span>
        <Link className="botao-link" to="/previsoes">
          Todas as POs
        </Link>
      </header>

      <Resumo detalhe={detalhe} condominioId={condominioId} />
      {aguardando && !pode("ADMIN") && <p className="aviso alerta">Aguardando a confirmação do Admin.</p>}
      {aguardando && pode("ADMIN") && <FormConfirmacaoPo detalhe={detalhe} />}
      <ConferenciasPo conferencias={detalhe.checks} avisos={detalhe.warnings} />
      {previsao.status === "CONFIRMED" && pode("ADMIN") ? (
        <EditarFundosPo detalhe={detalhe} />
      ) : (
        detalhe.funds.length > 0 && <FundosLigados detalhe={detalhe} />
      )}
      {detalhe.findings.length > 0 && <Achados detalhe={detalhe} />}
      <EventosPo poId={previsao.id} />
      {/* Rubricas só existem depois da confirmação (RF-11.7); a lista é para todos, a edição só do Admin */}
      {(previsao.status === "CONFIRMED" || previsao.status === "SUPERSEDED") && <RubricasPo previsao={previsao} />}
      <LinhasPo linhas={detalhe.lines} colunaOrcadoAnterior={detalhe.previousBudgetedColumn} colunaOrcado={detalhe.budgetedColumn} />
    </>
  );
}

function Resumo({ detalhe, condominioId }: { detalhe: PrevisaoDetalhe; condominioId: string }) {
  const { budget: previsao, confirmation: confirmacao } = detalhe;
  return (
    <section className="bloco">
      <dl className="lista-numeros">
        <dt>Arquivo</dt>
        <dd>
          <button className="botao-link" onClick={() => void abrirArquivo(`/condominiums/${condominioId}/files/${previsao.fileId}/content`)}>
            {previsao.fileName ?? "Abrir original"}
          </button>{" "}
          <span className="hash">{previsao.sha256}</span>
        </dd>
        <dt>Exercício</dt>
        <dd>
          {previsao.fiscalYearStart && previsao.fiscalYearEnd
            ? `${formatarMes(previsao.fiscalYearStart)} a ${formatarMes(previsao.fiscalYearEnd)}`
            : `impresso: ${previsao.printedFiscalYear ?? "—"}`}
        </dd>
        <dt>Previsto do mês</dt>
        <dd>
          {formatarMoeda(previsao.monthlyPlanned)} <span className="discreto">(soma das linhas de despesa)</span>
          {detalhe.printedMonthlyPlanned !== null && detalhe.printedMonthlyPlanned !== undefined && (
            <span className="discreto"> · impresso: {formatarMoeda(detalhe.printedMonthlyPlanned)}</span>
          )}
        </dd>
        <dt>Lida em</dt>
        <dd>{formatarDataHora(previsao.readAt)}</dd>
        {previsao.confirmedAt && (
          <>
            <dt>Confirmada</dt>
            <dd>
              {formatarDataHora(previsao.confirmedAt)} por {previsao.confirmedBy}
              {confirmacao?.withoutMinutes ? " · sem ata" : confirmacao?.approvalDate ? ` · ata de ${formatarData(confirmacao.approvalDate)}` : ""}
            </dd>
          </>
        )}
        {detalhe.supersededFrom && (
          <>
            <dt>Substituída desde</dt>
            <dd>{formatarMes(detalhe.supersededFrom)}</dd>
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
          {detalhe.funds.map((f) => (
            <tr key={f.lineId}>
              <td>
                {f.effectiveCode} {f.description}
              </td>
              <td className="numero">{formatarMoeda(f.budgeted)}</td>
              <td>{f.fund}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function Achados({ detalhe }: { detalhe: PrevisaoDetalhe }) {
  return (
    <section className="bloco">
      <h2>Achados desta PO</h2>
      <ul className="lista-simples">
        {detalhe.findings.map((a) => (
          <li key={a.id}>
            <span className={a.severity === "CRITICAL" ? "selo critico" : "selo alerta"}>{rotuloSeveridade[a.severity]}</span>{" "}
            <span className="selo neutro">{rotuloEstadoAchado[a.status]}</span> {formatarMes(a.referenceMonth)} · {a.description}
          </li>
        ))}
      </ul>
    </section>
  );
}
