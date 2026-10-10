import { useState, type FormEvent } from "react";
import { useChangeFeature } from "../../api/queries";
import type { CondominiumFeature } from "../../api/types";
import { useSession } from "../../context";

interface Props {
  feature: CondominiumFeature;
  onClose: () => void;
}

/**
 * Confirmation on the page itself (no browser confirm()), with an optional reason that goes to the trail.
 * The backend decides who may do it: a role without permission gets 403 and the message shows up here.
 */
export function ConfirmFeatureChange({ feature, onClose }: Props) {
  const { condominiumId, condominiumName } = useSession();
  const change = useChangeFeature(condominiumId);
  const [reason, setReason] = useState("");
  const enable = !feature.enabled;
  const titleId = `confirm-${feature.code}`;

  function confirm(event: FormEvent) {
    event.preventDefault();
    // The reason is optional: blank goes as null
    change.mutate({ code: feature.code, change: { enabled: enable, reason: reason.trim() || null } }, { onSuccess: onClose });
  }

  return (
    <form className="confirmation" onSubmit={confirm} aria-labelledby={titleId}>
      <p id={titleId}>
        <strong>
          {enable ? "Ligar" : "Desligar"} o módulo {feature.name} em {condominiumName}?
        </strong>
      </p>
      <p className="muted">
        {enable
          ? "Os arquivos já enviados entram na fila de indexação; o andamento aparece na tela Arquivos."
          : "Os arquivos originais e o índice ficam guardados; as funções do módulo deixam de responder até religar."}
      </p>
      <label className="field">
        Motivo (opcional, fica registrado na trilha)
        <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={2} maxLength={500} autoFocus />
      </label>
      {change.isError && (
        <p className="notice error" role="alert">
          {change.error.message}
        </p>
      )}
      <div className="actions">
        <button className="button secondary" type="button" onClick={onClose} disabled={change.isPending}>
          Cancelar
        </button>
        <button className="button" type="submit" disabled={change.isPending}>
          {change.isPending ? "Gravando…" : enable ? "Confirmar e ligar" : "Confirmar e desligar"}
        </button>
      </div>
    </form>
  );
}
