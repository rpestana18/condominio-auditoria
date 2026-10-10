import { useModulos } from "../api/consultas";
import { CartaoModulo } from "../componentes/modulos/CartaoModulo";
import { UsoModulos } from "../componentes/modulos/UsoModulos";
import { useSessao } from "../contexto";

/**
 * Administração › Módulos (RF-10). O menu só aparece para ADMIN, mas quem barra de verdade é o backend:
 * se outro perfil chegar aqui pelo endereço, as chamadas de alteração, trilha e uso voltam 403.
 */
export function AdministracaoModulos() {
  const { condominioId, condominioNome, pode } = useSessao();
  const { data: modulos = [], isLoading, error } = useModulos(condominioId);
  const nomesModulos = Object.fromEntries(modulos.map((m) => [m.code, m.name]));

  if (!pode("ADMIN")) {
    return <p className="aviso">Esta tela é do administrador da plataforma.</p>;
  }

  return (
    <>
      <header className="titulo-pagina">
        <h1>
          <span className="discreto migalha">Administração ›</span> Módulos
        </h1>
        <span className="discreto">{condominioNome}</span>
      </header>

      {isLoading ? (
        <p className="aviso">Carregando…</p>
      ) : error ? (
        <p className="aviso erro">{error.message}</p>
      ) : modulos.length === 0 ? (
        <p className="aviso">O catálogo não tem módulos opcionais.</p>
      ) : (
        modulos.map((m) => <CartaoModulo key={m.code} modulo={m} />)
      )}

      <UsoModulos nomesModulos={nomesModulos} />
    </>
  );
}
