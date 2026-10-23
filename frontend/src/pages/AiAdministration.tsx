import { useAiConfiguration, useAiProviders } from "../api/queries";
import { AiConfigurationForm } from "../components/ai/AiConfigurationForm";
import { useSession } from "../context";

/**
 * Administração › IA do condomínio (RF-09.1, RF-09.2, RF-09.6). The menu only shows up for ADMIN;
 * the backend refuses (403) the other roles.
 */
export function AiAdministration() {
  const { condominiumId, condominiumName, can } = useSession();
  // Only the Admin asks the API: another role reaching this address sees the notice below, not a 403
  const configuration = useAiConfiguration(condominiumId, can("ADMIN"));
  const providers = useAiProviders(can("ADMIN"));

  if (!can("ADMIN")) {
    return <p className="notice">Esta tela é do administrador da plataforma.</p>;
  }

  return (
    <>
      <header className="page-title">
        <h1>
          <span className="muted breadcrumb">Administração ›</span> IA do condomínio
        </h1>
        <span className="muted">{condominiumName}</span>
      </header>

      {configuration.isLoading || providers.isLoading ? (
        <p className="notice">Carregando…</p>
      ) : configuration.error ? (
        <p className="notice error">{configuration.error.message}</p>
      ) : configuration.data ? (
        // key: switching condominium restarts the form with the other one's data
        <AiConfigurationForm
          key={condominiumId}
          configuration={configuration.data}
          providers={providers.data ?? []}
          catalogError={providers.error ? `Catálogo de provedores indisponível: ${providers.error.message}` : null}
        />
      ) : null}
    </>
  );
}
