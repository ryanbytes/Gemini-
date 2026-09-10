import { GeminiMDIUModel } from './model.js';

const model = new GeminiMDIUModel(window.localStorage);
const digitEls = [...document.querySelectorAll('.digit')];
const powerToggle = document.querySelector('#power-toggle');
const instrument = document.querySelector('.instrument');
const page = document.querySelector('.page');
let readoutTimer = null;
let modeTimer = null;
let mode = 'mdiu';
let dimmed = false;
let dreamStep = 0;

function clockDigits() {
  const d = new Date();
  return String(d.getHours()).padStart(2,'0') +
         String(d.getMinutes()).padStart(2,'0') +
         String(d.getSeconds()).padStart(2,'0') +
         String(Math.floor(d.getMilliseconds()/100));
}

function dreamDigits() {
  // Modern ambient/self-test presentation, deliberately not mission telemetry.
  const stored = [];
  for (let i=1;i<=99;i++) {
    const a=String(i).padStart(2,'0');
    const v=model.read(a);
    if (v !== '00000') stored.push(a+v);
  }
  if (stored.length) return stored[dreamStep++ % stored.length];
  const patterns=['1234567','7654321','0123456','9876543','2468135','1357924','0000000'];
  return patterns[dreamStep++ % patterns.length];
}

function currentDisplay() {
  if (mode === 'clock') return clockDigits();
  if (mode === 'dream') return dreamDigits();
  return model.snapshot().display;
}

function render() {
  const state = model.snapshot();
  const display = currentDisplay();
  digitEls.forEach((el, i) => {
    el.textContent = display[i] || '0';
    el.classList.toggle('unpowered', !state.powered && mode === 'mdiu');
  });
  powerToggle.classList.toggle('on', state.powered);
  powerToggle.setAttribute('aria-pressed', String(state.powered));
  instrument.classList.toggle('error', state.error && mode === 'mdiu');
  page.classList.toggle('dimmed', dimmed);
  document.querySelectorAll('[data-mode]').forEach(b=>b.classList.toggle('active', b.dataset.mode===mode));
}

function pulse(el) {
  el.classList.remove('pressed'); void el.offsetWidth; el.classList.add('pressed');
  setTimeout(() => el.classList.remove('pressed'), 130);
}
function cancelReadout(){ if(readoutTimer) clearInterval(readoutTimer); readoutTimer=null; }
function cancelModeTimer(){ if(modeTimer) clearInterval(modeTimer); modeTimer=null; }
function setMode(next){
  cancelReadout(); cancelModeTimer(); mode=next; dreamStep=0;
  if(mode==='clock') modeTimer=setInterval(render,100);
  else if(mode==='dream') modeTimer=setInterval(render,1200);
  render();
}
function ensureMDIU(){ if(mode!=='mdiu') setMode('mdiu'); }

function digit(d, el) {
  ensureMDIU(); cancelReadout(); const result=model.pressDigit(d);
  if(result.accepted) pulse(el); render();
}
function readOut(el) {
  ensureMDIU(); cancelReadout(); pulse(el); const result=model.readOut(); render();
  if(!result.ok) return;
  model.display=result.address+'00000'; render(); let i=0;
  readoutTimer=setInterval(()=>{ model.setReadoutDigit(i+2,result.message[i]); render(); i++; if(i>=5) cancelReadout(); },500);
}

document.querySelectorAll('[data-digit]').forEach(el=>el.addEventListener('click',()=>digit(el.dataset.digit,el)));
document.querySelector('#clear').addEventListener('click',e=>{ ensureMDIU(); cancelReadout(); pulse(e.currentTarget); model.clear(); render(); });
document.querySelector('#enter').addEventListener('click',e=>{ ensureMDIU(); cancelReadout(); pulse(e.currentTarget); model.enter(); render(); });
document.querySelector('#readout').addEventListener('click',e=>readOut(e.currentTarget));
powerToggle.addEventListener('click',()=>{ ensureMDIU(); cancelReadout(); model.togglePower(); render(); });
document.querySelectorAll('[data-mode]').forEach(el=>el.addEventListener('click',()=>setMode(el.dataset.mode)));
document.querySelector('#dim').addEventListener('click',()=>{ dimmed=!dimmed; render(); });
document.querySelector('#fullscreen').addEventListener('click',async()=>{
  try { if(!document.fullscreenElement) await document.documentElement.requestFullscreen(); else await document.exitFullscreen(); } catch(_) {}
});

window.addEventListener('keydown',e=>{
  if(/^\d$/.test(e.key)) digit(e.key,document.querySelector(`[data-digit="${e.key}"]`));
  else if(e.key.toLowerCase()==='c') document.querySelector('#clear').click();
  else if(e.key.toLowerCase()==='r') document.querySelector('#readout').click();
  else if(e.key.toLowerCase()==='p') powerToggle.click();
  else if(e.key.toLowerCase()==='k') setMode('clock');
  else if(e.key.toLowerCase()==='d') setMode('dream');
  else if(e.key.toLowerCase()==='m') setMode('mdiu');
  else if(e.key==='Enter') document.querySelector('#enter').click();
});

render();
if('serviceWorker' in navigator) window.addEventListener('load',()=>navigator.serviceWorker.register('./sw.js').catch(()=>{}));
