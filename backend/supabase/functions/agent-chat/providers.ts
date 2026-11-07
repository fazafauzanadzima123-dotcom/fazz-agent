// Provider adapters for the Fazz Agent backend.
// The app always speaks one neutral wire format (Gemini-style {role, parts[]} contents and Gemini-style SSE).
// This file translates that format to/from OpenAI Chat Completions, so the app never knows which provider answered.

export interface ToolDecl {
  name: string;
  description: string;
  parameters?: Record<string, unknown>;
}

const NUMERIC_KEYS = new Set(["maxLength", "minLength", "minimum", "maximum", "maxItems", "minItems"]);

/** JSON-schema cleanup: numeric constraints sometimes arrive as strings; OpenAI requires numbers. */
export function sanitizeSchema(v: unknown): unknown {
  if (Array.isArray(v)) return v.map(sanitizeSchema);
  if (v && typeof v === "object") {
    const out: Record<string, unknown> = {};
    for (const [k, val] of Object.entries(v as Record<string, unknown>)) {
      if (NUMERIC_KEYS.has(k) && typeof val === "string" && /^-?\d+(\.\d+)?$/.test(val)) out[k] = Number(val);
      else out[k] = sanitizeSchema(val);
    }
    return out;
  }
  return v;
}

/** Converts neutral contents to OpenAI chat messages (tool calls, tool results, images). */
// deno-lint-ignore no-explicit-any
export function toOpenAIMessages(contents: any[], system: string): any[] {
  // deno-lint-ignore no-explicit-any
  const msgs: any[] = [];
  if (system) msgs.push({ role: "system", content: system });
  const pending: Record<string, string[]> = {};
  let auto = 0;
  for (const c of contents) {
    // deno-lint-ignore no-explicit-any
    const parts: any[] = Array.isArray(c?.parts) ? c.parts : [];
    if (c.role === "model") {
      const text = parts.filter((p) => typeof p?.text === "string" && !p?.thought).map((p) => p.text).join("");
      const calls = parts.filter((p) => p?.functionCall).map((p) => {
        const fc = p.functionCall;
        const id = typeof fc.id === "string" && fc.id ? fc.id : `call_fazz_${auto++}`;
        (pending[fc.name] ||= []).push(id);
        return { id, type: "function", function: { name: fc.name, arguments: JSON.stringify(fc.args ?? {}) } };
      });
      if (!text && calls.length === 0) continue;
      // deno-lint-ignore no-explicit-any
      const m: any = { role: "assistant", content: text || null };
      if (calls.length) m.tool_calls = calls;
      msgs.push(m);
    } else {
      // deno-lint-ignore no-explicit-any
      const toolMsgs: any[] = [];
      // deno-lint-ignore no-explicit-any
      const userContent: any[] = [];
      for (const p of parts) {
        if (p?.functionResponse) {
          const fr = p.functionResponse;
          let id: string | undefined = typeof fr.id === "string" && fr.id ? fr.id : undefined;
          const queue = pending[fr.name] ?? [];
          if (id) { const i = queue.indexOf(id); if (i >= 0) queue.splice(i, 1); } else id = queue.shift() ?? `call_fazz_${auto++}`;
          toolMsgs.push({ role: "tool", tool_call_id: id, content: JSON.stringify(fr.response ?? {}) });
        } else if (typeof p?.text === "string") {
          if (p.text) userContent.push({ type: "text", text: p.text });
        } else if (p?.inlineData?.data) {
          userContent.push({ type: "image_url", image_url: { url: `data:${p.inlineData.mimeType ?? "image/jpeg"};base64,${p.inlineData.data}` } });
        }
      }
      msgs.push(...toolMsgs);
      if (userContent.length === 1 && userContent[0].type === "text") msgs.push({ role: "user", content: userContent[0].text });
      else if (userContent.length) msgs.push({ role: "user", content: userContent });
    }
  }
  return msgs;
}

export function toOpenAITools(tools: ToolDecl[]) {
  return tools.map((t) => ({
    type: "function",
    function: { name: t.name, description: t.description, parameters: sanitizeSchema(t.parameters ?? { type: "object", properties: {} }) },
  }));
}

const enc = new TextEncoder();
const sse = (obj: unknown) => enc.encode(`data: ${JSON.stringify(obj)}\n\n`);

/** Translates an OpenAI Chat Completions SSE stream into the neutral (Gemini-style) SSE the app understands. */
export function openAIStreamToNeutral(body: ReadableStream<Uint8Array>): ReadableStream<Uint8Array> {
  const decoder = new TextDecoder();
  let buffer = "";
  const calls = new Map<number, { id: string; name: string; args: string }>();
  let finished = false;

  // deno-lint-ignore no-explicit-any
  function handle(obj: any, controller: ReadableStreamDefaultController<Uint8Array>) {
    if (obj?.usage) {
      controller.enqueue(sse({ usageMetadata: {
        promptTokenCount: obj.usage.prompt_tokens ?? 0,
        candidatesTokenCount: obj.usage.completion_tokens ?? 0,
        totalTokenCount: obj.usage.total_tokens ?? 0,
      } }));
    }
    const choice = obj?.choices?.[0];
    if (!choice) return;
    const d = choice.delta ?? {};
    if (typeof d.content === "string" && d.content.length) {
      controller.enqueue(sse({ candidates: [{ content: { role: "model", parts: [{ text: d.content }] } }] }));
    }
    for (const tc of d.tool_calls ?? []) {
      const cur = calls.get(tc.index) ?? { id: "", name: "", args: "" };
      if (tc.id) cur.id = tc.id;
      if (tc.function?.name) cur.name += tc.function.name;
      if (tc.function?.arguments) cur.args += tc.function.arguments;
      calls.set(tc.index, cur);
    }
    if (choice.finish_reason && !finished) {
      finished = true;
      if (calls.size) {
        const parts = [...calls.entries()].sort((a, b) => a[0] - b[0]).map(([, c]) => {
          let args = {};
          try { args = c.args ? JSON.parse(c.args) : {}; } catch (_) { args = {}; }
          return { functionCall: { name: c.name, args, id: c.id || undefined } };
        });
        controller.enqueue(sse({ candidates: [{ content: { role: "model", parts } }] }));
      }
      const reason = choice.finish_reason === "length" ? "MAX_TOKENS" : choice.finish_reason === "content_filter" ? "SAFETY" : "STOP";
      controller.enqueue(sse({ candidates: [{ finishReason: reason }] }));
    }
  }

  return new ReadableStream<Uint8Array>({
    async start(controller) {
      const reader = body.getReader();
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          let nl: number;
          while ((nl = buffer.indexOf("\n")) >= 0) {
            const line = buffer.slice(0, nl).trim();
            buffer = buffer.slice(nl + 1);
            if (!line.startsWith("data:")) continue;
            const payload = line.slice(5).trim();
            if (payload === "[DONE]") continue;
            try { handle(JSON.parse(payload), controller); } catch (_) { /* ignore malformed chunk */ }
          }
        }
      } catch (e) {
        controller.enqueue(sse({ error: { code: 502, message: "AI stream interrupted: " + String(e).slice(0, 120) } }));
      } finally {
        controller.close();
      }
    },
  });
}

/**
 * Gemini requires thought signatures on function calls in history. Calls produced by another provider
 * have none, so the documented dummy value is added when falling back to Gemini.
 */
// deno-lint-ignore no-explicit-any
export function addDummySignatures(contents: any[]): any[] {
  return contents.map((c) => {
    if (c?.role !== "model" || !Array.isArray(c.parts)) return c;
    let first = true;
    // deno-lint-ignore no-explicit-any
    const parts = c.parts.map((p: any) => {
      if (p?.functionCall && first) { first = false; return p.thoughtSignature ? p : { ...p, thoughtSignature: "skip_thought_signature_validator" }; }
      return p;
    });
    return { ...c, parts };
  });
}
