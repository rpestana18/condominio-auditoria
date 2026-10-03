# Auditoria do Condomínio

Contabilidade e auditoria mensal de condomínios: lê os relatórios da administradora (PDF, Excel e Word), confere as contas, aponta inconsistências com evidência e mostra os números num painel. Multi-condomínio; piloto: Mio Residencial Parque.

## Rodar na sua máquina (passo a passo)

### 1. Instale os pré-requisitos

Só dois programas. Java, Node e Python **não** são necessários para rodar, porque tudo é compilado dentro do Docker.

| Programa | Onde baixar | Como conferir |
|---|---|---|
| **Git** | https://git-scm.com/downloads | `git --version` |
| **Docker Desktop** (Windows/Mac) ou **Docker Engine + Compose** (Linux) | https://www.docker.com/products/docker-desktop | `docker compose version` |

No Docker Desktop, reserve pelo menos **4 GB de memória** (Settings → Resources) e deixe o Docker aberto.

As portas **8080**, **8081**, **8090**, **8180** e **5432** precisam estar livres. Se você já tem um PostgreSQL local na 5432, pare o serviço antes.

### 2. Baixe o código

```bash
git clone https://github.com/rpestana18/condominio-auditoria.git
cd condominio-auditoria
```

### 3. Suba o sistema

```bash
cd infra
docker compose up --build
```

A primeira vez leva de 5 a 10 minutos, porque baixa as imagens, as dependências do Gradle e do pnpm e compila tudo. Nas próximas vezes leva segundos.

O sistema está pronto quando o log do backend mostrar `Started AuditoriaApplication`. Deixe esse terminal aberto. Para rodar em segundo plano, use `docker compose up --build -d` e acompanhe com `docker compose logs -f backend`.

### 4. Entre no sistema

Abra **http://localhost:8080**. A tela de login é do Keycloak.

| Usuário | Senha | Pode |
|---|---|---|
| `gestor` | `gestor` | consultar, enviar e reprocessar arquivos |
| `usuario` | `usuario` | só consultar |
| `admin` | `admin` | tudo, em todos os condomínios |

### 5. Envie o primeiro arquivo

1. Entre como `gestor` e vá em **Arquivos**.
2. Escolha a categoria **Balancetes e fluxos de caixa** e envie o PDF do fluxo de caixa mensal da administradora.
3. A situação passa por "Na fila", "Processando" e "Concluído", e a lista se atualiza sozinha. Clique no arquivo para ver as conferências.
4. Volte em **Início** para ver os números do mês.

### 6. Pare, reinicie ou recomece do zero

```bash
docker compose stop          # para tudo e mantém os dados
docker compose start         # sobe de novo
docker compose down          # remove os contêineres e mantém o banco
docker compose down -v       # apaga o banco também (recomeça do zero)
```

Os arquivos enviados ficam na pasta `dados/` na raiz do projeto e não são apagados pelo `down -v`. Para zerar tudo, apague também o conteúdo de `dados/` (mantenha o `.gitkeep`).

### Endereços úteis

| O quê | Endereço | Acesso |
|---|---|---|
| Sistema | http://localhost:8080 | usuários acima |
| Keycloak (administração de usuários) | http://localhost:8180 | `admin` / `admin` |
| API (direto) | http://localhost:8081/api | token Bearer do Keycloak |
| Saúde do backend | http://localhost:8081/actuator/health | livre |
| Leitor de documentos | http://localhost:8090/saude | livre |

### Se algo der errado

| Sintoma | O que fazer |
|---|---|
| `port is already allocated` | Outra coisa usa a porta. Pare o programa ou mude a porta da esquerda em `infra/docker-compose.yml` (ex.: `"8085:80"`) |
| A tela de login não abre logo após subir | O Keycloak leva cerca de 30 s para iniciar. Aguarde e recarregue |
| O arquivo fica em "Falhou" com "O leitor de documentos não respondeu" | Confira com `docker compose ps` se o contêiner `leitor` está de pé e use **Reprocessar** |
| "Arquivo já enviado" | O sistema reconhece o mesmo conteúdo pelo hash. É proteção contra duplicidade |
| Quer ver os logs | `docker compose logs -f backend` (ou `leitor`, `keycloak`, `banco`) |
| Linux: "Falhou" com erro de permissão ao gravar em `/dados` | O backend roda com o usuário 1001. Libere a pasta: `chmod 777 dados` na raiz do projeto |
| Mudou o código e quer ver na tela | `docker compose up --build` de novo |

## Criar usuários e dar acesso a um condomínio

1. Entre em http://localhost:8180 com `admin` / `admin` e escolha o realm **condominio**.
2. Em **Users → Add user**, crie o usuário. Na aba **Credentials**, defina a senha.
3. Na aba **Role mapping**, atribua `USUARIO`, `GESTOR` ou `ADMIN`.
4. Na aba **Attributes**, preencha `condominios` com o id do condomínio. O do piloto é `6f1d2c1e-3b4a-4c8e-9a51-2815a0000001`.

Os usuários de exemplo e as regras de sessão (token de 5 min, sessão que cai após 30 min sem uso) estão em `infra/keycloak/realm-condominio.json`. Esse arquivo só é importado quando o Keycloak sobe com o banco dele vazio.

## Perfis

- **Usuário**: consulta e abre arquivos.
- **Gestor**: também envia e reprocessa arquivos.
- **Admin**: tudo, em todos os condomínios.

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

## Desenvolvimento (rodar as partes fora do Docker)

Para mexer no código com recarga rápida. Precisa de **Java 25**, **Node 22 + pnpm** (`corepack enable`) e, para o leitor, **Python 3.12**.

```bash
# 1. Só a infraestrutura no Docker
cd infra && docker compose up -d banco keycloak leitor

# 2. Backend na porta 8081 (outro terminal, na raiz do projeto)
PASTA_DADOS=$(pwd)/dados ./gradlew :backend:app:bootRun --args='--server.port=8081'

# 3. Frontend com recarga automática (outro terminal)
cd frontend && pnpm install && pnpm dev     # http://localhost:5173
```

Testes:

```bash
./gradlew test                                          # backend
cd services/ingestion-py && python -m venv .venv && .venv/bin/pip install -r requirements-dev.txt && .venv/bin/pytest
cd frontend && pnpm build                               # checagem de tipos
```

Mudou `contracts/openapi.yaml`? Rode `pnpm gerar-api` dentro de `frontend/` para atualizar os tipos.
