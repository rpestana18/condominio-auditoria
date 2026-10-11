import { NavLink, Outlet } from "react-router";
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
          {/* RF-11.1: every role sees the section; the editing actions inside the screens are Admin only */}
          <span className="menu-section">Análise da PO</span>
          <NavLink to="/previsto-realizado">Previsto × realizado</NavLink>
          <NavLink to="/comparar-exercicios">Comparar exercícios</NavLink>
          <NavLink to="/indicadores">Indicadores</NavLink>
          <NavLink to="/previsoes">PO</NavLink>
          <NavLink to="/depara">De-para</NavLink>
          {/* Only hides the menu: the backend refuses (403) anyone who is not ADMIN */}
          {can("ADMIN") && (
            <>
              <span className="menu-section" id="administration-menu">
                Administração
              </span>
              <NavLink to="/administracao/modulos" aria-describedby="administration-menu">
                Módulos
              </NavLink>
              <NavLink to="/administracao/ia" aria-describedby="administration-menu">
                IA do condomínio
              </NavLink>
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
