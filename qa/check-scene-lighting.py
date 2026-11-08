"""Check the native scene and, when installed, compare against actual Create GuiGameElement pixels."""
from pathlib import Path
import json
import sys
from PIL import Image

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "build/ui-test"
meta = json.loads((root / "scene-lighting-pixels.json").read_text(encoding="utf-8"))
path = max(root.glob(meta["imagePattern"]), key=lambda p: p.stat().st_mtime)
image = Image.open(path).convert("RGB")
radius = round(meta["radius"])

def sample(center):
    cx, cy = (round(n) for n in center)
    crop = image.crop((cx-radius, cy-radius, cx+radius, cy+radius))
    pixels = list(crop.get_flattened_data())
    mask = [g > r*1.2 and b > r*1.2 for r, g, b in pixels]
    colors = [p for p, visible in zip(pixels, mask) if visible]
    assert len(colors) > 1000, "Diamond block did not render"
    return pixels, mask, [sum(p[i] for p in colors)/len(colors) for i in range(3)]

actual, mask, mean = sample(meta["vjce"])
report = {"status": "PASS", "image": str(path.relative_to(root)), "create": meta["create"], "vjceMeanRGB": mean}
if meta["create"]:
    expected, expected_mask, expected_mean = sample(meta["reference"])
    area_ratio = sum(mask)/sum(expected_mask)
    intersection = sum(a and b for a, b in zip(mask, expected_mask))
    union = sum(a or b for a, b in zip(mask, expected_mask))
    error = sum(abs(a[i]-b[i]) for a, b, visible, reference in zip(actual, expected, mask, expected_mask) if visible and reference for i in range(3))/(intersection*3)
    assert .97 <= area_ratio <= 1.03, f"Create comparison geometry mismatch: area ratio={area_ratio}"
    assert intersection/union > .97, f"Create comparison alignment mismatch: IoU={intersection/union}"
    assert error < 4, f"Lighting differs from actual Create: mean channel error={error}"
    report.update(createMeanRGB=expected_mean, areaRatio=area_ratio, intersectionOverUnion=intersection/union, meanAbsoluteChannelError=error)
(root / "scene-lighting-pixel-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
