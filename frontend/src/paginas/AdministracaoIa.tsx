import { useConfiguracaoIa, useProvedoresIa } from "../api/consultas";
import { FormularioIa } from "../componentes/ia/FormularioIa";
import { useSessao } from "../contexto";

/**
 * Administração › IA do condomínio (RF-09.1, RF-09.2, RF-09.6). O menu só aparece para ADMIN;
 * o backend recusa (403) os demais perfis.
 */
export function AdministracaoIa() {
  const { condominioId, condominioNome, pode } = useSessao();
  const configuracao = useConfiguracaoIa(condominioId);
  const provedores = useProvedoresIa();

  if (!pode("ADMIN")) {
    return <p className="aviso">Esta tela é do administrador da plataforma.</p>;
  }

  return (
    <>
      <header className="titulo-pagina">
        <h1>
          <span className="discreto migalha">Administração ›</span> IA do condomínio
        </h1>
        <span className="discreto">{condominioNome}</span>
      </header>

      {configuracao.isLoading || provedores.isLoading ? (
        <p className="aviso">Carregando…</p>
      ) : configuracao.error ? (
        <p className="aviso erro">{configuracao.error.message}</p>
      ) : configuracao.data ? (
        // key: trocar de condomínio recomeça o formulário com os dados do outro
        <FormularioIa
          key={condominioId}
          configuracao={configuracao.data}
          provedores={provedores.data ?? []}
          erroCatalogo={provedores.error ? `Catálogo de provedores indisponível: ${provedores.error.message}` : null}
        />
      ) : null}
    </>
  );
}
