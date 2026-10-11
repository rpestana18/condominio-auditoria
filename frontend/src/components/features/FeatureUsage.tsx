import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { downloadFile } from "../../api/client";
import { usagePath, isPeriodFilled, useUsage } from "../../api/queries";
import type { UsageFunction, UsageTotal } from "../../api/types";
import { useSession } from "../../context";
import { formatDate, formatDollarText, formatInteger, formatMonthAbbreviated, todayIso } from "../../format";

const functionLabels: Record<UsageFunction, string> = {
  document_search: "Busca nos documentos (tela)",
  mcp_call: "Busca pelo MCP",
  indexing: "Indexação de arquivos",
  embeddings: "Embeddings",
  question: "Perguntas ao assistente",
};

/** Feature usage in the period, by function and by month (RF-09.7), and spreadsheet export (RF-10.6). */
export function FeatureUsage({ featureNames }: { featureNames: Record<string, string> }) {
  const { condominiumId } = useSession();
  const today = todayIso();
  const [start, setStart] = useState(`${today.slice(0, 4)}-01-01`);
  const [end, setEnd] = useState(today);
  const { data: usage, isLoading, error } = useUsage(condominiumId, start, end);
  const periodOk = isPeriodFilled(start, end);

  // Exporting is always an explicit click; the file name comes from the server (Content-Disposition)
  const exportSheet = useMutation({
    mutationFn: () =>
      downloadFile(usagePath(condominiumId, start, end, true), `uso-modulos-${start}-a-${end}.xlsx`),
  });

  return (
    <section className="block" aria-labelledby="usage-title">
      <header className="block-header">
        <h2 id="usage-title">Uso no período</h2>
        <div className="filters">
          <label className="field">
            De
            <input type="date" value={start} max={end || undefined} onChange={(e) => setStart(e.target.value)} />
          </label>
          <label className="field">
            Até
            <input type="date" value={end} min={start || undefined} onChange={(e) => setEnd(e.target.value)} />
          </label>
          <button
            className="button secondary"
            disabled={!periodOk || exportSheet.isPending}
            onClick={() => exportSheet.mutate()}
          >
            {exportSheet.isPending ? "Exportando…" : "Exportar planilha"}
          </button>
        </div>
      </header>
      <p className="muted">
        Quantidades registradas pelo sistema. O custo é uma estimativa em dólar (tokens × preço do catálogo de IA),
        calculada pelo servidor; não é valor de cobrança. A planilha traz também os períodos ativos.
      </p>
      {exportSheet.isError && (
        <p className="notice error" role="alert">
          Não foi possível exportar: {exportSheet.error.message}
        </p>
      )}

      {!periodOk ? (
        <p className="notice warning">Informe as duas datas, com a inicial antes da final.</p>
      ) : isLoading ? (
        <p className="notice">Carregando…</p>
      ) : error ? (
        <p className="notice error">{error.message}</p>
      ) : !usage ? null : (
        <>
          <CostWarnings costAvailable={usage.costAvailable !== false} modelsWithoutPrice={usage.modelsWithoutPrice ?? []} />
          <h3>
            Por função · {formatDate(usage.start)} a {formatDate(usage.end)}
          </h3>
          <UsageTable rows={usage.byFunction} featureNames={featureNames} />
          <p className="cost-total">
            Custo estimado do período:{" "}
            <strong>
              {usage.costAvailable === false
                ? "indisponível"
                : usage.estimatedTotalCostUsd
                  ? formatDollarText(usage.estimatedTotalCostUsd)
                  : usage.estimatedTotalCostUsd === null
                    ? "sem preço para todos os modelos"
                    : "—"}
            </strong>
          </p>
          <h3>Por mês</h3>
          <UsageTable rows={usage.byMonth} featureNames={featureNames} withMonth />
        </>
      )}
    </section>
  );
}

/** Why the cost may be missing: rag's catalog is down or a model has no price in the catalog. */
function CostWarnings({ costAvailable, modelsWithoutPrice }: { costAvailable: boolean; modelsWithoutPrice: string[] }) {
  return (
    <>
      {!costAvailable && <p className="notice warning">Custo indisponível: o serviço de IA não respondeu.</p>}
      {costAvailable && modelsWithoutPrice.length > 0 && (
        <p className="notice warning">
          Sem preço no catálogo para {modelsWithoutPrice.join(", ")}: o custo dessas linhas e o total não foram estimados.
        </p>
      )}
    </>
  );
}

/** Cost cell: a dash without tokens or without a calculated cost; "sem preço" when the model has no price. */
function costText(row: UsageTotal): string {
  if (row.inputTokens === 0 && row.outputTokens === 0) return "—";
  if (row.estimatedCostUsd === null) return "sem preço";
  return row.estimatedCostUsd ? formatDollarText(row.estimatedCostUsd) : "—";
}

interface TableProps {
  rows: UsageTotal[];
  featureNames: Record<string, string>;
  withMonth?: boolean;
}

function UsageTable({ rows, featureNames, withMonth = false }: TableProps) {
  if (rows.length === 0) return <p className="notice">Nenhum uso registrado neste período.</p>;
  return (
    <div className="scroll">
      <table className="table compact">
        <thead>
          <tr>
            {withMonth && <th>Mês</th>}
            <th>Módulo</th>
            <th>Função</th>
            <th className="number">Registros</th>
            <th className="number">Tokens de entrada</th>
            <th className="number">Tokens de saída</th>
            <th className="number">Arquivos</th>
            <th className="number">Páginas</th>
            <th className="number">Custo estimado (US$)</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={`${r.month ?? ""}-${r.feature}-${r.function}`}>
              {withMonth && <td>{formatMonthAbbreviated(r.month)}</td>}
              <td>{featureNames[r.feature] ?? r.feature}</td>
              <td>{functionLabels[r.function] ?? r.function}</td>
              <td className="number">{formatInteger(r.count)}</td>
              <td className="number">{formatInteger(r.inputTokens)}</td>
              <td className="number">{formatInteger(r.outputTokens)}</td>
              <td className="number">{formatInteger(r.files)}</td>
              <td className="number">{formatInteger(r.pages)}</td>
              <td className="number">{costText(r)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
