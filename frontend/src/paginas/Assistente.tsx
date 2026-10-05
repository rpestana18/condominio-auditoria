import { useState } from "react";
import { AvisoMcp, MotivoSemChat } from "../componentes/assistente/AvisoModo";
import { BuscaDocumentos } from "../componentes/assistente/BuscaDocumentos";
import { Chat } from "../componentes/assistente/Chat";
import { FiltrosDocumentos } from "../componentes/assistente/FiltrosDocumentos";
import { useSessao } from "../contexto";

type Aba = "CONVERSA" | "BUSCA";

/**
 * Tela "Assistente" (RF-04.8 a 04.18). O que aparece depende do modo de IA efetivo que vem do contexto (RF-04.16):
 * - chat disponível (API_KEY com chave): conversa + busca nos documentos;
 * - MCP_EXTERNO: aviso de que o assistente é o Claude do usuário, instruções de conexão + busca;
 * - DESLIGADO ou API_KEY sem chave: só a busca, com o motivo.
 * Esconder não basta: o backend recusa o chat (409) fora do modo certo e tudo (403) sem o módulo.
 */
export function Assistente() {
  const { condominioId, condominioNome, moduloLigado, assistente, contextoCarregado } = useSessao();
  const [aba, setAba] = useState<Aba>("CONVERSA");

  if (!contextoCarregado) return <p className="aviso">Carregando…</p>;
  if (!moduloLigado("ASSISTENTE") || !assistente) {
    return <p className="aviso">O módulo Assistente não está ligado neste condomínio.</p>;
  }

  const chat = assistente.chatDisponivel;
  return (
    // key: trocar de condomínio limpa o resultado da busca (a conversa já é zerada no ProvedorConversa)
    <div key={condominioId}>
      <header className="titulo-pagina">
        <h1>Assistente</h1>
        <span className="discreto">{condominioNome}</span>
      </header>

      {!chat && (assistente.modoRespostas === "MCP_EXTERNO" ? <AvisoMcp /> : <MotivoSemChat assistente={assistente} />)}

      <FiltrosDocumentos />

      {chat ? (
        <>
          <div className="abas" role="tablist" aria-label="Modo da tela">
            <button role="tab" id="aba-conversa" aria-selected={aba === "CONVERSA"} aria-controls="painel-assistente" onClick={() => setAba("CONVERSA")}>
              Conversa
            </button>
            <button role="tab" id="aba-busca" aria-selected={aba === "BUSCA"} aria-controls="painel-assistente" onClick={() => setAba("BUSCA")}>
              Busca nos documentos
            </button>
          </div>
          <div id="painel-assistente" role="tabpanel" aria-labelledby={aba === "CONVERSA" ? "aba-conversa" : "aba-busca"}>
            {/* A conversa fica no ProvedorConversa: trocar de aba não apaga nada */}
            {aba === "CONVERSA" ? <Chat /> : <BuscaDocumentos />}
          </div>
        </>
      ) : (
        <>
          <h2>Busca nos documentos</h2>
          <BuscaDocumentos />
        </>
      )}
    </div>
  );
}
