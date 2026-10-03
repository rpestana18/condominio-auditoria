import { NavLink, Outlet } from "react-router";
import { sair } from "../autenticacao/keycloak";
import { useSessao } from "../contexto";
import { UltimoArquivo } from "./UltimoArquivo";

export function Layout() {
  const { usuario, condominioId, condominioNome, trocarCondominio } = useSessao();
  return (
    <div className="layout">
      <aside className="menu">
        <div className="marca">
          <img src="/favicon.svg" alt="" width={28} height={28} />
          <span>Auditoria</span>
        </div>
        <nav>
          <NavLink to="/" end>
            Início
          </NavLink>
          <NavLink to="/arquivos">Arquivos</NavLink>
        </nav>
      </aside>
      <div className="conteudo">
        <header className="topo">
          {usuario.condominios.length > 1 ? (
            <select value={condominioId} onChange={(e) => trocarCondominio(e.target.value)} aria-label="Condomínio">
              {usuario.condominios.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nome}
                </option>
              ))}
            </select>
          ) : (
            <strong>{condominioNome}</strong>
          )}
          <div className="usuario">
            <span>
              {usuario.nome} <small>({usuario.perfis.join(", ").toLowerCase()})</small>
            </span>
            <button className="botao-link" onClick={sair}>
              Sair
            </button>
          </div>
        </header>
        <main>
          <Outlet />
        </main>
      </div>
      <UltimoArquivo />
    </div>
  );
}
