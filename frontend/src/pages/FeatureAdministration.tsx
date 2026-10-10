import { useFeatures } from "../api/queries";
import { FeatureCard } from "../components/features/FeatureCard";
import { FeatureUsage } from "../components/features/FeatureUsage";
import { useSession } from "../context";

/**
 * Administração › Módulos (RF-10). The menu only shows up for ADMIN, but the backend is the one that really blocks:
 * if another role reaches this address, the change, trail and usage calls come back 403.
 */
export function FeatureAdministration() {
  const { condominiumId, condominiumName, can } = useSession();
  const { data: features = [], isLoading, error } = useFeatures(condominiumId);
  const featureNames = Object.fromEntries(features.map((f) => [f.code, f.name]));

  if (!can("ADMIN")) {
    return <p className="notice">Esta tela é do administrador da plataforma.</p>;
  }

  return (
    <>
      <header className="page-title">
        <h1>
          <span className="muted breadcrumb">Administração ›</span> Módulos
        </h1>
        <span className="muted">{condominiumName}</span>
      </header>

      {isLoading ? (
        <p className="notice">Carregando…</p>
      ) : error ? (
        <p className="notice error">{error.message}</p>
      ) : features.length === 0 ? (
        <p className="notice">O catálogo não tem módulos opcionais.</p>
      ) : (
        features.map((f) => <FeatureCard key={f.code} feature={f} />)
      )}

      <FeatureUsage featureNames={featureNames} />
    </>
  );
}
