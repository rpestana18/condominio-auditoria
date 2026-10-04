import { useState } from "react";
import { useConfirmarFundoOrdinario } from "../api/consultas";
import type { Painel } from "../api/tipos";
import { useSessao } from "../contexto";
import { CartaoNumero } from "./CartaoNumero";

const DICA = "Saldo guardado no fundo ordinário até esta data. Não é o resultado do mês.";

/** Sexto cartão da tela inicial (RF-05.1a). Sem fundo ordinário confirmado, o Gestor vê a sugestão (RF-05.1b). */
export function CartaoSaldoAcumulado({ painel }: { painel: Painel }) {
  const { condominioId, pode } = useSessao();
  const ordinario = painel.fundoOrdinario;
  if (!ordinario) return null;

  if (ordinario.confirmado) {
    if (ordinario.saldoAtual === null) {
      return (
        <div className="cartao-numero" title={DICA}>
          <span className="cartao-titulo">Saldo acumulado</span>
          <span className="discreto">Fundo ordinário não encontrado neste relatório</span>
        </div>
      );
    }
    return (
      <CartaoNumero
        titulo="Saldo acumulado"
        valor={ordinario.saldoAtual}
        destaque={ordinario.saldoAtual < 0 ? "negativo" : undefined}
        dica={`${DICA} Fundo: ${ordinario.fundo}.`}
      />
    );
  }

  if (!pode("GESTOR", "ADMIN")) return null;
  return <ConfirmarFundo condominioId={condominioId} painel={painel} sugerido={ordinario.fundoId} />;
}

function ConfirmarFundo({ condominioId, painel, sugerido }: { condominioId: string; painel: Painel; sugerido: string }) {
  const confirmar = useConfirmarFundoOrdinario(condominioId);
  const [trocando, setTrocando] = useState(false);
  const [escolhido, setEscolhido] = useState(sugerido);
  const nomeSugerido = painel.fundos.find((f) => f.fundoId === sugerido)?.fundo ?? "";

  return (
    <div className="cartao-numero">
      <span className="cartao-titulo">Saldo acumulado</span>
      {trocando ? (
        <select value={escolhido} onChange={(e) => setEscolhido(e.target.value)} aria-label="Fundo ordinário">
          {painel.fundos.map((f) => (
            <option key={f.fundoId} value={f.fundoId}>
              {f.fundo}
            </option>
          ))}
        </select>
      ) : (
        <span>
          Este é o fundo ordinário? <strong className="nome-fundo">{nomeSugerido}</strong>
        </span>
      )}
      <div className="acoes-cartao">
        <button className="botao" disabled={confirmar.isPending} onClick={() => confirmar.mutate(escolhido)}>
          Confirmar
        </button>
        {!trocando && (
          <button className="botao-link" onClick={() => setTrocando(true)}>
            Trocar
          </button>
        )}
      </div>
      {confirmar.error && <span className="aviso erro">{confirmar.error.message}</span>}
    </div>
  );
}
