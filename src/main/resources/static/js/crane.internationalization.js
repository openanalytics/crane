/*
 * Crane
 *
 * Copyright (C) 2021-2026 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
Crane = window.Crane || {};
Crane.internationalization = {
    state: {
        messages: {}
    },
    async init() {
        Crane.internationalization.state.messages = await Crane.api.getLanguageMessages();
    },
    t(code) {
        return Crane.internationalization.state.messages[code] || code;
    },
    f(code) {
        // https://stackoverflow.com/a/4673436
        const args = Array.prototype.slice.call(arguments, 1);
        const format = Crane.internationalization.t(code);
        return format.replace(/{(\d+)}/g, function(match, number) {
            if (typeof args[number] !== 'undefined') {
                return args[number];
            }
            return match;
        });
    }
}
