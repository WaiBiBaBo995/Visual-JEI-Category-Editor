"""Compare actual JEI screenshots with a zero-scale control; require fluid in all five tank tiers."""
from pathlib import Path
import json
import sys
from PIL import Image, ImageFilter

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "build/ui-test"
meta = json.loads((root / "scene-snapshot-pixels.json").read_text(encoding="utf-8"))
report = {"status": "PASS", "mekanism": meta["mekanism"]}
if meta["mekanism"]:
    filled_path = max(root.glob(meta["filled"]), key=lambda p: p.stat().st_mtime)
    empty_path = max(root.glob(meta["empty"]), key=lambda p: p.stat().st_mtime)
    x, y, w, h = (round(v) for v in meta["bounds"])
    filled = Image.open(filled_path).convert("RGB").crop((x, y, x+w, y+h))
    empty = Image.open(empty_path).convert("RGB").crop((x, y, x+w, y+h))
    a, b = list(filled.get_flattened_data()), list(empty.get_flattened_data())
    report.update(filled=str(filled_path.relative_to(root)), empty=str(empty_path.relative_to(root)))
    for name, predicate in (
        ("water", lambda r, g, b: b > r*1.2 and b > g*1.15),
        ("lava", lambda r, g, b: r > 60 and r > g*1.4 and r > b*1.5),
    ):
        mask = [predicate(*p) and sum(abs(u-v) for u, v in zip(p, q)) > 45 for p, q in zip(a, b)]
        assert sum(mask) > 100, f"{name} did not visibly render in actual JEI"
        merged = Image.new("L", (w, h)); merged.putdata([255 if p else 0 for p in mask])
        pixels = list(merged.filter(ImageFilter.MaxFilter(17)).get_flattened_data())
        pending = {i for i, value in enumerate(pixels) if value}
        components = []
        while pending:
            seed = pending.pop(); queue = [seed]; size = 0
            while queue:
                p = queue.pop(); size += 1; px, py = p % w, p // w
                for nx, ny in ((px-1, py), (px+1, py), (px, py-1), (px, py+1)):
                    if 0 <= nx < w and 0 <= ny < h:
                        index = ny*w+nx
                        if index in pending:
                            pending.remove(index); queue.append(index)
            if size >= 50:
                components.append(size)
        assert len(components) == 5, f"Expected visible {name} in all five tank tiers, found {components}"
        report[name] = {"changedFluidPixels": sum(mask), "visibleTanks": len(components)}
(root / "scene-snapshot-pixel-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
