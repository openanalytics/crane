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
Crane.ui = {
    elements: {
        createDirectoryDialog: document.getElementById("create-directory-dialog"),
        uppyDialog: document.getElementById("uppy-dialog"),
        createDirectoryForm: document.getElementById("create-directory-form"),
        createDirectoryButton: document.getElementById("create-directory"),
        table: $("table"),
        datatable: null,
        uppy: null,
    },

    closeCreateDirectoryDialog() {
        const cancelButton = document.getElementById("cancel-create-directory");
        cancelButton.disabled = false;
        if (Crane.ui.elements.createDirectoryButton.classList.contains("uploading")) {
            Crane.ui.elements.createDirectoryButton.innerText = Crane.internationalization.t("index.create.button");
            Crane.ui.elements.createDirectoryButton.classList.remove("uploading");
        }
        Crane.ui.elements.createDirectoryDialog.close();
        Crane.ui.elements.createDirectoryForm.reset();
    },

    createDirectory(e) {
        e.preventDefault();
        const dirNameField = document.getElementById("directory-name");
        const cancelButton = document.getElementById("cancel-create-directory");
        cancelButton.disabled = true;
        Crane.ui.elements.createDirectoryButton.disabled = true;
        Crane.ui.elements.createDirectoryButton.innerText = Crane.internationalization.t("index.creating.button") + "...";
        Crane.ui.elements.createDirectoryButton.classList.toggle("uploading");

        const url = Crane.ui.elements.createDirectoryForm.action + dirNameField.value;
        fetch(url, {
            method: 'POST',
            body: '',
            headers: {[Crane.api.csrfHeader]: Crane.api.csrfToken}
        }).then(async (resp) => {
            if (resp.ok) {
                location.replace(url);
                return;
            }
            let error;
            try {
                error = (await resp.json()).data.message;
            } catch (e) {
                error = resp.statusText;
            }
            if (error === "Forbidden") {
                error = Crane.internationalization.t("index.not_allowed_created_directory.error");
            }
            Crane.ui.closeCreateDirectoryDialog();
            alert(error);
        }).catch((error) => {
            Crane.ui.closeCreateDirectoryDialog();
            alert(error);
        });
    },

    disableCreateDirectory() {
        const dirNameField = document.getElementById("directory-name");
        Crane.ui.elements.createDirectoryButton.disabled = dirNameField.value === "";
    },

    startDeleting(button, data) {
        let deleteInfo = document.getElementById(`progress-${button.dataset.dirname || button.dataset.filename}`);
        if (deleteInfo !== null || deleteInfo !== undefined) {
            deleteInfo.innerText = `${data.deleted}/${data.todo + data.deleted}`
        }
    },

    updateDeleting(button, data) {
        let deleteInfo = document.getElementById(`progress-${button.dataset.dirname || button.dataset.filename}`);
        if (deleteInfo !== null || deleteInfo !== undefined) {
            deleteInfo.innerText = `${data.deleted}/${data.todo + data.deleted}`
        }
    },

    stopDeleting(button) {
        let deleteInfo = document.getElementById(`progress-${button.dataset.dirname || button.dataset.filename}`);
        if (deleteInfo !== null || deleteInfo !== undefined) {
            deleteInfo.innerText = ""
        }
        button.classList.remove("deleting")
    },

    otherDeleteInProgress() {
        let deleteInfos = document.getElementsByClassName("delete-info");
        for (const deleteInfo of deleteInfos) {
            if (deleteInfo.innerText !== "") {
                return true;
            }
        }
        return false;
    },

    uppyUploadButtonDisable() {
        const uploadButton = document.getElementById("uppy-upload");
        uploadButton.disabled = Crane.ui.elements.uppy.getFiles().length === 0;
    },

    init() {
        Crane.ui.initLanguageSelector();
        Crane.ui.initTable();
        Crane.ui.initDialogs();
        $(window).keydown(function (event) {
            if (event.code === 'Enter') {
                event.preventDefault();
                return false;
            }
        });
    },

    initTable() {
        if (Crane.ui.elements.table.length === 0 ) {
            return;
        }
        const dataTableLanguageUrl = document.querySelector("meta[name='_dataTableLanguageUrl']")?.content;
        let dataTableOptions = {
            aaSorting: [], // apply no sort by default
            paging: false,
            language: {
                url: dataTableLanguageUrl
            },
            initComplete: function() {
                Crane.ui.elements.datatable = this.api();
                if (Crane.ui.elements.table.data("has-write-access")) {
                    Crane.ui.initUppy();
                }
                Crane.ui.elements.table.css("visibility", "visible");
            }
        };
        if (Crane.ui.elements.table.data("has-write-access")) {
            dataTableOptions["layout"] = {
                topStart: {
                    buttons: [
                        {
                            action: () => Crane.ui.elements.uppyDialog.showModal(),
                            text: Crane.internationalization.t("index.upload.button")
                        },
                        {
                            action: () => Crane.ui.elements.createDirectoryDialog.showModal(),
                            text: Crane.internationalization.t("index.create_directory.button")
                        }
                    ]
                }
            }
        }
        Crane.ui.elements.table.DataTable(dataTableOptions);

        document.getElementById("directory-name").addEventListener("input", Crane.ui.disableCreateDirectory);
        document.getElementById("cancel-create-directory").addEventListener("click", Crane.ui.closeCreateDirectoryDialog);
        document.getElementById("create-directory").addEventListener("click", Crane.ui.createDirectory);
        Crane.ui.elements.createDirectoryForm.addEventListener('keydown', function (event) {
            if (event.code === "Enter") {
                event.preventDefault();
                Crane.ui.createDirectory(event);
            }
        });

        Crane.ui.disableCreateDirectory();

        const deleteButtons = document.querySelectorAll("button.delete-button");
        for (let deleteButton of deleteButtons) {
            deleteButton.addEventListener("click", () => {
                const objectName = deleteButton.dataset.filename !== undefined ? deleteButton.dataset.filename : deleteButton.dataset.dirname;
                const action = deleteButton.dataset.ispathcomponent === 'true' ? 'empty' : 'delete';
                const fileType = deleteButton.dataset.filename ? 'file' : 'directory';
                const message = Crane.internationalization.f(`index.${action}_${fileType}.confirmation`, objectName);

                if (window.confirm(message)) {
                    Crane.api.deleteObject(objectName, deleteButton);
                }
            });
        }
    },

    initUppy() {
        Crane.ui.elements.datatable.button(0)[0].node.id = "upload-button";
        Crane.ui.elements.uppy = new Uppy.Uppy();
        Crane.ui.elements.uppy.use(Uppy.Dashboard, {
            inline: true,
            target: '#uppy',
            showProgressDetails: true,
            hideUploadButton: true,
            hideCancelButton: true,
            fileManagerSelectionType: "both",
        }).use(Uppy.XHRUpload, {
            endpoint: (file) => {
                const uppy = document.getElementById("uppy")
                if (file.meta.relativePath) {
                    return uppy.attributes.action.value + file.meta.relativePath;
                }
                return uppy.attributes.action.value + file.name;
            },
            headers: () => {
                return {[Crane.api.csrfHeader]: Crane.api.csrfToken}
            },
            onAfterResponse(response) {
                if (response.status == 200) {
                    return
                }

                if (JSON.parse(response.responseText).data?.message) {
                    throw new Error(JSON.parse(response.responseText).data.message)
                }
            }
        })


        Crane.ui.elements.uppy.on("complete", (result) => {
            if (result.failed.length === 0) {
                document.location.reload();
            }
        });
        Crane.ui.elements.uppy.on("file-added", Crane.ui.uppyUploadButtonDisable);
        Crane.ui.elements.uppy.on("files-added", Crane.ui.uppyUploadButtonDisable);
        Crane.ui.elements.uppy.on("file-removed", Crane.ui.uppyUploadButtonDisable);
        Crane.ui.elements.uppy.on("upload-error", () => {
            const uploadButton = document.getElementById("uppy-upload");
            uploadButton.innerText = Crane.internationalization.t("index.retry.button")
            uploadButton.classList.remove("uploading");
            uploadButton.classList.add("retry");

            const addMore = document.getElementsByClassName("uppy-DashboardContent-addMore")[0];
            addMore.disabled = true;
        })

        Crane.ui.elements.uppyDialog.addEventListener("close", () => {
            const uploadButton = document.getElementById("uppy-upload");
            uploadButton.classList.remove("uploading");
            uploadButton.classList.remove("retry");
            uploadButton.innerText = Crane.internationalization.t("index.upload.button");

            const files = Crane.ui.elements.uppy.getState().files;
            for (const key in files) {
                const file = files[key];
                if (file.response?.status === 200) {
                    document.location.reload();
                    return;
                }
            }

            const addMore = document.getElementsByClassName("uppy-DashboardContent-addMore")[0];
            if (addMore) {
                addMore.disabled = false;
            }
            Crane.ui.elements.uppy.cancelAll();
        })

        document.getElementById("cancel-uppy-upload").addEventListener("click", () => {
            Crane.ui.elements.uppyDialog.close();
        });

        document.getElementById("uppy-upload").addEventListener("click", () => {
            const button = document.getElementById("uppy-upload");
            button.classList.add("uploading");
            button.innerText = Crane.internationalization.t("index.uploading") + "..."
            if (!button.classList.contains("retry")) {
                Crane.ui.elements.uppy.upload();
            } else {
                button.classList.remove("retry");
                Crane.ui.elements.uppy.retryAll();
            }
        });
        Crane.ui.uppyUploadButtonDisable();
    },

    initDialogs() {
        for (const dialog of document.getElementsByTagName('dialog')) {
            dialog.addEventListener('click', function (event) {
                if (event.target.open) {
                    const rect = this.getBoundingClientRect();
                    const isInDialog = (
                        rect.top <= event.clientY && event.clientY <= rect.top + rect.height &&
                        rect.left <= event.clientX && event.clientX <= rect.left + rect.width
                    );
                    if (!isInDialog) {
                        this.close();
                    }
                }
            });
        }
    },

    initLanguageSelector() {
        document.querySelector('#language-selector .dropdown-toggle').addEventListener('click', () => {
            document.querySelector('#language-selector').classList.toggle('open');
        });
        document.querySelectorAll('#language-selector li').forEach(li => {
            li.addEventListener('click', async () => {
                if (await Crane.api.changeLanguage(li.dataset.language)) {
                    window.location.reload();
                } else {
                    alert("Failed to change language!");
                }
                document.querySelector('#language-selector').classList.remove("open");
            });
        });
    }

}

document.addEventListener('DOMContentLoaded', async () => {
    await Crane.internationalization.init();
    Crane.ui.init();
});
