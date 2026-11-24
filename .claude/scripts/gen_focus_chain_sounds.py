#!/usr/bin/env python3
"""Genera los sonidos de las cadenas de Focus (bloqueo de apps con el pomodoro en marcha).

Es la misma síntesis que el prototipo web aprobado (artifact «Cadenas en Focus», ronda 5, A2g),
renderizada offline a WAV estéreo de 16 bits para SoundPool:

  app/src/main/res/raw/focus_chains_lock.wav     entrada: cuatro cadenas, golpes, candado que cierra
  app/src/main/res/raw/focus_chains_release.wav  salida: las cadenas se retiran

El calendario de golpes coincide con FocusChainsMotion (D = 900 ms, IMPACT = .78, STAGGER = 70 ms,
orden sup. izq., inf. der., sup. der., inf. izq.; candado: aparece a 1002 ms y cierra a 1242 ms).
Si cambias esos tiempos, cámbialos aquí también y vuelve a generar.

Uso:  python .claude/scripts/gen_focus_chain_sounds.py [--seed N]
Requiere numpy. La semilla fija hace que el resultado sea reproducible.
"""
import argparse
import math
import os
import wave

import numpy as np

SR = 44100

# --- calendario (ms), igual que FocusChainsMotion ---
D = 900
IMPACT = 0.78
STAGGER = 70
ORDER = [0, 3, 1, 2]                 # índice de esquina -> turno de salida
PAN = [-0.6, 0.6, -0.6, 0.6]         # sup. izq., sup. der., inf. izq., inf. der.
LAST_HIT = 3 * STAGGER + D * IMPACT  # 912
CLOSE_AT = LAST_HIT + 90             # aparece el candado abierto
SNAP_AT = CLOSE_AT + 240             # se cierra


class Mix:
    def __init__(self, seconds, rng):
        self.n = int(seconds * SR)
        self.out = np.zeros((2, self.n))
        self.rng = rng
        t = np.arange(self.n) / SR
        # Fuente metálica: dos bancos de cuadradas inarmónicas, uno modulando al otro (en anillo).
        a = sum(np.sign(np.sin(2 * math.pi * f * t)) for f in (539, 724, 846, 1176, 1356, 1663)) * 0.17
        b = sum(np.sign(np.sin(2 * math.pi * f * t)) for f in (416, 579, 694, 920, 1138, 1368)) * 0.17
        self.metal = a * b + a * 0.35

    # ---------------------------------------------------------------- piezas
    def noise(self, length):
        return self.rng.uniform(-1, 1, length)

    def env(self, length, peak, dur):
        """Rampa exponencial como en Web Audio: .0001 -> pico en 2 ms -> .0001 en dur, luego 0."""
        t = np.arange(length) / SR
        g = np.zeros(length)
        att = 0.002
        peak = max(peak, 0.0002)
        up = t < att
        g[up] = 0.0001 * (peak / 0.0001) ** (t[up] / att)
        down = (t >= att) & (t < dur)
        g[down] = peak * (0.0001 / peak) ** ((t[down] - att) / max(dur - att, 1e-4))
        return g

    def add(self, sig, start, pan):
        i = int(start * SR)
        if i >= self.n:
            return
        sig = sig[: self.n - i]
        # Panorámica de potencia constante (StereoPannerNode con fuente mono)
        x = (pan + 1) / 2
        self.out[0, i:i + len(sig)] += sig * math.cos(x * math.pi / 2)
        self.out[1, i:i + len(sig)] += sig * math.sin(x * math.pi / 2)

    def burst(self, t, kind, freq, q, dur, peak, pan=0.0):
        dur = max(dur, 0.004)
        length = int((dur + 0.01) * SR)
        sig = biquad(self.noise(length), kind, freq, q)
        self.add(sig * self.env(length, peak, dur), t, pan)

    def metal_hit(self, t, freq, q, dur, peak, pan=0.0):
        dur = max(dur, 0.004)
        warm = int(0.02 * SR)
        i = int(t * SR) - warm
        length = int((dur + 0.01) * SR) + warm
        src = np.zeros(length)
        lo = max(i, 0)
        hi = min(i + length, self.n)
        src[lo - i:hi - i] = self.metal[lo:hi]
        sig = biquad(src, 'bandpass', freq, q)[warm:]
        self.add(sig * self.env(len(sig), peak, dur), t, pan)

    def ring_n(self, t, freq, q, dur, peak, pan=0.0):
        self.burst(t, 'bandpass', freq, q, dur, peak * min(30, math.sqrt(22050 * q / freq) * 0.5), pan)

    # ---------------------------------------------------------------- sonidos
    def clink(self, t, pan, vol):
        r = self.rng.random
        self.burst(t, 'highpass', 2500, 0.7, 0.004 + r() * 0.004, vol * 0.9, pan)
        self.metal_hit(t, 1500 + r() * 3200, 1.2 + r() * 2, 0.025 + r() * 0.06, vol * 1.6, pan)
        if r() < 0.6:
            self.metal_hit(t, 700 + r() * 700, 2, 0.03 + r() * 0.04, vol * 0.9, pan)
        self.ring_n(t, 2200 + r() * 4200, 35 + r() * 30, 0.05 + r() * 0.08, vol * 0.35, pan)

    def clank(self, t, pan, vol):
        amps = [0.5, 0.45, 0.35, 0.25, 0.18]
        self.burst(t, 'lowpass', 320, 0.8, 0.07, vol * 1.1, pan)
        self.burst(t, 'highpass', 1800, 0.7, 0.006, vol, pan)
        self.metal_hit(t, 1150, 1.1, 0.16, vol * 2.2, pan)
        self.metal_hit(t, 3300, 1.4, 0.08, vol * 1.4, pan)
        for i, fr in enumerate((640, 1480, 2370, 3190, 4520)):
            fr *= 1 + (self.rng.random() - 0.5) * 0.06
            self.ring_n(t, fr, 55, 0.32 / (1 + i * 0.35), vol * amps[i], pan)

    def click(self, t, vol):
        self.burst(t, 'bandpass', 2500, 1, 0.006, vol)
        self.metal_hit(t, 3000, 1.5, 0.016, vol * 1.2)
        self.burst(t + 0.075, 'bandpass', 1100, 2, 0.025, vol * 0.9)
        self.metal_hit(t + 0.075, 1400, 1.2, 0.03, vol * 1.2)
        self.ring_n(t + 0.075, 900, 30, 0.06, vol * 0.4)

    def drag(self, t, dur, vol):
        length = int((dur + 0.01) * SR)
        i = int(t * SR)
        src = self.metal[i:i + length]
        sig = biquad(src, 'bandpass', 2600, 0.6)
        tt = np.arange(len(sig)) / SR
        g = np.where(tt < 0.06, 0.0001 * (vol / 0.0001) ** (tt / 0.06), vol)
        tail = tt > dur * 0.8
        g[tail] = vol * (0.0001 / vol) ** ((tt[tail] - dur * 0.8) / (dur * 0.2))
        g[tt > dur] = 0
        self.add(sig * g, t, 0.0)

    # ---------------------------------------------------------------- salida
    def master(self):
        x = self.out * 0.5
        x = np.vstack([biquad(ch, 'lowpass', 10500, 0.0) for ch in x])
        # Sala pequeña: respuesta de 0,45 s con caída (1 - i/n)^3.2, mezclada al 16 %
        ir_len = int(SR * 0.45)
        wet = []
        for ch in range(2):
            ir = self.rng.uniform(-1, 1, ir_len) * (1 - np.arange(ir_len) / ir_len) ** 3.2
            wet.append(fft_convolve(x[ch], ir)[: self.n])
        wet = np.array(wet)
        # El convolver de Web Audio normaliza la respuesta; aquí se iguala por energía
        wet *= 0.16 * np.sqrt(np.mean(x ** 2) / max(np.mean(wet ** 2), 1e-12))
        return compress(x + wet)


def biquad(x, kind, freq, q):
    """Filtros de Web Audio (RBJ). bandpass: Q lineal y 0 dB de pico; lowpass/highpass: Q en dB."""
    w0 = 2 * math.pi * min(freq, SR / 2 - 1) / SR
    cw, sw = math.cos(w0), math.sin(w0)
    if kind == 'bandpass':
        alpha = sw / (2 * q)
        b = (alpha, 0.0, -alpha)
    else:
        alpha = sw / (2 * 10 ** (q / 20))
        if kind == 'lowpass':
            b = ((1 - cw) / 2, 1 - cw, (1 - cw) / 2)
        else:
            b = ((1 + cw) / 2, -(1 + cw), (1 + cw) / 2)
    a0 = 1 + alpha
    b0, b1, b2 = b[0] / a0, b[1] / a0, b[2] / a0
    a1, a2 = -2 * cw / a0, (1 - alpha) / a0
    y = np.empty(len(x))
    x1 = x2 = y1 = y2 = 0.0
    for i, xi in enumerate(x.tolist()):
        yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, xi, y1, yi
        y[i] = yi
    return y


def fft_convolve(a, b):
    n = len(a) + len(b) - 1
    size = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(a, size) * np.fft.rfft(b, size), size)[:n]


def compress(x, threshold_db=-24.0, ratio=12.0, attack=0.003, release=0.25):
    """Compresor de pico sencillo (valores por defecto de DynamicsCompressorNode) + normalizado a -1 dBFS."""
    level = np.max(np.abs(x), axis=0)
    ga, gr = math.exp(-1 / (attack * SR)), math.exp(-1 / (release * SR))
    env = np.empty(len(level))
    e = 0.0
    for i, v in enumerate(level.tolist()):
        e = ga * e + (1 - ga) * v if v > e else gr * e + (1 - gr) * v
        env[i] = e
    db = 20 * np.log10(np.maximum(env, 1e-9))
    over = np.maximum(db - threshold_db, 0)
    gain = 10 ** (-(over - over / ratio) / 20)
    y = x * gain
    peak = np.max(np.abs(y))
    return y * (10 ** (-1 / 20) / peak) if peak > 0 else y


def lock_sequence(rng):
    m = Mix(1.75, rng)
    t0 = 0.03
    run = D * IMPACT / 1000
    m.drag(t0, run + 3 * STAGGER / 1000, 0.05)
    for c in range(4):
        start = t0 + ORDER.index(c) * STAGGER / 1000
        for _ in range(18):
            tc = start + rng.random() * run
            vc = 0.1 + rng.random() * 0.2
            m.clink(tc, PAN[c], vc)
            if rng.random() < 0.35:
                m.clink(tc + 0.012 + rng.random() * 0.02, PAN[c], vc * 0.5)  # rebote del eslabón
        m.clank(start + run, PAN[c] * 0.7, 0.5)
        for _ in range(3):
            m.clink(start + run + 0.04 + rng.random() * 0.22, PAN[c], 0.06 + rng.random() * 0.06)
    m.clink(t0 + (CLOSE_AT + 150) / 1000, 0.5, 0.14)   # el candado toca el anillo
    m.click(t0 + SNAP_AT / 1000, 0.85)                 # y se cierra (más fuerte que en la web: altavoz del móvil)
    return m.master()


def release_sequence(rng):
    m = Mix(0.75, rng)
    t0 = 0.03
    m.drag(t0, 0.36, 0.04)
    for i in range(16):
        m.clink(t0 + rng.random() * 0.32, PAN[i % 4], 0.08 + rng.random() * 0.1)
    return m.master()


def trim_tail(x, floor_db=-60.0):
    """Corta el silencio final (deja 30 ms de margen)."""
    level = np.max(np.abs(x), axis=0)
    loud = np.nonzero(level > 10 ** (floor_db / 20))[0]
    end = min(len(level), (loud[-1] if len(loud) else 0) + int(0.03 * SR))
    return x[:, :end]


def write_wav(path, x):
    x = trim_tail(x)
    pcm = (np.clip(x, -1, 1) * 32767).astype('<i2').T.copy()
    with wave.open(path, 'wb') as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(f'{path}: {x.shape[1] / SR:.2f} s, {os.path.getsize(path) // 1024} KB')


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--seed', type=int, default=7)
    args = ap.parse_args()
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
    raw = os.path.join(root, 'app', 'src', 'main', 'res', 'raw')
    write_wav(os.path.join(raw, 'focus_chains_lock.wav'), lock_sequence(np.random.default_rng(args.seed)))
    write_wav(os.path.join(raw, 'focus_chains_release.wav'), release_sequence(np.random.default_rng(args.seed + 1)))


if __name__ == '__main__':
    main()
