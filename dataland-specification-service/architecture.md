# Framework Assistant and MCP — Architecture Overview

**One existing service, one shared set of tools, two ways to access framework knowledge.**

Open [architecture.drawio](architecture.drawio) in diagrams.net to view the diagram.

## How it works

The existing **Specification Service** loads Dataland's framework specifications
from JSON files into memory. We added Spring AI to make this knowledge available
through a frontend chatbot and an MCP server.

### Frontend chatbot

1. A user asks a question in the Vue chat dialog.
2. The frontend sends the question and a bounded conversation history to
   `POST /specifications/chat` through a TanStack mutation.
3. Spring AI sends the conversation and available tool definitions to an
   OpenAI-compatible model endpoint.
4. The model requests the relevant tools. The service executes them locally and
   sends their results back to the model to produce the answer.
5. The frontend displays sanitized Markdown, retrieved specification links and an
   expandable record of tool calls, including arguments, status and duration.

The model endpoint can be **OpenAI** or **LiteLLM backed by an on-premises model**,
provided it supports OpenAI-compatible Chat Completions and tool calling.
Endpoint, model and API key are configured in the backend `.env`.

### External clients through MCP

**MCP (Model Context Protocol)** exposes the same tools to clients such as OpenCode
at `/specifications/mcp` using Streamable HTTP. The external client manages its own
model and conversation; Dataland provides the tools and their results. The MCP
server itself does not need model credentials.

## Shared tools

| Tool | Purpose |
| --- | --- |
| `list_frameworks` | Discover available framework IDs, versions and descriptions. |
| `search_fields` | Find fields by keywords in their names, paths and definitions. |
| `get_field_definition` | Retrieve a field's definition, type, constraints and framework usage. |
| `compare_frameworks` | Calculate exact shared-data-point counts and list shared fields. |

Comparisons use **identical data point type IDs**, rather than similarity inferred
by the model. For example, the current catalog contains **nine shared data points
between SFDR and PCAF**. Pairwise counts cover the complete schemas; detailed field
results are paginated.

## Why this design stays compact

- Extends the existing service; no additional backend service or vector database.
- Reuses the same tool implementation for frontend chat and external MCP clients.
- Calls tools directly inside the service for frontend chat; no internal MCP round trip.
- Keeps conversation history in browser memory and API credentials on the backend.

The assistant explains the available specification catalog. It does not retrieve
company values or provide comprehensive regulatory coverage. Tool traces and source
links make retrieval visible, but do not guarantee that every generated statement
is correct. The existing inbound proxy handles external routes and is omitted from
the diagram for clarity.

## Suggested demo

Ask: **“Which data points do SFDR and PCAF share?”** Show the formatted answer,
expand **Tool calls**, and point out `compare_frameworks` and its parameters.
Then explain that OpenCode can access exactly the same tool through MCP.

## 30-second presentation script

> We extended Dataland's existing Specification Service with Spring AI. Users can
> ask questions in the frontend, and the model retrieves framework information
> through four read-only tools. The same tools are exposed through MCP, so external
> clients such as OpenCode can reuse them. Framework overlap is calculated directly
> from the schemas rather than guessed by the model. The frontend shows formatted
> answers, sources and tool executions. This keeps the architecture small while
> supporting both OpenAI and compatible on-premises deployments through LiteLLM.
