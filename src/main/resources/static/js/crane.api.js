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
    csrfToken: document.querySelector("meta[name='_csrf']")?.content,
    csrfHeader: document.querySelector("meta[name='_csrf_header']")?.content,
    contextPath: document.querySelector("meta[name='_contextPath']")?.content,
    instanceId: document.querySelector("meta[name='_instanceId']")?.content,

    buildURL(location) {
        const baseURL = new URL(Crane.api.contextPath, window.location.origin);
        return new URL(location, baseURL);
    },

    async changeLanguage(language) {
        const resp = await fetch(Crane.api.buildURL('__language'), {
            method: 'PUT',
            body: JSON.stringify({"language": language}),
            headers: {
                'Content-Type': 'application/json',
                [Crane.api.csrfHeader]: Crane.api.csrfToken
            },
        });
        const json = await Crane.api._getResponseJson(resp);
        return json !== null;
    },

    async getLanguageMessages() {
        const lang = document.documentElement.lang;
        const resp = await fetch(Crane.api.buildURL(`__language/messages/${lang}?instanceId=${Crane.api.instanceId}`));
        const json = await Crane.api._getResponseJson(resp);
        return json.data;
    },

    async deleteObject(fileName, button) {
        const url = document.URL + fileName;
        const isPathComponent = button.dataset?.ispathcomponent === "true";
        button.classList.add("deleting")
        const delay = ms => new Promise(res => setTimeout(res, ms));
        const options = {
            method: 'DELETE',
            headers: {[Crane.api.csrfHeader]: Crane.api.csrfToken}
        };
        fetch(url, options).then(async (resp) => {
            if (resp.status === 200) {
                // this is an immediate (sync) delete
                const otherInProgress = Crane.ui.otherDeleteInProgress()
                if (!isPathComponent) {
                    if (!otherInProgress) {
                        location.reload();
                    } else {
                        button.closest("tr").hidden = true;
                    }
                }
                Crane.ui.stopDeleting(button);
                return;
            } else if (resp.status === 202) {
                // this is an async delete
                let response = resp;
                let json = await resp.json();
                Crane.ui.startDeleting(button, json.data);

                while (response.status === 202 && json.data.total !== 0) {
                    await delay(250)
                    response = await fetch(url, options);
                    json = await response.json();
                    Crane.ui.updateDeleting(button, json.data)
                }
                const otherInProgress = Crane.ui.otherDeleteInProgress()
                if (!isPathComponent) {
                    if (!otherInProgress) {
                        location.reload();
                    } else {
                        button.closest("tr").hidden = true;
                    }
                }
                Crane.ui.stopDeleting(button);
                return;
            }
            let error;
            try {
                error = (await resp.json()).data.message;
            } catch (e) {
                error = resp.statusText;
            }
            alert(error);
        }).catch((error) => {
            alert(error);
        });
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
    },

    /**
     * Get contents of fileName (relative to current directory)
     * @param fileName
     * @returns {Promise<Response|null>}
     */
    async getFile(fileName) {
        try {
            const response = await fetch(window.location + fileName);
            if (response.status !== 200) {
                console.log("Received invalid response (not 200 OK) ", response);
                return null;
            }
            return response;
        } catch (e) {
            console.error(e);
            return null;
        }
    },

    /**
     * Get metadata file for a file (relative to the current directory).
     * @param fileName
     * @returns {Promise<any|null>}
     */
    async getMetadataFile(fileName) {
        try {
            const response = await fetch(window.location + ".crane/" + fileName + ".yaml");
            if (response.status !== 200) {
                console.log("Received invalid response (not 200 OK) ", response);
                return null;
            }
            return await response.json();
        } catch (e) {
            console.error(e);
            return null;
        }
    },

    /**
     * Upload a string as a file, replaces existing files.
     * @param fileName
     * @param data
     * @returns {Promise<boolean>} whether the upload was successful
     */
    async uploadStringAsFile(fileName, data) {
        try {
            const url = window.location + fileName + "?replace=true"
            const file = new File([data], fileName, {
                type: "text/plain",
            });
            const formData = new FormData();
            formData.append("file", file);
            const resp = await fetch(url, {
                method: 'POST',
                body: formData,
                headers: {[Crane.api.csrfHeader]: Crane.api.csrfToken}
            });
            const json = await Crane.api._getResponseJson(resp);
            return json !== null;
        } catch (e) {
            console.error(e);
            return null;
        }
    }

}
