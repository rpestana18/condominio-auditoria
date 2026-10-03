import Keycloak from "keycloak-js";
import { config } from "../config";

/**
 * Login pelo Keycloak (Authorization Code + PKCE). O token de acesso dura poucos minutos e é renovado
 * a cada chamada à API; se a pessoa ficar parada além do tempo de sessão do Keycloak, a renovação falha
 * e ela volta para a tela de login.
 */
export const keycloak = new Keycloak({
  url: config.keycloakUrl,
  realm: config.keycloakRealm,
  clientId: config.keycloakCliente,
});

export async function iniciarLogin(): Promise<void> {
  await keycloak.init({
    onLoad: "login-required",
    pkceMethod: "S256",
    checkLoginIframe: false,
  });
}

/** Devolve um token válido, renovando se faltar menos de 30 segundos para expirar. */
export async function tokenValido(): Promise<string> {
  try {
    await keycloak.updateToken(30);
  } catch {
    // Sessão expirou por inatividade: volta para o login
    await keycloak.login();
  }
  return keycloak.token!;
}

export function sair(): void {
  void keycloak.logout({ redirectUri: window.location.origin });
}
