return {
    node_name: '',
    stride_m: 0.75,  // no distance in the watch's data: estimated from today's steps
    config: {
        printer: undefined,
    },
    printer_end: 0,  // unix seconds when the 3D print is done, 0 = no print running
    handler: function(event, response) {
        if (event.type === 'node_config_update') {
            if (this.config.printer !== undefined) {
                this.printer_end = Number(this.config.printer) || 0;
                this.config.printer = undefined;
            }
            this.draw(response);
        } else if ((event.type === 'watch_face_update') || (event.type === 'display_data_updated')) {
            this.draw(response);
        }
    },
    unix_time: function() {
        if (get_common().U('OFFSET_UNIX_TIME') != undefined) {
            return get_common().U('OFFSET_UNIX_TIME') + get_unix_time();
        }
        return get_unix_time();
    },
    // "1h05" or "42m" while a print is running, else undefined
    print_text: function() {
        var left = this.printer_end - this.unix_time();
        if (!(left > 0)) {
            return undefined;
        }
        var mins = Math.max(1, Math.round(left / 60));
        var mm = mins % 60;
        return mins >= 60 ? Math.floor(mins / 60) + 'h' + (mm < 10 ? '0' + mm : mm) : mins + 'm';
    },
    draw: function(response) {
        var print = this.print_text();
        var icon = 'icPrint';
        var text = print;
        if (print === undefined) {
            var steps = get_common().step_count;
            var km = (typeof steps === 'number' ? steps : 0) * this.stride_m / 1000;
            icon = 'icDist';
            text = (Math.round(km * 10) / 10) + 'km';
        }
        response.draw = {};
        response.draw[this.node_name] = {
            json_file: 'complication_layout',
            icon: icon,
            ci: text,
        };
    },
};
