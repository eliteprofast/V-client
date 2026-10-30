#!/usr/bin/env python3
"""
Ultra Smooth PvP - resource pack generator (Minecraft Java 1.21.4, pack_format 46)

Run:   python generate_pack.py
Needs: pip install pillow numpy

Output (re-run any time, everything is regenerated from scratch):
    pack/Ultra Smooth PvP/            main pack folder  (textures, models, pack.png)
    pack/Ultra Smooth PvP Shaders/    OPTIONAL shader pack folder (sky + cloud core shaders)
    dist/Ultra Smooth PvP.zip         <- drop into .minecraft/resourcepacks
    dist/Ultra Smooth PvP Shaders.zip <- optional, load ABOVE the main pack

Only client-side visuals are touched: no hitboxes, no entity sizes, no
transparency tricks that reveal hidden blocks/players (nothing here is an X-ray),
so it is fine on servers.

Every make_*() function below is labelled with the feature number from the brief.
"""
import math
import os
import shutil
import zipfile

import numpy as np
from PIL import Image, ImageDraw

# ---------------------------------------------------------------------------
# Config / paths
# ---------------------------------------------------------------------------
PACK_NAME = "Ultra Smooth PvP"
PACK_FORMAT = 46  # Minecraft 1.21.4 resource pack format
ROOT = os.path.dirname(os.path.abspath(__file__))
MAIN = os.path.join(ROOT, "pack", PACK_NAME)
SHADERS = os.path.join(ROOT, "pack", PACK_NAME + " Shaders")
DIST = os.path.join(ROOT, "dist")
T = "assets/minecraft/textures"


# ---------------------------------------------------------------------------
# Small helpers
# ---------------------------------------------------------------------------
def _path(base, rel):
    p = os.path.join(base, *rel.split("/"))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


def save_png(base, rel, arr):
    """Save an HxWx4 uint8 array as RGBA PNG."""
    Image.fromarray(np.ascontiguousarray(arr, dtype=np.uint8), "RGBA").save(_path(base, rel))


def write_text(base, rel, text):
    with open(_path(base, rel), "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def blank(w=16, h=16):
    return np.zeros((h, w, 4), np.uint8)


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


def clamp8(v):
    return int(max(0, min(255, round(v))))


def dilate(mask):
    """8-neighbour dilation of a boolean mask (used for 1px outlines)."""
    out = mask.copy()
    h, w = mask.shape
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            sh = np.zeros_like(mask)
            ys, yd = (slice(max(dy, 0), h + min(dy, 0)), slice(max(-dy, 0), h + min(-dy, 0)))
            xs, xd = (slice(max(dx, 0), w + min(dx, 0)), slice(max(-dx, 0), w + min(-dx, 0)))
            sh[yd, xd] = mask[ys, xs]
            out |= sh
    return out


def supersample(fn, size=16, ss=4):
    """Anti-aliased coverage (0..1) of a shape. fn(X, Y) -> bool array, X/Y in pixel units."""
    n = size * ss
    c = (np.arange(n) + 0.5) / ss
    X, Y = np.meshgrid(c, c)
    return fn(X, Y).astype(float).reshape(size, ss, size, ss).mean(axis=(1, 3))


# ===========================================================================
# 1. LOW FIRE  -> block/fire_0.png, block/fire_1.png (+ animation mcmeta)
#    fire_1 is the first-person screen overlay. Flames only fill the bottom
#    ~third of each frame and are translucent, so the overlay sits low and
#    you can still see. The same textures are used by fire blocks in the world.
# ===========================================================================
def make_low_fire():
    frames = 16
    for name, phase in (("fire_0", 0.0), ("fire_1", math.pi / 3)):
        sheet = np.zeros((16 * frames, 16, 4), np.uint8)
        for f in range(frames):
            t = 2 * math.pi * f / frames
            for x in range(16):
                h = 4.2 + 1.5 * math.sin(0.85 * x + t + phase) + 1.0 * math.sin(1.7 * x - 2 * t + 1.3 + phase)
                h = max(1.5, h)
                for k in range(16):  # k = pixel rows up from the bottom
                    cover = min(max(h - k, 0.0), 1.0)
                    if cover <= 0:
                        break
                    r = k / h
                    col = lerp((255, 236, 120), (255, 92, 24), r)  # yellow base -> orange tip
                    alpha = 0.62 * cover * (1 - 0.35 * r)          # translucent, fades at tip
                    sheet[f * 16 + 15 - k, x] = (*(clamp8(c) for c in col), clamp8(255 * alpha))
        save_png(MAIN, f"{T}/block/{name}.png", sheet)
        write_text(MAIN, f"{T}/block/{name}.png.mcmeta", '{\n  "animation": {\n    "frametime": 2,\n    "interpolate": true\n  }\n}\n')


# ===========================================================================
# 2. LOWERED SHIELD (first person) -> models/item/shield.json, shield_blocking.json
#    Only the first-person display transform is changed (client side look only).
#    NOTE: a semi-transparent shield is NOT possible in vanilla: the shield is
#    rendered with an opaque entity render type, so texture alpha is ignored.
# ===========================================================================
def make_shield_models():
    def model(fp_right, fp_left, extra):
        return (
            '{\n  "gui_light": "front",\n  "textures": { "particle": "block/dark_oak_planks" },\n  "display": {\n'
            + extra
            + f'    "firstperson_righthand": {{ "rotation": {fp_right[0]}, "translation": {fp_right[1]}, "scale": [ 1.1, 1.1, 1.1 ] }},\n'
            + f'    "firstperson_lefthand": {{ "rotation": {fp_left[0]}, "translation": {fp_left[1]}, "scale": [ 1.1, 1.1, 1.1 ] }}'
            + "\n  }\n}\n"
        )

    idle_extra = (
        '    "thirdperson_righthand": { "rotation": [ 0, 90, 0 ], "translation": [ 10, 6, -4 ], "scale": [ 1, 1, 1 ] },\n'
        '    "thirdperson_lefthand": { "rotation": [ 0, 90, 0 ], "translation": [ 10, 6, 12 ], "scale": [ 1, 1, 1 ] },\n'
        '    "gui": { "rotation": [ 15, -25, -5 ], "translation": [ 2, 3, 0 ], "scale": [ 0.65, 0.65, 0.65 ] },\n'
        '    "fixed": { "rotation": [ 0, 180, 0 ], "translation": [ -4.5, 4.5, -5 ], "scale": [ 0.55, 0.55, 0.55 ] },\n'
        '    "ground": { "rotation": [ 0, 0, 0 ], "translation": [ 2, 4, 2 ], "scale": [ 0.25, 0.25, 0.25 ] },\n'
    )
    block_extra = (
        '    "thirdperson_righthand": { "rotation": [ 45, 155, 0 ], "translation": [ -3.49, 11, -2 ], "scale": [ 1, 1, 1 ] },\n'
        '    "thirdperson_lefthand": { "rotation": [ 45, 155, 0 ], "translation": [ 11.51, 7, 2.5 ], "scale": [ 1, 1, 1 ] },\n'
        '    "gui": { "rotation": [ 15, -25, -5 ], "translation": [ 2, 3, 0 ], "scale": [ 0.65, 0.65, 0.65 ] },\n'
    )
    # vanilla idle y = 2 / 0, blocking y = 5 -> pushed down so it stays out of your sightline
    write_text(MAIN, "assets/minecraft/models/item/shield.json",
               model(("[ 0, 180, 5 ]", "[ -10, -4, -10 ]"), ("[ 0, 180, 5 ]", "[ 10, -6, -10 ]"), idle_extra))
    write_text(MAIN, "assets/minecraft/models/item/shield_blocking.json",
               model(("[ 0, 180, -5 ]", "[ -15, 0, -11 ]"), ("[ 0, 180, -5 ]", "[ 5, 0, -11 ]"), block_extra))


# ===========================================================================
# 3. CLEAN CROSSHAIR -> gui/sprites/hud/crosshair.png (15x15, the size the game draws)
#    Small white plus with a 1px black outline and an empty centre.
#    NOTE: vanilla draws the crosshair with an "invert" blend, so in game it
#    inverts the background colour; that is a game limitation, not the texture.
# ===========================================================================
def make_crosshair():
    white = np.zeros((15, 15), bool)
    for i in (3, 4, 5, 9, 10, 11):
        white[7, i] = True   # horizontal arms
        white[i, 7] = True   # vertical arms
    outline = dilate(white) & ~white
    img = np.zeros((15, 15, 4), np.uint8)
    img[outline] = (0, 0, 0, 255)
    img[white] = (255, 255, 255, 255)
    save_png(MAIN, f"{T}/gui/sprites/hud/crosshair.png", img)


# ===========================================================================
# 4. CLEAR GLASS (no borders) incl. stained glass + panes
#    glass.png is cutout (pixels are fully opaque or fully gone), so clear glass
#    is empty except 2 tiny glints so it stays visible. Stained glass is
#    translucent, so it gets a real alpha tint.
# ===========================================================================
DYES = {
    "white": (249, 255, 254), "orange": (249, 128, 29), "magenta": (199, 78, 189),
    "light_blue": (58, 179, 218), "yellow": (254, 216, 61), "lime": (128, 199, 31),
    "pink": (243, 139, 170), "gray": (71, 79, 82), "light_gray": (157, 157, 151),
    "cyan": (22, 156, 156), "purple": (137, 50, 184), "blue": (60, 68, 170),
    "brown": (131, 84, 50), "green": (94, 124, 22), "red": (176, 46, 38), "black": (29, 29, 33),
}


def make_glass():
    clear = blank()
    for x, y in ((2, 3), (3, 2), (3, 4), (4, 3), (4, 5), (5, 4)):  # tiny diagonal glints, top-left
        clear[y, x] = (228, 242, 250, 255)
    save_png(MAIN, f"{T}/block/glass.png", clear)
    save_png(MAIN, f"{T}/block/glass_pane_top.png", blank())
    for dye, col in DYES.items():
        tile = blank()
        tile[:, :] = (*col, 78)  # soft tint, translucent
        for x, y in ((2, 3), (3, 2), (3, 4), (4, 3), (4, 5), (5, 4)):
            tile[y, x] = (*[clamp8(c + (255 - c) * 0.55) for c in col], 150)
        save_png(MAIN, f"{T}/block/{dye}_stained_glass.png", tile)
        save_png(MAIN, f"{T}/block/{dye}_stained_glass_pane_top.png", blank())


# ===========================================================================
# 5. SHORT GRASS -> block/short_grass.png, tall_grass_bottom/top.png
#    Greyscale (the game tints it per biome). Blades only fill the lower rows.
# ===========================================================================
def _grass(heights, xs):
    img = blank()
    for x, h in zip(xs, heights):
        for k in range(h):
            y = 15 - k
            lean = 1 if (k >= h - 2 and x % 2 == 0 and x < 15) else 0
            g = clamp8(110 + 95 * (k / max(h - 1, 1)))  # darker root, lighter tip
            img[y, x + lean] = (g, g, g, 255)
            if k < h - 2:  # blades are 2px wide below the tip
                img[y, x + 1] = (max(g - 25, 0),) * 3 + (255,)
    return img


def make_short_grass():
    save_png(MAIN, f"{T}/block/short_grass.png", _grass([4, 6, 3, 7, 4, 5], [1, 4, 6, 9, 11, 14]))
    save_png(MAIN, f"{T}/block/tall_grass_bottom.png", _grass([8, 11, 6, 12, 8, 9], [1, 4, 6, 9, 11, 14]))
    save_png(MAIN, f"{T}/block/tall_grass_top.png", blank())


# ===========================================================================
# 9 + 5b. PARTICLES -> textures/particle/*.png (all 16x16)
#    Particle size on screen comes from the particle, not the image, so less
#    painted area = visibly smaller particles.
#      generic_0..7   smoke / campfire / poof puffs (smaller soft discs)
#      effect_0..7    potion swirl particles (tiny soft dots)
#      spell_0..7     splash/instant spell particles (tiny soft dots)
#      flame          flame particle (small teardrop)
#      critical_hit   crit hit particle (bright 4-point star)
#      enchanted_hit  sharpness/enchanted hit particle (white star, game tints it)
#      damage         damage indicator hearts (clean red heart)
# ===========================================================================
def _disc(radius, feather=1.5, core=255, edge=215, size=16):
    c = (size - 1) / 2.0
    ys, xs = np.mgrid[0:size, 0:size]
    d = np.hypot(xs - c, ys - c)
    a = np.clip((radius + feather * 0.5 - d) / feather, 0, 1)
    v = core + (edge - core) * np.clip(d / max(radius, 0.1), 0, 1)
    img = np.zeros((size, size, 4), np.uint8)
    img[..., :3] = np.clip(v, 0, 255).astype(np.uint8)[..., None]
    img[..., 3] = (a * 255).astype(np.uint8)
    return img


def _star(arm, thick=0.9, color=(255, 255, 255), tip=(255, 235, 140), size=16):
    c = (size - 1) / 2.0
    ys, xs = np.mgrid[0:size, 0:size]
    dx, dy = np.abs(xs - c), np.abs(ys - c)
    # two thin crossing diamonds: |dx|/arm + |dy|*k<1  (horizontal)  and the vertical twin
    along = np.maximum(dx, dy)
    across = np.minimum(dx, dy)
    a = np.clip(1.0 - across / thick, 0, 1) * np.clip(1.0 - along / arm, 0, 1)
    a = np.maximum(a, np.clip(1.0 - np.hypot(dx, dy) / (thick * 1.8), 0, 1))
    t = np.clip(along / arm, 0, 1)[..., None]
    rgb = np.array(color) * (1 - t) + np.array(tip) * t
    img = np.zeros((size, size, 4), np.uint8)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    img[..., 3] = (np.clip(a * 1.6, 0, 1) * 255).astype(np.uint8)
    return img


def make_particles():
    p = f"{T}/particle"
    radii = [4.6, 4.2, 3.8, 3.3, 2.8, 2.3, 1.8, 1.3]
    for i, r in enumerate(radii):
        save_png(MAIN, f"{p}/generic_{i}.png", _disc(r))
        save_png(MAIN, f"{p}/effect_{i}.png", _disc(max(1.0, r * 0.55), feather=1.2))
        save_png(MAIN, f"{p}/spell_{i}.png", _disc(max(1.0, r * 0.55), feather=1.2))

    # flame teardrop
    def flame(X, Y):
        w = np.clip((Y - 4) / 8.5, 0, 1) * 2.8 + 0.25  # narrow at the top, wide at the bottom
        return (np.abs(X - 8) < w) & (Y > 4) & (Y < 12.5)
    cov = supersample(flame)
    img = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        t = np.clip((y - 4) / 8.0, 0, 1)
        col = lerp((255, 120, 30), (255, 240, 140), t)
        img[y, :, :3] = [clamp8(c) for c in col]
    img[..., 3] = (cov * 255).astype(np.uint8)
    save_png(MAIN, f"{p}/flame.png", img)

    save_png(MAIN, f"{p}/critical_hit.png", _star(5.2))
    save_png(MAIN, f"{p}/enchanted_hit.png", _star(5.2, tip=(255, 255, 255)))

    # damage indicator heart (implicit heart curve, anti-aliased, red gradient + dark outline)
    def heart(X, Y, grow=0.0):
        x = (X - 8) / (6.2 + grow)
        y = -(Y - 8.6) / (6.2 + grow)
        return (x * x + y * y - 1) ** 3 - x * x * y ** 3 <= 0
    inner = supersample(heart)
    outer = supersample(lambda X, Y: heart(X, Y, 0.35))
    img = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        t = y / 15.0
        fill = lerp((255, 70, 80), (176, 14, 34), t)
        for x in range(16):
            o, i = outer[y, x], inner[y, x]
            if o <= 0:
                continue
            col = lerp((70, 4, 14), fill, i)
            img[y, x] = (*[clamp8(c) for c in col], clamp8(255 * o))
    save_png(MAIN, f"{p}/damage.png", img)


# ===========================================================================
# 6a. SWORDS -> item/{wooden,stone,iron,golden,diamond,netherite}_sword.png
#     Diagonal blade with a smooth tip-to-base gradient, a bright edge,
#     a darker spine and a 1px outline.
# ===========================================================================
SWORD_MATERIALS = {
    "wooden": ((222, 178, 112), (150, 108, 60)),
    "stone": ((196, 196, 196), (112, 112, 116)),
    "iron": ((248, 250, 252), (160, 166, 176)),
    "golden": ((255, 244, 150), (214, 150, 20)),
    "diamond": ((190, 255, 250), (36, 168, 190)),
    "netherite": ((128, 112, 124), (48, 40, 48)),
}


def make_swords():
    handle_hi, handle_lo = (140, 94, 50), (88, 56, 28)
    for name, (light, dark) in SWORD_MATERIALS.items():
        img = blank()
        body = np.zeros((16, 16), bool)
        shade = {}
        # blade runs along x+y=15 from the tip (14,1) to the base (6,9)
        for i in range(9):
            x, y = 14 - i, 1 + i
            t = i / 8.0  # 0 at tip .. 1 at base
            width = 1 if i < 2 else 2
            body[y, x] = True
            shade[(x, y)] = lerp(light, dark, 0.15 + 0.45 * t)  # bright spine core
            if width == 2:
                body[y, x - 1] = True
                shade[(x - 1, y)] = lerp(light, dark, 0.55 + 0.4 * t)  # shaded side
        # crossguard perpendicular to the blade, through (5,10)
        for k in range(-2, 3):
            x, y = 5 + k, 10 + k
            body[y, x] = True
            g = lerp(handle_hi, handle_lo, (k + 2) / 4.0) if name in ("wooden", "stone") else lerp(light, dark, 0.35 + (k + 2) / 8.0)
            shade[(x, y)] = g
        # handle + pommel
        for j, (x, y) in enumerate(((4, 11), (3, 12), (2, 13), (1, 14))):
            body[y, x] = True
            shade[(x, y)] = lerp(handle_hi, handle_lo, j / 3.0)
        outline = dilate(body) & ~body
        img[outline] = (24, 22, 28, 255)
        for (x, y), c in shade.items():
            img[y, x] = (*[clamp8(v) for v in c], 255)
        save_png(MAIN, f"{T}/item/{name}_sword.png", img)


# ===========================================================================
# 6b. ARMOR ICONS -> item/{chainmail,iron,golden,diamond,netherite}_{helmet,...}.png
#     16x16 inventory icons with a smooth vertical gradient, soft highlight on
#     the left edge, shade on the right edge, 1px outline.
# ===========================================================================
ARMOR_SHAPES = {
    "helmet": [
        "................", "................", "................",
        "....########....", "...##########...", "..############..",
        "..############..", "..############..", "..###......###..",
        "..###......###..", "..###......###..", "................",
        "................", "................", "................", "................",
    ],
    "chestplate": [
        "................", "................", ".####......####.",
        ".#####....#####.", ".##############.", ".##############.",
        ".##############.", "..############..", "..############..",
        "..############..", "..############..", "..############..",
        "..############..", "................", "................", "................",
    ],
    "leggings": [
        "................", "................", "..############..",
        "..############..", "..############..", "..############..",
        "..####....####..", "..####....####..", "..####....####..",
        "..####....####..", "..####....####..", "..####....####..",
        "................", "................", "................", "................",
    ],
    "boots": [
        "................", "................", "................",
        "................", "................", "................",
        "..####....####..", "..####....####..", "..####....####..",
        "..####....####..", ".#####....#####.", ".#####....#####.",
        ".#####....#####.", "................", "................", "................",
    ],
}
ARMOR_COLORS = {  # (icon item prefix, light, dark)
    "chainmail": ((196, 198, 206), (104, 108, 120)),
    "iron": ((244, 246, 250), (150, 156, 168)),
    "golden": ((255, 240, 140), (210, 140, 16)),
    "diamond": ((190, 255, 248), (30, 160, 184)),
    "netherite": ((126, 110, 122), (46, 38, 46)),
}


def make_armor_icons():
    for mat, (light, dark) in ARMOR_COLORS.items():
        for piece, rows in ARMOR_SHAPES.items():
            m = np.array([[c == "#" for c in r] for r in rows])
            ys = np.where(m.any(axis=1))[0]
            y0, y1 = ys.min(), ys.max()
            img = blank()
            outline = dilate(m) & ~m
            img[outline] = (24, 22, 28, 255)
            for y in range(16):
                xs = np.where(m[y])[0]
                for x in xs:
                    t = (y - y0) / max(y1 - y0, 1)
                    c = lerp(light, dark, 0.1 + 0.8 * t)
                    if x == xs.min():
                        c = lerp(c, (255, 255, 255), 0.25)  # left edge highlight
                    if x == xs.max():
                        c = lerp(c, (0, 0, 0), 0.22)        # right edge shade
                    img[y, x] = (*[clamp8(v) for v in c], 255)
            save_png(MAIN, f"{T}/item/{mat}_{piece}.png", img)


# ===========================================================================
# 6c. WORN ARMOR -> entity/equipment/humanoid[_leggings]/<material>.png (64x32)
#     The masks below are the UV silhouettes of the armor model, so only the
#     parts the game actually draws are painted. Smooth shading per 8px tile.
# ===========================================================================
# UV silhouettes of the armor model ('#' = painted). 64x32, one string per texture row.
MASK_HUMANOID = [
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "........########................................................",
    "################################................................",
    "################################................................",
    "################################................................",
    "#########..##..#################................................",
    "####.......##.......############................................",
    "........................########................................",
    "..........................####..................................",
    "................................................................",
    "........####................................####................",
    "........####................................####................",
    "........####................................####................",
    "........####................................####................",
    "................######....########....##################........",
    "................#######..###############################........",
    "................########################################........",
    "................########################################........",
    "................########################################........",
    "................########################........................",
    "########################################........................",
    "########################################........................",
    "########################################........................",
    "################.....######......######.........................",
    "################......####......................................",
    "################................................................",
]
MASK_LEGGINGS = [
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "................................................................",
    "....####........................................................",
    "....####........................................................",
    "....####........................................................",
    "....####........................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "################................................................",
    "########################################........................",
    "########################################........................",
    "................########################........................",
    "................########################........................",
    "................########################........................",
]

EQUIP_COLORS = {  # texture name -> base colour
    "chainmail": (170, 172, 182),
    "iron": (216, 220, 226),
    "gold": (250, 206, 60),
    "diamond": (78, 228, 212),
    "netherite": (84, 72, 80),
}


def make_worn_armor():
    for mat, base in EQUIP_COLORS.items():
        for folder, mask in (("humanoid", MASK_HUMANOID), ("humanoid_leggings", MASK_LEGGINGS)):
            img = blank(64, 32)
            for y in range(32):
                for x in range(64):
                    if mask[y][x] != "#":
                        continue
                    ly, lx = y % 8, x % 8
                    v = 1.10 - 0.22 * (ly / 7.0)           # light top -> darker bottom of every 8px tile
                    if lx == 0 or lx == 7:
                        v -= 0.05                           # soft seam between faces
                    if lx == 1:
                        v += 0.04                           # subtle highlight
                    img[y, x] = (*[clamp8(c * v) for c in base], 255)
            save_png(MAIN, f"{T}/entity/equipment/{folder}/{mat}.png", img)


# ===========================================================================
# 7. NO PUMPKIN BLUR -> misc/pumpkinblur.png (fully transparent)
# ===========================================================================
def make_pumpkin():
    save_png(MAIN, f"{T}/misc/pumpkinblur.png", blank())


# ===========================================================================
# 8. CLEAR, SLIGHTLY DARKER WATER
#    water_still / water_flow: greyscale (the game tints it by biome), a bit
#    darker (vanilla ~177 grey) and more transparent (vanilla alpha 180).
#    water_overlay: transparent, removes the lines between adjacent water blocks.
#    underwater: the screen overlay is very faint so you can see players.
# ===========================================================================
def make_water():
    yy, xx = np.mgrid[0:16, 0:16].astype(float)
    tau = 2 * math.pi / 16
    still = 152 + 7 * np.sin(tau * (xx + yy)) + 4 * np.sin(tau * (2 * xx - yy) + 1.0)
    flow = 148 + 8 * np.sin(tau * (yy * 1 + xx * 2)) + 3 * np.sin(tau * (yy * 2) + 0.5)
    for name, g in (("water_still", still), ("water_flow", flow)):
        img = np.zeros((16, 16, 4), np.uint8)
        img[..., :3] = np.clip(g, 0, 255).astype(np.uint8)[..., None]
        img[..., 3] = 128
        save_png(MAIN, f"{T}/block/{name}.png", img)
    save_png(MAIN, f"{T}/block/water_overlay.png", blank())
    uw = np.zeros((16, 16, 4), np.uint8)
    uw[...] = (70, 130, 220, 45)
    save_png(MAIN, f"{T}/misc/underwater.png", uw)


# ===========================================================================
# 10. SMOOTH FLUFFY CLOUDS -> environment/clouds.png (256x256, tiles seamlessly)
#     The game draws one 12-block cell per pixel and ignores partial alpha, so
#     "soft" is done with shape + shade: rounded blobs from wrapped, blurred
#     noise, with cell colour shading from white (thick centre) to pale blue-grey
#     (edges).
# ===========================================================================
def _wrapped_blur_noise(rng, size, sigma):
    noise = rng.standard_normal((size, size))
    f = np.fft.fftfreq(size)
    fx, fy = np.meshgrid(f, f)
    kernel = np.exp(-2 * (math.pi ** 2) * (sigma ** 2) * (fx ** 2 + fy ** 2))
    out = np.real(np.fft.ifft2(np.fft.fft2(noise) * kernel))
    return (out - out.mean()) / out.std()


def make_clouds():
    rng = np.random.default_rng(1214)
    field = _wrapped_blur_noise(rng, 256, 9.0) + 0.25 * _wrapped_blur_noise(rng, 256, 4.0)
    thresh = np.quantile(field, 0.58)           # ~42% of the sky covered
    inside = field > thresh
    depth = np.clip((field - thresh) / 1.2, 0, 1)  # 0 at the edge .. 1 in the thick middle
    img = np.zeros((256, 256, 4), np.uint8)
    for c, (edge, core) in enumerate(zip((226, 234, 246), (255, 255, 255))):
        img[..., c] = np.where(inside, edge + (core - edge) * depth, 0)
    img[..., 3] = np.where(inside, 255, 0)
    save_png(MAIN, f"{T}/environment/clouds.png", img)


# ===========================================================================
# 12. OPTIONAL SHADER PACK (separate zip, never inside the main pack)
#     Two tiny core-shader edits, both cheap (a few ALU ops per fragment):
#       position.vsh/.fsh             sky disc: zenith -> horizon gradient
#       rendertype_clouds.fsh         clouds: lifted shadows + soft distance fade
#     Uniform lists are unchanged, so the vanilla .json files are reused.
# ===========================================================================
POSITION_VSH = """#version 150

#moj_import <minecraft:fog.glsl>

in vec3 Position;

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;
uniform int FogShape;

out float vertexDistance;
out vec3 localPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(Position, FogShape);
    localPos = Position; // Ultra Smooth PvP: needed for the sky gradient
}
"""

POSITION_FSH = """#version 150

#moj_import <minecraft:fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec3 localPos;

out vec4 fragColor;

void main() {
    vec4 color = ColorModulator;

    // Ultra Smooth PvP: the sky disc is the flat fan at exactly y = +16 around the camera.
    // Stars and everything else using this shader are not at that height, so they are untouched.
    if (abs(localPos.y - 16.0) < 0.01) {
        float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        float t = smoothstep(0.0, 190.0, length(localPos.xz));          // 0 = zenith, 1 = horizon
        vec3 zenith = color.rgb * vec3(0.86, 0.92, 1.05);                // slightly deeper and bluer overhead
        vec3 horizon = color.rgb + (1.0 - color.rgb) * (0.20 * lum);     // paler near the horizon, stays dark at night
        color.rgb = mix(zenith, horizon, t);
    }

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
"""

CLOUDS_FSH = """#version 150

#moj_import <minecraft:fog.glsl>

uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor;

    // Ultra Smooth PvP: lift the baked-in side/bottom shading so clouds look soft and fluffy,
    // add a faint cool tint, and fade them out near the fog edge instead of a hard cut.
    color.rgb = pow(color.rgb, vec3(0.82)) * vec3(0.99, 1.0, 1.03);
    color.a *= 1.0 - smoothstep(FogEnd * 0.55, FogEnd, vertexDistance);

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
"""


def make_shader_pack():
    write_text(SHADERS, "assets/minecraft/shaders/core/position.vsh", POSITION_VSH)
    write_text(SHADERS, "assets/minecraft/shaders/core/position.fsh", POSITION_FSH)
    write_text(SHADERS, "assets/minecraft/shaders/core/rendertype_clouds.fsh", CLOUDS_FSH)
    write_text(SHADERS, "pack.mcmeta", pack_mcmeta(
        "Ultra Smooth PvP - Shaders (optional)\\nSoft sky gradient + fluffy clouds. Load above the main pack."))
    icon = make_icon(sword=False)
    icon.save(_path(SHADERS, "pack.png"))


# ===========================================================================
# pack.mcmeta + pack.png (128x128)
# ===========================================================================
def pack_mcmeta(description):
    return '{\n  "pack": {\n    "pack_format": %d,\n    "description": "%s"\n  }\n}\n' % (PACK_FORMAT, description)


def make_icon(sword=True):
    s = 4  # supersampling
    W = 128 * s
    img = Image.new("RGBA", (W, W))
    px = img.load()
    for y in range(W):  # vertical sky gradient
        t = y / W
        c = lerp((74, 150, 240), (170, 215, 255), t)
        for x in range(W):
            px[x, y] = (clamp8(c[0]), clamp8(c[1]), clamp8(c[2]), 255)
    d = ImageDraw.Draw(img)
    for cx, cy, r in ((30, 100, 16), (46, 94, 20), (64, 102, 15), (96, 98, 18), (112, 104, 12)):  # clouds
        d.ellipse([(cx - r) * s, (cy - r * 0.7) * s, (cx + r) * s, (cy + r * 0.7) * s], fill=(255, 255, 255, 235))
    if sword:
        # diamond sword, drawn diagonally
        blade = [(104, 14), (112, 22), (52, 82), (44, 74)]
        d.polygon([(x * s, y * s) for x, y in blade], fill=(120, 240, 232, 255), outline=(20, 30, 50, 255))
        d.polygon([(x * s, y * s) for x, y in [(104, 14), (112, 22), (82, 52), (74, 44)]], fill=(200, 255, 250, 255))
        d.polygon([(x * s, y * s) for x, y in [(36, 66), (44, 58), (66, 80), (58, 88)]], fill=(150, 100, 56, 255), outline=(20, 30, 50, 255))
        d.line([(50 * s, 80 * s), (22 * s, 108 * s)], fill=(110, 70, 36, 255), width=8 * s)
        d.ellipse([14 * s, 100 * s, 28 * s, 114 * s], fill=(150, 100, 56, 255), outline=(20, 30, 50, 255))
    else:
        d.ellipse([40 * s, 20 * s, 88 * s, 68 * s], fill=(255, 238, 150, 255))  # sun for the shader pack
    return img.resize((128, 128), Image.LANCZOS)


# ===========================================================================
# Build + zip
# ===========================================================================
def zip_pack(folder, zip_path):
    os.makedirs(os.path.dirname(zip_path), exist_ok=True)
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for dp, _, files in os.walk(folder):
            for f in sorted(files):
                full = os.path.join(dp, f)
                z.write(full, os.path.relpath(full, folder).replace(os.sep, "/"))


def main():
    for d in (MAIN, SHADERS):
        shutil.rmtree(d, ignore_errors=True)
    write_text(MAIN, "pack.mcmeta", pack_mcmeta("Ultra Smooth PvP\\nLow fire, clear glass, clean crosshair, soft clouds"))
    make_icon().save(_path(MAIN, "pack.png"))
    make_low_fire()
    make_shield_models()
    make_crosshair()
    make_glass()
    make_short_grass()
    make_particles()
    make_swords()
    make_armor_icons()
    make_worn_armor()
    make_pumpkin()
    make_water()
    make_clouds()
    make_shader_pack()
    zip_pack(MAIN, os.path.join(DIST, PACK_NAME + ".zip"))
    zip_pack(SHADERS, os.path.join(DIST, PACK_NAME + " Shaders.zip"))
    print("Done ->", DIST)


if __name__ == "__main__":
    main()
