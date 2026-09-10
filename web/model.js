export class GeminiMDIUModel {
  constructor(storage = null, now = () => Date.now()) {
    this.storage = storage;
    this.now = now;
    this.powered = true;
    this.entry = '';
    this.display = '0000000';
    this.readyAfter = 0;
    this.error = false;
    this.armed = false;
  }

  togglePower() {
    this.powered = !this.powered;
    this.readyAfter = 0;
    this.armed = false;
    return this.snapshot();
  }

  clear() {
    if (!this.powered) return this.snapshot();
    this.entry = '';
    this.display = '0000000';
    this.error = false;
    this.armed = true;
    this.readyAfter = this.now() + 500;
    return this.snapshot();
  }

  canAcceptDigit() {
    return this.powered && this.now() >= this.readyAfter;
  }

  pressDigit(digit) {
    if (!this.powered) return { ...this.snapshot(), accepted: false };
    if (!this.armed) return this.fail();
    if (!this.canAcceptDigit()) return { ...this.snapshot(), accepted: false };
    const d = String(digit);
    if (!/^\d$/.test(d)) return { ...this.snapshot(), accepted: false };
    if (this.entry.length >= 7) return this.fail();

    this.entry += d;
    this.display = this.entry.padEnd(7, '0');
    this.error = false;
    this.readyAfter = this.now() + 500;
    return { ...this.snapshot(), accepted: true };
  }

  enter() {
    if (!this.powered) return this.snapshot();
    if (!this.armed) return this.fail();
    if (this.entry.length !== 7 || !this.validAddress(this.entry.slice(0, 2))) return this.fail();
    const address = this.entry.slice(0, 2);
    const message = this.entry.slice(2);
    this.write(address, message);
    this.display = address + message;
    this.entry = '';
    this.error = false;
    this.armed = false;
    return this.snapshot();
  }

  readOut() {
    if (!this.powered) return { ...this.snapshot(), ok: false };
    if (!this.armed) return { ...this.fail(), ok: false };
    if (this.entry.length !== 2 || !this.validAddress(this.entry)) return { ...this.fail(), ok: false };
    const address = this.entry;
    const message = this.read(address);
    this.entry = '';
    this.error = false;
    this.armed = false;
    return { ...this.snapshot(), ok: true, address, message };
  }

  setReadoutDigit(index, digit) {
    if (index < 0 || index > 6) return this.snapshot();
    const chars = this.display.split('');
    chars[index] = String(digit).slice(0, 1);
    this.display = chars.join('');
    return this.snapshot();
  }

  fail() {
    this.entry = '';
    this.display = '0000000';
    this.error = true;
    this.armed = false;
    this.readyAfter = this.now() + 500;
    return { ...this.snapshot(), accepted: false };
  }

  validAddress(address) {
    return /^\d{2}$/.test(address) && address !== '00';
  }

  read(address) {
    const key = `gemini-mdiu-${address}`;
    const value = this.storage?.getItem?.(key);
    return /^\d{5}$/.test(value || '') ? value : '00000';
  }

  write(address, message) {
    const key = `gemini-mdiu-${address}`;
    this.storage?.setItem?.(key, message);
  }

  snapshot() {
    return {
      powered: this.powered,
      entry: this.entry,
      display: this.display,
      error: this.error,
      armed: this.armed,
      readyAfter: this.readyAfter,
    };
  }
}
