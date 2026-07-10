# Sculpture Garden

**Sculpture Garden** is a premium Android prototype for cultivating abstract digital sculptures.

It is not a CAD tool. It is not a classic 3D editor. It is a quiet creative ecosystem where forms grow, mutate, branch and evolve through gestures.

The experience is designed to sit between a museum, a botanical garden and an experimental art instrument.

## Core idea

Every sculpture begins as **The Seed**.

The user does not insert primitives. There are no cubes, cylinders or toolbars. The sculpture grows as an organism:

- Swipe: grow and orbit the organism
- Long press: increase mass
- Pinch: split structure
- Rotate gesture movement: twist the organism
- Shake phone: mutate
- Volume Up: generate branch
- Volume Down: collapse branch
- Tap **EVOLVE**: create five descendants
- Tap a descendant: choose the survivor
- Double tap: Relax Mode
- Tap **MUSEUM / VOID**: switch gallery atmosphere

## DNA system

Each sculpture has a living DNA profile:

- Shape curvature
- Elasticity
- Chaos
- Rhythm
- Density
- Thickness
- Balance
- Growth
- Branch frequency
- Material
- Shadow softness
- Surface noise
- Palette shift

The current prototype already creates descendants by mutating DNA intelligently, keeping the family coherent and visually pleasing.

## Evolution system

There is no **New Sculpture** flow.

The central action is **EVOLVE**.

The active organism creates five descendants. The user chooses which one survives. The chosen sculpture becomes the next generation.

## Visual direction

Dark gallery by default. Minimal typography. Large empty spaces. No colorful buttons. The interface is intended to disappear so the sculpture can breathe.

A white museum mode is included as a quiet alternate atmosphere.

## Build APK

Push this repository to GitHub. The included GitHub Actions workflow builds a debug APK automatically.

Download the APK from:

`GitHub → Actions → Build debug APK → Artifacts → Sculpture-Garden-debug-apk`

## Local build

```bash
gradle :app:assembleDebug --no-daemon
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Roadmap

- Save and share DNA codes
- Forests instead of folders
- Evolutionary tree view
- Materials library
- Cinematic camera paths
- Poster export
- GLB / OBJ / STL export
- AR placement
- Ambient sound mode
- Genetic crossover between two sculptures
- AI-assisted mutation suggestions

## Package

```text
com.mangoscam.sculpturegarden
```

## Artistic note

This app is a garden for impossible organisms. The user does not model the sculpture. The user discovers what the sculpture wants to become.
