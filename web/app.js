import { GeminiMDIUModel } from './model.js';

const model = new GeminiMDIUModel(window.localStorage);
const digitEls = [...document.querySelectorAll('.digit')];
const powerToggle = document.querySelector('#power-toggle');
const instrument = document.querySelector('.instrument');
let readoutTimer = null;

function render() {
  const state = model.snapshot();
  digitEls.forEach((el, i) => {
    el.textContent = state.display[i];
    el.classList.toggle('unpowered', !state.powered);
  });
  powerToggle.classList.toggle('on', state.powered);
  powerToggle.setAttribute('aria-pressed', String(state.powered));
  instrument.classList.toggle('error', state.error);
}

function pulse(el) {
  el.classList.remove('pressed');
  void el.offsetWidth;
  el.classList.add('pressed');
  setTimeout(() => el.classList.remove('pressed'), 130);
}

function cancelReadout() {
  if (readoutTimer) clearInterval(readoutTimer);
  readoutTimer = null;
}

function digit(d, el) {
  cancelReadout();
  const result = model.pressDigit(d);
  if (result.accepted) {
    pulse(el);
    render();
  } else {
    render();
  }
}

function readOut(el) {
  cancelReadout();
  pulse(el);
  const result = model.readOut();
  render();
  if (!result.ok) return;
  model.display = result.address + '00000';
  render();
  let i = 0;
  readoutTimer = setInterval(() => {
    model.setReadoutDigit(i + 2, result.message[i]);
    render();
    i += 1;
    if (i >= 5) cancelReadout();
  }, 500);
}

document.querySelectorAll('[data-digit]').forEach((el) => {
  el.addEventListener('click', () => digit(el.dataset.digit, el));
});

document.querySelector('#clear').addEventListener('click', (e) => {
  cancelReadout();
  pulse(e.currentTarget);
  model.clear();
  render();
});

document.querySelector('#enter').addEventListener('click', (e) => {
  cancelReadout();
  pulse(e.currentTarget);
  model.enter();
  render();
});

document.querySelector('#readout').addEventListener('click', (e) => readOut(e.currentTarget));
powerToggle.addEventListener('click', () => {
  cancelReadout();
  model.togglePower();
  render();
});

window.addEventListener('keydown', (e) => {
  if (/^\d$/.test(e.key)) {
    const el = document.querySelector(`[data-digit="${e.key}"]`);
    digit(e.key, el);
  } else if (e.key.toLowerCase() === 'c') {
    document.querySelector('#clear').click();
  } else if (e.key.toLowerCase() === 'r') {
    document.querySelector('#readout').click();
  } else if (e.key.toLowerCase() === 'p') {
    powerToggle.click();
  } else if (e.key === 'Enter') {
    document.querySelector('#enter').click();
  }
});

render();
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('./sw.js').catch(() => {}));
}
