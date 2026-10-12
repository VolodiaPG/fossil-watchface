JERRY := ../jerry21/build/bin/jerry-snapshot
PY := ../.venv/bin/python
OUT := eventWatchface.wapp

# source file -> code node name
CODE := event_watchface.js:eventWatchface widget_event.js:widgetEvent \
        widget_weather.js:widgetWeather widget_distance.js:widgetDistance

.PHONY: all assets code pack clean
all: assets code pack

assets:
	$(PY) tools/gen_assets.py

code:
	mkdir -p build/files/code
	rm -f build/files/code/*
	for p in $(CODE); do $(JERRY) generate -f '' $${p%%:*} -o build/files/code/$${p##*:} >/dev/null || exit 1; done

pack:
	$(PY) ../sdk/tools/pack.py -i build -o $(OUT)

clean:
	rm -f build/files/code/* $(OUT)
