import { useState } from "react";
import type { ModuloDoCondominio } from "../../api/tipos";
import { formatarDataHora } from "../../formato";
import { ConfirmarAlteracaoModulo } from "./ConfirmarAlteracaoModulo";
import { TrilhaModulo } from "./TrilhaModulo";

/** Um módulo do catálogo: estado no condomínio, o que inclui, ligar/desligar e a trilha (RF-10.1, RF-10.2, RF-10.6). */
export function CartaoModulo({ modulo }: { modulo: ModuloDoCondominio }) {
  const [confirmando, setConfirmando] = useState(false);
  const [verTrilha, setVerTrilha] = useState(false);
  const idTitulo = `modulo-${modulo.code}`;

  return (
    <article className="bloco modulo" aria-labelledby={idTitulo}>
      <header className="modulo-topo">
        <div>
          <h2 id={idTitulo}>{modulo.name}</h2>
          <p className="discreto">{modulo.description}</p>
        </div>
        <span className={`selo ${modulo.enabled ? "ok" : "neutro"}`}>{modulo.enabled ? "Ligado" : "Desligado"}</span>
      </header>

      <dl className="modulo-dados">
        <dt>Desde</dt>
        <dd>
          {modulo.since
            ? formatarDataHora(modulo.since)
            : `Nunca alterado (padrão para condomínio novo: ${modulo.enabledByDefault ? "ligado" : "desligado"})`}
        </dd>
        {modulo.includes.length > 0 && (
          <>
            <dt>Inclui</dt>
            <dd>{modulo.includes.join(" · ")}</dd>
          </>
        )}
        {modulo.dependsOn.length > 0 && (
          <>
            <dt>Depende de</dt>
            <dd>{modulo.dependsOn.join(", ")}</dd>
          </>
        )}
      </dl>

      {confirmando ? (
        <ConfirmarAlteracaoModulo modulo={modulo} aoFechar={() => setConfirmando(false)} />
      ) : (
        <div className="acoes">
          <button className={modulo.enabled ? "botao secundario" : "botao"} onClick={() => setConfirmando(true)}>
            {modulo.enabled ? "Desligar…" : "Ligar…"}
          </button>
          <button className="botao-link" aria-expanded={verTrilha} onClick={() => setVerTrilha((v) => !v)}>
            {verTrilha ? "Esconder trilha e períodos" : "Ver trilha e períodos"}
          </button>
        </div>
      )}

      {verTrilha && <TrilhaModulo codigo={modulo.code} />}
    </article>
  );
}
