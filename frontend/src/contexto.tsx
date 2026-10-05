import { createContext, useContext, useState, type ReactNode } from "react";
import { useContexto } from "./api/consultas";
import type { ContextoAssistente, Perfil, UsuarioLogado } from "./api/tipos";

interface Sessao {
  usuario: UsuarioLogado;
  condominioId: string;
  condominioNome: string;
  trocarCondominio: (id: string) => void;
  /** Ex.: pode("GESTOR", "ADMIN") para mostrar o botão de envio. O backend é quem barra de verdade. */
  pode: (...perfis: Perfil[]) => boolean;
  /** Códigos dos módulos ligados no condomínio (GET /contexto). Vazio enquanto carrega. */
  modulosLigados: string[];
  /** Ex.: moduloLigado("ASSISTENTE") para mostrar o menu Assistente. O backend também barra (403). */
  moduloLigado: (codigo: string) => boolean;
  /** Modo de IA efetivo do Assistente (RF-04.16); nulo com o módulo desligado ou enquanto carrega. */
  assistente: ContextoAssistente | null;
  /** O contexto já chegou da API (evita mostrar "módulo desligado" enquanto carrega). */
  contextoCarregado: boolean;
}

const ContextoSessao = createContext<Sessao | null>(null);

export function ProvedorSessao({ usuario, children }: { usuario: UsuarioLogado; children: ReactNode }) {
  const [condominioId, setCondominioId] = useState(usuario.condominios[0].id);
  const condominio = usuario.condominios.find((c) => c.id === condominioId)!;
  // Recarrega ao trocar de condomínio (a chave da consulta inclui o id) e depois de ligar/desligar um módulo
  const { data: contexto } = useContexto(condominioId);
  const modulosLigados = contexto?.modulosLigados ?? [];

  const sessao: Sessao = {
    usuario,
    condominioId,
    condominioNome: condominio.nome,
    trocarCondominio: setCondominioId,
    pode: (...perfis) => perfis.some((p) => usuario.perfis.includes(p)),
    modulosLigados,
    moduloLigado: (codigo) => modulosLigados.includes(codigo),
    assistente: contexto?.assistente ?? null,
    contextoCarregado: contexto !== undefined,
  };
  return <ContextoSessao.Provider value={sessao}>{children}</ContextoSessao.Provider>;
}

export function useSessao(): Sessao {
  const sessao = useContext(ContextoSessao);
  if (!sessao) throw new Error("useSessao fora do ProvedorSessao");
  return sessao;
}
