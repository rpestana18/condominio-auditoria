import { Link, NavLink, Outlet } from "react-router";
import { logout } from "../auth/keycloak";
import { useSession } from "../context";
import { LatestFileIndicator } from "./LatestFileIndicator";

export function Layout() {
  const { user, condominiumId, condominiumName, switchCondominium, can, isFeatureEnabled } = useSession();
  return (
    <div className="layout">
      <aside className="menu">
        <div className="brand">
          <img src="/favicon.svg" alt="" width={28} height={28} />
          <span>Auditoria</span>
        </div>
        <nav>
          <NavLink to="/" end>
            Início
          </NavLink>
          <NavLink to="/arquivos">Arquivos</NavLink>
          {/* Only with the feature enabled (RF-10.3), for every role; the backend refuses (403) when it is disabled */}
          {isFeatureEnabled("ASSISTANT") && <NavLink to="/assistente">Assistente</NavLink>}
          {/* Revised menu (RF-13, Q53): every role sees the eight items in this order; clicking the section opens
              "Entradas e saídas". "Previsto × realizado" left the menu: the month header of the tracking grid opens it.
              The editing actions inside the screens are Admin only (and the backend refuses the others) */}
          <Link className="menu-section menu-section-link" to="/entradas-e-saidas">
            Análise da PO
          </Link>
          <NavLink to="/entradas-e-saidas">Entradas e saídas</NavLink>
          <NavLink to="/acompanhamento-po">Acompanhamento da PO</NavLink>
          <NavLink to="/origem-do-saldo">Origem do saldo</NavLink>
          <NavLink to="/fundos">Fundos</NavLink>
          <NavLink to="/previsoes">PO aprovada</NavLink>
          <NavLink to="/depara">De-para</NavLink>
          <NavLink to="/comparar-exercicios">Comparar exercícios</NavLink>
          <NavLink to="/indicadores">Indicadores</NavLink>
          {/* Only hides the menu: the backend refuses (403) anyone without the role */}
          {can("MANAGER", "ADMIN") && (
            <>
              <span className="menu-section" id="administration-menu">
                Administração
              </span>
              <NavLink to="/concessionarias" aria-describedby="administration-menu">
                Concessionárias
              </NavLink>
              {can("ADMIN") && (
                <>
                  <NavLink to="/administracao/modulos" aria-describedby="administration-menu">
                    Módulos
                  </NavLink>
                  <NavLink to="/administracao/ia" aria-describedby="administration-menu">
                    IA do condomínio
                  </NavLink>
                </>
              )}
            </>
          )}
        </nav>
      </aside>
      <div className="content">
        <header className="topbar">
          {user.condominiums.length > 1 ? (
            <select value={condominiumId} onChange={(e) => switchCondominium(e.target.value)} aria-label="Condomínio">
              {user.condominiums.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          ) : (
            <strong>{condominiumName}</strong>
          )}
          <div className="user">
            <span>
              {user.name} <small>({user.roles.join(", ").toLowerCase()})</small>
            </span>
            <button className="link-button" onClick={logout}>
              Sair
            </button>
          </div>
        </header>
        <main>
          <Outlet />
        </main>
      </div>
      <LatestFileIndicator />
    </div>
  );
}
