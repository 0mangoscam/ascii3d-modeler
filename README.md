# WEMWEM

**WEMWEM** is an experimental Android application where sound becomes volumetric 3D ASCII sculpture.

It is not a traditional audio visualizer. It is an artistic instrument: audio creates ASCII particles, particles gather into fields, fields condense into living structures, and silence erodes them back into black space.

## Current prototype

This alpha focuses on the core live instrument:

- Fullscreen Android app with retro black terminal atmosphere.
- Volumetric ASCII rendering using characters such as `. : - = + * # % @ █`.
- Sound-born particle field driven by microphone input.
- Organic condensation from particles into sculptural forms.
- Silence decay: particles fade, weak forms simplify, the sculpture slowly loses density.
- MIDI USB / OTG input through Android MIDI APIs.
- MIDI notes create growth pulses.
- MIDI CC controls steer the sculpture DNA.
- Hardware keyboard piano mapping: `A W S E D F T G Y H U J K`.
- On-screen virtual piano strip.
- Touch navigation: orbit, zoom, gesture growth.
- Volume Up: new branch / burst.
- Volume Down: collapse / simplify.
- Shake: mutate.
- EVOLVE: generate descendants.

## Sound mapping

- RMS / volume → global energy.
- Bass → mass, thickness, heavy ASCII particles.
- Mids → structure, tubes and arches.
- Highs → root lines, glyphs, bright dust.
- Onset / attack → particle bursts and new growth.
- Sustain → slow cultivation.
- Silence → erosion, simplification and decay.

## MIDI mapping

- Notes → growth pulses.
- Velocity → initial energy.
- Program Change → palette / mode shift.
- CC 1 → curvature.
- CC 2 → thickness.
- CC 7 → growth.
- CC 10 → spread.
- CC 11 → accent lines.
- CC 71 → chaos.
- CC 74 → loop frequency.
- CC 91 → surface noise / atmosphere.

## Artistic direction

WEMWEM should feel like:

- Unix terminal ritual.
- Demoscene sculpture chamber.
- Hylics-like abstract weirdness.
- Processing / TouchDesigner instrument.
- Digital matter made of characters.
- A temporary organism created by sound.

## Roadmap

Planned systems:

- Import audio files.
- Bluetooth MIDI testing.
- Virtual synthesizer engine.
- Full ADSR extraction.
- Pitch tracking.
- Beat detection.
- Stereo width analysis.
- Configurable disappearance speeds.
- Persistence mode.
- Memory mode.
- Artistic modes: Coral, Crystal, Architecture, Fractal, Biological, Insect, Bone, Root, City, DNA, Fluid, Wireframe, Ruin, Glitch, Vaporwave, Brutalist.
- Exports: TXT ASCII, PNG, GIF, MP4, OBJ, PLY, GLTF, STL, JSON evolution history.
- Gallery archive with title, author, date, instrument and performance metadata.

## Build

GitHub Actions builds the debug APK automatically.

Local build:

```bash
gradle :app:assembleDebug --no-daemon
```

