import Keycloak from "keycloak-js";
import { config } from "../config";

/**
 * Login through Keycloak (Authorization Code + PKCE). The access token lasts a few minutes and is renewed
 * on each API call; if the person stays idle beyond Keycloak's session time, the renewal fails
 * and they go back to the login page.
 */
export const keycloak = new Keycloak({
  url: config.keycloakUrl,
  realm: config.keycloakRealm,
  clientId: config.keycloakClient,
});

export async function startLogin(): Promise<void> {
  await keycloak.init({
    onLoad: "login-required",
    pkceMethod: "S256",
    checkLoginIframe: false,
  });
}

/** Returns a valid token, renewing it if less than 30 seconds are left before it expires. */
export async function validToken(): Promise<string> {
  try {
    await keycloak.updateToken(30);
  } catch {
    // Session expired due to inactivity: back to login
    await keycloak.login();
  }
  return keycloak.token!;
}

export function logout(): void {
  void keycloak.logout({ redirectUri: window.location.origin });
}
