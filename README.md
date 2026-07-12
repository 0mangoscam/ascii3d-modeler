# Sculpture Garden V0.4 — Sound Growth

Premium Android art instrument for cultivating ASCII sculptures with 3D volume.

## Concept

Sculpture Garden is not a CAD app and not a classic sculpting tool. It is a living audiovisual garden where abstract sculptures are cultivated from sound.

The app now uses **Organic Sound Growth**:

- Microphone input feeds the sculpture in real time.
- Bass creates mass and thickness.
- Mids grow the main soft tubular structure.
- Highs add root-lines, coils and glyph accents.
- Onsets create biological sprout events.
- Sustain bends and cultivates existing forms.
- Silence stabilizes and calms the organism.

## Visual DNA

The visual language is based on mangoscam / Brian Novillo's abstract soft-form aesthetic:

- rounded ASCII volume
- tubes
- loops
- arches
- coils
- soft glyphs
- root-like fine lines
- floating compositions
- flat canvas backgrounds
- curated color families

## Interaction

- Sound grows the sculpture organically.
- Swipe rotates and adds gesture.
- Pinch splits structure.
- Long press increases mass.
- Shake mutates.
- Volume Up adds a new gesture.
- Volume Down simplifies.
- MIDI notes still grow and pulse the organism.
- MIDI CC can steer DNA parameters.

## Audio Mapping

```text
RMS    -> global organism energy
Bass   -> mass, thickness, capsules
Mids   -> structure, tubes, arcs, loops
Highs  -> accents, root-lines, coils
Onset  -> sprout events
Sustain-> slow cultivation
Silence-> calm / stabilization
```

## Build

The GitHub Actions workflow builds a debug APK automatically.

```bash
gradle :app:assembleDebug --no-daemon
```

## Version

V0.4.0 — ASCII volume + microphone-driven organic sound growth + MIDI steering.
