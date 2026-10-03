import { createContext, useContext, useState, type ReactNode } from "react";
import type { Perfil, UsuarioLogado } from "./api/tipos";

interface Sessao {
  usuario: UsuarioLogado;
  condominioId: string;
  condominioNome: string;
  trocarCondominio: (id: string) => void;
  /** Ex.: pode("GESTOR", "ADMIN") para mostrar o botão de envio. O backend é quem barra de verdade. */
  pode: (...perfis: Perfil[]) => boolean;
}

const ContextoSessao = createContext<Sessao | null>(null);

export function ProvedorSessao({ usuario, children }: { usuario: UsuarioLogado; children: ReactNode }) {
  const [condominioId, setCondominioId] = useState(usuario.condominios[0].id);
  const condominio = usuario.condominios.find((c) => c.id === condominioId)!;
  const sessao: Sessao = {
    usuario,
    condominioId,
    condominioNome: condominio.nome,
    trocarCondominio: setCondominioId,
    pode: (...perfis) => perfis.some((p) => usuario.perfis.includes(p)),
  };
  return <ContextoSessao.Provider value={sessao}>{children}</ContextoSessao.Provider>;
}

export function useSessao(): Sessao {
  const sessao = useContext(ContextoSessao);
  if (!sessao) throw new Error("useSessao fora do ProvedorSessao");
  return sessao;
}
