# Auditoria do Condomínio

Contabilidade e auditoria mensal de condomínios: lê os relatórios da administradora (PDF, Excel e Word), confere as contas, aponta inconsistências com evidência e mostra os números num painel. Multi-condomínio; piloto: Mio Residencial Parque.

## Rodar na sua máquina

Pré-requisito: **Docker** (com Docker Compose). Não precisa instalar Java, Node nem Python.

```bash
cd infra
docker compose up --build
```

Na primeira vez demora alguns minutos (baixa imagens e compila). Depois:

| O quê | Endereço | Acesso |
|---|---|---|
| Sistema | http://localhost:8080 | `gestor` / `gestor`, `usuario` / `usuario`, `admin` / `admin` |
| Keycloak (administração de usuários) | http://localhost:8180 | `admin` / `admin` |
| API (direto) | http://localhost:8081/api | token Bearer do Keycloak |

Os arquivos enviados ficam em `dados/` (fora do git). O banco fica num volume do Docker; `docker compose down -v` apaga tudo e recomeça do zero.

## Perfis

- **Usuário**: consulta e abre arquivos.
- **Gestor**: também envia e reprocessa arquivos.
- **Admin**: tudo, em todos os condomínios.

Os usuários de exemplo estão em `infra/keycloak/realm-condominio.json`. O vínculo com o condomínio é o atributo `condominios` do usuário no Keycloak.

## O que já funciona (primeira entrega)

1. Login pelo Keycloak; a sessão cai após 30 minutos sem uso.
2. Envio de arquivo por categoria. O original vai para a pasta `dados/<condomínio>/<categoria>/<ano>/`, com hash SHA-256 para detectar envio repetido.
3. Processamento em segundo plano com status (na fila, processando, concluído, precisa revisão, falhou). Gravação numa transação só; reprocessar não duplica; o que estava pela metade volta para a fila quando o sistema reinicia.
4. Leitura do **fluxo de caixa por fundo** (layout da administradora do piloto) com quatro conferências: saldo linha a linha, totais por fundo, saldo final × Posição Financeira e soma dos fundos × total.
5. Tela inicial com os números do mês, gráfico por fundo, fundos negativos e maiores despesas, mais o indicador discreto do último arquivo.

Outros documentos (PO, contratos, atas…) já podem ser enviados e ficam guardados; a leitura de cada tipo vem nas próximas entregas.

## Estrutura

```
backend/            Java 25 + Spring Boot (Gradle multi-módulo)
  domain/           modelo e regras puras (sem Spring)
  storage/          interface Armazenamento (disco local; S3 depois)
  ingestion/        contrato do leitor + interpretação dos layouts
  app/              API, segurança, processamento @Async, banco (Flyway)
services/ingestion-py/  leitor de documentos: arquivo → JSON (sem estado)
frontend/           React + TypeScript + Vite
contracts/          openapi.yaml e JSON Schema do leitor
infra/              docker-compose e realm do Keycloak
docs/               requisitos, arquitetura, tecnologias e ADRs
```

## Desenvolvimento

```bash
cd infra && docker compose up -d banco keycloak leitor   # só a infraestrutura
./gradlew :backend:app:bootRun --args='--server.port=8081'  # backend (Java 25)
cd frontend && pnpm install && pnpm dev                   # tela em http://localhost:5173
```
