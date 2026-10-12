# Event watchface for the Fossil Hybrid HR

Shows your next calendar event of the day as ticks on the dial rim, plus date, battery, weather and distance.

- **Minutes:** one long tick, with 5 small ticks leading up to it.
- **Hours:** one short tick with a dot.

## Install

Requires Gadgetbridge 0.94 or later, with the watch already paired.

1. Download `eventWatchface-vX.Y.Z.wapp` from the Releases page.
2. In Gadgetbridge, open the app manager, tap `+`, select the file and activate the face.
3. Enable calendar sync and grant the calendar permission.
4. Under *Device settings → Developer options*, set *Target app on watch for calendar events* to `widgetEvent`.

## Things to know

- **Resync:** Gadgetbridge only resends events when they change. After changing the target, edit an event or reconnect the watch.
- **Today only:** only events starting today are shown, as `description - title`. Gadgetbridge sends no location, so put the place in the description.
- **Weather:** needs a phone weather app that feeds Gadgetbridge (e.g. Breezy). Without it the widget shows `– –`.
- **Distance:** estimated from today's steps at 0.75 m each.

## Build it yourself

Run `make` in `mywatch/`. It needs JerryScript 2.1.0 (exactly that version) and the Fossil-HR-SDK; see `.github/workflows/build.yml` for the full setup.
