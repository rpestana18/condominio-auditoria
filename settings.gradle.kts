rootProject.name = "condominio-auditoria"

// Backend Java (Spring Boot). Frontend e leitor Python têm build próprio (pnpm e Docker).
include("backend:domain", "backend:storage", "backend:ingestion", "backend:app")
