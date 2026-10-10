import { useState } from "react";
import type { CondominiumFeature } from "../../api/types";
import { formatDateTime } from "../../format";
import { ConfirmFeatureChange } from "./ConfirmFeatureChange";
import { FeatureTrail } from "./FeatureTrail";

/** A catalog feature: its status in the condominium, what it includes, enable/disable and the trail (RF-10.1, RF-10.2, RF-10.6). */
export function FeatureCard({ feature }: { feature: CondominiumFeature }) {
  const [confirming, setConfirming] = useState(false);
  const [showTrail, setShowTrail] = useState(false);
  const titleId = `feature-${feature.code}`;

  return (
    <article className="block feature" aria-labelledby={titleId}>
      <header className="feature-header">
        <div>
          <h2 id={titleId}>{feature.name}</h2>
          <p className="muted">{feature.description}</p>
        </div>
        <span className={`badge ${feature.enabled ? "ok" : "neutral"}`}>{feature.enabled ? "Ligado" : "Desligado"}</span>
      </header>

      <dl className="feature-details">
        <dt>Desde</dt>
        <dd>
          {feature.since
            ? formatDateTime(feature.since)
            : `Nunca alterado (padrão para condomínio novo: ${feature.enabledByDefault ? "ligado" : "desligado"})`}
        </dd>
        {feature.includes.length > 0 && (
          <>
            <dt>Inclui</dt>
            <dd>{feature.includes.join(" · ")}</dd>
          </>
        )}
        {feature.dependsOn.length > 0 && (
          <>
            <dt>Depende de</dt>
            <dd>{feature.dependsOn.join(", ")}</dd>
          </>
        )}
      </dl>

      {confirming ? (
        <ConfirmFeatureChange feature={feature} onClose={() => setConfirming(false)} />
      ) : (
        <div className="actions">
          <button className={feature.enabled ? "button secondary" : "button"} onClick={() => setConfirming(true)}>
            {feature.enabled ? "Desligar…" : "Ligar…"}
          </button>
          <button className="link-button" aria-expanded={showTrail} onClick={() => setShowTrail((v) => !v)}>
            {showTrail ? "Esconder trilha e períodos" : "Ver trilha e períodos"}
          </button>
        </div>
      )}

      {showTrail && <FeatureTrail code={feature.code} />}
    </article>
  );
}
