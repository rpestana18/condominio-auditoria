interface Props {
  keyRegistered: boolean;
  keySuffix: string | null | undefined;
  apiKey: string;
  onChangeKey: (apiKey: string) => void;
  remove: boolean;
  onChangeRemove: (remove: boolean) => void;
}

/**
 * The condominium's API key: write only. The screen never gets the key back, only its last 4 characters.
 * Blank keeps the stored one; filled replaces it; "Remover chave" deletes it on save.
 */
export function KeyField({ keyRegistered, keySuffix, apiKey, onChangeKey, remove, onChangeRemove }: Props) {
  return (
    <div className="key-field">
      <p className="muted">
        {remove
          ? "A chave será removida ao salvar."
          : keyRegistered
            ? `Chave cadastrada terminando em ••••${keySuffix ?? ""}.`
            : "Nenhuma chave cadastrada."}
      </p>
      <label className="field">
        {keyRegistered ? "Trocar a chave (deixe em branco para manter)" : "Chave de API"}
        <input
          type="password"
          value={apiKey}
          onChange={(e) => onChangeKey(e.target.value)}
          autoComplete="new-password"
          spellCheck={false}
          minLength={8}
          maxLength={500}
          disabled={remove}
        />
      </label>
      {keyRegistered && (
        <button type="button" className="link-button" onClick={() => onChangeRemove(!remove)}>
          {remove ? "Desfazer remoção" : "Remover chave"}
        </button>
      )}
    </div>
  );
}
