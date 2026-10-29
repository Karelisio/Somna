"""Prepare a looping ambience: steadiest window, high-pass, (compress), level, limit, OGG Vorbis.
Iterates until the decoded OGG hits the target RMS with peaks safely below full scale.
Needs ffmpeg (libvorbis) and numpy. Settings used for res/raw (sources: Freesound HQ previews):
  fire   81801  --seconds 80 --hp 70  --rms -22 --limit 0.7 --eq "treble=g=-4:f=5000"
  rain   725603 --seconds 80 --hp 90  --rms -19
  waves  578524 --seconds 90 --hp 60  --rms -20 --comp "threshold=0.08:ratio=2:attack=40:release=600:knee=4"
  birds  573080 --seconds 80 --hp 300 --rms -21 --limit 0.72 --comp "threshold=0.08:ratio=2.5:attack=8:release=200:knee=4"
"""
import argparse, subprocess, numpy as np
ap = argparse.ArgumentParser()
ap.add_argument("src"); ap.add_argument("dst")
ap.add_argument("--seconds", type=float, default=75); ap.add_argument("--channels", type=int, default=2)
ap.add_argument("--hp", type=float, default=60); ap.add_argument("--rms", type=float, default=-22)
ap.add_argument("--start", type=float, default=-1); ap.add_argument("--comp", default=""); ap.add_argument("--eq", default="")
ap.add_argument("--limit", type=float, default=0.79); ap.add_argument("--quality", type=float, default=4)
a = ap.parse_args()
sr, ch = 44100, a.channels
def ff(args, data=None):
    return subprocess.run(["ffmpeg","-v","error",*args], input=data, capture_output=True, check=True).stdout
def f32(b): return np.frombuffer(b, dtype=np.float32).reshape(-1, ch).astype(np.float64)
x = f32(ff(["-i",a.src,"-af",f"highpass=f={a.hp}:poles=2"+(f",{a.eq}" if a.eq else ""),"-f","f32le","-ac",str(ch),"-ar",str(sr),"-"]))
mono = x.mean(axis=1)
db = 10*np.log10(np.array([np.mean(mono[i:i+sr]**2) for i in range(0, len(mono)-sr, sr)])+1e-12)
L = int(a.seconds)
if a.start >= 0: s0 = int(a.start)
else: s0 = min(range(1, max(2, len(db)-L-1)), key=lambda s: (db[s:s+L].max()-np.median(db[s:s+L])) + 0.5*db[s:s+L].std())
seg = x[s0*sr:(s0+L)*sr]
rms_db = lambda y: 20*np.log10(np.sqrt(np.mean(y.mean(axis=1)**2)))
# The compressor sees the clip at a fixed -26 dB RMS (its threshold is relative to that); the
# make-up gain after it is what iterates towards the target.
pcm = (seg*10**((-26 - rms_db(seg))/20)).astype(np.float32).tobytes()
gain_db = a.rms + 26
for it in range(8):
    chain = (f"acompressor={a.comp}," if a.comp else "") + f"volume={gain_db:.2f}dB,alimiter=limit={a.limit}:attack=5:release=80:asc=1:level=disabled"
    ff(["-y","-f","f32le","-ac",str(ch),"-ar",str(sr),"-i","-","-af",chain,"-c:a","libvorbis","-q:a",str(a.quality),a.dst], pcm)
    y = f32(ff(["-i",a.dst,"-f","f32le","-"]))
    err, peak = a.rms - rms_db(y), np.abs(y).max()
    if peak > 0.97: gain_db -= max(1.0, 20*np.log10(peak/0.9)); continue
    if abs(err) < 0.3: break
    gain_db += err
print(f"{a.dst.split('/')[-1]}: window {s0}-{s0+L}s, gain {gain_db:+.1f} dB, rms {rms_db(y):.1f} dB, decoded peak {peak:.2f}")
