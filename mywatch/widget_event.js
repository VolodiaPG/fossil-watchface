return {
    node_name: '',
    manifest: {
        timers: [],
    },
    persist: {},
    config: {
        events: undefined,
        alarm: undefined,
    },
    events: [],
    alarm: 0,  // unix seconds of the phone's next alarm, 0 = none
    handler: function(event, response) {
        if (event.type === 'node_config_update') {
            this.store_events();
            this.store_alarm();
            this.draw(response);
        } else if ((event.type === 'watch_face_update') || (event.type === 'display_data_updated')) {
            this.draw(response);
        }
    },
    // Pushed by the phone app: [{start, end, title, location, desc}], seconds since epoch. Kept compact as [start, end, title, desc, location]
    store_events: function() {
        var incoming = this.config.events;
        if (incoming === undefined) {
            return;
        }
        var list = [];
        try {
            for (var i = 0; i < incoming.length; i++) {
                var e = incoming[i];
                list.push([e.start, e.end, String(e.title), e.desc ? String(e.desc) : '', e.location ? String(e.location) : '']);
            }
            list.sort(function(a, b) { return a[0] - b[0]; });
        } catch (err) {
            list = [];
        }
        this.events = list;
        this.config.events = undefined;
    },
    store_alarm: function() {
        if (this.config.alarm !== undefined) {
            this.alarm = Number(this.config.alarm) || 0;
            this.config.alarm = undefined;
        }
    },
    // "07:30", or "Dem 07:30" when it rings tomorrow; undefined unless it rings within the next 24 hours
    next_alarm_text: function() {
        var now = this.unix_time();
        if (!(this.alarm > now && this.alarm <= now + 86400)) {
            return undefined;
        }
        var offset = this.tz_offset();
        var local = this.alarm + offset;
        var secs = local - 86400 * Math.floor(local / 86400);
        var hh = Math.floor(secs / 3600);
        var mm = Math.floor((secs % 3600) / 60);
        var text = (hh < 10 ? '0' + hh : hh) + ':' + (mm < 10 ? '0' + mm : mm);
        var tomorrow = Math.floor(local / 86400) > Math.floor((now + offset) / 86400);
        return tomorrow ? 'Dem ' + text : text;
    },
    unix_time: function() {
        if (get_common().U('OFFSET_UNIX_TIME') != undefined) {
            return get_common().U('OFFSET_UNIX_TIME') + get_unix_time();
        }
        return get_unix_time();
    },
    tz_offset: function() {
        if (get_common().U('TZ_OFFSET') != undefined && get_common().U('TZ_OFFSET') > 0) {
            return 60 * get_common().U('TZ_OFFSET');
        }
        return 60 * get_common().time_zone_local;
    },
    // first event still running or upcoming, starting before local midnight
    next_event: function() {
        var now = this.unix_time();
        var offset = this.tz_offset();
        var midnight = 86400 * (Math.floor((now + offset) / 86400) + 1) - offset;
        for (var i in this.events) {
            var e = this.events[i];
            if (e[1] > now) {
                return e[0] < midnight ? e : undefined;
            }
        }
        return undefined;
    },
    // 60 pre-rendered tick icons per kind, one every 6 degrees, centred on the dial at the given radius
    // (hour / minute ticks: 30px box at radius 105, 50px between 5 and 7 o'clock; small ticks: 12px box at radius 114;
    // boxes never leave the display: layout coordinates stay >= 0). half = 0 picks the hour / minute box size.
    place: function(angle, radius, prefix, half) {
        var step = ((Math.round(angle / 6) % 60) + 60) % 60;
        var a = step * 6 * Math.PI / 180;
        if (half === 0) {
            half = (step >= 25 && step <= 35) ? 25 : 15;
        }
        return {
            name: prefix + step,
            size: half * 2,
            x: Math.round(120 + radius * Math.sin(a) - half),
            y: Math.round(120 - radius * Math.cos(a) - half),
        };
    },
    battery_show_at_or_below: 30,  // percent: the battery tab shows at or below this level
    day_of_week: ['Dim', 'Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam'],
    draw: function(response) {
        var e = this.next_event();
        var d = get_common().date;
        var soc = get_common().battery_soc;
        var level = Math.max(0, Math.min(8, Math.round(soc * 8 / 100)));
        var out = {
            json_file: 'event_layout',
            show: false,
            l1: 'No events or alarms',
            l2: '',
            showalarm: false,
            date: this.day_of_week[get_common().day] + ' ' + (d < 10 ? '0' + d : d),
            showbat: soc <= this.battery_show_at_or_below,
            bat: 'icBat' + level,
        };
        if (e !== undefined) {
            var local = e[0] + this.tz_offset();
            var secs = local - 86400 * Math.floor(local / 86400);
            var h = Math.floor(secs / 3600) % 12;
            var m = Math.floor((secs % 3600) / 60);
            var hp = this.place((h + m / 60) * 30, 105, 'icTkH', 0);
            var mp = this.place(m * 6, 105, 'icTkM', 0);
            out.show = true;
            out.hname = hp.name;
            out.hsz = hp.size;
            out.hx = hp.x;
            out.hy = hp.y;
            out.mname = mp.name;
            out.msz = mp.size;
            out.mx = mp.x;
            out.my = mp.y;
            for (var k = 1; k <= 15; k++) {  // small ticks for the 15 minutes before the event minute: long, middle, short
                var sp = this.place((m - k) * 6, 114, k <= 5 ? 'icTkB' : (k <= 10 ? 'icTkS' : 'icTkT'), 6);
                out['s' + k + 'name'] = sp.name;
                out['s' + k + 'x'] = sp.x;
                out['s' + k + 'y'] = sp.y;
            }
            // location above the rest; without a location the title takes the first line
            var lines = [];
            if (e[4]) {
                lines.push(e[4]);
            }
            lines.push(e[2]);
            if (e[3]) {
                lines.push(e[3]);
            }
            out.l1 = lines[0];
            out.l2 = lines.slice(1).join(' - ');
        } else {
            var alarm = this.next_alarm_text();
            if (alarm !== undefined) {
                out.l1 = '';
                out.showalarm = true;
                out.aicon = 'icAlarm';
                out.atxt = alarm;
            }
        }
        response.draw = {};
        response.draw[this.node_name] = out;
    },
};
