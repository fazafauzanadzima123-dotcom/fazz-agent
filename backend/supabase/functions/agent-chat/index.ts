// Orbit agent backend: a thin, validating proxy between the Android app and Google Gemini.
// The Gemini API key lives ONLY here, in the GEMINI_API_KEY secret. It is never sent to the app.
//
// Modes (POST JSON body, field "mode"):
//   chat   -> streams Gemini streamGenerateContent (SSE) with the app's function declarations
//   search -> one grounded Google Search answer with real source URLs (used by the web_search tool)
//   models -> lists Gemini models this key can use (connection / model check)
//
// Environment / secrets:
//   GEMINI_API_KEY        (required)  Google AI Studio key
//   GEMINI_MODEL          (optional)  default model id, default "gemini-flash-latest"
//   DAILY_REQUEST_LIMIT   (optional)  per-install requests per UTC day, default 300
//   SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY are injected by Supabase automatically.

import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta";
const DEFAULT_MODEL = Deno.env.get("GEMINI_MODEL") ?? "gemini-flash-latest";
const DAILY_LIMIT = Number(Deno.env.get("DAILY_REQUEST_LIMIT") ?? "300");

const MAX_BODY_BYTES = 400_000;
const MAX_CONTENTS = 80;
const MAX_TOOLS = 32;
const MAX_SYSTEM_CHARS = 12_000;
const MODEL_RE = /^[a-z0-9][a-z0-9.\-]{1,62}$/;
const INSTALL_RE = /^[A-Za-z0-9\-]{8,64}$/;
const TOOL_NAME_RE = /^[a-zA-Z_][a-zA-Z0-9_]{0,63}$/;

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type ErrorCode =
  | "bad_request"
  | "missing_api_key"
  | "invalid_api_key"
  | "model_not_found"
  | "rate_limited"
  | "quota_exceeded"
  | "upstream_error"
  | "internal_error";

function errorResponse(status: number, code: ErrorCode, message: string): Response {
  return new Response(JSON.stringify({ error: { code, message } }), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

/** Maps a non-OK Gemini response to a clear error the app can show. Never leaks the key. */
async function mapUpstreamError(res: Response): Promise<Response> {
  let detail = "";
  try {
    const body = await res.json();
    detail = body?.error?.message ?? "";
  } catch (_) { /* ignore */ }
  const lower = detail.toLowerCase();
  if (res.status === 400 && lower.includes("api key")) {
    return errorResponse(401, "invalid_api_key", "Gemini rejected GEMINI_API_KEY: " + detail.slice(0, 160));
  }
  if (res.status === 401 || res.status === 403) {
    return errorResponse(401, "invalid_api_key", "Gemini rejected the server's API key (" + res.status + ").");
  }
  if (res.status === 404) {
    return errorResponse(404, "model_not_found", "Model not available for this key: " + detail.slice(0, 200));
  }
  if (res.status === 429) {
    return errorResponse(429, "quota_exceeded", "Gemini free-tier quota reached. Wait a minute and try again.");
  }
  return errorResponse(502, "upstream_error", "Gemini error " + res.status + ": " + detail.slice(0, 300));
}

/** Increments today's counter for this install. Returns false when the daily limit is exceeded. */
async function checkRateLimit(installId: string): Promise<boolean> {
  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) {
    console.warn("rate limit skipped: service credentials unavailable");
    return true;
  }
  try {
    const res = await fetch(`${url}/rest/v1/rpc/agent_bump_usage`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
      },
      body: JSON.stringify({ p_install_id: installId }),
    });
    if (!res.ok) {
      console.error("rate limit rpc failed", res.status, await res.text());
      return true; // fail open so a DB hiccup does not break the assistant
    }
    const count = Number(await res.json());
    return count <= DAILY_LIMIT;
  } catch (e) {
    console.error("rate limit error", e);
    return true;
  }
}

interface ToolDecl {
  name: string;
  description: string;
  parameters?: Record<string, unknown>;
}

function validateTools(tools: unknown): ToolDecl[] | string {
  if (tools === undefined || tools === null) return [];
  if (!Array.isArray(tools)) return "tools must be an array";
  if (tools.length > MAX_TOOLS) return "too many tools";
  for (const t of tools) {
    if (typeof t?.name !== "string" || !TOOL_NAME_RE.test(t.name)) return "invalid tool name";
    if (typeof t?.description !== "string" || t.description.length > 2000) return "invalid tool description";
  }
  return tools as ToolDecl[];
}

function validateContents(contents: unknown): string | null {
  if (!Array.isArray(contents) || contents.length === 0) return "contents must be a non-empty array";
  if (contents.length > MAX_CONTENTS) return "conversation too long";
  for (const c of contents) {
    if (c?.role !== "user" && c?.role !== "model") return "invalid role";
    if (!Array.isArray(c?.parts) || c.parts.length === 0) return "each content needs parts";
  }
  return null;
}

async function handleChat(body: any, apiKey: string): Promise<Response> {
  const contentsErr = validateContents(body.contents);
  if (contentsErr) return errorResponse(400, "bad_request", contentsErr);
  const tools = validateTools(body.tools);
  if (typeof tools === "string") return errorResponse(400, "bad_request", tools);

  const model = typeof body.model === "string" && MODEL_RE.test(body.model) ? body.model : DEFAULT_MODEL;
  const system = typeof body.systemInstruction === "string" ? body.systemInstruction.slice(0, MAX_SYSTEM_CHARS) : "";

  const geminiBody: Record<string, unknown> = {
    contents: body.contents,
    generationConfig: { temperature: 0.7, maxOutputTokens: 4096 },
  };
  if (system) geminiBody.systemInstruction = { parts: [{ text: system }] };
  if (tools.length > 0) {
    geminiBody.tools = [{ functionDeclarations: tools }];
    geminiBody.toolConfig = { functionCallingConfig: { mode: "AUTO" } };
  }

  const upstream = await fetch(`${GEMINI_BASE}/models/${model}:streamGenerateContent?alt=sse`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
    body: JSON.stringify(geminiBody),
  });
  if (!upstream.ok || !upstream.body) return await mapUpstreamError(upstream);

  // Pass the SSE stream straight through to the app.
  return new Response(upstream.body, {
    status: 200,
    headers: {
      ...corsHeaders,
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      "X-Orbit-Model": model,
    },
  });
}

async function handleSearch(body: any, apiKey: string): Promise<Response> {
  const query = typeof body.query === "string" ? body.query.trim() : "";
  if (query.length < 2 || query.length > 500) return errorResponse(400, "bad_request", "query must be 2-500 characters");
  const model = typeof body.model === "string" && MODEL_RE.test(body.model) ? body.model : DEFAULT_MODEL;

  const upstream = await fetch(`${GEMINI_BASE}/models/${model}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text: query }] }],
      tools: [{ google_search: {} }],
      systemInstruction: {
        parts: [{ text: "Answer using Google Search results only. Be factual and concise. If results are insufficient, say so." }],
      },
      generationConfig: { temperature: 0.2, maxOutputTokens: 1500 },
    }),
  });
  if (!upstream.ok) return await mapUpstreamError(upstream);
  const data = await upstream.json();
  const cand = data?.candidates?.[0];
  const text = (cand?.content?.parts ?? []).map((p: any) => p?.text ?? "").join("").trim();
  const chunks = cand?.groundingMetadata?.groundingChunks ?? [];
  const sources = chunks
    .map((c: any) => ({ title: c?.web?.title ?? "", url: c?.web?.uri ?? "" }))
    .filter((s: any) => s.url)
    .slice(0, 8);
  const queries = cand?.groundingMetadata?.webSearchQueries ?? [];
  return json({ answer: text, sources, searchQueries: queries, grounded: sources.length > 0, usage: data?.usageMetadata ?? null });
}

async function handleModels(apiKey: string): Promise<Response> {
  const upstream = await fetch(`${GEMINI_BASE}/models?pageSize=200`, { headers: { "x-goog-api-key": apiKey } });
  if (!upstream.ok) return await mapUpstreamError(upstream);
  const data = await upstream.json();
  const models = (data?.models ?? [])
    .filter((m: any) => (m?.supportedGenerationMethods ?? []).includes("generateContent"))
    .map((m: any) => String(m.name ?? "").replace(/^models\//, ""))
    .filter((n: string) => n.includes("gemini"));
  return json({ defaultModel: DEFAULT_MODEL, models });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return errorResponse(405, "bad_request", "POST only");

  const raw = await req.text();
  if (raw.length > MAX_BODY_BYTES) return errorResponse(413, "bad_request", "request too large");
  let body: any;
  try {
    body = JSON.parse(raw);
  } catch (_) {
    return errorResponse(400, "bad_request", "invalid JSON");
  }

  const apiKey = Deno.env.get("GEMINI_API_KEY");
  if (!apiKey) {
    return errorResponse(503, "missing_api_key", "GEMINI_API_KEY is not configured on the server yet.");
  }

  const installId = typeof body.installId === "string" ? body.installId : "";
  if (!INSTALL_RE.test(installId)) return errorResponse(400, "bad_request", "missing or invalid installId");

  const mode = body.mode ?? "chat";
  if (mode !== "models") {
    const allowed = await checkRateLimit(installId);
    if (!allowed) return errorResponse(429, "rate_limited", `Daily limit of ${DAILY_LIMIT} requests reached for this device.`);
  }

  try {
    if (mode === "chat") return await handleChat(body, apiKey);
    if (mode === "search") return await handleSearch(body, apiKey);
    if (mode === "models") return await handleModels(apiKey);
    return errorResponse(400, "bad_request", "unknown mode");
  } catch (e) {
    console.error("agent-chat failure", e);
    return errorResponse(500, "internal_error", "Unexpected server error.");
  }
});
