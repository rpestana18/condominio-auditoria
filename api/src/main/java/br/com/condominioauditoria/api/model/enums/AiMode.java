package br.com.condominioauditoria.api.model.enums;

/**
 * Who ran the AI part of an operation (RF-09.6). LOCAL = model on the system's infrastructure (e.g. embeddings on
 * Ollama); DESLIGADO = no model at all (e.g. keyword-only search).
 */
public enum AiMode {
    API_KEY, MCP_EXTERNO, LOCAL, DESLIGADO
}
