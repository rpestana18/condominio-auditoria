import { BrowserRouter, Route, Routes } from "react-router";
import { useUsuario } from "./api/consultas";
import { Layout } from "./componentes/Layout";
import { ProvedorSessao } from "./contexto";
import { sair } from "./autenticacao/keycloak";
import { AdministracaoModulos } from "./paginas/AdministracaoModulos";
import { Arquivos } from "./paginas/Arquivos";
import { Depara } from "./paginas/Depara";
import { Inicio } from "./paginas/Inicio";
import { PrevisaoPo } from "./paginas/PrevisaoPo";
import { Previsoes } from "./paginas/Previsoes";
import { PrevistoRealizado } from "./paginas/PrevistoRealizado";

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
            <Route path="previsto-realizado" element={<PrevistoRealizado />} />
            <Route path="depara" element={<Depara />} />
            <Route path="previsoes" element={<Previsoes />} />
            <Route path="previsoes/:poId" element={<PrevisaoPo />} />
            <Route path="administracao/modulos" element={<AdministracaoModulos />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ProvedorSessao>
  );
}
