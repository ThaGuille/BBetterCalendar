UI feedback sounds
==================

Drop short .ogg files here to enable in-app audio feedback. Until then,
SoundFeedback is a no-op and only haptics fire.

Expected files
--------------
  tap.ogg       generic button taps           (< 100 ms)
  start.ogg     timer / session start         (< 600 ms)
  stop.ogg      timer pause / cancel          (< 400 ms)
  success.ogg   session / streak success      (< 800 ms)

Format: 22 kHz mono OGG Vorbis, normalized to -12 LUFS.

Where to source them (free, no attribution preferred)
-----------------------------------------------------
  - https://pixabay.com/sound-effects/search/ui/
  - https://mixkit.co/free-sound-effects/click/
  - https://m2.material.io/design/sound/sound-resources.html
  - https://freesound.org/  (check per-clip license)

After adding files, uncomment the matching pool.load(...) calls inside
SoundFeedback#SoundFeedback(Context).
