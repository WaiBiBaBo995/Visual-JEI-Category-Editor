"""Package the current Gradle build and editable project, without caches or game data."""
from pathlib import Path
from hashlib import sha256
from zipfile import ZipFile, ZIP_DEFLATED
import re
import shutil

root = Path(__file__).resolve().parents[1]
version = re.search(r"^mod_version=(.+)$", (root / "gradle.properties").read_text(), re.M)[1].strip()
dist = root / "dist"
dist.mkdir(exist_ok=True)
artifacts = []
for suffix in (".jar", "-sources.jar"):
    name = f"vjce-1.21.1-{version}{suffix}"
    shutil.copy2(root / "build/libs" / name, dist / name)
    artifacts.append(dist / name)

with ZipFile(artifacts[0]) as jar:
    assert b'Visual JEI Category Editor' in jar.read("META-INF/neoforge.mods.toml")
    assert f'version="{version}"'.encode() in jar.read("META-INF/neoforge.mods.toml")
    assert "assets/vjce/lss/recipe.lss" in jar.namelist()
    for name in ("client/SceneBlockImports", "client/RecipeDataView", "client/SceneEditingView", "client/SceneAssetResource", "client/SceneAssets", "client/SceneTransforms", "client/EditorForms", "client/HoverNumberField", "client/SceneRenderGuard", "client/SceneRenderState", "mixin/SceneBlockEntityMixin"):
        assert f"com/pureneko/vjce/{name}.class" in jar.namelist()
    for name in ("client/WorldSceneCapture", "client/WorldSelectionDialog", "client/RecipeSplitView", "client/SceneLighting", "client/SceneBlockModels", "mixin/ShaderLightsAccess", "mixin/SceneBlockModelsMixin", "client/SceneRenderer", "client/SceneEntities", "mixin/EntityDispatcherAccess", "mixin/SceneAnimationMixin", "mixin/ShaderSamplerAccess", "mixin/SceneVisualLayerMixin", "client/SceneResources", "client/BlockSceneElement", "client/CreateAnimationElement", "client/IngredientPicker", "client/CanvasGrouping", "integration/NativeDrawing", "integration/MekElements", "integration/CreateAnimation", "mixin/GuiDrawingMixin", "mixin/MekCategoryMixin", "mixin/MekChildMixin", "mixin/CreateDrawingMixin", "mixin/PonderClockMixin", "client/NativePartElement", "client/RecipeCanvasSelection", "client/RecipeCanvasTransform", "client/CanvasModalGuard", "client/VjceEditorSettings", "integration/NativePage", "integration/NativeSession", "integration/ElementTransform", "integration/NativeSlotTransforms", "integration/ItemPresentationCodec", "data/ProjectStore", "mixin/RecipeLayoutAccess", "mixin/TextWidgetAccess", "mixin/RecipeSlotMixin", "mixin/CanvasKeyboardMixin"):
        assert f"com/pureneko/vjce/{name}.class" in jar.namelist()

project = dist / f"Visual-JEI-Category-Editor-{version}-project.zip"
with ZipFile(project, "w", ZIP_DEFLATED) as archive:
    for name in (".gitignore", "LICENSE", "README.md", "build.gradle", "gradle.properties", "settings.gradle", "gradlew", "gradlew.bat"):
        archive.write(root / name, name)
    for name in ("COPYING", "NOTICE.md"):
        if (root / name).is_file():
            archive.write(root / name, name)
    archive.write(root / "docs/GUIDE.zh-CN.md", "docs/GUIDE.zh-CN.md")
    for directory in ("gradle", "src", "examples", "qa", "docs/screenshots"):
        for path in sorted((root / directory).rglob("*")):
            if path.is_file() and "__pycache__" not in path.parts:
                archive.write(path, path.relative_to(root))
artifacts.append(project)

example = dist / "vjce-example-modpack.zip"
with ZipFile(example, "w", ZIP_DEFLATED) as archive:
    for path in sorted((root / "examples").rglob("*")):
        if path.is_file():
            archive.write(path, path.relative_to(root / "examples"))
artifacts.append(example)

(dist / f"sha256sums-{version}.txt").write_text("".join(f"{sha256(path.read_bytes()).hexdigest()}  {path.name}\n" for path in artifacts), encoding="utf-8")
for path in artifacts:
    print(f"{path.name}: {path.stat().st_size:,} bytes")
