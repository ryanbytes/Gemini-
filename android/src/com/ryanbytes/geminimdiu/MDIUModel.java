package com.ryanbytes.geminimdiu;

import java.util.HashMap;
import java.util.Map;

public final class MDIUModel {
    public interface Store {
        String get(String address);
        void put(String address, String message);
    }

    private final Store store;
    private final Map<String, String> fallback = new HashMap<>();
    private boolean powered = true;
    private String entry = "";
    private String display = "0000000";
    private long readyAfterMs = 0;
    private boolean error = false;
    private boolean armed = false;

    public MDIUModel(Store store) { this.store = store; }

    public boolean isPowered() { return powered; }
    public String getEntry() { return entry; }
    public String getDisplay() { return display; }
    public boolean isError() { return error; }

    public void togglePower() {
        powered = !powered;
        readyAfterMs = 0;
        armed = false;
    }

    public void clear(long nowMs) {
        if (!powered) return;
        // CLEAR resets entry/data-ready latches, but leaves the physical
        // display wheels at their existing positions.
        entry = "";
        error = false;
        armed = true;
        readyAfterMs = nowMs + 500;
    }

    public boolean pressDigit(int digit, long nowMs) {
        if (!powered) return false;
        if (!armed) { fail(nowMs); return false; }
        if (nowMs < readyAfterMs || digit < 0 || digit > 9) return false;
        if (entry.length() >= 7) { fail(nowMs); return false; }
        int position = entry.length();
        entry += digit;
        char[] chars = display.toCharArray();
        chars[position] = (char)('0' + digit);
        display = new String(chars);
        error = false;
        readyAfterMs = nowMs + 500;
        return true;
    }

    public boolean enter(long nowMs) {
        if (!powered) return false;
        if (!armed) { fail(nowMs); return false; }
        if (entry.length() != 7 || !validAddress(entry.substring(0, 2))) {
            fail(nowMs);
            return false;
        }
        String address = entry.substring(0, 2);
        String message = entry.substring(2);
        put(address, message);
        entry = "";
        error = false;
        armed = false;
        return true;
    }

    public String[] readOut(long nowMs) {
        if (!powered || !armed || entry.length() != 2 || !validAddress(entry)) {
            fail(nowMs);
            return null;
        }
        String address = entry;
        String message = get(address);
        entry = "";
        error = false;
        armed = false;
        return new String[]{address, message};
    }

    public void setReadoutDigit(int index, char digit) {
        if (index < 0 || index > 6 || digit < '0' || digit > '9') return;
        char[] chars = display.toCharArray();
        chars[index] = digit;
        display = new String(chars);
    }

    private boolean validAddress(String address) {
        return address != null && address.matches("\\d{2}") && !"00".equals(address);
    }

    private void fail(long nowMs) {
        entry = "";
        display = "0000000";
        error = true;
        armed = false;
        readyAfterMs = nowMs + 500;
    }

    private String get(String address) {
        String value = store != null ? store.get(address) : fallback.get(address);
        return value != null && value.matches("\\d{5}") ? value : "00000";
    }

    private void put(String address, String message) {
        if (store != null) store.put(address, message); else fallback.put(address, message);
    }
}
