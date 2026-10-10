import { NavLink, Outlet } from "react-router";
import { sair } from "../autenticacao/keycloak";
import { useSessao } from "../contexto";
import { UltimoArquivo } from "./UltimoArquivo";

export function Layout() {
  const { usuario, condominioId, condominioNome, trocarCondominio, pode, moduloLigado } = useSessao();
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
          {/* Só com o módulo ligado (RF-10.3), para todos os perfis; o backend recusa (403) se estiver desligado */}
          {moduloLigado("ASSISTANT") && <NavLink to="/assistente">Assistente</NavLink>}
          {/* RF-11.1: todos os perfis veem a seção; as ações de edição dentro das telas são só do Admin */}
          <span className="menu-secao">Análise da PO</span>
          <NavLink to="/previsto-realizado">Previsto × realizado</NavLink>
          <NavLink to="/comparar-exercicios">Comparar exercícios</NavLink>
          <NavLink to="/indicadores">Indicadores</NavLink>
          <NavLink to="/previsoes">PO</NavLink>
          <NavLink to="/depara">De-para</NavLink>
          {/* Só esconde o menu: o backend recusa (403) quem não é ADMIN */}
          {pode("ADMIN") && (
            <>
              <span className="menu-secao" id="menu-administracao">
                Administração
              </span>
              <NavLink to="/administracao/modulos" aria-describedby="menu-administracao">
                Módulos
              </NavLink>
              <NavLink to="/administracao/ia" aria-describedby="menu-administracao">
                IA do condomínio
              </NavLink>
            </>
          )}
        </nav>
      </aside>
      <div className="conteudo">
        <header className="topo">
          {usuario.condominiums.length > 1 ? (
            <select value={condominioId} onChange={(e) => trocarCondominio(e.target.value)} aria-label="Condomínio">
              {usuario.condominiums.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          ) : (
            <strong>{condominioNome}</strong>
          )}
          <div className="usuario">
            <span>
              {usuario.name} <small>({usuario.roles.join(", ").toLowerCase()})</small>
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
