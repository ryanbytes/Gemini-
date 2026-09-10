// Procedural electromechanical sound model for the Gemini MDIU recreation.
// Historical documents identify electro-mechanical wheel displays and a
// display-device drive, but do not identify a specific motor model/RPM.
// These sounds therefore model the documented mechanism class rather than
// pretending to be recordings of flight hardware.

export class MDIUSound {
  constructor() {
    this.ctx = null;
    this.enabled = true;
    this.lastDisplay = null;
  }

  async arm() {
    if (!this.enabled) return;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    if (!this.ctx) this.ctx = new AC({ latencyHint: 'interactive' });
    if (this.ctx.state === 'suspended') {
      try { await this.ctx.resume(); } catch (_) {}
    }
  }

  setEnabled(value) { this.enabled = !!value; }

  noiseBuffer(seconds = .12) {
    const n = Math.max(1, Math.floor(this.ctx.sampleRate * seconds));
    const b = this.ctx.createBuffer(1, n, this.ctx.sampleRate);
    const d = b.getChannelData(0);
    let x = 0x5eed1234;
    for (let i = 0; i < n; i++) {
      x ^= x << 13; x ^= x >>> 17; x ^= x << 5;
      d[i] = ((x >>> 0) / 0xffffffff) * 2 - 1;
    }
    return b;
  }

  transient({duration=.05, tone=1800, gain=.08, noise=.08, lowpass=5000, endTone=null}) {
    if (!this.enabled || !this.ctx) return;
    const c = this.ctx, t = c.currentTime;
    const out = c.createGain();
    out.gain.setValueAtTime(gain, t);
    out.gain.exponentialRampToValueAtTime(.0001, t + duration);
    out.connect(c.destination);

    const osc = c.createOscillator();
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(tone, t);
    if (endTone) osc.frequency.exponentialRampToValueAtTime(endTone, t + duration);
    osc.connect(out); osc.start(t); osc.stop(t + duration);

    if (noise > 0) {
      const src = c.createBufferSource(); src.buffer = this.noiseBuffer(duration);
      const f = c.createBiquadFilter(); f.type = 'lowpass'; f.frequency.value = lowpass;
      const ng = c.createGain(); ng.gain.setValueAtTime(noise, t); ng.gain.exponentialRampToValueAtTime(.0001, t + duration);
      src.connect(f); f.connect(ng); ng.connect(c.destination); src.start(t); src.stop(t + duration);
    }
  }

  key() {
    this.transient({ duration:.034, tone:2850, gain:.035, noise:.050, lowpass:6200, endTone:1450 });
    setTimeout(() => this.transient({ duration:.022, tone:930, gain:.018, noise:.018, lowpass:3300 }), 24);
  }

  command() {
    this.transient({ duration:.060, tone:720, gain:.050, noise:.070, lowpass:4200, endTone:220 });
    setTimeout(() => this.transient({ duration:.032, tone:430, gain:.032, noise:.028, lowpass:2600 }), 54);
  }

  toggle() {
    this.transient({ duration:.075, tone:510, gain:.055, noise:.050, lowpass:5000, endTone:135 });
    setTimeout(() => this.transient({ duration:.028, tone:2050, gain:.022, noise:.025, lowpass:6500 }), 58);
  }

  wheel(strength = 1) {
    if (!this.enabled || !this.ctx) return;
    const c = this.ctx, t = c.currentTime, dur = .18;
    const out = c.createGain();
    out.gain.setValueAtTime(.0001, t);
    out.gain.exponentialRampToValueAtTime(.030 * strength, t + .012);
    out.gain.setValueAtTime(.024 * strength, t + .11);
    out.gain.exponentialRampToValueAtTime(.0001, t + dur);
    out.connect(c.destination);

    // Low DC-motor/armature component plus a higher gear-mesh component.
    const motor = c.createOscillator(); motor.type = 'sawtooth';
    motor.frequency.setValueAtTime(96, t); motor.frequency.exponentialRampToValueAtTime(132, t + .04); motor.frequency.exponentialRampToValueAtTime(82, t + dur);
    const motorLP = c.createBiquadFilter(); motorLP.type='lowpass'; motorLP.frequency.value=900;
    motor.connect(motorLP); motorLP.connect(out); motor.start(t); motor.stop(t+dur);

    const gear = c.createOscillator(); gear.type='triangle';
    gear.frequency.setValueAtTime(610, t); gear.frequency.exponentialRampToValueAtTime(760, t+.035); gear.frequency.exponentialRampToValueAtTime(540, t+dur);
    const gg=c.createGain(); gg.gain.value=.22; gear.connect(gg); gg.connect(out); gear.start(t); gear.stop(t+dur);

    this.transient({ duration:.018, tone:1250, gain:.018*strength, noise:.035*strength, lowpass:5200 });
    setTimeout(() => this.transient({ duration:.024, tone:520, gain:.020*strength, noise:.020*strength, lowpass:3200 }), 155);
  }

  displayChanged(previous, next) {
    if (!this.enabled || !this.ctx || !previous || previous.length !== 7 || next.length !== 7) {
      this.lastDisplay = next;
      return;
    }
    let changed = 0;
    for (let i=0;i<7;i++) if (previous[i] !== next[i]) changed++;
    if (changed) this.wheel(Math.min(1.45, .72 + changed * .12));
    this.lastDisplay = next;
  }
}
