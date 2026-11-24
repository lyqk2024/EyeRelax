package com.eye.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/** Small synthesized notification chime. No external audio asset is required. */
public final class SoftChime {
    private SoftChime() {}

    public static void play() {
        Thread t = new Thread(SoftChime::playInternal, "eyerelax-soft-chime");
        t.setDaemon(true);
        t.start();
    }

    private static void playInternal() {
        final float sampleRate = 44_100f;
        final AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        // A short, gentle ascending phrase. Frequencies are deliberately modest and volume stays low.
        final double[] frequencies = {523.25, 659.25, 783.99};
        final int[] durationsMs = {155, 155, 245};
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, 4_096);
            line.start();
            for (int n = 0; n < frequencies.length; n++) {
                writeTone(line, sampleRate, frequencies[n], durationsMs[n]);
                writeSilence(line, sampleRate, n == frequencies.length - 1 ? 35 : 35);
            }
            line.drain();
        } catch (Exception ignored) {
            try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch (Exception ignoredToo) {}
        }
    }

    private static void writeTone(SourceDataLine line, float sampleRate, double frequency, int durationMs) {
        int samples = Math.max(1, Math.round(sampleRate * durationMs / 1_000f));
        byte[] data = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            double p = i / (double) Math.max(1, samples - 1);
            double attack = Math.min(1.0, p / 0.12);
            double release = Math.min(1.0, (1.0 - p) / 0.28);
            double envelope = Math.min(attack, release);
            // Blend a very quiet second harmonic so the chime stays warm instead of sounding like a test tone.
            double wave = Math.sin(2 * Math.PI * frequency * i / sampleRate)
                    + 0.16 * Math.sin(2 * Math.PI * frequency * 2 * i / sampleRate);
            short value = (short) Math.round(wave * envelope * 2_800);
            data[i * 2] = (byte) (value & 0xff);
            data[i * 2 + 1] = (byte) ((value >>> 8) & 0xff);
        }
        line.write(data, 0, data.length);
    }

    private static void writeSilence(SourceDataLine line, float sampleRate, int durationMs) {
        int bytes = Math.max(2, Math.round(sampleRate * durationMs / 1_000f) * 2);
        line.write(new byte[bytes], 0, bytes);
    }
}
