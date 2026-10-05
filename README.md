# Auditoria do Condomínio

Contabilidade e auditoria mensal de condomínios: lê os relatórios da administradora (PDF, Excel e Word), confere as contas, aponta inconsistências com evidência e mostra os números num painel. Multi-condomínio; piloto: Mio Residencial Parque.

## Rodar na sua máquina (passo a passo)

### 1. Instale os pré-requisitos

Só dois programas. Java, Node e Python **não** são necessários para rodar, porque tudo é compilado dentro do Docker.

| Programa | Onde baixar | Como conferir |
|---|---|---|
| **Git** | https://git-scm.com/downloads | `git --version` |
| **Docker Desktop** (Windows/Mac) ou **Docker Engine + Compose** (Linux) | https://www.docker.com/products/docker-desktop | `docker compose version` |

No Docker Desktop, reserve pelo menos **6 GB de memória** (o Ollama, que gera os embeddings da busca nos documentos, usa cerca de 1,5 GB) e **3 GB de disco** livres para o modelo (Settings → Resources) e deixe o Docker aberto.

As portas **8080** a **8083**, **8090**, **8180**, **9090**, **5432**, **5672**, **11434** (só no endereço local) e **15672** precisam estar livres. Se você já tem um PostgreSQL local na 5432 ou um RabbitMQ na 5672, pare o serviço antes.

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

A primeira vez leva de 10 a 15 minutos, porque baixa as imagens, as dependências do Gradle e do pnpm e compila os três serviços Java. Nas próximas vezes leva segundos.

O sistema está pronto quando o log mostrar `Started BackendApplication`, `Started RagApplication` e `Started McpApplication`. Deixe esse terminal aberto. Para rodar em segundo plano, use `docker compose up --build -d` e acompanhe com `docker compose logs -f backend rag`.

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
3. A situação passa por "Na fila", "Processando" e "Concluído", e a lista se atualiza sozinha. Por trás, o backend guarda o original e pede a leitura ao serviço **rag** pela fila; o rag lê, confere e devolve os dados, que o backend grava. Clique no arquivo para ver as conferências.
4. Volte em **Início** para ver os números do mês.

### 6. Pare, reinicie ou recomece do zero

```bash
docker compose stop          # para tudo e mantém os dados
docker compose start         # sobe de novo
docker compose down          # remove os contêineres e mantém o banco
docker compose down -v       # apaga o banco também (recomeça do zero)
```

Os arquivos enviados ficam na pasta `dados/` na raiz do projeto e não são apagados pelo `down -v`. Para zerar tudo, apague também o conteúdo de `dados/` (mantenha o `.gitkeep`).

Já rodava a versão anterior (antes dos serviços separados)? O backend passou a usar o schema `backend` do banco e cria as tabelas de novo nele. Os arquivos da pasta `dados/` continuam lá: envie de novo ou rode `docker compose down -v` para começar limpo.

### Busca nos documentos (modelo bge-m3 e índice)

A busca nos documentos usa embeddings locais gerados pelo **Ollama** com o modelo **bge-m3** (nada sai da sua máquina). Na **primeira** subida, o passo `ollama-modelo` baixa o modelo (cerca de **1,2 GB**) para o volume `ollama`, e o `rag` só sobe depois que o download termina: conte alguns minutos a mais. Acompanhe com `docker compose logs -f ollama-modelo`. Nas próximas subidas o modelo já está no volume e nada é baixado (`docker compose down -v` apaga o volume e o modelo é baixado de novo).

Cada arquivo enviado entra no índice em segundo plano, separado da leitura contábil. A situação da indexação (`NA_FILA`, `INDEXANDO`, `INDEXADO`, `SEM_TEXTO`, `ERRO`) vem no campo `indexacao` de cada arquivo na API (`GET /api/condominios/{id}/arquivos`). Arquivos enviados **antes desta versão** não estão no índice: use **Reprocessar** em cada um para que entrem na busca.

Sem o Ollama, a busca continua funcionando só por palavra para o que já foi indexado; arquivos novos ficam com a indexação em `ERRO` até o Ollama voltar e o arquivo ser reprocessado.

### Endereços úteis

| O quê | Endereço | Acesso |
|---|---|---|
| Sistema | http://localhost:8080 | usuários acima |
| Keycloak (administração de usuários) | http://localhost:8180 | `admin` / `admin` |
| API (direto) | http://localhost:8081/api | token Bearer do Keycloak |
| Fila (painel do RabbitMQ) | http://localhost:15672 | `condominio` / `condominio` |
| MCP (para o Claude) | http://localhost:8083/mcp | token Bearer do Keycloak |
| Saúde dos serviços | http://localhost:8081/actuator/health (backend), :8082 (rag), :8083 (mcp) | livre |
| Leitor de documentos | http://localhost:8090/saude | livre |

### Se algo der errado

| Sintoma | O que fazer |
|---|---|
| `port is already allocated` | Outra coisa usa a porta. Pare o programa ou mude a porta da esquerda em `infra/docker-compose.yml` (ex.: `"8085:80"`) |
| A tela de login não abre logo após subir | O Keycloak leva cerca de 30 s para iniciar. Aguarde e recarregue |
| O arquivo fica em "Falhou" com "O leitor de documentos não respondeu" | Confira com `docker compose ps` se o contêiner `leitor` está de pé e use **Reprocessar** |
| O arquivo fica em "Na fila" | O serviço `rag` está parado. Suba com `docker compose start rag`: o pedido esperou na fila e é lido na hora |
| Mensagens na fila `.erro` (painel do RabbitMQ) | Uma leitura ou gravação falhou três vezes. O log do `rag` ou do `backend` diz o motivo |
| "Arquivo já enviado" | O sistema reconhece o mesmo conteúdo pelo hash. É proteção contra duplicidade |
| Quer ver os logs | `docker compose logs -f backend` (ou `rag`, `mcp`, `leitor`, `fila`, `keycloak`, `banco`) |
| Linux: erro de permissão ao gravar ou ler em `/dados` | Backend e rag rodam com o usuário 1001. Libere a pasta: `chmod 777 dados` na raiz do projeto |
| Build para em `./gradlew ... bootJar` com `exit code: 127` (comum no Windows) | O `gradlew` foi baixado com final de linha do Windows (CRLF). Rode `git pull` e suba de novo: o Dockerfile e o `.gitattributes` já corrigem isso. Se ainda falhar, clone o projeto de novo |
| A subida para em `ollama-modelo` (`service "ollama-modelo" didn't complete successfully`) | O modelo bge-m3 não foi baixado: sem internet ou acesso bloqueado a `registry.ollama.ai`. Veja o motivo em `docker compose logs ollama-modelo`, libere o acesso e rode `docker compose up` de novo (o download continua de onde parou). O `rag` não sobe sem o modelo |
| Indexação do arquivo em `ERRO` com motivo sobre embeddings ou Ollama | O contêiner `ollama` está parado ou sem o modelo. Confira com `docker compose ps ollama` e `docker compose logs ollama-modelo`, suba com `docker compose up -d ollama ollama-modelo` e use **Reprocessar** no arquivo |
| `buscar_documentos` responde "Busca nos documentos indisponível no momento" | O serviço `rag` está parado. Suba com `docker compose start rag` |
| Mudou o código e quer ver na tela | `docker compose up --build` de novo |

## Criar usuários e dar acesso a um condomínio

1. Entre em http://localhost:8180 com `admin` / `admin` e escolha o realm **condominio**.
2. Em **Users → Add user**, crie o usuário. Na aba **Credentials**, defina a senha.
3. Na aba **Role mapping**, atribua `USUARIO`, `GESTOR` ou `ADMIN`.
4. Na aba **Attributes**, preencha `condominios` com o id do condomínio. O do piloto é `6f1d2c1e-3b4a-4c8e-9a51-2815a0000001`.

Os usuários de exemplo e as regras de sessão (token de 5 min, sessão que cai após 30 min sem uso) estão em `infra/keycloak/realm-condominio.json`. Esse arquivo só é importado quando o Keycloak sobe com o banco dele vazio.

## Assistente na tela (chat sobre os documentos)

O Assistente é um módulo contratável: aparece no menu só quando está ligado em **Administração › Módulos** (o piloto já vem ligado). A tela tem a **Busca nos documentos** (por palavra, sem IA) em qualquer modo e o **chat** só no modo `API_KEY`.

Para ligar o chat no condomínio:

1. Entre como `admin` e abra **Administração › IA do condomínio**.
2. Em "Assistente — respostas", escolha `API_KEY`, o provedor `anthropic` e o modelo (Claude Sonnet 5.5 é o padrão; Claude Haiku 4.5 é a opção mais barata).
3. Cole a chave de API do condomínio (crie em https://console.anthropic.com) e salve. A chave é cifrada com a chave pública do `rag` e nunca mais aparece: a tela mostra só os 4 últimos caracteres.

Cada pergunta responde em dois blocos: **Nos documentos** (com citações que abrem o original na página) e **Nos dados gravados** (números das consultas ao banco, nunca calculados pela IA). Sem fonte, a resposta é "Não encontrei nos documentos". A conversa fica só na tela e some ao trocar de condomínio ou sair. O uso (perguntas, tokens e custo estimado em US$) aparece em **Administração › Módulos**.

Nos modos `MCP_EXTERNO` e `DESLIGADO` o sistema não chama nenhuma IA externa: a tela mostra a busca e, em `MCP_EXTERNO`, como conectar o seu Claude (seção abaixo).

| Situação | O que fazer |
|---|---|
| "A chave de IA do condomínio foi recusada pelo provedor" | A chave foi revogada ou digitada errada. Cadastre de novo em **IA do condomínio** |
| "cadastre a chave de novo" depois de recriar os volumes | O par de chaves do `rag` fica no volume `chaves-rag`. Se o volume foi apagado, as chaves já cadastradas não decifram mais: cadastre de novo |
| "O assistente está indisponível no momento" | O `rag` está parado ou sem acesso a `api.anthropic.com`. Veja `docker compose logs rag` |

## Conectar o Claude (MCP)

O serviço **mcp** deixa o Claude consultar o sistema com as permissões do seu usuário: fundos, arquivos, conferências e lançamentos (com arquivo e página de origem) e trechos dos documentos enviados. Ferramentas: `listar_condominios`, `resumo_fundos`, `listar_arquivos`, `conferencias_do_arquivo`, `buscar_lancamentos` e `buscar_documentos`.

`buscar_documentos` procura no texto dos documentos indexados (atas, contratos, convenção, extratos, planilhas…) e devolve cada trecho com o documento, a categoria e onde ele está no original ("página 3", "aba Plan1, linhas 2–31" ou "parágrafos 4–7"). Aceita `"frase entre aspas"`, `-palavra` para excluir, e filtros por categoria, período e arquivo. O texto dos trechos é transcrição, não conferida: para somar ou comparar valores, o Claude usa as ferramentas numéricas.

1. Gere um token do seu usuário (o cliente `mcp-local` dá um token de 8 horas; é só para uso local):

   ```bash
   TOKEN=$(curl -s -d client_id=mcp-local -d grant_type=password -d username=gestor -d password=gestor \
     http://localhost:8180/realms/condominio/protocol/openid-connect/token | sed 's/.*"access_token":"\([^"]*\)".*/\1/')
   ```

2. No **Claude Code**:

   ```bash
   claude mcp add --transport http condominio http://localhost:8083/mcp --header "Authorization: Bearer $TOKEN"
   ```

   Em outro cliente MCP, use o endereço `http://localhost:8083/mcp` e o cabeçalho `Authorization: Bearer <token>`.

3. Pergunte, por exemplo: "quanto saiu pelo Mercado Pago em setembro e em quais páginas?" ou "o que a ata da última assembleia diz sobre o reajuste da taxa condominial? cite a página".

Quando o token vence, repita os passos 1 e 2. O login pelo navegador (OAuth), sem copiar token, entra quando o sistema for para a nuvem.

## Perfis

- **Usuário**: consulta e abre arquivos.
- **Gestor**: também envia e reprocessa arquivos.
- **Admin**: tudo, em todos os condomínios.

## O que já funciona

1. Login pelo Keycloak; a sessão cai após 30 minutos sem uso.
2. Envio de arquivo por categoria. O original vai para a pasta `dados/<condomínio>/<categoria>/<ano>/`, com hash SHA-256 para detectar envio repetido.
3. Leitura em segundo plano pelo serviço **rag**, pedida pela fila (RabbitMQ), com status (na fila, processando, concluído, precisa revisão, falhou). Se um serviço reinicia, o trabalho espera na fila. Gravação numa transação só; reprocessar não duplica.
4. Leitura do **fluxo de caixa por fundo** (layout da administradora do piloto) com quatro conferências: saldo linha a linha, totais por fundo, saldo final × Posição Financeira e soma dos fundos × total.
5. Tela inicial com os números do mês, gráfico por fundo, fundos negativos e maiores despesas, mais o indicador discreto do último arquivo.
6. Consulta pelo Claude via **MCP**, com o token do próprio usuário (ver acima).

Outros documentos (PO, contratos, atas…) já podem ser enviados e ficam guardados; a leitura de cada tipo vem nas próximas entregas.

## Arquitetura

Cada serviço roda no seu contêiner e só conversa com os outros por contrato (decisão na `docs/adr/0002-servicos-separados.md`):

```
frontend ──REST──▶ backend ──fila (RabbitMQ)──▶ rag ──HTTP──▶ leitor (Python)
                     ▲  ◀──────fila──────────────┘ │
Claude ──MCP──▶ mcp ─┘ gRPC   backend ──gRPC──▶ rag └─HTTP──▶ ollama (embeddings)
```

| Serviço | Faz | Fala com |
|---|---|---|
| `frontend` | telas | backend (REST, `contracts/openapi.yaml`) |
| `backend` | API, contábil, auditoria, relatórios, registro dos arquivos; banco no schema `backend` | rag (fila), mcp (gRPC) |
| `rag` | lê, interpreta, confere e enriquece os documentos; índice e busca nos documentos (schema `rag`) | backend (fila e gRPC), leitor (HTTP), ollama (HTTP) |
| `ollama` | gera os embeddings (modelo bge-m3) para a busca nos documentos | — |
| `mcp` | porta de entrada do Claude, sem banco nem regra | backend (gRPC, `contracts/grpc/`) |
| `leitor` | Python: arquivo → JSON com posições, sem estado | — |

## Estrutura

```
backend/            serviço backend (Java 25 + Spring Boot)
rag/                serviço rag: leitura, interpretação e conferência dos documentos
mcp/                serviço mcp: ferramentas MCP que chamam o backend por gRPC
leitor/             leitor de documentos em Python (sem estado)
libs/
  armazenamento/    interface Armazenamento (pasta local; S3 depois), usada por backend e rag
  contrato-grpc/    código gerado de contracts/grpc (usado por backend, rag e mcp)
frontend/           React + TypeScript + Vite
contracts/          openapi.yaml, leitor/v1, mensagens/v1 e v2 (fila) e grpc/ (.proto)
infra/              docker-compose, Dockerfile dos serviços Java e realm do Keycloak
docs/               requisitos, arquitetura, tecnologias e ADRs
```

## Desenvolvimento (rodar as partes fora do Docker)

Para mexer no código com recarga rápida. Precisa de **Java 25**, **Node 22 + pnpm** (`corepack enable`) e, para o leitor, **Python 3.12**.

```bash
# 1. Só a infraestrutura no Docker
cd infra && docker compose up -d banco fila keycloak leitor ollama ollama-modelo

# 2. Cada serviço Java no seu terminal, na raiz do projeto
PASTA_DADOS=$(pwd)/dados ./gradlew :backend:bootRun     # API na 8081, gRPC na 9090
PASTA_DADOS=$(pwd)/dados ./gradlew :rag:bootRun         # 8082, gRPC na 9091
./gradlew :mcp:bootRun                                  # 8083

# 3. Frontend com recarga automática (outro terminal)
cd frontend && pnpm install && pnpm dev     # http://localhost:5173
```

Testes:

```bash
./gradlew test                                          # backend, rag, mcp e libs
cd leitor && python -m venv .venv && .venv/bin/pip install -r requirements-dev.txt && .venv/bin/pytest
cd frontend && pnpm build                               # checagem de tipos
```

Mudou um contrato? `contracts/openapi.yaml`: rode `pnpm gerar-api` dentro de `frontend/`. `contracts/grpc/`: o Gradle gera o código de novo no build. `contracts/mensagens/`: crie uma nova versão e ajuste backend e rag no mesmo PR; os exemplos em `contracts/mensagens/v2/exemplos/` (resultado) são testados pelos dois lados. O pedido de leitura (`ArquivoRecebido`) continua na v1; o resultado (`ResultadoProcessamento`) é v2 desde a ADR 0004, e a v1 do resultado vai para a fila de erro.
