# Event watchface for the Fossil Hybrid HR
Shows your next calendar event of the day as ticks on the dial rim (long = minutes, short with a dot = hours, 5 small lead-in ticks), plus date, battery, weather and distance.
**Install** (Gadgetbridge 0.94+, watch already paired): download `eventWatchface-vX.Y.Z.wapp` from Releases, then Gadgetbridge app manager → `+` → select it → activate.
**Calendar:** enable calendar sync with the calendar permission, then set *Device settings → Developer options → Target app on watch for calendar events* to `widgetEvent`.
Gadgetbridge only resends when events change: after changing the target, edit an event or reconnect the watch.
Only events starting today show, as `description - title`: Gadgetbridge sends no location, so put the place in the description.
Weather needs a phone weather app feeding Gadgetbridge (e.g. Breezy), otherwise it shows `– –`; distance is estimated from steps (0.75 m each).
Build yourself: `make` in `mywatch/` needs JerryScript 2.1.0 (exactly) and the Fossil-HR-SDK, see `.github/workflows/build.yml`.
