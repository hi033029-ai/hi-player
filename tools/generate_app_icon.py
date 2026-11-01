#!/usr/bin/env python3
"""Generate Pixel Play SVG and Android vectors; no Gradle or APK tasks.

Small-size vector adaptation of the first AI-generated concept selected by Khan.
One 48-unit master: rounded ribbon, transparent aperture, five substantial pixels.
"""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
OUTLINE = ('M14,10 C14,6.5 17.5,5 20.5,6.8 L41,19.8 '
           'C44.5,21.8 44.5,26.2 41,28.2 L20.5,41.2 '
           'C17.5,43 14,41.5 14,38 V33 H17 V27 H14 V19 H17 V16 H14 Z')
HOLE = 'M21,18 C21,16.6 21.8,16.2 23,17 L32.3,22.8 Q34,24 32.3,25.2 L23,31 C21.8,31.8 21,31.4 21,30 Z'
PIXELS = [(10.5,12.5,3,'#098F9C'),(5.5,15.5,3,'#087F8C'),
          (8.5,18.5,4,'#07909B'),(10.5,27.5,3.5,'#087D89'),(7.5,31,3,'#087B87')]
FOLD = 'M14,33 Q16,36 20.5,33 L29,27.7 Q33,25.2 36,28 Q38,31 33,34.3 L20.5,41.2 C17.5,43 14,41.5 14,38 Z'
UPPER = 'M21,18 C21,11 25.5,9 30.5,12.9 L41,19.8 C44.5,21.8 44.5,26.2 41,28.2 L32.3,22.8 L23,17 C21.8,16.2 21,16.6 21,18 Z'
COLORS = [('#047482',0),('#099EAD',.5),('#22C7D0',1)]
def rect(x,y,s): return f'M{x},{y}h{s}v{s}h-{s}z'
def write(path,text):
    path.parent.mkdir(parents=True,exist_ok=True); path.write_text(text+'\n')
def vector(adaptive=False,mono=False,tile=False,round_tile=False):
    vp=108 if adaptive else 48; size=108 if adaptive else (24 if mono else 48)
    p=['<?xml version="1.0" encoding="utf-8"?>','<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"',f'    android:width="{size}dp" android:height="{size}dp" android:viewportWidth="{vp}" android:viewportHeight="{vp}">']
    if tile:
        bg='M24,0 A24,24 0,1 1,24,48 A24,24 0,1 1,24,0Z' if round_tile else 'M10,0 H38 Q48,0 48,10 V38 Q48,48 38,48 H10 Q0,48 0,38 V10 Q0,0 10,0Z'
        p += [f'    <path android:fillColor="#FFFFFF" android:pathData="{bg}" />']
    # The full silhouette bounds (5.5..43.625, 6..42) are centered, not just the triangle.
    # Adaptive scale 1.4 puts every path inside the central 66dp safe circle.
    scale=1.4 if adaptive else (.88 if tile else 1)
    tx=(vp/2)-24.5625*scale; ty=(vp/2)-24*scale
    p += [f'    <group android:scaleX="{scale}" android:scaleY="{scale}" android:translateX="{tx}" android:translateY="{ty}">']
    if mono:
        p += [f'        <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{OUTLINE} {HOLE}" />']
    else:
        p += [f'        <path android:fillType="evenOdd" android:pathData="{OUTLINE} {HOLE}">','            <aapt:attr name="android:fillColor">','                <gradient android:type="linear" android:startX="14" android:startY="30" android:endX="42" android:endY="17">']
        p += [f'                    <item android:offset="{o}" android:color="{c}" />' for c,o in COLORS]
        p += ['                </gradient>','            </aapt:attr>','        </path>', '        <group>',f'            <clip-path android:fillType="evenOdd" android:pathData="{OUTLINE} {HOLE}" />',f'            <path android:fillColor="#056E7A" android:fillAlpha="0.72" android:pathData="{FOLD}" />',f'            <path android:fillColor="#12B4C0" android:fillAlpha="0.35" android:pathData="{UPPER}" />','        </group>']
    for x,y,s,c in PIXELS:
        p += [f'        <path android:fillColor="{"#FFFFFFFF" if mono else c}" android:pathData="{rect(x,y,s)}" />']
    return '\n'.join(p+['    </group>','</vector>'])
def svg(mono=False,adaptive=False,tile=False,round_tile=False):
    vp=108 if adaptive else 48; scale=1.4 if adaptive else (.88 if tile else 1)
    tx=vp/2-24.5625*scale;ty=vp/2-24*scale
    p=[f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 {vp} {vp}">','<defs><linearGradient id="teal" gradientUnits="userSpaceOnUse" x1="14" y1="30" x2="42" y2="17">']
    p += [f'<stop offset="{o}" stop-color="{c}"/>' for c,o in COLORS]
    p += [f'</linearGradient><clipPath id="mark"><path clip-rule="evenodd" d="{OUTLINE} {HOLE}"/></clipPath></defs>']
    if adaptive: p += ['<circle cx="54" cy="54" r="36" fill="white"/>']
    if tile: p += ['<circle cx="24" cy="24" r="24" fill="white"/>' if round_tile else '<rect width="48" height="48" rx="10" fill="white"/>']
    p += [f'<g transform="translate({tx} {ty}) scale({scale})">',f'<path fill="{"white" if mono else "url(#teal)"}" fill-rule="evenodd" d="{OUTLINE} {HOLE}"/>']
    if not mono:p += [f'<g clip-path="url(#mark)"><path fill="#056E7A" opacity=".72" d="{FOLD}"/><path fill="#12B4C0" opacity=".35" d="{UPPER}"/></g>']
    p += [f'<path fill="{"white" if mono else c}" d="{rect(x,y,s)}"/>' for x,y,s,c in PIXELS]
    return '\n'.join(p+['</g></svg>'])
write(RES/'drawable/hi_player_brand_mark.xml',vector())
write(RES/'drawable/ic_launcher_foreground.xml',vector(adaptive=True))
write(RES/'drawable/hi_player_monochrome.xml',vector(adaptive=True,mono=True))
write(RES/'drawable/hi_player_notification.xml',vector(mono=True))
for suffix in ('','_round'):
    write(RES/f'mipmap-anydpi/ic_launcher{suffix}.xml',vector(tile=True,round_tile=bool(suffix)))
write(ROOT/'docs/branding/hi-player-icon.svg',svg())
if __name__=='__main__':
    print('Generated Pixel Play SVG and Android vectors; version and signing unchanged.')
