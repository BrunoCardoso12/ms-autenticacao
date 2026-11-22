# Microsserviço de Autenticação

Cadastro com confirmação de email, login com JWT, refresh token, logout,
recuperação de senha, bloqueio por tentativas e perfis de acesso (USER/ADMIN).

**Escopo:** este serviço cuida **somente** de identidade (quem é o usuário e o que ele pode fazer).
Nenhuma regra de negócio de outros sistemas fica aqui. Os outros microsserviços apenas
validam os tokens emitidos por ele (veja [INTEGRACAO.md](INTEGRACAO.md)).

## Usando como base para um novo projeto

Este projeto é uma **base reutilizável**: clone e ajuste para cada sistema.

### Obrigatório (identidade do projeto)

| O quê | Onde |
|---|---|
| `groupId`, `artifactId`, `name` | `pom.xml` |
| Pacote `com.componentes.autenticacao` | pastas `src/main/java` e `src/test/java` (renomeie pelo VS Code: botão direito no pacote → Rename) |
| `spring.application.name` | `application.properties` |
| Nome/usuário/senha do banco de dev | `compose.yaml` **e** `application-dev.properties` (os dois precisam bater) |
| Textos e remetente dos emails | `service/EmailService.java` e `MAIL_FROM` |
| Admin de desenvolvimento | `application-dev.properties` |

### Ajuste conforme a necessidade

| O quê | Onde |
|---|---|
| Duração dos tokens (15 min / 7 dias) | `jwt.*` em `application.properties` |
| Tentativas de login e tempo de bloqueio | `app.login.*` em `application.properties` |
| Validade dos links de email | `app.token.*` em `application.properties` |
| Perfis (`USER`, `ADMIN`) | `model/Role.java` |
| Campos do usuário (telefone, CPF...) | `model/Usuario.java` + **nova** migration `V2__...sql` + DTOs |
| Regras de senha (tamanho mínimo...) | `dto/CadastroRequest.java` e `dto/RedefinirSenhaRequest.java` |

### Não precisa mexer

Segurança (`config/`), tokens (`TokenService`, `RefreshTokenService`, `TokenVerificacaoService`),
`security/TokenUtils`, tratamento de erros (`exception/`), Dockerfile e Caddyfile.

### Depois de clonar

```bash
./mvnw test                    # tudo deve passar antes de começar a alterar
docker compose up -d --build   # sobe e confere se funciona
```

### Mantendo os clones atualizados

Cada clone vira um projeto independente. Se você corrigir um problema de **segurança**
na base, precisa levar a correção para os clones. Com git, dá para fazer isso sem copiar à mão:

```bash
# no projeto clonado (uma vez):
git remote add base <url-do-repositorio-base>

# quando a base tiver correções:
git fetch base
git merge base/main
```

## Como rodar (desenvolvimento)

**Opção 1: tudo no Docker** (aplicação + banco + email)
```bash
docker compose up -d --build   # --build reconstrói a imagem se o código mudou
docker compose logs -f app     # acompanha os logs da aplicação
```

**Opção 2: aplicação na sua máquina** (melhor para debugar no VS Code)
```bash
docker compose up -d postgres mailpit   # só a infraestrutura
./mvnw spring-boot:run                  # ou Run/Debug no VS Code
```

Nas duas opções a API fica em http://localhost:8080 (perfil "dev").

- Emails enviados pela aplicação: http://localhost:8025 (Mailpit)
- Admin criado automaticamente em dev: `admin@local.com` / `admin12345`
- Parar os containers: `docker compose down` (com `-v` apaga também os dados)

## Testes

```bash
./mvnw test   # usa H2 em memória, não precisa do Docker
```

## Endpoints

| Método | Rota | Acesso | O que faz |
|---|---|---|---|
| POST | `/auth/cadastro` | público | Cria usuário desativado e envia email de confirmação |
| GET | `/auth/confirmar-email?token=` | público | Link do email: ativa a conta |
| POST | `/auth/reenviar-confirmacao` | público | Reenvia o email de confirmação |
| POST | `/auth/login` | público | Devolve `accessToken` (15 min) + `refreshToken` (7 dias) |
| POST | `/auth/refresh` | público | Troca o refresh token por um novo par de tokens |
| POST | `/auth/logout` | público | Revoga o refresh token |
| POST | `/auth/esqueci-senha` | público | Envia token de recuperação por email |
| POST | `/auth/redefinir-senha` | público | Define nova senha com o token do email |
| GET | `/auth/me` | token | Dados do usuário logado |
| GET | `/admin/usuarios` | token ADMIN | Lista todos os usuários |

Rotas protegidas: enviar o header `Authorization: Bearer <accessToken>`.

## Estrutura (fluxo de uma requisição)

```
controller  ->  service  ->  repository  ->  banco
   (HTTP)      (regras)      (consultas)
```

| Pacote | Conteúdo |
|---|---|
| `controller` | Entrada HTTP. Sem regra de negócio |
| `dto` | Formato dos JSONs de entrada (com validação) e saída |
| `service` | Regras de negócio. `AuthService` orquestra os fluxos |
| `repository` | Acesso ao banco (Spring Data JPA) |
| `model` | Entidades/tabelas |
| `config` | Segurança, JWT e criação do admin |
| `security` | Geração e hash de tokens aleatórios |
| `exception` | Conversão de erros em respostas JSON |

## Banco de dados (Flyway)

As tabelas são criadas por scripts SQL versionados em `src/main/resources/db/migration`.
O Hibernate só valida (`ddl-auto=validate`) se as entidades batem com as tabelas.

- Para mudar o banco, crie um **novo** script: `V2__descricao.sql`, `V3__...`
- **Nunca** altere um script que já rodou em produção

## Produção

```bash
cp .env.example .env    # preencha com os valores reais (o .env NÃO vai para o git)

# chave privada do JWT (uma vez só; NÃO vai para o git)
mkdir -p secrets && chmod 700 secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt-private.pem
chmod 644 secrets/jwt-private.pem

docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml logs -f app
```

O que muda em relação ao dev:

| | Dev (`compose.yaml`) | Produção (`compose.prod.yaml`) |
|---|---|---|
| Perfil | `dev` | `prod` |
| Segredos | fixos no `application-dev.properties` | arquivo `.env` (fora do git) |
| Chave do JWT | temporária, gerada ao subir | `secrets/jwt-private.pem` (fora do git) |
| Acesso | `http://localhost:8080` | `https://DOMAIN` via Caddy (HTTPS automático) |
| Banco | porta 5432 aberta | sem porta publicada (só a aplicação acessa) |
| Email | Mailpit (falso) | SMTP real |
| Logs | texto | JSON (formato ECS) |
| Se cair | não reinicia | `restart: unless-stopped` |

Requisitos do servidor: Docker, o `DOMAIN` apontando (DNS) para o servidor e as portas 80/443 abertas.

Health check: `GET /actuator/health/liveness` (usado pelo Docker).

### Variáveis (veja `.env.example`)

| Variável | Descrição |
|---|---|
| `DOMAIN` | domínio público da API |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | banco de dados |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | servidor SMTP |
| `CORS_ALLOWED_ORIGINS` | opcional: frontends que podem chamar a API |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | opcional: cria o primeiro admin |

Sem as variáveis obrigatórias, o compose se recusa a subir, de propósito.

## CI

`.github/workflows/ci.yml` roda os testes e o build da imagem Docker a cada push/PR
(funciona depois que o projeto estiver no GitHub).
# ms-autenticacao
