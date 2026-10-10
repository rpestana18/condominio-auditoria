import { useFeatureEvents, useFeaturePeriods } from "../../api/queries";
import type { FeatureEvent, ActivePeriod } from "../../api/types";
import { useSession } from "../../context";
import { formatDateTime } from "../../format";

/** Activation trail and active periods of a feature (RF-10.6). ADMIN only; the backend refuses the others. */
export function FeatureTrail({ code }: { code: string }) {
  return (
    <div className="two-columns trail">
      <Events code={code} />
      <Periods code={code} />
    </div>
  );
}

const stateLabel = (enabled: boolean) => (enabled ? "Ligado" : "Desligado");

function Events({ code }: { code: string }) {
  const { condominiumId } = useSession();
  const { data: events = [], isLoading, error } = useFeatureEvents(condominiumId, code);
  // The API sends them from oldest to newest; on screen, the newest comes first
  const newestFirst: FeatureEvent[] = [...events].reverse();

  return (
    <section>
      <h3>Trilha de ativação</h3>
      {isLoading ? (
        <p className="notice">Carregando…</p>
      ) : error ? (
        <p className="notice error">{error.message}</p>
      ) : newestFirst.length === 0 ? (
        <p className="notice">Nenhuma alteração registrada. Vale o estado padrão.</p>
      ) : (
        <table className="table compact">
          <thead>
            <tr>
              <th>Quando</th>
              <th>Mudança</th>
              <th>Quem</th>
              <th>Motivo</th>
            </tr>
          </thead>
          <tbody>
            {newestFirst.map((e) => (
              <tr key={e.id}>
                <td>{formatDateTime(e.occurredAt)}</td>
                <td>
                  {stateLabel(e.enabledBefore)} → <strong>{stateLabel(e.enabledAfter)}</strong>
                </td>
                <td>{e.username}</td>
                <td>{e.reason || <span className="muted">—</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function Periods({ code }: { code: string }) {
  const { condominiumId } = useSession();
  const { data: periods = [], isLoading, error } = useFeaturePeriods(condominiumId, code);

  return (
    <section>
      <h3>Períodos ativos</h3>
      {isLoading ? (
        <p className="notice">Carregando…</p>
      ) : error ? (
        <p className="notice error">{error.message}</p>
      ) : periods.length === 0 ? (
        <p className="notice">O módulo nunca esteve ligado neste condomínio.</p>
      ) : (
        <table className="table compact">
          <thead>
            <tr>
              <th>Início</th>
              <th>Fim</th>
              <th>Ligado por</th>
              <th>Desligado por</th>
            </tr>
          </thead>
          <tbody>
            {periods.map((p, i) => (
              <PeriodRow key={`${p.start ?? "default"}-${i}`} period={p} />
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function PeriodRow({ period: p }: { period: ActivePeriod }) {
  return (
    <tr>
      <td>{p.start ? formatDateTime(p.start) : <span className="muted">Ligado por padrão</span>}</td>
      <td>{p.end ? formatDateTime(p.end) : <strong className="positive">Ainda ligado</strong>}</td>
      <td>
        <WhoAndReason who={p.enabledBy} reason={p.enableReason} />
      </td>
      <td>
        <WhoAndReason who={p.disabledBy} reason={p.disableReason} />
      </td>
    </tr>
  );
}

function WhoAndReason({ who, reason }: { who?: string | null; reason?: string | null }) {
  if (!who) return <span className="muted">—</span>;
  return (
    <>
      {who}
      {reason && <small className="muted block-text">{reason}</small>}
    </>
  );
}
