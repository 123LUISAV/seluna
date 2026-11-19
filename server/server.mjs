import { createServer } from 'node:http';
import { existsSync, readFileSync } from 'node:fs';
import { parseEnv } from 'node:util';

const demo = process.argv.includes('--demo');
const envFile = new URL('.env', import.meta.url);
const config = existsSync(envFile) ? parseEnv(readFileSync(envFile, 'utf8')) : {};
const apiKey = demo ? undefined : config.BAI_API_KEY?.trim();
const port = Number(config.PORT || 8787);
const baseUrl = 'https://api.b.ai/v1';
const model = 'qwen3.8-flash';
const guideUrl = new URL('../app/src/main/assets/seluna-guide.json', import.meta.url);
const guide = JSON.parse(readFileSync(guideUrl, 'utf8'));
const knownQuestions = new Map(guide.flatMap(article =>
  [article.question, ...article.aliases].map(question => [normalize(question), article])));
const instruction = `Eres Seluna. Responde en español, máximo 90 palabras, con párrafos breves, **negritas** y viñetas simples si ayudan.
Brinda educación menstrual general. Evita diagnósticos, dosis y certezas de embarazo, ovulación o anticoncepción. Ante una posible urgencia indica buscar atención urgente local. Para evaluación individual remite a un profesional. No solicites datos identificables o íntimos ni afirmes tener acceso al diario.`;

function normalize(text) {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}

function modelMessages(messages, question) {
  const isFollowUp = /^(?:y|entonces|eso|esa|asi|tambien|en ese caso)\b/.test(question) ||
    /^(?:cuanto dura|es normal|por que|que significa)$/.test(question);
  const history = isFollowUp
    ? messages.slice(-3, -1).map(message => ({ ...message, content: message.content.slice(0, 320) }))
    : [];
  const query = ` ${question} `;
  const article = guide.find(item => item.keywords.some(keyword => query.includes(` ${keyword} `)));
  const context = article ? `\nGuía de referencia: ${article.context}` : '';
  return [{ role: 'system', content: instruction + context }, ...history, messages.at(-1)];
}

if (process.argv.includes('--check-model')) {
  if (!apiKey) throw new Error('Configura BAI_API_KEY en server/.env.');
  const upstream = await fetch(`${baseUrl}/models`, {
    headers: { Authorization: `Bearer ${apiKey}` },
    signal: AbortSignal.timeout(15_000),
    redirect: 'error',
  });
  if (!upstream.ok) throw new Error(`B.AI rechazó la verificación (HTTP ${upstream.status}).`);
  const result = await upstream.json();
  if (!result.data?.some(item => item.id === model)) throw new Error(`${model} no aparece en la cuenta.`);
  console.log(`${model} disponible para esta clave.`);
  process.exit(0);
}

function send(response, status, data) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  response.end(JSON.stringify(data));
}

async function readMessages(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request.iterator({ destroyOnReturn: false })) {
    size += chunk.length;
    if (size > 60_000) {
      request.resume();
      return null;
    }
    chunks.push(chunk);
  }
  const { messages } = JSON.parse(Buffer.concat(chunks).toString('utf8')) ?? {};
  if (!Array.isArray(messages) || !messages.length || messages.length > 9 || messages.at(-1)?.role !== 'user') return null;
  if (!messages.every(item => item && ['user', 'assistant'].includes(item.role) &&
      typeof item.content === 'string' && item.content.trim() && item.content.length <= 1_500)) return null;
  return messages.map(({ role, content }) => ({ role, content }));
}

async function readChatReply(body) {
  let pending = '';
  let reply = '';
  let finished = false;
  for await (const chunk of body.pipeThrough(new TextDecoderStream())) {
    const events = (pending + chunk).split(/\r?\n\r?\n/);
    pending = events.pop();
    for (const event of events) {
      const data = event.split(/\r?\n/)
        .filter(line => line.startsWith('data:'))
        .map(line => line.slice(5).trimStart())
        .join('\n');
      if (!data) continue;
      if (data === '[DONE]') return reply;

      const frame = JSON.parse(data);
      if (frame.error) throw new Error('B.AI interrumpió la generación.');
      const choice = frame.choices?.[0];
      const content = choice?.delta?.content;
      if (typeof content === 'string') reply += content;
      if (choice?.finish_reason) finished = true;
    }
  }
  if (!finished) throw new Error('B.AI cerró la respuesta antes de completarla.');
  return reply;
}

let busy = false;
const server = createServer(async (request, response) => {
  if (request.method === 'GET' && request.url === '/health') {
    return send(response, 200, { mode: demo ? 'demo' : apiKey ? 'configured' : 'unconfigured' });
  }
  if (request.method !== 'POST' || request.url !== '/chat') return send(response, 404, { error: 'Ruta desconocida.' });

  let messages;
  try {
    messages = await readMessages(request);
  } catch {
    return send(response, 400, { error: 'El cuerpo debe ser JSON válido.' });
  }
  if (!messages) return send(response, 400, { error: 'Historial de mensajes inválido o excede el tamaño permitido.' });
  const normalizedQuestion = normalize(messages.at(-1).content);
  const article = knownQuestions.get(normalizedQuestion);
  if (article) return send(response, 200, { reply: article.answer, source: 'guide' });
  if (demo) {
    return send(response, 200, { reply: 'Demostración local · El día 1 del ciclo corresponde al inicio de la menstruación. Registrar tus fechas te permite observar cómo varía tu ciclo.' });
  }
  if (!apiKey) return send(response, 503, { error: 'Configura BAI_API_KEY en el servidor.' });
  if (busy) return send(response, 429, { error: 'Hay una consulta en curso.' });

  busy = true;
  try {
    const upstream = await fetch(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({
        model,
        messages: modelMessages(messages, normalizedQuestion),
        stream: true,
        enable_thinking: false,
        max_tokens: 240,
      }),
      signal: AbortSignal.timeout(33_000),
      redirect: 'error',
    });
    if (!upstream.ok) {
      await upstream.body?.cancel();
      console.warn(`B.AI respondió HTTP ${upstream.status}.`);
      return send(response, upstream.status === 429 ? 429 : 502, { error: 'El proveedor no pudo responder.' });
    }
    const reply = await readChatReply(upstream.body);
    if (!reply.trim()) {
      return send(response, 502, { error: 'El proveedor devolvió una respuesta vacía.' });
    }
    send(response, 200, { reply: reply.trim(), source: 'model' });
  } catch {
    send(response, 502, { error: 'El proveedor no está disponible.' });
  } finally {
    busy = false;
  }
});
server.requestTimeout = 10_000;
server.listen(port, '127.0.0.1', () => console.log(`Seluna en http://127.0.0.1:${port}${demo ? ' (demostración)' : ''}`));
