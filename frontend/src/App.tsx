import { BrowserRouter, Route, Routes } from "react-router";
import { useUsuario } from "./api/consultas";
import { Layout } from "./componentes/Layout";
import { ProvedorSessao } from "./contexto";
import { sair } from "./autenticacao/keycloak";
import { AdministracaoModulos } from "./paginas/AdministracaoModulos";
import { Arquivos } from "./paginas/Arquivos";
import { Inicio } from "./paginas/Inicio";

export function App() {
  const { data: usuario, error } = useUsuario();
  if (error) return <p className="aviso erro">Não foi possível falar com o servidor: {error.message}</p>;
  if (!usuario) return <p className="aviso">Carregando…</p>;
  if (usuario.condominios.length === 0) {
    return (
      <p className="aviso">
        Seu usuário ainda não está vinculado a nenhum condomínio. Peça ao administrador.{" "}
        <button className="botao-link" onClick={sair}>
          Sair
        </button>
      </p>
    );
  }
  return (
    <ProvedorSessao usuario={usuario}>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<Inicio />} />
            <Route path="arquivos" element={<Arquivos />} />
            <Route path="administracao/modulos" element={<AdministracaoModulos />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ProvedorSessao>
  );
}
