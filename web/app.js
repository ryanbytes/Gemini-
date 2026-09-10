import { GeminiMDIUModel } from './model.js';
import { GeminiObcCore } from './obc-core.js';
import { MDIUSound } from './sound.js';

const model = new GeminiMDIUModel(window.localStorage);
const obc = GeminiObcCore.diagnostic();
const sound = new MDIUSound();
const digitEls = [...document.querySelectorAll('.digit')];
const powerToggle = document.querySelector('#power-toggle');
const instrument = document.querySelector('.instrument');
const page = document.querySelector('.page');
const soundButton = document.querySelector('#sound');
let readoutTimer = null;
let modeTimer = null;
let mode = 'mdiu';
let dimmed = false;
let lastRendered = null;

function clockDigits() {
  const d = new Date();
  return String(d.getHours()).padStart(2,'0') +
         String(d.getMinutes()).padStart(2,'0') +
         String(d.getSeconds()).padStart(2,'0') + '0';
}

function dreamDigits() {
  return String(obc.acc & GeminiObcCore.MASK26).padStart(7,'0').slice(-7);
}

function currentDisplay() {
  if (mode === 'clock') return clockDigits();
  if (mode === 'dream') return dreamDigits();
  return model.snapshot().display;
}

function render(playWheel = true) {
  const state = model.snapshot();
  const display = currentDisplay();
  if (playWheel && lastRendered && display !== lastRendered) sound.displayChanged(lastRendered, display);
  lastRendered = display;
  digitEls.forEach((el, i) => {
    el.textContent = display[i] || '0';
    el.classList.toggle('unpowered', !state.powered && mode === 'mdiu');
  });
  powerToggle.classList.toggle('on', state.powered);
  powerToggle.setAttribute('aria-pressed', String(state.powered));
  instrument.classList.toggle('error', state.error && mode === 'mdiu');
  page.classList.toggle('dimmed', dimmed);
  document.querySelectorAll('[data-mode]').forEach(b=>b.classList.toggle('active', b.dataset.mode===mode));
  soundButton.classList.toggle('active', sound.enabled);
  soundButton.setAttribute('aria-pressed', String(sound.enabled));
}

function pulse(el) {
  el.classList.remove('pressed'); void el.offsetWidth; el.classList.add('pressed');
  setTimeout(() => el.classList.remove('pressed'), 130);
}
function cancelReadout(){ if(readoutTimer) clearInterval(readoutTimer); readoutTimer=null; }
function cancelModeTimer(){ if(modeTimer) clearInterval(modeTimer); modeTimer=null; }
function setMode(next){
  cancelReadout(); cancelModeTimer(); mode=next;
  if(mode==='clock') modeTimer=setInterval(()=>render(true),1000);
  else if(mode==='dream') modeTimer=setInterval(()=>{ obc.runCycles(7143); render(true); },1000);
  render(true);
}
function ensureMDIU(){ if(mode!=='mdiu') setMode('mdiu'); }

async function digit(d, el) {
  await sound.arm();
  ensureMDIU(); cancelReadout(); sound.key();
  const result=model.pressDigit(d);
  if(result.accepted) pulse(el);
  render(true);
}
function readOut(el) {
  sound.arm(); ensureMDIU(); cancelReadout(); sound.command(); pulse(el);
  const result=model.readOut(); render(true);
  if(!result.ok) return;
  let i=0;
  readoutTimer=setInterval(()=>{
    model.setReadoutDigit(i+2,result.message[i]);
    render(true);
    i++;
    if(i>=5) cancelReadout();
  },500);
}

document.querySelectorAll('[data-digit]').forEach(el=>el.addEventListener('click',()=>digit(el.dataset.digit,el)));
document.querySelector('#clear').addEventListener('click',async e=>{ await sound.arm(); ensureMDIU(); cancelReadout(); sound.command(); pulse(e.currentTarget); model.clear(); render(true); });
document.querySelector('#enter').addEventListener('click',async e=>{ await sound.arm(); ensureMDIU(); cancelReadout(); sound.command(); pulse(e.currentTarget); model.enter(); render(true); });
document.querySelector('#readout').addEventListener('click',e=>readOut(e.currentTarget));
powerToggle.addEventListener('click',async()=>{ await sound.arm(); ensureMDIU(); cancelReadout(); sound.toggle(); model.togglePower(); render(false); });
document.querySelectorAll('[data-mode]').forEach(el=>el.addEventListener('click',async()=>{ await sound.arm(); sound.command(); setMode(el.dataset.mode); }));
soundButton.addEventListener('click',async()=>{
  if(!sound.enabled){ sound.setEnabled(true); await sound.arm(); sound.command(); }
  else sound.setEnabled(false);
  render(false);
});
document.querySelector('#dim').addEventListener('click',async()=>{ await sound.arm(); sound.command(); dimmed=!dimmed; render(false); });
document.querySelector('#fullscreen').addEventListener('click',async()=>{
  await sound.arm(); sound.command();
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

render(false);
if('serviceWorker' in navigator) window.addEventListener('load',()=>navigator.serviceWorker.register('./sw.js').catch(()=>{}));
