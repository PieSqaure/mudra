# ASL static-sign recognition (on-device)

MediaPipe hand landmarks -> normalized features -> tiny MLP -> sign text.
Offline, CPU-only, no UI.

| # | Deliverable | File | Status |
|---|-------------|------|--------|
| 1 | Landmark extraction | `extract.py` | done |
| 2 | Data collection      | `collect.py` | not started |
| 3 | Training + TFLite    | `train.py`   | not started |
| 4 | Inference test       | `infer.py`   | not started |

Target labels (15 static, one-handed):
`1 2 3 4 5 YES NO ME YOU PAIN HELP MEDICINE LEFT RIGHT STOP`

## Setup

```bash
cd asl_recognition
python -m venv .venv && source .venv/bin/activate    # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

## 1. Landmark extraction

```bash
python extract.py                 # webcam + overlay window + printed vectors
python extract.py --no-window     # headless, print only
python extract.py --full          # print all 42 values instead of a preview
python extract.py --image hand.jpg    # one still image, writes hand_landmarks.png
```

Keys in the window: `ESC`/`q` quit, `p` toggle printing.

On the first run it downloads `hand_landmarker.task` (~7.8 MB) into `models/`
and uses that local copy from then on — nothing leaves the machine at inference
time.

### Feature vector (42 floats)

1. 21 landmarks in **pixel** space, so the camera aspect ratio does not
   distort the hand shape.
2. Subtract landmark 0 (wrist) -> **position invariant**.
3. Divide by the largest absolute coordinate -> **scale invariant**, all values
   in `[-1, 1]`.
4. Flatten: `[x0, y0, x1, y1, ... x20, y20]` (`x0`,`y0` are always 0).

`normalize_landmarks()` in `extract.py` is the single source of truth for this —
deliverables 2, 3 and 4 import it so training and inference can never drift apart.

Verified on a two-hand still: re-running the same image scaled to 55% and shifted
by (200, 120) px changes the vector by <0.05 (detector resampling noise), i.e. the
representation is position- and scale-invariant as intended.

## Troubleshooting

- **`cv2.error` about a display / no window appears** — run with `--no-window`.
- **Linux `libGL.so.1` / `libEGL.so.1` missing** — `sudo apt install libgl1 libegl1 libgles2 libglib2.0-0`.
- **Wrong camera** — `python extract.py --camera 1`.
- **Hand not detected** — better lighting, or lower the threshold:
  `--min-detection-confidence 0.4`.

Reference implementations studied (Apache-2.0, patterns only, no code copied):
`Kazuhito00/hand-gesture-recognition-mediapipe`, `Muhib-Mehdi/ASL-Recognition-System`.
