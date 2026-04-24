# Snoozr — TODO

## In Progress
_nothing yet_

---

## Backlog

### 🔊 Audio Engine
- [x] Implement `NoiseGenerator` — AudioTrack-based PCM streaming on a background thread
  - [x] White noise (uniform random samples)
  - [x] Pink noise (Voss-McCartney algorithm, 1/f spectrum)
  - [x] Brown noise (cumulative sum of white noise)
- [x] Wire volume slider to AudioTrack gain in real time
- [x] Wire power button to start/stop audio playback
- [x] Graceful AudioTrack release on stop (avoid audio glitches)

### 🔁 Background Playback (ForegroundService)
- [x] Create `NoiseService` as a ForegroundService so audio keeps playing with screen off
- [x] Add `FOREGROUND_SERVICE` permission to AndroidManifest
- [x] Show a persistent notification with play/pause control
- [x] Bind the UI to the service so controls stay in sync

### 🎛 UI — Wire Up & Polish
- [x] Sound selector: implement dropdown menu with all noise types
- [ ] Update accent color based on selected noise type (white → blue, pink → rose, brown → amber)
- [ ] Dim/grey the volume slider when power is off

### ⏱ Sleep Timer
- [ ] Add a sleep timer option (15 / 30 / 60 min, or custom)
- [ ] Show countdown in the UI when timer is active
- [ ] Auto-stop audio and cancel notification when timer fires

### 🏗 Architecture
- [ ] Extract state into a `ViewModel` so it survives screen rotation
- [ ] Connect ViewModel to NoiseService via a repository or shared state

### 🔮 Nice-to-Have (Future)
- [ ] Additional sounds: rain, fan, ocean waves (MediaPlayer + looping audio files)
- [ ] Equalizer / bass boost option
- [ ] Widget for home screen play/pause
- [ ] Auto-start on phone restart (BOOT_COMPLETED receiver)

---

## Done
_nothing yet_
