import { useState } from "react";
import type { ModuloDoCondominio } from "../../api/tipos";
import { formatarDataHora } from "../../formato";
import { ConfirmarAlteracaoModulo } from "./ConfirmarAlteracaoModulo";
import { TrilhaModulo } from "./TrilhaModulo";

/** Um módulo do catálogo: estado no condomínio, o que inclui, ligar/desligar e a trilha (RF-10.1, RF-10.2, RF-10.6). */
export function CartaoModulo({ modulo }: { modulo: ModuloDoCondominio }) {
  const [confirmando, setConfirmando] = useState(false);
  const [verTrilha, setVerTrilha] = useState(false);
  const idTitulo = `modulo-${modulo.codigo}`;

  return (
    <article className="bloco modulo" aria-labelledby={idTitulo}>
      <header className="modulo-topo">
        <div>
          <h2 id={idTitulo}>{modulo.nome}</h2>
          <p className="discreto">{modulo.descricao}</p>
        </div>
        <span className={`selo ${modulo.ligado ? "ok" : "neutro"}`}>{modulo.ligado ? "Ligado" : "Desligado"}</span>
      </header>

      <dl className="modulo-dados">
        <dt>Desde</dt>
        <dd>
          {modulo.desde
            ? formatarDataHora(modulo.desde)
            : `Nunca alterado (padrão para condomínio novo: ${modulo.ligadoPorPadrao ? "ligado" : "desligado"})`}
        </dd>
        {modulo.inclui.length > 0 && (
          <>
            <dt>Inclui</dt>
            <dd>{modulo.inclui.join(" · ")}</dd>
          </>
        )}
        {modulo.dependeDe.length > 0 && (
          <>
            <dt>Depende de</dt>
            <dd>{modulo.dependeDe.join(", ")}</dd>
          </>
        )}
      </dl>

      {confirmando ? (
        <ConfirmarAlteracaoModulo modulo={modulo} aoFechar={() => setConfirmando(false)} />
      ) : (
        <div className="acoes">
          <button className={modulo.ligado ? "botao secundario" : "botao"} onClick={() => setConfirmando(true)}>
            {modulo.ligado ? "Desligar…" : "Ligar…"}
          </button>
          <button className="botao-link" aria-expanded={verTrilha} onClick={() => setVerTrilha((v) => !v)}>
            {verTrilha ? "Esconder trilha e períodos" : "Ver trilha e períodos"}
          </button>
        </div>
      )}

      {verTrilha && <TrilhaModulo codigo={modulo.codigo} />}
    </article>
  );
}
