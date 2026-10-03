rootProject.name = "condominio-auditoria"

// Cada serviço Java é um projeto Gradle próprio, com seu jar e seu contêiner.
// Frontend e leitor Python têm build próprio (pnpm e Docker).
include(
    "libs:armazenamento",   // interface de armazenamento dos originais (pasta local ou S3)
    "libs:contrato-grpc",   // código gerado do contrato gRPC (contracts/grpc)
    "backend",              // API, contábil, auditoria, orçamento, relatórios
    "rag",                  // leitura, extração, enriquecimento e (depois) embeddings e busca
    "mcp",                  // porta de entrada do Claude externo (MCP), fala com o backend por gRPC
)
