"""Decode shipped audio and check duration, silence, clipping and loop seams."""
from pathlib import Path
import hashlib
import json
import subprocess
import numpy as np

root = Path(__file__).resolve().parents[1] / "app/src/main/assets/music"
manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
assert {m["arena"] for m in manifest} == {"neon", "volcano", "orbit", "chemical", "arctic", "scrap", "canyon", "harbor", "jungle", "ruins", "storm", "crystal", "rush", "nocturne", "retro", "boss"}
hashes = set()
reports = []
for track in manifest:
    path = root / track["file"]
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    assert digest not in hashes, "Two arenas have the same recording"
    hashes.add(digest)
    decoded = subprocess.check_output(["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-ac", "2", "-ar", "32000", "-"])
    audio = np.frombuffer(decoded, dtype="<f4").reshape(-1, 2)
    assert np.isfinite(audio).all(), track
    seconds = len(audio) / 32000
    rms = float(np.sqrt(np.mean(audio**2)))
    peak = float(np.max(np.abs(audio)))
    seam = float(np.max(np.abs(audio[0] - audio[-1])))
    assert abs(seconds - track["seconds"]) < .03, (track, seconds)
    assert .025 < rms < .4, (track, rms)
    assert peak < .98, (track, peak)
    assert abs(float(audio.mean())) < .01, "Excessive DC offset"
    assert np.sqrt(np.mean((audio[:, 0]-audio[:, 1])**2)) > .001, "Stereo field missing"
    assert np.sqrt(np.mean(audio[:8000]**2)) > .01, "Silent opening"
    assert np.sqrt(np.mean(audio[-8000:]**2)) > .01, "Silent loop ending"
    assert seam < .08, ("Potential loop click", track, seam)
    reports.append(dict(arena=track["arena"], seconds=seconds, rms=round(rms, 5), peak=round(peak, 5), seam=round(seam, 5), sha256=digest))
print(json.dumps(dict(result="PASS", tracks=reports), ensure_ascii=False, indent=2))
