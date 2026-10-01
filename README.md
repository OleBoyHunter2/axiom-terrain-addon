# Terrain Generator for Axiom

A native **Axiom** brush that generates terrain with layered / fractal noise, ported from the
WorldEdit `//terrain` addon. It registers as a tool in Axiom's own tool menu and behaves like any
other Axiom brush:

- **Right-click** places an anchored region.
- Tweak **noise type / height / scale / octaves / roughness / block / footprint** in Axiom's
  ImGui options panel and the **ghost-block preview updates live** (it is rebuilt whenever a
  parameter changes; no world edit is committed).
- **Enter** commits the previewed blocks **undoably** through Axiom's history system.
- **Delete** (or re-selecting the tool) **clears the preview** with no world change.

Targets **Minecraft 1.21.10, Fabric, Yarn mappings, Java 21**, addon API versions verified
against the actual Axiom 5.4.2 build for 1.21.10.

---

## Files

| Path | Purpose |
| --- | --- |
| `build.gradle` / `gradle.properties` | Loom build pinned to 1.21.10 + Axiom 5.4.2 / addon-api 1.0.87 |
| `src/main/resources/fabric.mod.json` | Mod metadata (client-side, `recommends: axiom`) |
| `src/client/.../client/TerrainAddonClient.java` | Fabric client initializer |
| `src/client/.../client/AxiomIntegration.java` | Discovers Axiom's addon services via `ServiceLoader` and registers the tool |
| `src/client/.../client/terrain/NoiseType.java` | Noise patterns (PERLIN, SIMPLEX, RIDGE, FLAT, CELLULAR) |
| `src/client/.../client/terrain/TerrainGenerator.java` | **Standalone, reusable noise/heightmap class** (pure Java, ported verbatim) |
| `src/client/.../client/terrain/TerrainSettings.java` | Live-editable settings + ImGui panel rendering |
| `src/client/.../client/terrain/BlockChoices.java` | Block picker palette (dropdown) |
| `src/client/.../client/terrain/TerrainTool.java` | The `CustomTool`: live ghost preview + confirm/cancel |

---

## The exact dependency

Axiom is **closed-source and not published to any public Maven repository** (a JitPack pull of
`com.github.Moulberry:Axiom` returns HTTP 401 / private), so it is resolved from local jars in
`libs/` via `flatDir` instead. In `build.gradle`:

```groovy
repositories {
    flatDir { dirs 'libs' }
}

dependencies {
    minecraft "com.mojang:minecraft:1.21.10"
    mappings "net.fabricmc:yarn:1.21.10+build.3:v2"
    modImplementation "net.fabricmc:fabric-loader:0.19.3"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.138.4+1.21.10"

    // Axiom (compile against + dev runtime only, never bundled).
    modCompileOnly "axiom:axiomclientapi:1.0.87"   // nested addon-api inside Axiom 5.4.2
    modCompileOnly "axiom:axiom:5.4.2"             // the mod jar (provides the ImGui bindings)
    modLocalRuntime "axiom:axiom:5.4.2"
}
```

**Obtain the jars** (they are `All Rights Reserved`, so not committed here) — see `libs/README.md`:

1. Download **Axiom 5.4.2 for MC 1.21.10** (`Axiom-5.4.2-for-MC1.21.10.jar`) from Modrinth /
   axiom.moulberry.com and place it as `libs/axiom-5.4.2.jar`.
2. Extract its nested addon-api: `unzip -j libs/axiom-5.4.2.jar "META-INF/jars/axiomclientapi.jar" -d libs`
   and name it `libs/axiomclientapi-1.0.87.jar`.

The `axiomclientapi` jar is the small, supported API. The full `axiom` jar is an additional
`modCompileOnly` because Axiom's bundled ImGui bindings (`imgui.moulberry92.*`) live in the main
jar, not in the addon-api jar.

## Build & run

No Gradle wrapper is checked in (matching the working `mountools-for-axiom` reference):

```bash
gradle build       # compile and package
gradle runClient   # dev client with Axiom + this addon loaded
```

---

## Honest API-model notes (Axiom 5.4.2 / MC 1.21.10)

You asked to confirm the current addon entrypoint and to reuse Axiom's ghost-rendering,
edit/history, and settings-panel systems. Here is exactly what is and is not public.

- **Module registration.** Axiom's *internal* module system is

  `com.moulberry.axiom.AxiomAddon` (+ `com.moulberry.axiom.AxiomModRegistration`,

  `AxiomEventBus`, `@AxiomScreenConstructor`) in the main jar. It is **not a public, supported
  third-party entrypoint** on 1.21.10, is absent from the addon-api jar, and is version-fragile.
  The **supported** third-party entrypoint is the `ServiceLoader`-based provider ecosystem in

  `com.moulberry.axiomclientapi` (bundled in Axiom): `ToolRegistryService.register(CustomTool)`.
  This addon uses that, and it is what real 1.21.x Axiom addons (e.g. `mountools-for-axiom`) use.

- **Settings panel.** Axiom's internal `AxiomSettingsPanel` / `SettingsGroupRegistry` are internal.
  The public, supported panel system for *tools* is `CustomTool.displayImguiOptions()`, rendered
  inside Axiom's options panel with Axiom's bundled ImGui (`imgui.moulberry92.*`), which is what
  this addon's panel uses (noise-type dropdown, sliders, block-picker combo).

- **Ghost preview.** The public preview pipeline is `RegionProvider.createBlock()/createBoolean()`
  returning regions that Axiom renders as a translucent ghost overlay in
  `BooleanRegion.render(camera, ..., effects)` (this addon uses `Effects.SELECTION`, a confirmed
  translucence+outline flag). These regions are backed by Axiom's own ghost-block renderer — no
  renderer was written from scratch. (Axiom's internal `GhostBlock`/`PreviewWidget` classes exist
  but are not public API.)

- **Edit / history.** `ToolService.pushBlockRegionChange(BlockRegion)` commits through Axiom's
  edit/history stack (undoable), so confirm is undoable. (The internal `Edit`/`History`/
  `Session.addClientEdit` classes exist but are not public API.)

- **Selection.** Axiom's public addon API exposes **no** read-only "current selection". The
  WorldEdit `CuboidRegion` selection therefore maps to an **anchor-centred, configurable
  `width × depth × height` box** (`anchor` from a raycast). This is the only faithful mapping the
  public API allows; internal selection widgets exist but are not exposed.

I verified every signature above against the actual `META-INF/jars/axiomclientapi.jar` extracted
from `Axiom-5.4.2-for-MC1.21.10.jar` (intermediary names below differ from the Yarn names used
in source; Loom remaps them):

```
CustomTool        render(Camera, float, long, MatrixStack, Matrix4f)   // class_4184/class_4587
Effects           SELECTION = BLUE|RED|OUTLINE (=7)
BooleanRegion     render(Camera, Vec3d, MatrixStack, Matrix4f, long, int effects); add(x,y,z); close()
BlockRegion       addBlock(x,y,z,BlockState); isEmpty(); count()
ToolService       raycastBlock(); pushBlockRegionChange(BlockRegion); getActiveBlock()
RegionProvider    createBlock(); createBoolean()
ToolRegistryService register(CustomTool)
ImGui (Imgui.moulberry92)  combo(String, ImInt, String[]); sliderInt(String,int[],int,int);
                            sliderFloat(String,float[],float,float); button(String); textWrapped(String)
```

---

## WorldEdit → Axiom mapping

| `//terrain` arg | This brush |
| --- | --- |
| `<noise type>` PERLIN / SIMPLEX / RIDGE / FLAT / CELLULAR | "Noise Type" dropdown (RIDGE shown as RIDGED) |
| `<height>` | "Height" slider (amplitude = vertical region span) |
| `<scale>` | "Scale" slider (coordinate divisor before sampling) |
| `<octaves>` | "Octaves" slider (fBm octave count) |
| `<roughness>` | "Roughness" slider (per-octave amplitude falloff / fBm gain) |
| `<block>` | "Block" picker dropdown |
| (selection `CuboidRegion`) | Region footprint sliders around the raycast anchor |

The original generator's exact math is preserved in `TerrainGenerator` (fade/lerp/grad gradient
hashing, the simplex skew `0.366025` warp, ridged `1 - |2·perlin|`, cellular feature distance,
and fBm with lacunarity `2.0` + gain `= roughness`, remapped `(n+1)/2` and clamped) so previews
match the WorldEdit output for a given seed.

## License

MIT (this addon). **Axiom** itself is a separate closed-source mod by Moulberry and is not
distributed with or embedded in this project.