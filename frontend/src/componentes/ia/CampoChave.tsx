interface Props {
  chaveCadastrada: boolean;
  chaveFinal: string | null | undefined;
  chave: string;
  aoMudarChave: (chave: string) => void;
  remover: boolean;
  aoMudarRemover: (remover: boolean) => void;
}

/**
 * Chave de API do condomínio: só de escrita. A tela nunca recebe a chave de volta, só os 4 últimos caracteres.
 * Em branco mantém a guardada; preenchida troca; "Remover chave" apaga ao salvar.
 */
export function CampoChave({ chaveCadastrada, chaveFinal, chave, aoMudarChave, remover, aoMudarRemover }: Props) {
  return (
    <div className="campo-chave">
      <p className="discreto">
        {remover
          ? "A chave será removida ao salvar."
          : chaveCadastrada
            ? `Chave cadastrada terminando em ••••${chaveFinal ?? ""}.`
            : "Nenhuma chave cadastrada."}
      </p>
      <label className="campo">
        {chaveCadastrada ? "Trocar a chave (deixe em branco para manter)" : "Chave de API"}
        <input
          type="password"
          value={chave}
          onChange={(e) => aoMudarChave(e.target.value)}
          autoComplete="new-password"
          spellCheck={false}
          minLength={8}
          maxLength={500}
          disabled={remover}
        />
      </label>
      {chaveCadastrada && (
        <button type="button" className="botao-link" onClick={() => aoMudarRemover(!remover)}>
          {remover ? "Desfazer remoção" : "Remover chave"}
        </button>
      )}
    </div>
  );
}
