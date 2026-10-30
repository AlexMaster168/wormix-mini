"""Render sixteen original, deterministic, seamless instrumental loops.

Requires numpy and FFmpeg; no samples, downloaded music or runtime synthesis.
"""
from pathlib import Path
import json
import subprocess
import wave
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
RATE = 32000
THEMES = [
    ("neon", "Неоновые крыши", 112, 45, "minor", "synthwave"),
    ("volcano", "Сердце обсидиана", 96, 38, "minor", "cinematic drums"),
    ("orbit", "Тихая орбита", 88, 40, "minor", "space ambient"),
    ("chemical", "Кислотный контур", 120, 36, "minor", "acid groove"),
    ("arctic", "Северное сияние", 80, 50, "major", "bells and ambient"),
    ("scrap", "Пульс машин", 108, 30, "minor", "industrial"),
    ("canyon", "Красные горизонты", 100, 43, "minor", "desert plucks"),
    ("harbor", "Огни причала", 104, 41, "major", "harbor bells"),
    ("jungle", "Зелёный горизонт", 110, 46, "minor", "jungle percussion"),
    ("ruins", "Эхо руин", 90, 39, "minor", "ancient bells"),
    ("storm", "Грозовой фронт", 118, 35, "minor", "driving synth"),
    ("crystal", "Кристальный свет", 84, 53, "major", "crystal ambient"),
    ("rush", "Рывок", 128, 42, "minor", "action groove"),
    ("nocturne", "Ночной патруль", 92, 44, "minor", "night synth"),
    ("retro", "Аркадный разлом", 124, 48, "major", "arcade plucks"),
    ("boss", "Последний рубеж", 106, 34, "minor", "heavy drums"),
]


def compose(index, name, bpm, root, mode):
    rng = np.random.default_rng(7100 + index)
    beat = 60 / bpm
    length = round(64 * beat * RATE)
    mix = np.zeros((length, 2), dtype=np.float64)
    scale = [0, 2, 4 if mode == "major" else 3, 5, 7, 9 if mode == "major" else 8, 11 if mode == "major" else 10]

    def note(degree):
        return root + scale[degree % 7] + 12 * (degree // 7)

    def put(signal, start, gain, pan=0):
        ids = (round(start * beat * RATE) + np.arange(len(signal))) % length
        # Equal-power panning and circular accumulation preserve reverb at the loop seam.
        np.add.at(mix[:, 0], ids, signal * gain * np.sqrt((1 - pan) / 2))
        np.add.at(mix[:, 1], ids, signal * gain * np.sqrt((1 + pan) / 2))

    def voice(midi, beats, style):
        t = np.arange(round(beats * beat * RATE)) / RATE
        hz = 440 * 2 ** ((midi - 69) / 12)
        p = 2 * np.pi * hz * t
        if style == "pad":
            s = (np.sin(p) + .32*np.sin(p*1.003) + .16*np.sin(2*p)) / 1.48
            env = np.minimum(1, t/.22) * np.minimum(1, (t[-1]-t)/.45)
        elif style == "bell":
            s = np.sin(p) + .35*np.sin(p*2.001)*np.exp(-t*2) + .12*np.sin(p*3.997)*np.exp(-t*4)
            env = np.minimum(1, t/.008) * np.exp(-t*2.1) * np.minimum(1, (t[-1]-t)/.07)
        elif style == "bass":
            s = np.sin(p) + .24*np.sin(2*p) + (.12 if name == "chemical" else .05)*np.sin(3*p)
            env = np.minimum(1, t/.008) * np.minimum(1, (t[-1]-t)/.055) * np.exp(-t*.9)
        elif style == "metal":
            s = np.sin(p) * np.sin(p*1.414) + .22*np.sin(p*3.17)
            env = np.minimum(1, t/.004) * np.exp(-t*13)
        else:
            s = np.sin(p) + .25*np.sin(2*p) + .10*np.sin(3*p)
            env = np.minimum(1, t/.012) * np.exp(-t*3) * np.minimum(1, (t[-1]-t)/.07)
        return s * np.maximum(0, env)

    def drum(kind):
        t = np.arange(round(.42*RATE)) / RATE
        if kind == "kick":
            return np.sin(2*np.pi*(47*t + 1.7*(1-np.exp(-t*28)))) * np.exp(-t*15)
        noise = rng.standard_normal(len(t))
        high = noise - np.concatenate(([0], noise[:-1]))
        if kind == "snare":
            return (.24*np.sin(2*np.pi*175*t) + .28*high) * np.exp(-t*24) * np.minimum(1, t/.003)
        return .18*high*np.exp(-t*70)*np.minimum(1, t/.002)

    motifs = [[0, 2, 4, 6, 4, 2, 1, 2], [0, 4, 3, 2, 0, 2, 4, 6],
              [0, 2, 6, 4, 2, 1, 4, 2], [0, 1, 4, 2, 6, 4, 1, 2],
              [4, 2, 0, 6, 4, 5, 2, 0], [0, 0, 4, 3, 2, 0, 6, 4],
              [0, 4, 1, 3, 6, 3, 1, 4], [0, 2, 5, 4, 2, 6, 4, 1]]
    for bar in range(16):
        degree = [0, 5, 2, 6][bar % 4]
        start = bar*4
        for j, interval in enumerate((0, 2, 4)):
            put(voice(note(degree+interval)+12, 4.6, "pad"), start, .10 if name in ("orbit", "arctic") else .065, (j-1)*.55)
        bass_times = [0, .75, 1.5, 2, 2.75, 3.5] if name in ("chemical", "scrap") else [0, 1, 2, 3]
        if name in ("orbit", "arctic"):
            bass_times = [0, 2]
        for k, pos in enumerate(bass_times):
            put(voice(note(degree)+(0 if root < 40 else -12), .65 if name != "orbit" else 1.5, "bass"), start+pos, .21)
        positions = [0, 1.5, 2.5] if name == "arctic" else ([.5, 2, 3.5] if name == "orbit" else list(np.arange(0, 4, .5)))
        for k, pos in enumerate(positions):
            d = motifs[index % len(motifs)][(k+bar//4*2) % 8]
            put(voice(note(degree+d)+24, 1.6 if name in ("arctic", "orbit", "harbor") else .7, "bell" if name in ("arctic", "orbit", "harbor") else "pluck"), start+pos, .09 if name != "chemical" else .065, np.sin(k+bar)*.45)
        kick_times = [0, 2] if name == "volcano" else [0, 1, 2, 3]
        if name in ("arctic", "orbit"):
            kick_times = [0] if bar % 2 == 0 else []
        for pos in kick_times:
            put(drum("kick"), start+pos, .34 if name not in ("arctic", "orbit") else .13)
        if name not in ("arctic", "orbit"):
            for pos in (1, 3):
                put(drum("snare"), start+pos, .25, .08)
            for k in range(8):
                put(drum("hat"), start+k*.5, .16 if k % 2 else .10, -.35 if k % 2 else .35)
        if name in ("volcano", "scrap", "jungle", "boss"):
            for pos in (0, 1.75, 2.5):
                put(voice(55+bar % 3, .45, "metal"), start+pos, .07, -.3)
    # A short circular stereo echo leaves the loop continuous, rather than fading out.
    dry = mix.copy()
    for seconds, gain in ((beat*.75, .16), (beat*1.5, .09), (beat*2.25, .05)):
        mix += np.roll(dry[:, ::-1], round(seconds*RATE), axis=0)*gain
    mix = np.tanh(mix*1.2)
    mix *= .78 / np.max(np.abs(mix))
    return mix


def main():
    output = ROOT / "app/src/main/assets/music"
    temporary = ROOT / ".tmp/music"
    output.mkdir(parents=True, exist_ok=True)
    temporary.mkdir(parents=True, exist_ok=True)
    manifest = []
    for i, (name, title, bpm, root, mode, style) in enumerate(THEMES):
        if "--only-new" in __import__("sys").argv and (output / f"{name}.ogg").exists():
            manifest.append(dict(arena=name, title=title, bpm=bpm, style=style, seconds=round(64*60/bpm, 3), file=f"{name}.ogg"))
            continue
        samples = compose(i, name, bpm, root, mode)
        wav = temporary / f"{name}.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(2); w.setsampwidth(2); w.setframerate(RATE)
            w.writeframes((samples*32767).astype("<i2").tobytes())
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4", "-metadata", f"title={title}", "-metadata", "artist=RIFT Artillery", str(output / f"{name}.ogg")], check=True)
        manifest.append(dict(arena=name, title=title, bpm=bpm, style=style, seconds=round(len(samples)/RATE, 3), file=f"{name}.ogg"))
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
