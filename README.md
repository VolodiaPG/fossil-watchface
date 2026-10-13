# Event watchface for the Fossil Hybrid HR

Shows your next calendar event of the day as ticks on the dial rim, plus date, battery, weather and distance.

- **Minutes:** one long tick, with 15 small ticks leading up to it.
- **Hours:** one short tick with a dot.

It comes with a small phone app, Fossil Feed, that sends the watch your events (with location), your next alarm and, optionally, the time left on a 3D print from Home Assistant.

## Install

Requires Gadgetbridge 0.94 or later, with the watch already paired. Both files are on the Releases page.

1. **Watchface:** download `eventWatchface-vX.Y.Z.wapp`. In Gadgetbridge, open the app manager, tap `+`, select the file and activate the face.
2. **Phone app:** install `fossil-feed-vX.Y.Z.apk`, open it, allow calendar access and tap *Push to watch now*.
3. Turn Gadgetbridge's own calendar sync off, otherwise it overwrites the events with ones that have no location.

## Things to know

- **Events:** only events starting today are shown: the location first, then the title and description.
- **Alarm:** when there is no event today, the phone's next alarm shows with a bell if it rings within 24 hours. With neither, the face says `No events or alarms`.
- **Updates:** the app pushes every 15 minutes and shortly after the calendar changes, but only when something changed. The watch forgets its data when the face is reinstalled, so tap *Push to watch now* then.
- **Weather:** needs a phone weather app that feeds Gadgetbridge (e.g. Breezy). Without it the widget shows `– –`.
- **Distance:** estimated from today's steps at 0.75 m each.
- **3D print:** while a print runs, the distance bubble shows the time left (`1h05`, `42m`) with a printer icon. In the app, fill in the Home Assistant address, a long-lived access token (profile page in Home Assistant), the entity, the route (default `/api/states/{entity}`), where the value sits in the answer (default `state`, or e.g. `attributes.remaining`) and whether it is seconds, minutes, hours or an end time, then tap *Save Home Assistant and test*. A time left can stay on a sensor after a print fails or the printer disconnects: to avoid that, also set a status entity (e.g. the printer's status sensor) and the states that mean printing (e.g. `printing`); any other state clears the bubble. The phone must reach Home Assistant; plain `http://` is allowed. If Home Assistant cannot be reached, the watch keeps its last value.
- **Battery:** a small tab under the date appears at 30% or below.

## Build it yourself

- **Watchface:** run `make` in `mywatch/`. It needs JerryScript 2.1.0 (exactly that version) and the Fossil-HR-SDK; see `.github/workflows/build.yml` for the full setup.
- **App:** run `./gradlew assembleRelease` in `app/` (JDK 17 and the Android SDK). A local build is signed with the Android debug key. The release workflow signs with your own key, stored in the GitHub secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`, and fails if they are missing.
