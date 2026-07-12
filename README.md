# WEMWEM

Experimental Android instrument where sound births volumetric ASCII sculpture.

This version starts from absolute emptiness. There is no initial sculpture, no seed, no random object at launch.

## Core rule

Sound creates particles. Particles condense into ASCII matter. Silence erodes everything back to black.

```text
sound -> ASCII particles -> particle field -> condensation -> 3D ASCII sculpture -> silence -> dust -> empty scene
```

## Controls

- `LISTEN`: microphone on/off.
- `PERSIST`: keep a tiny memory skeleton instead of total disappearance.
- `DECAY`: changes disappearance speed.
- Touch drag: orbit camera.
- Pinch: zoom.
- On-screen piano: creates sound-events without external MIDI.
- Keyboard piano: A W S E D F T G Y H U J K.
- Volume Up: burst.
- Volume Down: prune / collapse weak matter.

## Audio mapping

- Volume / RMS: particle birth rate and density.
- Bass: heavy mass particles.
- Mids: structural tubes.
- Highs: dust, roots and detail.
- Onset: burst and new branch direction.
- Sustain: condensation.
- Silence: erosion.

## MIDI-ready concept

MIDI notes and CC values are handled as sculptural field events. The app is structured so USB/Bluetooth MIDI can steer the particle field rather than spawning random premade sculptures.
