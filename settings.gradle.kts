rootProject.name = "condominio-auditoria"

// Cada serviço Java é um projeto Gradle próprio, com seu jar e seu contêiner.
// Frontend e leitor Python têm build próprio (pnpm e Docker).
include(
    "libs:storage",         // storage interface for the originals (local folder or S3)
    "libs:grpc-contract",   // generated code of the gRPC contract (contracts/grpc)
    "api",                  // API REST, contábil, auditoria, orçamento, relatórios (ex-backend, ADR 0006)
    "rag",                  // leitura, extração, enriquecimento e (depois) embeddings e busca
    "mcp",                  // porta de entrada do Claude externo (MCP), fala com o api por gRPC
)
