import { useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import { useSaveAiConfiguration } from "../../api/queries";
import type { AiConfiguration, AiMode, AiConfigurationRequest, AiProvider } from "../../api/types";
import { useSession } from "../../context";
import { formatDateTime } from "../../format";
import { KeyField } from "./KeyField";
import { ModeChoice, modeDescriptions, modeOptions } from "./ModeChoice";
import { ModelSelector } from "./ModelSelector";

/** Assistant answers: inherit the general mode or use a mode of their own. */
type AnswersMode = "INHERIT" | AiMode;
type EmbeddingsMode = "LOCAL" | "OFF";

interface Props {
  configuration: AiConfiguration;
  providers: AiProvider[];
  /** The catalog did not load (rag down): the screen warns, but still lets the modes change. */
  catalogError: string | null;
}

/** Initial provider: the stored one; otherwise, the first in the catalog for that use. */
const initial = (stored: string | null | undefined, list: AiProvider[]) => stored ?? list[0]?.code ?? "";

/**
 * AI configuration form (RF-09.1, RF-09.2, RF-09.6). Only builds the request: the backend is the one that validates
 * and refuses (422, with the reasons).
 */
export function AiConfigurationForm({ configuration, providers, catalogError }: Props) {
  const { condominiumId } = useSession();
  const save = useSaveAiConfiguration(condominiumId);
  const { answers, embeddings } = configuration.assistant;
  const answersProviders = providers.filter((p) => p.function === "ANSWERS");
  // Embeddings only with a local provider in this phase (Q12)
  const embeddingsProviders = providers.filter((p) => p.function === "EMBEDDINGS" && p.local);

  const [generalMode, setGeneralMode] = useState<AiMode>(configuration.generalMode);
  const [answersMode, setAnswersMode] = useState<AnswersMode>(answers.mode ?? "INHERIT");
  const [answersProvider, setAnswersProvider] = useState(initial(answers.provider, answersProviders));
  const [answersModel, setAnswersModel] = useState(answers.model ?? "");
  const [apiKey, setApiKey] = useState("");
  const [removeKey, setRemoveKey] = useState(false);
  const [embeddingsMode, setEmbeddingsMode] = useState<EmbeddingsMode>(embeddings.mode === "OFF" ? "OFF" : "LOCAL");
  const [embeddingsProvider, setEmbeddingsProvider] = useState(initial(embeddings.provider, embeddingsProviders));
  const [embeddingsModel, setEmbeddingsModel] = useState(embeddings.model ?? "");
  const [saved, setSaved] = useState(false);

  // Only to show or hide the provider and key fields; the real effective mode is calculated by the backend
  const answersModeOnScreen = answersMode === "INHERIT" ? generalMode : answersMode;
  const usesKey = answersModeOnScreen === "API_KEY";

  function submit(event: FormEvent) {
    event.preventDefault();
    setSaved(false);
    const request: AiConfigurationRequest = {
      generalMode,
      assistant: {
        answers: {
          mode: answersMode === "INHERIT" ? null : answersMode,
          provider: answersProvider || null,
          model: answersModel || null,
          // Blank = keep the stored key (the key never comes back from the server)
          key: removeKey ? null : apiKey.trim() || null,
          removeKey,
        },
        embeddings: {
          mode: embeddingsMode,
          provider: embeddingsMode === "LOCAL" ? embeddingsProvider || null : null,
          model: embeddingsMode === "LOCAL" ? embeddingsModel || null : null,
        },
      },
    };
    save.mutate(request, {
      onSuccess: () => {
        setApiKey("");
        setRemoveKey(false);
        setSaved(true);
      },
    });
  }

  const reasons = save.error instanceof ApiError ? (save.error.problem?.reasons ?? []) : [];

  return (
    <form onSubmit={submit} onChange={() => setSaved(false)}>
      <section className="block">
        <h2>Modo geral do condomínio</h2>
        <ModeChoice name="general-mode" legend="Quem executa a IA" options={modeOptions} value={generalMode} onChange={setGeneralMode} />
      </section>

      <section className="block">
        <h2>Assistente: respostas (chat)</h2>
        <ModeChoice<AnswersMode>
          name="answers-mode"
          legend="Modo das respostas"
          options={[
            {
              value: "INHERIT",
              label: "Herdar do geral",
              description: `Segue o modo geral (hoje: ${modeDescriptions[generalMode as keyof typeof modeDescriptions]?.label ?? generalMode}).`,
            },
            ...modeOptions,
          ]}
          value={answersMode}
          onChange={setAnswersMode}
        />
        <p className="muted">
          Modo efetivo gravado agora: <strong>{modeDescriptions[answers.effectiveMode as keyof typeof modeDescriptions]?.label ?? answers.effectiveMode}</strong>
        </p>

        {usesKey && (
          <>
            <h3>Provedor e modelo</h3>
            {answersProviders.length === 0 ? (
              <p className="notice warning">{catalogError ?? "O catálogo não tem provedor de respostas."}</p>
            ) : (
              <ModelSelector
                id="answers"
                providers={answersProviders}
                provider={answersProvider}
                model={answersModel}
                onChange={(p, m) => {
                  setAnswersProvider(p);
                  setAnswersModel(m);
                }}
              />
            )}
            <h3>Chave de API do condomínio</h3>
            <KeyField
              keyRegistered={answers.keyRegistered}
              keySuffix={answers.keySuffix}
              apiKey={apiKey}
              onChangeKey={setApiKey}
              remove={removeKey}
              onChangeRemove={(remove) => {
                setRemoveKey(remove);
                setApiKey("");
              }}
            />
          </>
        )}
        {!usesKey && answers.keyRegistered && (
          <p className="muted">
            Chave cadastrada terminando em ••••{answers.keySuffix}; fica guardada, mas não é usada neste modo.{" "}
            <button type="button" className="link-button" onClick={() => setRemoveKey(!removeKey)}>
              {removeKey ? "Desfazer remoção" : "Remover chave"}
            </button>
            {removeKey && " (será removida ao salvar)"}
          </p>
        )}
      </section>

      <section className="block">
        <h2>Assistente: embeddings (busca por significado)</h2>
        <ModeChoice<EmbeddingsMode>
          name="embeddings-mode"
          legend="Modo dos embeddings"
          options={[
            { value: "LOCAL", label: "Local", description: "Modelo na infraestrutura do sistema; nenhum texto vai para fora." },
            { value: "OFF", label: "Desligado", description: "Sem busca por significado; a busca por palavra continua." },
          ]}
          value={embeddingsMode}
          onChange={setEmbeddingsMode}
        />
        {embeddingsMode === "LOCAL" &&
          (embeddingsProviders.length === 0 ? (
            <p className="notice warning">{catalogError ?? "O catálogo não tem provedor local de embeddings."}</p>
          ) : (
            <ModelSelector
              id="embeddings"
              providers={embeddingsProviders}
              provider={embeddingsProvider}
              model={embeddingsModel}
              hidePrices
              onChange={(p, m) => {
                setEmbeddingsProvider(p);
                setEmbeddingsModel(m);
              }}
            />
          ))}
      </section>

      <div aria-live="polite">
        {save.isError && (
          <div className="notice error" role="alert">
            <p>{save.error.message}</p>
            {reasons.length > 0 && (
              <ul>
                {reasons.map((r) => (
                  <li key={r}>{r}</li>
                ))}
              </ul>
            )}
          </div>
        )}
        {saved && <p className="notice ok">Configuração gravada. O menu e a tela do Assistente já seguem o novo modo.</p>}
      </div>

      <div className="actions">
        <button type="submit" className="button" disabled={save.isPending}>
          {save.isPending ? "Gravando…" : "Salvar"}
        </button>
        {configuration.updatedBy && configuration.updatedAt ? (
          <span className="muted">
            Última alteração por {configuration.updatedBy} em {formatDateTime(configuration.updatedAt)}
          </span>
        ) : (
          <span className="muted">Nunca gravada: valem os padrões.</span>
        )}
      </div>
    </form>
  );
}
