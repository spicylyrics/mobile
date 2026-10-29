// Drives the Spotify desktop client over the Chrome DevTools Protocol, for testing the desktop app
// without touching the mouse. Start Spotify with --remote-debugging-port=9229 --remote-allow-origins=*.
// node tools/spotify-cdp.mjs status|play|pause|toggle|next|prev|eval <js>
const targets = await (await fetch('http://127.0.0.1:9229/json/list')).json();
const page = targets.find(t => t.type === 'page' && t.url.includes('xpui'));
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(r => ws.onopen = r);
let id = 0;
const send = (method, params) => new Promise(res => {
  const my = ++id;
  ws.addEventListener('message', function h(e) { const m = JSON.parse(e.data); if (m.id === my) { ws.removeEventListener('message', h); res(m); } });
  ws.send(JSON.stringify({ id: my, method, params }));
});
const evaluate = async js => {
  const r = await send('Runtime.evaluate', { expression: js, awaitPromise: true, returnByValue: true });
  return r.result?.exceptionDetails ? 'ERR ' + JSON.stringify(r.result.exceptionDetails.exception?.description) : r.result?.result?.value;
};
const btn = t => `(()=>{const b=document.querySelector('[data-testid="${t}"]'); if(!b) return 'no ${t}'; b.click(); return 'clicked ${t}'})()`;
const [cmd, ...rest] = process.argv.slice(2);
const arg = rest.join(' ');
const status = `(()=>{const q=s=>document.querySelector(s)?.textContent;return JSON.stringify({now:[...document.querySelectorAll('[data-testid=now-playing-widget] a')].map(a=>a.textContent).join(' | '),pos:q('[data-testid="playback-position"]'),dur:q('[data-testid="playback-duration"]'),playing:document.querySelector('[data-testid="control-button-playpause"]')?.getAttribute('aria-label')})})()`;
let out;
switch (cmd) {
  case 'status': out = await evaluate(status); break;
  case 'toggle': out = await evaluate(btn('control-button-playpause')); break;
  case 'play': out = await evaluate(`(()=>{const b=document.querySelector('[data-testid="control-button-playpause"]'); if(b.getAttribute('aria-label')==='Play'){b.click();return 'playing'} return 'already playing'})()`); break;
  case 'pause': out = await evaluate(`(()=>{const b=document.querySelector('[data-testid="control-button-playpause"]'); if(b.getAttribute('aria-label')==='Pause'){b.click();return 'paused'} return 'already paused'})()`); break;
  case 'next': out = await evaluate(btn('control-button-skip-forward')); break;
  case 'prev': out = await evaluate(btn('control-button-skip-back')); break;
  case 'eval': out = await evaluate(arg); break;
  default: out = 'unknown command';
}
console.log(out);
ws.close();
