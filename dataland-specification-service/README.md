# Framework assistant and MCP

Architecture sketch: [architecture.drawio](architecture.drawio) (open in diagrams.net or a draw.io editor).

The specification service exposes three read-only tools over Streamable HTTP at
`/specifications/mcp`: `list_frameworks`, `search_fields`, and
`get_field_definition`. They read the same in-memory specifications as the REST API.
No model credentials are required for MCP; the connecting client supplies its own model.

## Local demonstration

From the repository root:

```bash
./gradlew :dataland-specification-service:bootRun --args='--dataland.specification-folder=res:/specifications'
```

Add this entry to your OpenCode configuration's `mcp` object:

```json
{
  "dataland-frameworks": {
    "type": "remote",
    "url": "http://localhost:8489/specifications/mcp",
    "enabled": true
  }
}
```

When using the container stack, use
`https://local-dev.dataland.com/specifications/mcp` instead. The proxy forwards MCP
without response buffering. Ask OpenCode, for example:

- "Use dataland-frameworks to list the available frameworks."
- "Which SFDR fields describe greenhouse gas emissions?"
- "Explain the definition and constraints of the Scope 1 emissions field."

## Frontend chat

The Vue app includes a **Framework help** button. It calls `POST /specifications/chat`.
The chat uses the same tool implementation directly inside the service, without an
MCP client or an additional backend service.

For OpenAI, edit `.env` in the repository root and replace
`replace-with-your-openai-api-key` with your API key. A local placeholder file is
provided; `.env.example` is the versioned template. The `.env` file is Git-ignored.
Spring loads it when started from the repository root or service directory, and
Docker Compose reads it automatically for variable substitution. Restart the
service after changing it. Keep this file in the backend repository root, not in
the frontend's environment files.

```dotenv
FRAMEWORK_CHAT_PROVIDER=openai
FRAMEWORK_CHAT_API_KEY=replace-with-your-openai-api-key
FRAMEWORK_CHAT_BASE_URL=https://api.openai.com/v1
FRAMEWORK_CHAT_MODEL=gpt-4.1-mini
```

Then start the service using the local demonstration command above. Alternatively,
configure a tool-calling OpenAI-compatible cloud endpoint using environment variables
(which override the `.env` values):

```bash
export FRAMEWORK_CHAT_PROVIDER=openai
export FRAMEWORK_CHAT_API_KEY='<your API key>'
export FRAMEWORK_CHAT_BASE_URL=https://api.openai.com/v1
export FRAMEWORK_CHAT_MODEL=gpt-4.1-mini
./gradlew :dataland-specification-service:bootRun --args='--dataland.specification-folder=res:/specifications'
```

The Compose service forwards these four variables as well. The base URL includes
the API prefix (such as `/v1`); the client appends `/chat/completions`. This initial
integration supports OpenAI-compatible endpoints. Providers with different APIs
require their corresponding Spring AI model integration.

The default provider is `none`: the service and MCP still start, while chat returns
HTTP 503 with `framework-chat-unavailable`. No placeholder credentials are used.
The current specification endpoints, MCP and chat share the service's existing
public route; configuring a model enables model-backed requests on that route.

Conversation history exists only in browser component state, survives opening and
closing the dialog, and is cleared by navigation reload or **Clear conversation**.
Each request includes at most ten previous messages. The server validates message
sizes and roles, supplies its own system prompt, and returns only source paths
actually retrieved by tools. Source links identify retrieved specifications, not a
guarantee that every generated sentence is correct.

The source definitions are primarily English. The assistant can translate questions
and answers, but only explains information available in the specification catalog.
It does not provide company values or comprehensive regulatory documentation.

## Verification and generated clients

Run separately from the repository root:

```bash
./gradlew :dataland-specification-service:ktlintFormat
./gradlew :dataland-specification-service:test
./gradlew detekt
./gradlew :dataland-specification-service:generateOpenApiDocs
./gradlew :dataland-frontend:generateClients
```

Frontend checks, from `dataland-frontend/`:

```bash
npm run typecheck
npm run lintci
npm run testcomponent -- --spec tests/component/components/general/FrameworkChatWidget.cy.ts
```

Backend tests use no external model. A real cloud answer additionally requires the
configured provider credentials and a model supporting function/tool calling.
