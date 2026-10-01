# Changelog

## 1.0.1

Two fixes, and builds for newer Minecraft versions.

### New Minecraft versions

Clientify now also runs on Minecraft 26.1 (one jar for 26.1, 26.1.1 and 26.1.2), 26.2 and 26.3.
Each version has its own jar, named after the game version; 1.21.11 carries on as before.
Everything from 1.0.0 is in all of them.

### Fixes

- **Nametag text shadow.** With the shadow on, names the server colours (team or rank colours)
  came out smeared: the shadow was a second copy of the name, and it took the name's own colour.
  The game's font now draws the shadow, darkening each colour and keeping it behind the text.
- **Binding mouse buttons in Clientify's menus.** The key rows (Zoom Key, Lock Key, the waypoint
  keys and the rest) ignored mouse buttons, so a side button could only be bound from Minecraft's
  Controls screen. Any mouse button works there now too, the same as in Controls.

## 1.0.0

First public release. Minecraft 1.21.11, Fabric, client-side only.

### The editor

Right Shift opens a HUD editor: drag modules with snapping to the screen centre and to other
modules, scroll to resize, arrow keys to nudge a pixel at a time. The Mods button opens the
module list, and layout profiles let you keep several complete arrangements and switch between
them.

### Modules

Thirty in total.

**HUD** — FPS, Ping, Reach Display, Coordinates, Sprint Indicator, Armor HUD, Effects HUD,
Saturation, Keystrokes, Custom Text, Item Counter, Scoreboard, Tab List, Boss Bar, Action Bar,
Title, Nametags.

**Mechanics & visuals** — Zoom, Fullbright, GUI Scale, Custom Crosshair, Attack Indicator,
Hitboxes, Hit Color, Totem Tweaks, Shulker Tooltip, Waypoints, Overlay, Time Changer,
Weather Changer.

### Appearance

Every module shares a common appearance set — font, text shadow, background colour, extra
padding, rounded corners, frosted blur, border, and label/value colours — with per-entry
overrides where a module holds several items (Custom Text boxes, Item Counter entries,
Keystrokes keys, Coordinates cells, Armor pieces).

- **Apply To All** in Settings stamps one appearance template onto every module.
- **No Overlap Blending** stops two translucent backgrounds darkening where they cross.
- Colours support static, chroma, gradient and gradient-wave modes.

### Behaviour worth knowing

- Modules that replace a vanilla element (scoreboard, tab list, boss bar, action bar, title)
  default to the exact position vanilla draws them at.
- **Frosted blur needs OpenGL.** VulkanMod cannot supply the blurred copy it samples, so the
  option is disabled with a note when VulkanMod is loaded. Everything else works on it.
- Armor HUD can draw vanilla hotbar slots behind the pieces, joined into one strip or spaced
  apart, and can hold a slot open for a piece you are not wearing. Where a joined strip leaves
  no room for durability text — above or under the icons, or in a horizontal row — the text is
  dropped and the durability bars carry the wear instead.
- Turning a module's background off collapses its padding, so it can sit flush in a corner.
- Zoom leaves the held item alone by default, matching how the vanilla FOV slider behaves;
  **Zoom Hand** opts into magnifying it.
- **Fly Boost** (Sprint Indicator) changes creative flying speed, which the server validates.
  It is capped to what a server will accept, but it is a movement change — see the README.
