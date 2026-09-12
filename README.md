# mcp-config

Configuration server for the MCP stack. It serves what each service used to carry in its
own `application.yml`, so a setting changes in one place instead of once per repository.

**It holds the credentials too.** They live in `.env` here, which is not tracked, and are
served to clients as properties — which is what lets mcp-gateway start with nothing in its
own environment. `config-repo/` stays free of literals: every credential is a
`${PLACEHOLDER}` there, resolved against what this server sends.

The consequence is direct and worth stating plainly: **anyone who can reach this server
holds the stack's credentials.** It has no authentication today. That is tolerable while it
listens on `127.0.0.1` on one machine and nowhere else.

```
config-repo/
├── application.yml     served to every service
├── mcp-gateway.yml     served to spring.application.name = mcp-gateway
└── mcp-panel.yml       served to the Next.js panel
.env                    the credentials, not tracked
```

## Running

```bash
./mvnw spring-boot:run          # http://localhost:8888
```

Start it **before** the services that read from it. They fail fast rather than starting on
stale local defaults, so the order matters and the error when it is wrong says exactly
this.

Edits to `config-repo/` are served immediately; the server does not need restarting. A
client picks them up on its next start, or on `POST /actuator/refresh` for the beans that
support it.

## Endpoints

| Path | Returns |
|---|---|
| `/{application}/{profile}` | Merged configuration, as JSON |
| `/{application}-{profile}.yml` | The same, as YAML |
| `/actuator/health` | Liveness |

```bash
curl http://localhost:8888/mcp-gateway/default
curl http://localhost:8888/mcp-gateway-default.yml
```

An application with no file of its own is not an error: it still receives
`application.yml`. That is what lets a new service start before anyone has written its
configuration.

## Clients

| Service | Reads from here | How |
|---|---|---|
| `mcp-gateway` | yes | Spring Boot; `spring-cloud-starter-config` |
| `mcp-panel` (Next.js) | yes | Reads this server itself; see below |
| `mcp-cipher` (Go) | partly | Address and token only — **never the encryption keys** |
| `mcp-server` (Python) | no | Connects to nothing by design — the gateway pushes it everything it needs |

mcp-cipher is the deliberate exception. This server has no authentication and its overrides
reach every client, so a key placed here would be handed to all of them — which is the one
thing a separate cipher service exists to prevent. Its keys come from its own environment;
only where it listens and who may call it are configured here.

### Spring clients

A client needs two things locally, and nothing else:

```yaml
spring:
  application:
    name: mcp-gateway
  config:
    import: configserver:${CONFIG_SERVER_URL:http://127.0.0.1:8888}
```

The import is **mandatory**, not `optional:`. A service that starts without its central
configuration runs on whatever defaults happen to remain in its own file — harder to
notice, and harder to explain, than a refusal to start.

### The panel

Next.js is not a Spring client, so it does the two things a Spring client gets for free:
it fetches `/mcp-panel/default` server side and resolves the `${NAME:default}`
placeholders against its own environment. That lives in `lib/config/remote-config.ts`
there, and the browser's share is served from the panel's own `/api/config`.

It reads at request time rather than at build time on purpose. A `NEXT_PUBLIC_` variable
is inlined into the JavaScript bundle when the app is built, so the gateway's address
would be fixed at build and a change here would need a rebuild to take effect — which is
the situation moving configuration to this server exists to end.

The panel **falls back** rather than failing, unlike the gateway. Not because its
configuration matters less, but because the failure is already visible: if this server is
down then so is the gateway, and a panel that loads and reports an unreachable API tells
an operator more than a blank page. What makes that safe rather than sloppy is that the
fallback is announced — the panel shows a warning naming the address it settled for.

## Where the values come from

Two files, with different jobs and different visibility:

| File | Tracked | Holds |
|---|---|---|
| `config-repo/*.yml` | yes | Structure — every credential as `${PLACEHOLDER}` |
| `.env` | **no** | The values behind those placeholders |

`.env` is imported by `application.yaml` (`spring.config.import: optional:file:./.env[.properties]`)
and re-exported through `spring.cloud.config.server.overrides`, which is what puts the
variables into every client's property sources.

Overrides are the mechanism because the config server **cannot substitute a placeholder
into a file it serves** — verified: with `DB_PASSWORD` set in this process's own
environment, both `/mcp-gateway/default` and `/mcp-gateway-default.yml` still hand over the
literal `${DB_PASSWORD}`. Plain-text endpoints resolve only against the served properties
and inline defaults. So a value this server wants to supply has to be supplied *as a
property*, and overrides are how.

**Overrides are global.** There is no per-application scope, so mcp-panel's server receives
the database password as well. It is contained rather than solved: the panel reads an allow
list of `panel.*` keys and serves only those to the browser, so nothing reaches a visitor.
`ServedConfigurationTest` pins both halves of that, so neither is discovered by surprise.

Without `.env` the server still starts. It then serves empty values, and a client fails on
the missing credential rather than on something harder to read.

## The native backend

`spring.profiles.active=native` reads plain files instead of cloning a git repository.
Chosen because the configuration and the services live on one machine today: git adds a
commit and a fetch between editing a value and seeing it, which buys history the local
repository already provides.

The trade is that `search-locations` is `file:./config-repo`, relative to the working
directory — correct when started from the project root, which is how the IDE and
`./mvnw spring-boot:run` both start it. Set `CONFIG_REPO` to an absolute path for any
other launch. Moving to git later means adding a `spring.cloud.config.server.git` block
and dropping the profile; no client changes.

## Versions

Spring Cloud's release train is versioned against a Boot line of its own: `2025.1.3` is
built on Boot **4.0.8** while these services run **4.1.1**. The parent's 4.1.1 wins, and
the two are compatible within Boot 4 — verified by this server and the gateway both
starting and serving.

## Tests

```bash
./mvnw test
```

`ServedConfigurationTest` asserts what this server actually serves, read over HTTP: that
every key `mcp-gateway` needs to start is present, that the shared file is merged in, that
the tracked files carry no literal credential, and that the overrides reach every
application — the last one pinned because it is a cost that is easy to assume away.

That check lives here rather than in the gateway on purpose. The gateway used to prove its
configuration was complete by binding it; once the file moved here, that test could only
prove the gateway's own test fixture matched itself.
