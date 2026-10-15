#!/usr/bin/env python3
"""Generates the Rich Presence art assets of the Discord application (docs/assets/*.png).

All three look like the app's launcher icon and Poweramp's own round icon: a dark navy disc with a periwinkle
(#9DB9FF) rim and a soft glow that fades out to a transparent background, with a white symbol on it:

    poweramp.png  512x512  logo: a speech bubble with three equalizer bars cut out of it - the glyph of the launcher
                           icon (app/src/main/res/drawable/ic_launcher_foreground.xml) at the same size relative to
                           the disc (an original drawing, not Poweramp's trademark logo)
    play.png      512x512  white play triangle
    pause.png     512x512  white pause bars

Standard library only (zlib + struct), so it runs anywhere Python 3 does:

    python3 docs/assets/make_assets.py [output folder]

Every shape is a signed distance function (negative = inside, in pixels). Pixels far away from an edge are
filled at once; pixels near an edge are supersampled (SS x SS samples) - that is the anti-aliasing.
"""

import math
import os
import struct
import sys
import zlib

SIZE = 512
SS = 4  # samples per axis for edge pixels
C = SIZE / 2

WHITE = (0xFF, 0xFF, 0xFF)
PERIWINKLE = (0x9D, 0xB9, 0xFF)

# The disc: its radius stands for the launcher icon's 30.6 dp, so 1 dp of the 108 dp icon layers is K pixels here.
DISC_R = 221
K = DISC_R / 30.6
# Rim and glow, as in Poweramp's raster icon: a ring of the rim color at 75%, RIM px wide, right outside the disc,
# then the glow fades out to nothing over GLOW px.
RIM = 3.5
GLOW = 21
RIM_ALPHA = 0.75
# The disc's radial gradient, the same as in ic_launcher_background.xml: centered at (54, 40) dp, radius 46 dp -
# almost black in the upper middle, lighter navy towards the edge and the bottom.
DISC_STOPS = (
    (0.0, (0x0C, 0x0C, 0x10)),
    (0.4, (0x0D, 0x0E, 0x16)),
    (0.75, (0x14, 0x16, 0x20)),
    (1.0, (0x1F, 0x22, 0x2D)),
)
GRADIENT_CY = C + (40 - 54) * K
GRADIENT_R = 46 * K


# --- signed distance functions (after Inigo Quilez, iquilezles.org/articles/distfunctions2d) -----------------

def sd_circle(x, y, cx, cy, r):
    return math.hypot(x - cx, y - cy) - r


def sd_round_box(x, y, x0, y0, x1, y1, r):
    """Rectangle x0..x1 / y0..y1 with rounded corners: r is one radius or (top left, top right, bottom right,
    bottom left)."""
    px, py = x - (x0 + x1) / 2, y - (y0 + y1) / 2
    if not isinstance(r, (int, float)):
        r = (r[0] if px < 0 else r[1]) if py < 0 else (r[3] if px < 0 else r[2])
    qx = abs(px) - (x1 - x0) / 2 + r
    qy = abs(py) - (y1 - y0) / 2 + r
    return math.hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - r


def sd_polygon(x, y, pts):
    """Exact distance to a closed polygon (any winding, even-odd inside test)."""
    d = math.inf
    inside = False
    ax, ay = pts[-1]
    for bx, by in pts:
        ex, ey = bx - ax, by - ay
        wx, wy = x - ax, y - ay
        t = max(0.0, min(1.0, (wx * ex + wy * ey) / (ex * ex + ey * ey)))
        dx, dy = wx - ex * t, wy - ey * t
        d = min(d, dx * dx + dy * dy)
        if (ay > y) != (by > y) and x < ax + (y - ay) * ex / ey:
            inside = not inside
        ax, ay = bx, by
    d = math.sqrt(d)
    return -d if inside else d


def quad_points(p0, p1, p2, steps=8):
    """Points of the quadratic Bezier curve p0 -> p2 (control point p1), without p0."""
    pts = []
    for i in range(1, steps + 1):
        t = i / steps
        a, b, c = (1 - t) ** 2, 2 * (1 - t) * t, t * t
        pts.append((a * p0[0] + b * p1[0] + c * p2[0], a * p0[1] + b * p1[1] + c * p2[1]))
    return pts


# --- rendering -------------------------------------------------------------------------------------------------

def coverage(sdf, x, y):
    """Share of the pixel (x, y) covered by the shape: 0..1."""
    d = sdf(x + 0.5, y + 0.5)
    if d >= 1.0:   # the pixel's half diagonal is ~0.71, so this pixel is surely outside / inside
        return 0.0
    if d <= -1.0:
        return 1.0
    hits = 0
    for sy in range(SS):
        for sx in range(SS):
            if sdf(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS) <= 0:
                hits += 1
    return hits / (SS * SS)


def render(layers):
    """layers: list of (sdf, paint(x, y) -> (r, g, b, alpha 0..1)), bottom first. Returns RGBA rows (bytes per row)."""
    rows = []
    for y in range(SIZE):
        row = bytearray()
        for x in range(SIZE):
            # premultiplied "over" compositing
            r = g = b = a = 0.0
            for sdf, paint in layers:
                cov = coverage(sdf, x, y)
                if cov <= 0:
                    continue
                cr, cg, cb, ca = paint(x + 0.5, y + 0.5)
                cov *= ca
                r = cr * cov + r * (1 - cov)
                g = cg * cov + g * (1 - cov)
                b = cb * cov + b * (1 - cov)
                a = cov + a * (1 - cov)
            if a <= 0:
                row += b"\0\0\0\0"
            else:
                row += bytes((round(r / a), round(g / a), round(b / a), round(a * 255)))
        rows.append(bytes(row))
    return rows


def write_png(path, rows):
    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    # filter type 2 (Up) for every row: the smooth gradients compress far better as differences to the row above
    raw = bytearray()
    prev = bytes(len(rows[0]))
    for row in rows:
        raw += b"\2" + bytes((v - p) & 0xFF for v, p in zip(row, prev))
        prev = row
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))  # 8 bit RGBA
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


# --- the badge: glow, rim and disc -----------------------------------------------------------------------------

def glow_circle(x, y):
    return sd_circle(x, y, C, C, DISC_R + RIM + GLOW)


def glow_paint(x, y):
    # the rim color at RIM_ALPHA under the disc and just outside it, then fading out quadratically
    t = max(0.0, math.hypot(x - C, y - C) - DISC_R - RIM) / GLOW
    return PERIWINKLE + (RIM_ALPHA * max(0.0, 1 - t) ** 2,)


def disc(x, y):
    return sd_circle(x, y, C, C, DISC_R)


def disc_paint(x, y):
    t = min(1.0, math.hypot(x - C, y - GRADIENT_CY) / GRADIENT_R)
    for (t0, c0), (t1, c1) in zip(DISC_STOPS, DISC_STOPS[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0)
            return tuple(c0[i] + (c1[i] - c0[i]) * f for i in range(3)) + (1.0,)
    return DISC_STOPS[-1][1] + (1.0,)


def white(x, y):
    return WHITE + (1.0,)


# --- the symbols -----------------------------------------------------------------------------------------------

# The logo, in dp of the 108 dp launcher layer (ic_launcher_foreground.xml): the bubble's body with its corners, the
# tail from the bottom edge down to the rounded tip, and the three bars (rounded more at the top) cut out of it.
_BODY = (40.6, 42.0, 69.4, 64.0)
_BODY_CORNERS = (6.5, 6.5, 6.5, 4.0)
_TAIL = [(53.4, 64.0), (42.6, 71.3)] + quad_points((42.6, 71.3), (41.6, 72.0), (42.0, 70.9)) + [(44.6, 64.0)]
_BARS = ((44.4, 51.0, 49.2, 60.2), (52.6, 45.8, 57.4, 60.2), (60.8, 48.6, 65.6, 60.2))
_BAR_CORNERS = (1.6, 1.6, 0.8, 0.8)
_LOGO_BOX = (40.6, 42.0, 69.4, 72.0)


def logo_symbol(x, y):
    # pixels -> dp, centered on the disc as in the launcher icon (54, 54 = the disc's center)
    u = 54 + (x - C) / K
    v = 54 + (y - C) / K
    outside = sd_round_box(u, v, *_LOGO_BOX, 0)
    if outside * K > 2:  # far from the symbol: the distance to its bounds is close enough
        return outside * K
    bubble = min(sd_round_box(u, v, *_BODY, _BODY_CORNERS), sd_polygon(u, v, _TAIL))
    bars = min(sd_round_box(u, v, *bar, _BAR_CORNERS) for bar in _BARS)
    return max(bubble, -bars) * K


# Play: an equilateral triangle about as big as Poweramp's. Optical center: centered by its bounding box it looks
# too far left, centered by its centroid a bit too far right - so it goes 80% of the way to the centroid. Its
# straight side falls on a pixel edge, so it stays crisp.
_PLAY_H = 0.887 * DISC_R
_PLAY_W = _PLAY_H * math.sqrt(3) / 2
_PLAY_LEFT = round(C - _PLAY_W / 2 + 0.8 * _PLAY_W / 6)
_PLAY = [(_PLAY_LEFT, C - _PLAY_H / 2), (_PLAY_LEFT + _PLAY_W, C), (_PLAY_LEFT, C + _PLAY_H / 2)]


def play_symbol(x, y):
    return sd_polygon(x, y, _PLAY)


# Pause: two bars with round ends, about as tall as the triangle. Width and gap in even pixels, so the straight
# sides fall on pixel edges.
_PAUSE_H = 0.85 * DISC_R
_PAUSE_W = 36
_PAUSE_GAP = 56


def pause_symbol(x, y):
    off = _PAUSE_GAP / 2 + _PAUSE_W / 2
    hw, hh = _PAUSE_W / 2, _PAUSE_H / 2  # half sizes; the ends are half circles
    return min(sd_round_box(x, y, cx - hw, C - hh, cx + hw, C + hh, hw) for cx in (C - off, C + off))


BADGE = [(glow_circle, glow_paint), (disc, disc_paint)]

IMAGES = {
    "poweramp.png": BADGE + [(logo_symbol, white)],
    "play.png": BADGE + [(play_symbol, white)],
    "pause.png": BADGE + [(pause_symbol, white)],
}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
    os.makedirs(out, exist_ok=True)
    for name, layers in IMAGES.items():
        path = os.path.join(out, name)
        write_png(path, render(layers))
        print(f"{path}: {SIZE}x{SIZE}, {os.path.getsize(path)} bytes")


if __name__ == "__main__":
    main()
