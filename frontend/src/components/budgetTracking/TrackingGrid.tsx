import { Link } from "react-router";
import type { BudgetTracking, SpendingProfile, TrackingGroup, TrackingLine, TrackingMonth } from "../../api/types";
import { formatMoney, formatMoneyOrDash, formatMonthShort, formatPercentage } from "../../format";
import type { EvidenceTarget } from "../budgetVsActual/evidence";
import { markLabel, spendingProfileLabel, YEARLY_PROFILES } from "../budgetVsActual/labels";
import { MonthStatusNote, monthStatusText } from "./MonthStatusNote";
import { TrackingCellView } from "./TrackingCellView";
import { shownProfile } from "./trackingFilter";

/** Opens the entries behind a number: `period` is "AAAA-MM" or "cumulative" (API of the evidence, RF-03.1.12). */
export type OpenTrackingEvidence = (period: string, target: EvidenceTarget) => void;

interface Props {
  tracking: BudgetTracking;
  /** Groups already filtered by the screen (only hides rows). */
  groups: TrackingGroup[];
  onOpenEvidence: OpenTrackingEvidence;
}

/** Number of descriptive columns on the left: Item, Identificação, Descrição, Tipo, Previsto do mês, Contas, Observações. */
const LEFT_COLUMNS = 7;

/**
 * The cumulative evidence ("cumulative" period of budget vs actual) only matches the "Acumulado" column when no month
 * with numbers is outside the cumulative (partial month, RF-13.10). Otherwise the screen asks for the month cells.
 */
const cumulativeEvidenceMatches = (months: TrackingMonth[]) =>
  months.every((m) => m.status !== "WITH_CASH_FLOW" || m.inCumulative);

/** Grid of RF-13.4: every line of the budget × the 12 months, plus "Acumulado" and "Saldo da linha no exercício". */
export function TrackingGrid({ tracking, groups, onOpenEvidence }: Props) {
  const { months } = tracking;
  const cumulativeClickable = cumulativeEvidenceMatches(months);
  return (
    <div className="scroll tracking-scroll">
      <table className="table compact tracking-grid">
        <thead>
          <tr>
            <th className="sticky-column">Item</th>
            <th>Identificação</th>
            <th>Descrição</th>
            <th>Tipo</th>
            <th className="number">Previsto do mês</th>
            <th>Contas do extrato</th>
            <th>Observações</th>
            {months.map((m) => (
              <th key={m.month} className={`number month-column ${m.partialUntil ? "cell-partial" : ""}`}>
                {/* Q53: the month header opens "Previsto × realizado" of that month (out of the menu) */}
                <Link to={`/previsto-realizado?${new URLSearchParams({ periodo: m.month, ...(tracking.budget ? { po: tracking.budget.id } : {}) })}`} title="Abrir o previsto × realizado do mês">
                  {formatMonthShort(m.month)}
                </Link>
                <MonthStatusNote status={m.status} partialUntil={m.partialUntil} />
              </th>
            ))}
            <th className="number">
              Acumulado
              <span className="month-note">{tracking.cumulative?.months ?? 0} mês(es) completo(s)</span>
            </th>
            <th className="number">Saldo da linha no exercício</th>
          </tr>
        </thead>
        <tbody>
          {groups.map((g) => (
            <GroupRows key={g.lineId} group={g} months={months} cumulativeClickable={cumulativeClickable} onOpenEvidence={onOpenEvidence} />
          ))}
        </tbody>
        <TrackingFooter tracking={tracking} />
      </table>
    </div>
  );
}

interface RowsProps {
  group: TrackingGroup;
  months: TrackingMonth[];
  cumulativeClickable: boolean;
  onOpenEvidence: OpenTrackingEvidence;
}

function GroupRows({ group, months, cumulativeClickable, onOpenEvidence }: RowsProps) {
  const title = `${group.code} ${group.description}`;
  const target = { target: `group:${group.lineId}`, title };
  return (
    <>
      <tr className="group-row">
        <td className="sticky-column">{group.code}</td>
        <td colSpan={LEFT_COLUMNS - 1}>{group.description} · subtotal</td>
        {months.map((m) => (
          <TrackingCellView
            key={m.month}
            cell={group.cells.find((c) => c.month === m.month)}
            profile="RECURRING"
            partial={!!m.partialUntil}
            emptyText={monthStatusText(m) ?? ""}
            onOpen={() => onOpenEvidence(m.month, target)}
          />
        ))}
        <CumulativeCell
          actual={group.cumulativeActual}
          planned={group.cumulativePlanned}
          onOpen={cumulativeClickable ? () => onOpenEvidence("cumulative", target) : undefined}
        />
        <BalanceCell balance={group.balance} profile="RECURRING" />
      </tr>
      {group.lines.map((l) => (
        <LineRow key={l.lineId} line={l} months={months} cumulativeClickable={cumulativeClickable} onOpenEvidence={onOpenEvidence} />
      ))}
    </>
  );
}

interface LineRowProps {
  line: TrackingLine;
  months: TrackingMonth[];
  cumulativeClickable: boolean;
  onOpenEvidence: OpenTrackingEvidence;
}

function LineRow({ line, months, cumulativeClickable, onOpenEvidence }: LineRowProps) {
  const profile = shownProfile(line);
  const target: EvidenceTarget = {
    target: `line:${line.lineId}`,
    title: `${line.code} ${line.description}`,
    budgetLine: { page: line.page, notes: line.notes },
  };
  return (
    <tr>
      <td className="sticky-column">
        {line.code}
        {line.code !== line.printedCode && <small className="muted"> (impresso {line.printedCode})</small>}
      </td>
      <td>{line.mark ? <span className="badge neutral">{markLabel[line.mark]}</span> : (line.account ?? "")}</td>
      <td>{line.description}</td>
      <td>
        <ProfileBadge profile={profile} confirmed={line.spendingProfileStatus === "CONFIRMED"} />
      </td>
      <td className="number">{formatMoneyOrDash(line.monthlyPlanned)}</td>
      <td>{line.cashFlowAccounts.length > 0 ? line.cashFlowAccounts.join(", ") : <span className="muted">nenhuma</span>}</td>
      <td className="muted notes-cell" title={line.notes ?? undefined}>
        {line.notes}
      </td>
      {months.map((m) => (
        <TrackingCellView
          key={m.month}
          cell={line.cells.find((c) => c.month === m.month)}
          profile={profile}
          partial={!!m.partialUntil}
          emptyText={monthStatusText(m) ?? ""}
          onOpen={() => onOpenEvidence(m.month, target)}
        />
      ))}
      <CumulativeCell
        actual={line.cumulativeActual}
        planned={line.cumulativePlanned}
        onOpen={cumulativeClickable ? () => onOpenEvidence("cumulative", target) : undefined}
      />
      <BalanceCell balance={line.balance} profile={profile} monthsAbove={line.monthsAbove} />
    </tr>
  );
}

function ProfileBadge({ profile, confirmed }: { profile: SpendingProfile; confirmed: boolean }) {
  return (
    <span className="profile">
      {spendingProfileLabel[profile]}
      {!confirmed && <small className="muted">tipo não confirmado</small>}
    </span>
  );
}

function CumulativeCell({ actual, planned, onOpen }: { actual?: number | null; planned?: number | null; onOpen?: () => void }) {
  if (actual == null) return <td className="number">—</td>;
  const content = (
    <>
      <span>{formatMoney(actual)}</span>
      <small>de {formatMoneyOrDash(planned)}</small>
    </>
  );
  return (
    <td className="number tracking-cell">
      {onOpen ? (
        <button className="cell-button" onClick={onOpen} title="Abrir os lançamentos do acumulado">
          {content}
        </button>
      ) : (
        <span className="cell-content" title="Há mês parcial no exercício: abra os lançamentos pelo mês">
          {content}
        </span>
      )}
    </td>
  );
}

/**
 * "Saldo da linha no exercício" = planned − actual of the complete months, calculated by the API.
 * Positive = below planned; negative = above planned. Neutral highlight; for the yearly profiles (eventual, sazonal, anual) it is the main one (Q46).
 */
function BalanceCell({ balance, profile, monthsAbove }: { balance?: number | null; profile: SpendingProfile; monthsAbove?: number }) {
  if (profile === "SEPARATE_APPORTIONMENT") {
    return (
      <td className="number muted" title="Rateio à parte: só a movimentação, sem diferença">
        —
      </td>
    );
  }
  if (balance == null) return <td className="number">—</td>;
  const highlight = balance < 0 ? "cell-above" : balance > 0 ? "cell-below" : "";
  return (
    <td className={`number tracking-cell ${highlight} ${YEARLY_PROFILES.has(profile) ? "emphasis" : ""}`}>
      <span className="cell-content">
        <span>{formatMoney(balance)}</span>
        {monthsAbove !== undefined && monthsAbove > 0 && (
          <small>
            {monthsAbove} {monthsAbove === 1 ? "mês" : "meses"} acima do previsto
          </small>
        )}
      </span>
    </td>
  );
}

/** Month totals as the API sends them (the same numbers as "Previsto × realizado" of each month). */
function TrackingFooter({ tracking }: { tracking: BudgetTracking }) {
  const { months, cumulative } = tracking;
  return (
    <tfoot>
      <tr>
        <th colSpan={LEFT_COLUMNS} className="sticky-column">
          Previsto do mês (linhas da PO)
        </th>
        {months.map((m) => (
          <td key={m.month} className="number">
            {formatMoneyOrDash(m.planned)}
          </td>
        ))}
        <td className="number">{formatMoneyOrDash(cumulative?.planned)}</td>
        <td className="number" rowSpan={3}>
          {formatMoneyOrDash(cumulative?.balance)}
        </td>
      </tr>
      <tr>
        <th colSpan={LEFT_COLUMNS} className="sticky-column">
          Realizado em linhas da PO
        </th>
        {months.map((m) => (
          <td key={m.month} className="number">
            {formatMoneyOrDash(m.actualInLines)}
          </td>
        ))}
        <td className="number">{formatMoneyOrDash(cumulative?.actualInLines)}</td>
      </tr>
      <tr>
        <th colSpan={LEFT_COLUMNS} className="sticky-column">
          Excesso das linhas acima do previsto (regra dos 20%)
        </th>
        {months.map((m) => (
          <td
            key={m.month}
            className="number"
            title={m.partialUntil ? "Mês em andamento: só o realizado até a data, sem comparação com o previsto" : undefined}
          >
            {m.overrun == null ? (
              "—"
            ) : (
              <span className="cell-content">
                <span className={m.aboveLimit ? "critical-text" : undefined}>{formatMoney(m.overrun)}</span>
                <small>
                  {m.linesAbove ?? 0} linha(s) · {formatPercentage(m.overrunPercentage)}
                  {m.aboveLimit && " · acima de 20%"}
                </small>
              </span>
            )}
          </td>
        ))}
        <td className="number">—</td>
      </tr>
    </tfoot>
  );
}
