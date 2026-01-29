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
Crane.api = {
    async changeLanguage(language) {
        const csrfToken = document.querySelector("meta[name='_csrf']")?.content;
        const csrfHeader = document.querySelector("meta[name='_csrf_header']")?.content;
        const resp = await fetch("/__language", {
            method: 'PUT',
            body: JSON.stringify({"language": language}),
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            },
        });
        const json = await Crane.api._getResponseJson(resp);
        return json !== null;
    },

    async _getResponseJson(response) {
        if (response.status !== 200) {
            console.log("Received invalid response (not 200 OK) ", response);
            return null;
        }
        let json = await response.json();
        if (!json.hasOwnProperty("status")) {
            console.log("Received invalid response (missing status) ", json);
            return null;
        }
        if (json.status !== "success") {
            console.log("Received invalid response (status is not success) ", json);
            return null;
        }
        if (!json.hasOwnProperty("data")) {
            console.log("Received invalid response (missing data) ", json);
            return null;
        }
        return json;
    }

}
