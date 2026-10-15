return {
    node_name: '',
    manifest: {
        timers: ['change'],
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
            this.schedule();
        } else if ((event.type === 'watch_face_update') || (event.type === 'display_data_updated')
                || ((event.type === 'timer_expired') && is_this_timer_expired(event, this.node_name, 'change'))) {
            this.draw(response);
            this.schedule();
        }
    },
    // Wakes the widget when what it shows changes on its own (an event starts or ends, the alarm rings or comes
    // within 24 hours, midnight): its redraw makes the face do a partial refresh, like the weather widget does.
    schedule: function() {
        var now = this.unix_time();
        var offset = this.tz_offset();
        var next = 86400 * (Math.floor((now + offset) / 86400) + 1) - offset;  // midnight
        var times = [this.alarm, this.alarm - 86400];
        for (var i in this.events) {
            times.push(this.events[i][0], this.events[i][1]);
        }
        for (var j in times) {
            if (times[j] > now && times[j] < next) {
                next = times[j];
            }
        }
        stop_timer(this.node_name, 'change');
        start_timer(this.node_name, 'change', (Math.min(next - now, 3600) + 1) * 1000);
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
    // 60 pre-rendered hour and minute tick icons, one every 6 degrees, centred on the dial at the given radius
    // (30px box at radius 105, 50px between 5 and 7 o'clock; boxes never leave the display: layout coordinates stay >= 0).
    // half = 0 picks the box size.
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
    // the text field under the date: lines are centred vertically in it
    field_top: 152,
    field_height: 64,
    line_height: 19,
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
            aiy: 0,
            aty: 0,
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
            out.show = true;
            out.hname = hp.name;
            out.hsz = hp.size;
            out.hx = hp.x;
            out.hy = hp.y;
            var mp = this.place(m * 6, 105, 'icTkM', 0);
            out.mname = mp.name;
            out.msz = mp.size;
            out.mx = mp.x;
            out.my = mp.y;
            // the 15 minutes before the event: 3 images of 5 small ticks
            for (var g = 0; g < 3; g++) {
                var first = (((m - 5 * g - 1) % 60) + 60) % 60;  // the group's tick nearest the event
                var mid = (first - 2) * 6 * Math.PI / 180;
                var gx = Math.floor(120 + 113 * Math.sin(mid) + 0.5) - 28;
                var gy = Math.floor(120 - 113 * Math.cos(mid) + 0.5) - 28;
                out['g' + g + 'name'] = 'icG' + first;
                out['g' + g + 'x'] = Math.min(Math.max(0, gx), 184);
                out['g' + g + 'y'] = Math.min(Math.max(0, gy), 184);
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
        // vertical placement: wrapped lines are estimated from the text length (about 18 characters a line)
        var rows = (out.l1 ? 1 : 0) + (out.l2 ? Math.min(3, Math.ceil(out.l2.length / 18)) : 0);
        var top = this.field_top + Math.max(0, Math.round((this.field_height - rows * this.line_height) / 2));
        out.l1y = top;
        out.l2y = top + (out.l1 ? this.line_height : 0);
        if (out.showalarm) {  // one row of 22 px centred in the field; the bell sits 4 px lower than the text row
            out.aty = this.field_top + Math.round((this.field_height - 22) / 2);
            out.aiy = out.aty + 4;
        }
        response.draw = {};
        response.draw[this.node_name] = out;
    },
};
