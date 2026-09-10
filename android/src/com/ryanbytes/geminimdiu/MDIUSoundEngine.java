package com.ryanbytes.geminimdiu;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Procedural sound model for the documented Gemini MDIU mechanism class.
 * Gemini documentation identifies electro-mechanical wheel displays and a
 * display-device drive, but does not identify a specific motor model/RPM.
 * These are therefore physically motivated synthetic sounds, not claimed
 * recordings of flight hardware.
 */
public final class MDIUSoundEngine {
    private static final int SR = 24000;
    private final ExecutorService audio = Executors.newCachedThreadPool();
    private final AtomicInteger variation = new AtomicInteger();
    private volatile boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public void shutdown() { audio.shutdownNow(); }

    public void key() { play(Kind.KEY, 1f); }
    public void command() { play(Kind.COMMAND, 1f); }
    public void toggle() { play(Kind.TOGGLE, 1f); }
    public void wheel(int changedDigits) {
        float strength = Math.min(1.45f, 0.72f + Math.max(1, changedDigits) * 0.12f);
        play(Kind.WHEEL, strength);
    }

    private enum Kind { KEY, COMMAND, TOGGLE, WHEEL }

    private void play(final Kind kind, final float strength) {
        if (!enabled) return;
        final int seed = 0x5eed1234 ^ variation.incrementAndGet() * 1103515245;
        audio.execute(() -> {
            short[] pcm = synth(kind, strength, seed);
            if (!enabled || pcm.length == 0) return;
            AudioTrack track = null;
            try {
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SR)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(pcm.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                track.write(pcm, 0, pcm.length);
                track.play();
                Thread.sleep((pcm.length * 1000L / SR) + 35L);
            } catch (Throwable ignored) {
                // Sound failure must never break the instrument UI.
            } finally {
                if (track != null) try { track.release(); } catch (Throwable ignored) {}
            }
        });
    }

    private static short[] synth(Kind kind, float strength, int seed) {
        final double duration;
        switch (kind) {
            case KEY: duration = 0.060; break;
            case COMMAND: duration = 0.105; break;
            case TOGGLE: duration = 0.120; break;
            default: duration = 0.190; break;
        }
        int n = Math.max(1, (int)(SR * duration));
        short[] out = new short[n];
        int rng = seed;
        double phase1 = 0, phase2 = 0;
        for (int i=0; i<n; i++) {
            double t = i / (double)SR;
            double u = t / duration;
            rng ^= rng << 13; rng ^= rng >>> 17; rng ^= rng << 5;
            double noise = (((rng & 0x7fffffff) / (double)0x7fffffff) * 2.0 - 1.0);
            double v;
            if (kind == Kind.KEY) {
                double env = Math.exp(-t * 85.0);
                v = 0.30 * Math.sin(2*Math.PI*2450*t) * env + 0.30 * noise * env;
                if (t > .026) v += 0.10 * Math.sin(2*Math.PI*900*(t-.026)) * Math.exp(-(t-.026)*105);
            } else if (kind == Kind.COMMAND) {
                double env = Math.exp(-t * 38.0);
                v = 0.32 * Math.sin(2*Math.PI*610*t) * env + 0.26 * noise * env;
                if (t > .055) v += 0.16 * Math.sin(2*Math.PI*390*(t-.055)) * Math.exp(-(t-.055)*72);
            } else if (kind == Kind.TOGGLE) {
                double env = Math.exp(-t * 30.0);
                v = 0.30 * Math.sin(2*Math.PI*470*t) * env + 0.21 * noise * env;
                if (t > .060) v += 0.14 * Math.sin(2*Math.PI*1900*(t-.060)) * Math.exp(-(t-.060)*95);
            } else {
                // Short DC armature/gear run-up, gear mesh, selector engagement,
                // then a detent/settling impact. No continuous sci-fi tone.
                double motorHz = u < .25 ? 94 + 150*u : 131 - 48*(u-.25)/.75;
                double gearHz = u < .25 ? 590 + 650*u : 752 - 205*(u-.25)/.75;
                phase1 += 2*Math.PI*motorHz/SR;
                phase2 += 2*Math.PI*gearHz/SR;
                double env = Math.sin(Math.PI * Math.min(1.0, Math.max(0.0, u)));
                double motor = Math.sin(phase1) + .34*Math.sin(phase1*2.02);
                double gear = Math.sin(phase2);
                v = strength * env * (0.16*motor + 0.060*gear + 0.032*noise);
                if (t < .014) v += strength * 0.18*noise*Math.exp(-t*170);
                double settle = t - .158;
                if (settle > 0) v += strength * 0.13*Math.sin(2*Math.PI*510*settle)*Math.exp(-settle*95);
            }
            v = Math.max(-0.92, Math.min(0.92, v));
            out[i] = (short)Math.round(v * 32767.0 * 0.70);
        }
        return out;
    }
}
