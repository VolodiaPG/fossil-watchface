return {
    node_name: '',
    stride_m: 0.75,  // no distance in the watch's data: estimated from today's steps
    handler: function(event, response) {
        if ((event.type === 'watch_face_update') || (event.type === 'display_data_updated')) {
            this.draw(response);
        }
    },
    draw: function(response) {
        var steps = get_common().step_count;
        var km = (typeof steps === 'number' ? steps : 0) * this.stride_m / 1000;
        response.draw = {};
        response.draw[this.node_name] = {
            json_file: 'complication_layout',
            icon: 'icDist',
            ci: (Math.round(km * 10) / 10) + 'km',
        };
    },
};
