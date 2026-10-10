import { useState } from "react";
import { useColunaImpressa } from "../../api/consultasAnalisePo";
import type { GrupoConferenciaColuna } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarDiferenca, formatarMoeda, formatarMoedaOuTraco } from "../../formato";

interface Props {
  /** PO que imprimiu a coluna "Orçado anterior". */
  poId: string;
  aoFechar: () => void;
}

/**
 * Conferência da coluna "Orçado anterior" (RF-11.5): subtotal impresso × soma das linhas de cada grupo,
 * total impresso, avisos e, quando a PO anterior já foi enviada, as diferenças por grupo entre as duas.
 * Tudo vem pronto do GET /previsoes/{poId}/coluna-impressa.
 */
export function ConferenciaColunaImpressa({ poId, aoFechar }: Props) {
  const { condominioId } = useSessao();
  const { data: conferencia, isLoading, error } = useColunaImpressa(condominioId, poId);

  return (
    <aside className="detalhe" aria-label="Conferência da coluna impressa">
      <header>
        <h2>{conferencia?.label ?? "Coluna impressa"}</h2>
        <button className="botao-link" onClick={aoFechar} aria-label="Fechar">
          ✕
        </button>
      </header>
      {isLoading && <p className="aviso">Carregando…</p>}
      {error && <p className="aviso erro">{error.message}</p>}
      {conferencia && (
        <>
          {conferencia.superseded && (
            <p className="aviso">
              A PO {conferencia.previousBudgetLabel ?? "anterior"} foi enviada e é usada na comparação. A coluna impressa fica só
              como conferência.
            </p>
          )}
          <dl>
            <dt>Total impresso</dt>
            <dd>
              {formatarMoedaOuTraco(conferencia.printedTotal)}{" "}
              <span className="discreto">
                ({conferencia.totalIncludesFunds ? "confere com os subtotais incluindo os fundos" : "comparado aos subtotais sem os fundos"})
              </span>
            </dd>
            <dt>Fundos</dt>
            <dd>{formatarMoeda(conferencia.funds)}</dd>
            <dt>Previsto do mês</dt>
            <dd>
              {formatarMoeda(conferencia.monthlyPlanned)} <span className="discreto">(soma das linhas, sem os fundos)</span>
            </dd>
          </dl>
          {conferencia.warnings.map((a, i) => (
            <p key={i} className="aviso alerta">
              {a}
            </p>
          ))}

          <h3>Grupos: impresso × soma das linhas</h3>
          <table className="tabela compacta previsto">
            <thead>
              <tr>
                <th>Grupo</th>
                <th className="numero">Impresso</th>
                <th className="numero">Soma das linhas</th>
                <th className="numero">Diferença</th>
              </tr>
            </thead>
            {conferencia.groups.map((g) => (
              <GrupoColuna key={g.code} grupo={g} />
            ))}
          </table>

          {conferencia.differences.length > 0 && (
            <>
              <h3>PO anterior enviada × coluna impressa</h3>
              <table className="tabela compacta">
                <thead>
                  <tr>
                    <th>Grupo</th>
                    <th className="numero">PO enviada</th>
                    <th className="numero">Coluna impressa</th>
                  </tr>
                </thead>
                <tbody>
                  {conferencia.differences.map((d) => (
                    <tr key={d.code}>
                      <td>
                        {d.code} {d.description}
                      </td>
                      <td className="numero">{formatarMoeda(d.uploadedBudget)}</td>
                      <td className="numero">{formatarMoeda(d.printedColumn)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
        </>
      )}
    </aside>
  );
}

/** Um grupo da coluna; clicando, mostra as linhas com o valor e o "%" impresso (texto lido, não calculado). */
function GrupoColuna({ grupo }: { grupo: GrupoConferenciaColuna }) {
  const [aberto, setAberto] = useState(false);
  return (
    <tbody>
      <tr className="linha-grupo" onClick={() => setAberto(!aberto)}>
        <td>
          <span aria-hidden>{aberto ? "▾" : "▸"}</span> {grupo.code} {grupo.description}
          {grupo.funds && <span className="selo neutro">fundos</span>}
        </td>
        <td className="numero">{formatarMoeda(grupo.printed)}</td>
        <td className="numero">{formatarMoeda(grupo.amount)}</td>
        <td className="numero">
          {formatarDiferenca(grupo.difference)}
          <small className="observacao">{grupo.matches ? "confere" : "não confere"}</small>
        </td>
      </tr>
      {aberto &&
        grupo.lines.map((l) => (
          <tr key={l.lineId ?? l.code}>
            <td>
              {l.code} {l.description}
              {l.account && <small className="observacao">{l.account}</small>}
            </td>
            <td className="numero discreto">{l.percentageText ? `% impresso: ${l.percentageText}` : ""}</td>
            <td className="numero">{formatarMoeda(l.amount)}</td>
            <td />
          </tr>
        ))}
    </tbody>
  );
}
