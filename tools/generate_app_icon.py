#!/usr/bin/env python3
"""Regenerate the Hi Player vector icon family; no APK build or dependencies.

A small-size redraw of Khan's supplied teal pixel-play artwork. The 48-unit
master deliberately consolidates subpixel fragments into >=2-unit squares.
Android and SVG use the same paths, gradient and optical padding.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
OUTLINE = ('M18,6 Q19.5,5.7 21,6.7 L42,19.8 Q47,22.8 42,26 '
           'L21,40.9 Q19.5,42 18,41 V38 H16 V35 H18 V32 H16 V29 '
           'H18 V26 H16 V23 H18 V20 H16 V17 H18 V14 H16 V11 H18 Z')
HOLE = 'M25,17 L35,23.5 L25,30 Z'
# x, y, side, color: fewer, larger fragments survive small-size rasterization.
PIXELS = [(11,5,2,'#32C9DB'),(6,11,2,'#087586'),(11,12,2,'#20BDD0'),
          (9,16,2,'#086A78'),(5,20,3,'#55DAE5'),(12,17,3,'#087F8E'),
          (3,25,2,'#2AC8D9'),(9,26,2,'#087B8C'),(12,23,3,'#096B79'),
          (6,29,3,'#21BDCF'),(12,29,3,'#17ABBF'),(4,34,2,'#49D3E0'),
          (9,35,3,'#128F9F'),(12,40,2,'#29C1D1'),(16,41,2,'#22B5C7'),
          (18,14,3,'#2AB6C7'),(21,17,2,'#27BDCE'),(18,23,3,'#51D4DF'),
          (21,26,2,'#067B8C'),(18,29,3,'#55DCE5'),(18,35,3,'#39C8D7')]
COLORS = [('#076C7A',0),('#10ACC4',.56),('#63DFE9',1)]

def rect(x,y,s):return f'M{x},{y}h{s}v{s}h-{s}z'
def write(path,text):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(text+'\n')

def vector(*, adaptive=False, mono=False, tile=False, round_tile=False):
    viewport=108 if adaptive else 48
    size=108 if adaptive else (24 if mono else 48)
    parts=['<?xml version="1.0" encoding="utf-8"?>',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"',
           f'    android:width="{size}dp" android:height="{size}dp"',
           f'    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">']
    if tile:
        bg=('M24,0 A24,24 0,1 1,24,48 A24,24 0,1 1,24,0Z' if round_tile else
            'M10,0 H38 Q48,0 48,10 V38 Q48,48 38,48 H10 Q0,48 0,38 V10 Q0,0 10,0Z')
        parts.append(f'    <path android:fillColor="#FFFFFF" android:pathData="{bg}" />')
        parts.append('    <group android:scaleX="0.86" android:scaleY="0.86" android:translateX="3.36" android:translateY="3.36">')
    if adaptive:
        # All visible geometry fits inside Android's central 66dp safe circle.
        parts.append('    <group android:scaleX="1.35" android:scaleY="1.35" android:translateX="21.6" android:translateY="21.6">')
    if mono:
        parts.append(f'    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{OUTLINE} {HOLE}" />')
        # Large identifying fragments only; status-bar icons must be alpha-only.
        for x,y,s,_ in PIXELS[:15]:
            if s>=3:parts.append(f'    <path android:fillColor="#FFFFFFFF" android:pathData="{rect(x,y,s)}" />')
    else:
        parts.append(f'    <path android:fillType="evenOdd" android:pathData="{OUTLINE} {HOLE}">')
        parts.extend(['        <aapt:attr name="android:fillColor">',
                      '            <gradient android:type="linear" android:startX="13" android:startY="19" android:endX="45" android:endY="27">'])
        for color,offset in COLORS:parts.append(f'                <item android:offset="{offset}" android:color="{color}" />')
        parts.extend(['            </gradient>','        </aapt:attr>','    </path>'])
        for x,y,s,c in PIXELS:parts.append(f'    <path android:fillColor="{c}" android:pathData="{rect(x,y,s)}" />')
    if adaptive or tile:parts.append('    </group>')
    parts.append('</vector>')
    return '\n'.join(parts)

def svg():
    parts=['<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 48 48">',
           '<defs><linearGradient id="teal" gradientUnits="userSpaceOnUse" x1="13" y1="19" x2="45" y2="27">']
    for c,o in COLORS:parts.append(f'<stop offset="{o}" stop-color="{c}"/>')
    parts.extend(['</linearGradient></defs>',f'<path fill="url(#teal)" fill-rule="evenodd" d="{OUTLINE} {HOLE}"/>'])
    for x,y,s,c in PIXELS:parts.append(f'<path fill="{c}" d="{rect(x,y,s)}"/>')
    return '\n'.join(parts+['</svg>'])

write(RES/'drawable/hi_player_brand_mark.xml',vector())
write(RES/'drawable/ic_launcher_foreground.xml',vector(adaptive=True))
write(RES/'drawable/hi_player_monochrome.xml',vector(adaptive=True,mono=True))
write(RES/'drawable/hi_player_notification.xml',vector(mono=True))
write(RES/'drawable/ic_launcher_background.xml','''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#FFFFFF" />
</shape>''')
for suffix in ('','_round'):
    write(RES/f'mipmap-anydpi/ic_launcher{suffix}.xml',vector(tile=True,round_tile=bool(suffix)))
    adaptive='''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />'''
    write(RES/f'mipmap-anydpi-v26/ic_launcher{suffix}.xml',adaptive+'\n</adaptive-icon>')
    write(RES/f'mipmap-anydpi-v33/ic_launcher{suffix}.xml',adaptive+'''
    <monochrome android:drawable="@drawable/hi_player_monochrome" />
</adaptive-icon>''')
write(ROOT/'docs/branding/hi-player-icon.svg',svg())
print('Generated shared SVG and Android vector icon family.')
