"""Local mask approximations; not a substitute for Android rendering.
Requires Pillow and resvg-py in the optional artwork tools environment.
"""
from pathlib import Path
from io import BytesIO
from math import hypot
import xml.etree.ElementTree as E
from PIL import Image, ImageDraw
import resvg_py

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "dist/alfredts-phase-b-validation"
OUT.mkdir(exist_ok=True)
RES = ROOT / "app/src/main/res"
N = 432
background = Image.new("RGBA", (N, N))
for y in range(N):
    for x in range(N):
        t = min(1, hypot(x - N * .5, y - N * .65) / (72 * 4))
        background.putpixel((x,y), tuple(round(a + (b-a)*t) for a,b in zip((40,107,38),(5,36,20))) + (255,))
foreground = Image.open(RES / "drawable-xxxhdpi/ic_launcher_foreground.webp").convert("RGBA")
background.alpha_composite(foreground)
ns = "{http://schemas.android.com/apk/res/android}"
path = E.parse(RES / "drawable/ic_launcher_monochrome.xml").getroot().find("group/path").get(ns + "pathData")
svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 108 108"><g transform="translate(54 54) scale(0.9) translate(-54 -54)"><path fill="white" fill-rule="evenodd" d="{path}"/></g></svg>'
mono = Image.open(BytesIO(resvg_py.svg_to_bytes(svg_string=svg))).convert("RGBA")
# Verify the simplified monochrome contour fits Android's 66 dp diameter circle.
assert all(hypot(x-216, y-216) <= 132 for y in range(N) for x in range(N) if mono.getpixel((x,y))[3] >= 128)
# Approximate launcher viewport is the central 72 dp of the 108 dp layers.
colour = background.crop((72,72,360,360))
mono = mono.crop((72,72,360,360))
S = 288

def mask(kind):
    m = Image.new("L", (S,S))
    d = ImageDraw.Draw(m)
    if kind == "circle": d.ellipse((0,0,S-1,S-1),fill=255)
    elif kind == "rounded-square": d.rounded_rectangle((0,0,S-1,S-1),radius=S*.24,fill=255)
    else:
        for y in range(S):
            for x in range(S):
                if abs((x+.5-S/2)/(S/2))**4 + abs((y+.5-S/2)/(S/2))**4 <= 1: m.putpixel((x,y),255)
    return m

def themed(bg,fg):
    im = Image.new("RGBA",(S,S),bg)
    ink = Image.new("RGBA",(S,S),fg)
    ink.putalpha(mono.getchannel("A"))
    im.alpha_composite(ink)
    return im

variants = [("circle",colour,"circle"),("rounded-square",colour,"rounded-square"),("squircle",colour,"squircle"),
            ("monochrome-light",themed("#d7e3d4","#183b20"),"circle"),
            ("monochrome-dark",themed("#1b3020","#bcdfb5"),"rounded-square")]
canvas=Image.new("RGB",(1040,590),"#25282d")
d=ImageDraw.Draw(canvas)
for i,(name,im,kind) in enumerate(variants):
    im=im.copy(); im.putalpha(mask(kind)); im.save(OUT/(name+".png"))
    x=30+(i%3)*340; y=30+(i//3)*280
    canvas.paste(im.resize((200,200),Image.Resampling.LANCZOS),(x,y),im.resize((200,200)).getchannel("A"))
    small=im.resize((48,48),Image.Resampling.LANCZOS)
    canvas.paste(small,(x+215,y+75),small)
    d.text((x,y+210),name,fill="white")
d.text((710,340),"Header: actual 24 px artwork",fill="white")
header=Image.open(RES/"drawable-xxxhdpi/alfredts_header.webp").resize((24,24),Image.Resampling.LANCZOS)
canvas.paste(header,(710,380),header); d.text((742,386),"AlfredTS",fill="white")
d.text((30,568),"Local mask previews at 200 px and 48 px; actual Android launcher verification pending.",fill="white")
canvas.save(OUT/"icon-previews.png")
print(OUT / "icon-previews.png")
