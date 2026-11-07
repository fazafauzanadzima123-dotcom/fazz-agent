// Fazz Agent backend: a validating proxy between the Android app and the AI providers.
// Provider keys live ONLY here as Supabase secrets. The app never sees keys, provider names or model names.
//
// Modes (POST JSON body, field "mode"):
//   chat   -> streams a model turn (neutral Gemini-style SSE) with the app's function declarations.
//             Primary: Gemini (GEMINI_API_KEY). Fallback: OpenAI (OPENAI_API_KEY) when Gemini is rate-limited,
//             unavailable or times out. Set DISABLE_OPENAI_FALLBACK=true to turn the fallback off.
//   search -> web search with real source URLs via Tavily (TAVILY_API_KEY); fallback: Gemini grounding
//   models -> connection check (no provider/model names unless debug=true)
//
// Secrets / env:
//   OPENAI_API_KEY   fallback provider         OPENAI_MODEL   (default gpt-5.4-mini)
//   GEMINI_API_KEY   AI provider               GEMINI_MODEL   (default gemini-3.5-flash-lite)
//   TAVILY_API_KEY   web search                DAILY_REQUEST_LIMIT (default 600)
//   SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY are injected by Supabase automatically.

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { addDummySignatures, openAIStreamToNeutral, toOpenAIMessages, toOpenAITools, type ToolDecl } from "./providers.ts";

const GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta";
const OPENAI_BASE = "https://api.openai.com/v1";
const GEMINI_MODEL = Deno.env.get("GEMINI_MODEL") ?? "gemini-3.5-flash-lite";
const OPENAI_MODEL = Deno.env.get("OPENAI_MODEL") ?? "gpt-5.4-mini";
const DAILY_LIMIT = Number(Deno.env.get("DAILY_REQUEST_LIMIT") ?? "600");

const MAX_BODY_BYTES = 3_000_000; // room for one compressed screenshot or photo
const UPSTREAM_TIMEOUT_MS = 45_000;
const MAX_CONTENTS = 160;
const MAX_TOOLS = 32;
const MAX_SYSTEM_CHARS = 14_000;
const INSTALL_RE = /^[A-Za-z0-9\-]{8,64}$/;
const TOOL_NAME_RE = /^[a-zA-Z_][a-zA-Z0-9_]{0,63}$/;

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type ErrorCode =
  | "bad_request" | "missing_api_key" | "invalid_api_key" | "model_not_found"
  | "rate_limited" | "quota_exceeded" | "upstream_error" | "internal_error";

function errorResponse(status: number, code: ErrorCode, message: string): Response {
  return new Response(JSON.stringify({ error: { code, message } }), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });
}

const sseHeaders = { ...corsHeaders, "Content-Type": "text/event-stream", "Cache-Control": "no-cache" };

/** Provider-neutral error for the app. Details go to logs only. */
async function upstreamError(provider: string, res: Response): Promise<Response> {
  let detail = "";
  try { const b = await res.json(); detail = b?.error?.message ?? ""; } catch (_) { /* ignore */ }
  console.warn(`${provider} upstream error`, res.status, detail.slice(0, 300));
  const lower = detail.toLowerCase();
  if ((res.status === 400 && lower.includes("api key")) || res.status === 401 || res.status === 403) {
    return errorResponse(401, "invalid_api_key", "The AI service rejected the server's key.");
  }
  if (res.status === 404) return errorResponse(404, "model_not_found", "The configured AI model is not available.");
  if (res.status === 429) return errorResponse(429, "quota_exceeded", "The AI service quota is used up for now.");
  return errorResponse(502, "upstream_error", `AI service error ${res.status}.`);
}

/** Increments today's counter for this install. Returns false when the daily limit is exceeded. */
async function checkRateLimit(installId: string): Promise<boolean> {
  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) return true;
  try {
    const res = await fetch(`${url}/rest/v1/rpc/agent_bump_usage`, {
      method: "POST",
      headers: { "Content-Type": "application/json", apikey: serviceKey, Authorization: `Bearer ${serviceKey}` },
      body: JSON.stringify({ p_install_id: installId }),
    });
    if (!res.ok) { console.error("rate limit rpc failed", res.status, await res.text()); return true; }
    return Number(await res.json()) <= DAILY_LIMIT;
  } catch (e) {
    console.error("rate limit error", e);
    return true;
  }
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

async function fetchWithHeaderTimeout(url: string, init: RequestInit): Promise<Response> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(new DOMException("timeout", "TimeoutError")), UPSTREAM_TIMEOUT_MS);
  try {
    return await fetch(url, { ...init, signal: ctrl.signal });
  } finally {
    clearTimeout(timer); // the time limit covers waiting for the first response, not the whole stream
  }
}

// deno-lint-ignore no-explicit-any
async function openAIChat(contents: any[], system: string, tools: ToolDecl[], key: string): Promise<Response> {
  // deno-lint-ignore no-explicit-any
  const body: any = {
    model: OPENAI_MODEL,
    messages: toOpenAIMessages(contents, system),
    stream: true,
    stream_options: { include_usage: true },
    max_completion_tokens: 4096,
  };
  if (tools.length) { body.tools = toOpenAITools(tools); body.tool_choice = "auto"; }
  const res = await fetchWithHeaderTimeout(`${OPENAI_BASE}/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` },
    body: JSON.stringify(body),
  });
  console.log("chat openai(fallback)", res.status);
  if (!res.ok || !res.body) return res;
  return new Response(openAIStreamToNeutral(res.body), { status: 200, headers: sseHeaders });
}

// deno-lint-ignore no-explicit-any
async function geminiChat(contents: any[], system: string, tools: ToolDecl[], key: string): Promise<Response> {
  // deno-lint-ignore no-explicit-any
  const body: any = { contents: addDummySignatures(contents), generationConfig: { temperature: 0.7, maxOutputTokens: 4096 } };
  if (system) body.systemInstruction = { parts: [{ text: system }] };
  if (tools.length) {
    body.tools = [{ functionDeclarations: tools }];
    body.toolConfig = { functionCallingConfig: { mode: "AUTO" } };
  }
  const res = await fetchWithHeaderTimeout(`${GEMINI_BASE}/models/${GEMINI_MODEL}:streamGenerateContent?alt=sse`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": key },
    body: JSON.stringify(body),
  });
  console.log("chat gemini(primary)", res.status);
  if (!res.ok || !res.body) return res;
  return new Response(res.body, { status: 200, headers: sseHeaders });
}

const RETRYABLE = new Set([401, 403, 404, 408, 409, 429, 500, 502, 503, 504]);

// deno-lint-ignore no-explicit-any
async function handleChat(body: any): Promise<Response> {
  const contentsErr = validateContents(body.contents);
  if (contentsErr) return errorResponse(400, "bad_request", contentsErr);
  const tools = validateTools(body.tools);
  if (typeof tools === "string") return errorResponse(400, "bad_request", tools);
  const system = typeof body.systemInstruction === "string" ? body.systemInstruction.slice(0, MAX_SYSTEM_CHARS) : "";

  const geminiKey = Deno.env.get("GEMINI_API_KEY");
  const openaiKey = Deno.env.get("DISABLE_OPENAI_FALLBACK") === "true" ? undefined : Deno.env.get("OPENAI_API_KEY");
  if (!geminiKey && !openaiKey) return errorResponse(503, "missing_api_key", "No AI provider key is configured on the server yet.");

  // 1) Gemini (primary)
  let primaryFailure: Response | null = null;
  if (geminiKey) {
    try {
      const r = await geminiChat(body.contents, system, tools, geminiKey);
      if (r.ok) return r;
      if (!openaiKey || !RETRYABLE.has(r.status)) return await upstreamError("primary", r);
      primaryFailure = r;
      let why = "";
      try { const b = await r.clone().json(); why = `${b?.error?.status ?? ""} ${String(b?.error?.message ?? "").slice(0, 160)}`; } catch (_) { /* ignore */ }
      console.warn("primary failed, falling back", r.status, why);
    } catch (e) {
      console.warn("primary threw, falling back", String(e));
      if (!openaiKey) throw e;
    }
  }

  // 2) OpenAI (fallback)
  const r = await openAIChat(body.contents, system, tools, openaiKey!);
  if (r.ok) return r;
  if (primaryFailure) await primaryFailure.body?.cancel();
  return await upstreamError("fallback", r);
}

/** Web search via Tavily (built for AI agents; real URLs). Falls back to Gemini grounding if no Tavily key. */
// deno-lint-ignore no-explicit-any
async function handleSearch(body: any): Promise<Response> {
  const query = typeof body.query === "string" ? body.query.trim() : "";
  if (query.length < 2 || query.length > 500) return errorResponse(400, "bad_request", "query must be 2-500 characters");

  const tavilyKey = Deno.env.get("TAVILY_API_KEY");
  if (tavilyKey) {
    const res = await fetch("https://api.tavily.com/search", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${tavilyKey}` },
      body: JSON.stringify({ query, search_depth: "basic", include_answer: true, max_results: 6 }),
      signal: AbortSignal.timeout(30_000),
    });
    if (res.status === 401 || res.status === 403) return errorResponse(401, "invalid_api_key", "The search service rejected the server's key.");
    if (res.status === 429 || res.status === 432) return errorResponse(429, "quota_exceeded", "Search quota is used up for now.");
    if (!res.ok) return errorResponse(502, "upstream_error", "Search service error " + res.status + ".");
    const data = await res.json();
    const sources = (data?.results ?? [])
      // deno-lint-ignore no-explicit-any
      .map((r: any) => ({ title: String(r?.title ?? ""), url: String(r?.url ?? ""), snippet: String(r?.content ?? "").slice(0, 400) }))
      // deno-lint-ignore no-explicit-any
      .filter((s: any) => s.url)
      .slice(0, 6);
    return json({ answer: String(data?.answer ?? ""), sources, grounded: sources.length > 0 });
  }

  const geminiKey = Deno.env.get("GEMINI_API_KEY");
  if (!geminiKey) return errorResponse(503, "missing_api_key", "Web search is not configured on the server yet.");
  const upstream = await fetch(`${GEMINI_BASE}/models/${GEMINI_MODEL}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": geminiKey },
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text: query }] }],
      tools: [{ google_search: {} }],
      systemInstruction: { parts: [{ text: "Answer using Google Search results only. Be factual and concise. If results are insufficient, say so." }] },
      generationConfig: { temperature: 0.2, maxOutputTokens: 1500 },
    }),
  });
  if (!upstream.ok) return await upstreamError("search-fallback", upstream);
  const data = await upstream.json();
  const cand = data?.candidates?.[0];
  // deno-lint-ignore no-explicit-any
  const text = (cand?.content?.parts ?? []).map((p: any) => p?.text ?? "").join("").trim();
  const sources = (cand?.groundingMetadata?.groundingChunks ?? [])
    // deno-lint-ignore no-explicit-any
    .map((c: any) => ({ title: c?.web?.title ?? "", url: c?.web?.uri ?? "" }))
    // deno-lint-ignore no-explicit-any
    .filter((s: any) => s.url).slice(0, 8);
  return json({ answer: text, sources, grounded: sources.length > 0 });
}

/** Connection check. Provider/model names are only included for the owner's debugging (debug=true). */
// deno-lint-ignore no-explicit-any
async function handleModels(body: any): Promise<Response> {
  const openaiKey = Deno.env.get("DISABLE_OPENAI_FALLBACK") === "true" ? undefined : Deno.env.get("OPENAI_API_KEY");
  const geminiKey = Deno.env.get("GEMINI_API_KEY");
  // deno-lint-ignore no-explicit-any
  const out: any = { ok: Boolean(openaiKey || geminiKey), defaultModel: "fazz", models: [] };
  if (body?.debug === true) {
    out.primary = geminiKey ? GEMINI_MODEL : null;
    out.fallback = openaiKey ? OPENAI_MODEL : null;
    out.search = Deno.env.get("TAVILY_API_KEY") ? "tavily" : "gemini-grounding";
    if (openaiKey) {
      const r = await fetch(`${OPENAI_BASE}/models`, { headers: { Authorization: `Bearer ${openaiKey}` } });
      out.primaryStatus = r.status;
      if (r.ok) {
        const d = await r.json();
        // deno-lint-ignore no-explicit-any
        out.primaryModels = (d?.data ?? []).map((m: any) => String(m.id)).filter((id: string) => /^(gpt|o\d|chatgpt|computer)/.test(id)).sort();
      }
    }
  }
  return json(out);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return errorResponse(405, "bad_request", "POST only");

  const raw = await req.text();
  if (raw.length > MAX_BODY_BYTES) return errorResponse(413, "bad_request", "request too large");
  // deno-lint-ignore no-explicit-any
  let body: any;
  try { body = JSON.parse(raw); } catch (_) { return errorResponse(400, "bad_request", "invalid JSON"); }

  const installId = typeof body.installId === "string" ? body.installId : "";
  if (!INSTALL_RE.test(installId)) return errorResponse(400, "bad_request", "missing or invalid installId");

  const mode = body.mode ?? "chat";
  if (mode !== "models") {
    const allowed = await checkRateLimit(installId);
    if (!allowed) return errorResponse(429, "rate_limited", `Daily limit of ${DAILY_LIMIT} requests reached for this device.`);
  }

  try {
    if (mode === "chat") return await handleChat(body);
    if (mode === "search") return await handleSearch(body);
    if (mode === "models") return await handleModels(body);
    return errorResponse(400, "bad_request", "unknown mode");
  } catch (e) {
    if (e instanceof DOMException && (e.name === "TimeoutError" || e.name === "AbortError")) {
      return errorResponse(504, "upstream_error", "The AI service did not respond in time. Please try again.");
    }
    console.error("agent-chat failure", e);
    return errorResponse(500, "internal_error", "Unexpected server error.");
  }
});
