#!/usr/bin/env python3
"""
Deliverable 1 - Landmark extraction.

Runs MediaPipe Hands on a webcam (or a still image), draws the 21 landmarks on
the video, and prints the normalized, position- and scale-invariant feature
vector for every frame where a hand is found.

    python extract.py                 # webcam, window + printing
    python extract.py --no-window     # headless, just print vectors
    python extract.py --image hand.jpg

Everything is offline after the first run: the MediaPipe hand-landmark model is
downloaded once into ./models/ and reused from disk afterwards.

Feature vector (42 floats, the same representation used by deliverables 2-4):
  1. Take the 21 (x, y) landmarks in *pixel* space (so the camera's aspect
     ratio does not distort the hand shape).
  2. Translate so the wrist (landmark 0) is the origin  -> position invariant.
  3. Divide by the largest absolute coordinate          -> scale invariant,
     every value lands in [-1, 1] regardless of how far the hand is from
     the lens.
  4. Flatten row-major: [x0, y0, x1, y1, ... x20, y20]. x0/y0 are always 0.0
     and are kept so the vector length is fixed and indexing stays obvious.
"""

from __future__ import annotations

import argparse
import os
import sys
import time
import urllib.request
from dataclasses import dataclass

import cv2
import numpy as np

# --------------------------------------------------------------------------- #
# Model file (MediaPipe Tasks). Apache-2.0, free to redistribute/use offline.
# --------------------------------------------------------------------------- #
MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/hand_landmarker/"
    "hand_landmarker/float16/1/hand_landmarker.task"
)
DEFAULT_MODEL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                  "models", "hand_landmarker.task")

NUM_LANDMARKS = 21
FEATURE_DIM = NUM_LANDMARKS * 2  # x, y per landmark

# Bone list for the overlay (index pairs into the 21 landmarks).
HAND_CONNECTIONS = (
    (0, 1), (1, 2), (2, 3), (3, 4),            # thumb
    (0, 5), (5, 6), (6, 7), (7, 8),            # index
    (9, 10), (10, 11), (11, 12),               # middle
    (13, 14), (14, 15), (15, 16),              # ring
    (0, 17), (17, 18), (18, 19), (19, 20),     # pinky
    (5, 9), (9, 13), (13, 17),                 # palm
)
FINGERTIPS = (4, 8, 12, 16, 20)


@dataclass
class HandResult:
    """One detected hand in one frame."""
    points_px: np.ndarray   # (21, 2) float32, pixel coordinates
    features: np.ndarray    # (42,)   float32, normalized + flattened
    handedness: str         # "Left" / "Right" (as seen by the camera)
    score: float            # handedness/detection confidence


# --------------------------------------------------------------------------- #
# Normalization - the single source of truth for deliverables 2, 3 and 4.
# --------------------------------------------------------------------------- #
def normalize_landmarks(points_px: np.ndarray) -> np.ndarray:
    """(21, 2) pixel coords -> (42,) translation- and scale-invariant vector."""
    pts = np.asarray(points_px, dtype=np.float32).reshape(NUM_LANDMARKS, 2)
    pts = pts - pts[0]                       # wrist becomes the origin
    scale = float(np.max(np.abs(pts)))       # largest span from the wrist
    if scale > 1e-6:
        pts = pts / scale                    # fixed reference size -> [-1, 1]
    return pts.reshape(-1).astype(np.float32)


def landmarks_to_pixels(norm_xy: np.ndarray, width: int, height: int) -> np.ndarray:
    """MediaPipe's 0..1 image coords -> pixel coords, so aspect ratio is kept."""
    pts = np.asarray(norm_xy, dtype=np.float32).reshape(NUM_LANDMARKS, 2).copy()
    pts[:, 0] *= width
    pts[:, 1] *= height
    return pts


# --------------------------------------------------------------------------- #
# Detector. Uses the MediaPipe Tasks API when available (mediapipe >= 0.10),
# and falls back to the legacy mp.solutions.hands pipeline on older installs.
# --------------------------------------------------------------------------- #
def ensure_model(path: str = DEFAULT_MODEL_PATH, quiet: bool = False) -> str:
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if not quiet:
        print(f"[extract] downloading hand_landmarker.task -> {path} "
              f"(one time, ~7.8 MB)", file=sys.stderr)
    tmp = path + ".part"
    urllib.request.urlretrieve(MODEL_URL, tmp)
    os.replace(tmp, path)
    return path


class HandLandmarkDetector:
    """Thin wrapper so the rest of the pipeline never touches MediaPipe directly."""

    def __init__(self, max_hands: int = 1, detection_confidence: float = 0.6,
                 tracking_confidence: float = 0.5, model_path: str = DEFAULT_MODEL_PATH,
                 static_image_mode: bool = False, quiet: bool = False):
        import mediapipe as mp

        self._mp = mp
        self._static = static_image_mode
        self.backend = "tasks" if hasattr(mp, "tasks") else "solutions"

        if self.backend == "tasks":
            from mediapipe.tasks import python as mp_python
            from mediapipe.tasks.python import vision

            model_path = ensure_model(model_path, quiet=quiet)
            mode = (vision.RunningMode.IMAGE if static_image_mode
                    else vision.RunningMode.VIDEO)
            options = vision.HandLandmarkerOptions(
                base_options=mp_python.BaseOptions(model_asset_path=model_path),
                running_mode=mode,
                num_hands=max_hands,
                min_hand_detection_confidence=detection_confidence,
                min_hand_presence_confidence=detection_confidence,
                min_tracking_confidence=tracking_confidence,
            )
            self._detector = vision.HandLandmarker.create_from_options(options)
        else:  # legacy mediapipe (<= 0.10.x) - kept so the script is portable
            self._detector = mp.solutions.hands.Hands(
                static_image_mode=static_image_mode,
                max_num_hands=max_hands,
                min_detection_confidence=detection_confidence,
                min_tracking_confidence=tracking_confidence,
            )

    def detect(self, frame_bgr: np.ndarray, timestamp_ms: int = 0) -> list[HandResult]:
        h, w = frame_bgr.shape[:2]
        rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        hands: list[HandResult] = []

        if self.backend == "tasks":
            mp_image = self._mp.Image(image_format=self._mp.ImageFormat.SRGB, data=rgb)
            result = (self._detector.detect(mp_image) if self._static
                      else self._detector.detect_for_video(mp_image, timestamp_ms))
            for i, lm_list in enumerate(result.hand_landmarks):
                xy = np.array([[lm.x, lm.y] for lm in lm_list], dtype=np.float32)
                label, score = "?", 0.0
                if i < len(result.handedness) and result.handedness[i]:
                    label = result.handedness[i][0].category_name
                    score = float(result.handedness[i][0].score)
                pts_px = landmarks_to_pixels(xy, w, h)
                hands.append(HandResult(pts_px, normalize_landmarks(pts_px), label, score))
        else:
            result = self._detector.process(rgb)
            for i, lm in enumerate(result.multi_hand_landmarks or []):
                xy = np.array([[p.x, p.y] for p in lm.landmark], dtype=np.float32)
                label, score = "?", 0.0
                if result.multi_handedness and i < len(result.multi_handedness):
                    cls = result.multi_handedness[i].classification[0]
                    label, score = cls.label, float(cls.score)
                pts_px = landmarks_to_pixels(xy, w, h)
                hands.append(HandResult(pts_px, normalize_landmarks(pts_px), label, score))

        return hands

    def close(self) -> None:
        try:
            self._detector.close()
        except Exception:
            pass


# --------------------------------------------------------------------------- #
# Visualization
# --------------------------------------------------------------------------- #
def draw_landmarks(frame: np.ndarray, points_px: np.ndarray,
                   color=(0, 220, 120)) -> None:
    pts = points_px.astype(int)
    for a, b in HAND_CONNECTIONS:
        cv2.line(frame, tuple(pts[a]), tuple(pts[b]), (40, 40, 40), 5, cv2.LINE_AA)
        cv2.line(frame, tuple(pts[a]), tuple(pts[b]), color, 2, cv2.LINE_AA)
    for i, (x, y) in enumerate(pts):
        r = 6 if i in FINGERTIPS or i == 0 else 4
        cv2.circle(frame, (x, y), r, (255, 255, 255), -1, cv2.LINE_AA)
        cv2.circle(frame, (x, y), r, (0, 0, 0), 1, cv2.LINE_AA)


def draw_hud(frame: np.ndarray, lines: list[str]) -> None:
    for i, text in enumerate(lines):
        y = 26 + i * 24
        cv2.putText(frame, text, (10, y), cv2.FONT_HERSHEY_SIMPLEX, 0.6,
                    (0, 0, 0), 3, cv2.LINE_AA)
        cv2.putText(frame, text, (10, y), cv2.FONT_HERSHEY_SIMPLEX, 0.6,
                    (255, 255, 255), 1, cv2.LINE_AA)


def format_features(features: np.ndarray, decimals: int = 3,
                    max_items: int | None = None) -> str:
    vals = features if max_items is None else features[:max_items]
    body = " ".join(f"{v:+.{decimals}f}" for v in vals)
    if max_items is not None and len(features) > max_items:
        body += " ..."
    return body


# --------------------------------------------------------------------------- #
# CLI
# --------------------------------------------------------------------------- #
def parse_args(argv=None):
    p = argparse.ArgumentParser(description="MediaPipe hand landmark extraction.")
    p.add_argument("--camera", type=int, default=0, help="webcam index (default 0)")
    p.add_argument("--width", type=int, default=960, help="capture width")
    p.add_argument("--height", type=int, default=540, help="capture height")
    p.add_argument("--image", type=str, default=None,
                   help="run once on an image file instead of the webcam")
    p.add_argument("--max-hands", type=int, default=1)
    p.add_argument("--min-detection-confidence", type=float, default=0.6)
    p.add_argument("--min-tracking-confidence", type=float, default=0.5)
    p.add_argument("--model", type=str, default=DEFAULT_MODEL_PATH)
    p.add_argument("--no-mirror", action="store_true",
                   help="do not flip the frame horizontally (default is selfie view)")
    p.add_argument("--no-window", action="store_true",
                   help="headless: print vectors, never open a window")
    p.add_argument("--print-every", type=int, default=5,
                   help="print the feature vector every N frames with a hand (0 = never)")
    p.add_argument("--full", action="store_true",
                   help="print all 42 values instead of a truncated preview")
    return p.parse_args(argv)


def run_image(args) -> int:
    frame = cv2.imread(args.image)
    if frame is None:
        print(f"[extract] could not read image: {args.image}", file=sys.stderr)
        return 2
    det = HandLandmarkDetector(args.max_hands, args.min_detection_confidence,
                               args.min_tracking_confidence, args.model,
                               static_image_mode=True)
    hands = det.detect(frame)
    det.close()
    print(f"[extract] backend={det.backend}  hands={len(hands)}")
    for i, hand in enumerate(hands):
        print(f"  hand {i}: {hand.handedness} ({hand.score:.2f})  dim={hand.features.size}")
        print("  " + format_features(hand.features, max_items=None if args.full else 12))
        draw_landmarks(frame, hand.points_px)
    out = os.path.splitext(args.image)[0] + "_landmarks.png"
    cv2.imwrite(out, frame)
    print(f"[extract] overlay written to {out}")
    return 0 if hands else 1


def run_webcam(args) -> int:
    cap = cv2.VideoCapture(args.camera)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, args.width)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, args.height)
    if not cap.isOpened():
        print(f"[extract] cannot open camera {args.camera}", file=sys.stderr)
        return 2

    det = HandLandmarkDetector(args.max_hands, args.min_detection_confidence,
                               args.min_tracking_confidence, args.model)
    show = not args.no_window
    printing = args.print_every > 0
    print(f"[extract] backend={det.backend}  feature dim={FEATURE_DIM}  "
          f"({'window' if show else 'headless'})")
    print("[extract] keys: ESC/q quit   p toggle printing")

    frames = 0
    hand_frames = 0
    t0 = time.time()
    fps = 0.0
    start_ms = time.perf_counter()

    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                print("[extract] camera read failed", file=sys.stderr)
                break
            if not args.no_mirror:
                frame = cv2.flip(frame, 1)

            ts_ms = int((time.perf_counter() - start_ms) * 1000)
            hands = det.detect(frame, ts_ms)

            frames += 1
            if frames % 10 == 0:
                now = time.time()
                fps = 10.0 / max(now - t0, 1e-6)
                t0 = now

            for hand in hands:
                hand_frames += 1
                if show:
                    draw_landmarks(frame, hand.points_px)
                if printing and hand_frames % max(args.print_every, 1) == 0:
                    preview = format_features(hand.features,
                                              max_items=None if args.full else 12)
                    print(f"[{hand.handedness:>5} {hand.score:.2f}] "
                          f"dim={hand.features.size} {preview}", flush=True)

            if show:
                hud = [f"fps {fps:4.1f}   hands {len(hands)}   backend {det.backend}"]
                if hands:
                    h0 = hands[0]
                    hud.append(f"{h0.handedness} {h0.score:.2f}  "
                               f"|v|={np.linalg.norm(h0.features):.2f}")
                    hud.append(format_features(h0.features, decimals=2, max_items=8))
                else:
                    hud.append("no hand in frame")
                draw_hud(frame, hud)
                try:
                    cv2.imshow("extract - hand landmarks", frame)
                except cv2.error:
                    print("[extract] no display available; rerun with --no-window",
                          file=sys.stderr)
                    show = False
                    continue
                key = cv2.waitKey(1) & 0xFF
                if key in (27, ord("q")):
                    break
                if key == ord("p"):
                    printing = not printing
                    print(f"[extract] printing {'on' if printing else 'off'}")
    except KeyboardInterrupt:
        pass
    finally:
        cap.release()
        det.close()
        if show:
            cv2.destroyAllWindows()

    print(f"[extract] {frames} frames, {hand_frames} with a hand")
    return 0


def main(argv=None) -> int:
    args = parse_args(argv)
    return run_image(args) if args.image else run_webcam(args)


if __name__ == "__main__":
    raise SystemExit(main())
