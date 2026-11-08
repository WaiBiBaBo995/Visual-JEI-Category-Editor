"""Verify rendered area AND direction, so a correct angle property cannot hide a broken image.

Run after the vjce_scene_rotation UI scenario. Requires Pillow.
"""
from pathlib import Path
import json
import math
import sys
from PIL import Image

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "build/ui-test"
metadata = json.loads((root / "scene-rotation-pixels.json").read_text(encoding="utf-8"))
path = max(root.glob(metadata["imagePattern"]), key=lambda p: p.stat().st_mtime)
image = Image.open(path).convert("RGB")
results = []
for scene in metadata["scenes"]:
    cx, cy = scene["center"]
    # The fixture fits in this central circle; exclude the world sky and labels outside the panel.
    radius = scene["radius"] * .6
    points = {name: [] for name in ("red", "gold", "blue")}
    for y in range(max(0, int(cy-radius)), min(image.height, math.ceil(cy+radius))):
        for x in range(max(0, int(cx-radius)), min(image.width, math.ceil(cx+radius))):
            if (x-cx)**2 + (y-cy)**2 > radius**2:
                continue
            r, g, b = image.getpixel((x, y))
            if r > 65 and r > g*1.6 and r > b*1.6:
                points["red"].append((x, y))
            elif r > 70 and g > 65 and b < min(r, g)*.8 and r < g*1.8:
                points["gold"].append((x, y))
            elif b > 45 and b > r*1.5 and b > g*1.3:
                points["blue"].append((x, y))
    counts = {name: len(p) for name, p in points.items()}
    assert all(n > 200 for n in counts.values()), f"Missing or clipped scene at {scene['angle']} degrees: {counts}"
    def center(name):
        p = points[name]
        return tuple(sum(v[i] for v in p)/len(p) for i in range(2))
    gold, blue = center("gold"), center("blue")
    direction = math.degrees(math.atan2(blue[1]-gold[1], blue[0]-gold[0]))
    if results:
        base = results[0]
        ratios = {name: counts[name]/base["pixels"][name] for name in counts}
        assert all(.9 <= n <= 1.1 for n in ratios.values()), f"Rotation changed rendered area at {scene['angle']} degrees: {ratios}"
        angle_error = (direction-base["direction"]-scene["angle"]+180) % 360-180
        assert abs(angle_error) <= 3, f"Image direction does not match rotation: {scene['angle']} degrees, error {angle_error}"
    else:
        ratios = {name: 1.0 for name in counts}
    results.append({"angle": scene["angle"], "pixels": counts, "areaRatios": ratios, "direction": direction})
report = {"status": "PASS", "image": str(path.relative_to(root)), "scenes": results}
nested_path = root / "nested-scene-pixels.json"
if nested_path.exists():
    nested = json.loads(nested_path.read_text(encoding="utf-8"))
    shot = max(root.glob(nested["imagePattern"]), key=lambda p: p.stat().st_mtime)
    crop = Image.open(shot).convert("RGB").crop(tuple(round(v) for v in nested["bounds"]))
    red = sum(r > 75 and r > g*1.6 and r > b*1.6 for r, g, b in crop.get_flattened_data())
    blue = sum(b > 65 and b > r*1.5 and b > g*1.3 for r, g, b in crop.get_flattened_data())
    assert red > 300 and blue > 300, f"Scene missing from translucent parent: red={red}, blue={blue}"
    report["nestedClipAndOpacity"] = {"image": str(shot.relative_to(root)), "redPixels": red, "bluePixels": blue}
(root / "scene-rotation-pixel-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
