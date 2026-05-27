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
        editMetadataDialog: document.getElementById("edit-metadata-dialog"),
        renameFileDialog: document.getElementById("rename-file-dialog"),
        renameDirectoryDialog: document.getElementById("rename-directory-dialog"),
        renameFileForm: document.getElementById("rename-file-form"),
        renameDirectoryForm: document.getElementById("rename-directory-form"),
        searchDialog: document.getElementById("search-dialog"),
        newFileNameInput: document.getElementById("new-file-name"),
        newDirectoryNameInput: document.getElementById("new-directory-name"),
        moveFileDialog: document.getElementById("move-file-dialog"),
        uploadInstructionsDialog: document.getElementById("upload-instructions-dialog"),
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
            deleteInfo.innerText = `${data.deleted}/${data.total}`
        }
    },

    updateDeleting(button, data) {
        let deleteInfo = document.getElementById(`progress-${button.dataset.dirname || button.dataset.filename}`);
        if (deleteInfo !== null || deleteInfo !== undefined) {
            deleteInfo.innerText = `${data.deleted}/${data.total}`
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
        document.querySelector(".files-table").addEventListener("click", function (event) {
            const button = event.target.closest("button");
            if (button && this.contains(button) && button.classList.contains("metadata-button")) {
                const filename = button.dataset?.filename;
                Crane.ui.showEditMetadataDialog(button, filename);
            }
            if (button && this.contains(button) && button.classList.contains("copy-button")) {
                const filename = button.dataset?.filename;
                Crane.ui.copyLinkToFile(button, filename);
            }
            if (button && this.contains(button) && button.classList.contains("rename-file-button")) {
                const filename = button.dataset?.filename;
                Crane.ui.showRenameFileDialog(button, filename);
            }
            if (button && this.contains(button) && button.classList.contains("rename-directory-button")) {
                const dirname = button.dataset?.dirname;
                Crane.ui.showRenameDirectoryDialog(button, dirname);
            }
            if (button && this.contains(button) && button.classList.contains("move-file-button")) {
                const filename = button.dataset?.filename;
                document.querySelector(".move-file-header").style.display = "block";
                document.querySelector(".move-directory-header").style.display = "none";
                Crane.ui.showMoveFileDialog(button, filename);
            }
            if (button && this.contains(button) && button.classList.contains("move-directory-button")) {
                const filename = button.dataset?.dirname;
                document.querySelector(".move-file-header").style.display = "none";
                document.querySelector(".move-directory-header").style.display = "block";
                Crane.ui.showMoveFileDialog(button, filename);
            }
        });
        document.getElementById("cancel-edit-metadata").addEventListener("click", Crane.ui.closeEditMetadataDialog);
        document.getElementById("save-edit-metadata").addEventListener("click", Crane.ui.saveEditMetadataDialog);
        document.getElementById("cancel-search").addEventListener("click", Crane.ui.closeSearchDialog);
        document.getElementById("cancel-rename-file").addEventListener("click", Crane.ui.closeRenameFileDialog);
        document.getElementById("rename-file").addEventListener("click", Crane.ui.renameFile);
        document.getElementById("cancel-rename-directory").addEventListener("click", Crane.ui.closeRenameDirectoryDialog);
        document.getElementById("rename-directory").addEventListener("click", Crane.ui.renameDirectory);
        document.getElementById("move-file").addEventListener("click", Crane.ui.moveFile);
        document.getElementById("cancel-move-file").addEventListener("click", Crane.ui.closeMoveFileDialog);
        document.getElementById("directory-name").addEventListener("input", Crane.ui.disableCreateDirectory);
        document.getElementById("cancel-create-directory").addEventListener("click", Crane.ui.closeCreateDirectoryDialog);
        document.getElementById("create-directory").addEventListener("click", Crane.ui.createDirectory);
        document.getElementById("upload-button").addEventListener("click", () => Crane.ui.elements.uppyDialog.showModal());
        document.getElementById("create-directory-button").addEventListener("click", () => Crane.ui.elements.createDirectoryDialog.showModal());
        const searchButton = document.getElementById("search-button");
        if (searchButton) {
            searchButton.addEventListener("click", () => Crane.ui.showSearchDialog());
        }
        document.getElementById("upload-instructions-button").addEventListener("click", () => Crane.ui.elements.uploadInstructionsDialog.showModal());
        document.getElementById("cancel-upload-instructions").addEventListener("click", () => Crane.ui.elements.uploadInstructionsDialog.close());

        document.querySelectorAll(".copy-current-button").forEach(btn =>
            btn.addEventListener("click", Crane.ui.copyCurrentPath)
        );
        document.querySelectorAll("#upload-instructions-dialog code").forEach(c => {
            const button = document.getElementById('code-copy-button').content.cloneNode(true);
            c.parentNode.insertBefore(button, c);
            c.parentNode.querySelector("button").addEventListener('click', async () => {
                // Clone the node and remove the button before reading text
                const clone = c.cloneNode(true);
                clone.querySelectorAll('button').forEach(b => b.remove());
                await navigator.clipboard.writeText(clone.textContent.trim());

                const popover = c.parentNode.querySelector('.copy-popover');
                popover.classList.add('visible');

                clearTimeout(popover._hideTimeout);
                popover._hideTimeout = setTimeout(() => popover.classList.remove('visible'), 1500)
            });
        });

        const params = new URLSearchParams(window.location.search);
        const value = params.get('highlight-file');

        if (value) {
            const escaped = CSS.escape(value);
            const tr = document.querySelector(`tr[data-filename="${escaped}"]`) || document.querySelector(`tr[data-dirname="${escaped}"]`);
            tr.classList.add('highlight');
        }
    },

    initTable() {
        if (Crane.ui.elements.table.length === 0) {
            return;
        }
        const dataTableLanguageUrl = document.querySelector("meta[name='_dataTableLanguageUrl']")?.content;
        let dataTableOptions = {
            aaSorting: [], // apply no sort by default
            paging: false,
            language: {
                url: dataTableLanguageUrl
            },
            searching: !Crane.search.enabled,
            columnDefs: [
                {
                    targets: 1,
                    render: function (data, type, row) {
                        if (type === 'sort' || type === 'filter') {
                            return data;
                        }

                        if (data) {
                            return new Date(data).toLocaleString(document.documentElement.lang, {
                                dateStyle: 'long',
                                timeStyle: 'short'
                            });
                        }
                        return "";
                    }
                }
            ],
            initComplete: function () {
                Crane.ui.elements.datatable = this.api();
                if (Crane.ui.elements.table.data("has-write-access")) {
                    Crane.ui.initUppy();
                }
                Crane.ui.elements.table.css("visibility", "visible");
            }
        };
        Crane.ui.elements.table.DataTable(dataTableOptions);
        Crane.ui.elements.createDirectoryForm.addEventListener('keydown', function (event) {
            if (event.code === "Enter") {
                event.preventDefault();
                Crane.ui.createDirectory(event);
            }
        });
        Crane.ui.elements.renameFileForm.addEventListener('keydown', function (event) {
            if (event.code === "Enter") {
                event.preventDefault();
                Crane.ui.renameFile();
            }
        });
        Crane.ui.elements.renameDirectoryForm.addEventListener('keydown', function (event) {
            if (event.code === "Enter") {
                event.preventDefault();
                Crane.ui.renameDirectory();
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
                const uppy = document.getElementById("uppy");
                const uppyReplace = document.getElementById("uppy-replace");
                let query = "";
                if (uppyReplace.checked) {
                    query = "?replace=true";
                }
                if (file.meta.relativePath) {
                    return uppy.attributes.action.value + file.meta.relativePath + query;
                }
                return uppy.attributes.action.value + file.name + query;
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
                if (result.successful.length === 1) {
                    const name = result.successful[0].name;
                    window.location.replace(window.location.origin + window.location.pathname + "?highlight-file=" + name)
                } else {
                    window.location.replace(window.location.origin + window.location.pathname);
                }
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
                    window.location.replace(window.location.origin + window.location.pathname);
                } else {
                    alert("Failed to change language!");
                }
                document.querySelector('#language-selector').classList.remove("open");
            });
        });
    },

    async showEditMetadataDialog(button, fileName) {
        button.classList.add("uploading");
        const response = await Crane.api.getFile(".crane/metadata/schema.json")
        if (response === null) {
            alert("Failed to get metadata schema");
            return
        }
        const schema = await response.json();
        const extractedMetadata = await Crane.api.getExtractedMetadataFile(fileName);
        if (extractedMetadata) {
            Crane.ui.showExtractedMetadata(extractedMetadata);
        }
        const formData = await Crane.api.getMetadataFile(fileName);
        rjsfForm({
                schema,
                onSubmit: function (result) {
                    Crane.ui.submitMetadataDialog(fileName, result);
                },
                formData: formData
            },
            document.getElementById("edit-metadata-form")
        );
        button.classList.remove("uploading");
        Crane.ui.elements.editMetadataDialog.showModal()
    },

    showExtractedMetadata(extractedMetadata) {
        const container = document.getElementById("extracted-metadata-container");
        if (!container) return;

        container.style.display = "block";
        for (const field of container.querySelectorAll("[data-field]")) {
            const key = field.dataset.field;
            const value = extractedMetadata[key];

            if (value === undefined || value === null) {
                field.textContent = "";
                continue;
            }

            if (key === "authors") {
                const ul = document.createElement("ul");
                for (const author of value) {
                    const li = document.createElement("li");
                    li.textContent = author;
                    ul.appendChild(li);
                }
                field.innerHTML = "";
                field.appendChild(ul);
            } else {
                field.textContent = String(value);
            }
        }
    },

    closeEditMetadataDialog() {
        Crane.ui.elements.editMetadataDialog.close();
    },

    async submitMetadataDialog(filename, result) {
        const success = await Crane.api.uploadStringAsFile(
            ".crane/metadata/" + filename + ".yaml",
            JSON.stringify(result.formData)
        );
        if (!success) {
            alert("Failed to update metadata");
        } else {
            Crane.ui.closeEditMetadataDialog();
        }
    },

    saveEditMetadataDialog() {
        document.querySelector("#edit-metadata-form button[type=submit]").click();
    },

    async showSearchDialog() {
        Crane.ui.elements.searchDialog.showModal()
        await Crane.search.init();
    },

    closeSearchDialog() {
        Crane.ui.elements.searchDialog.close();
    },

    fileBeingRenamed: null,

    async showRenameFileDialog(button, fileName) {
        Crane.ui.fileBeingRenamed = fileName;
        Crane.ui.elements.newFileNameInput.value = fileName;
        Crane.ui.elements.renameFileDialog.showModal()
    },

    async renameFile() {
        const newFilename = Crane.ui.elements.newFileNameInput.value;
        const error = await Crane.api.renameResource(Crane.ui.fileBeingRenamed, Crane.api.path + newFilename);
        if (error) {
            alert(error);
        } else {
            Crane.ui.closeRenameFileDialog();
            window.location.replace(window.location.origin + window.location.pathname + "?highlight-file=" + newFilename)
        }
    },

    closeRenameFileDialog() {
        Crane.ui.elements.newFileNameInput.value = "";
        Crane.ui.elements.renameFileDialog.close();
    },

    directoryBeingRenamed: null,

    async showRenameDirectoryDialog(button, directoryName) {
        Crane.ui.directoryBeingRenamed = directoryName;
        Crane.ui.elements.newDirectoryNameInput.value = directoryName;
        Crane.ui.elements.renameDirectoryDialog.showModal()
    },

    async renameDirectory() {
        const newDirectoryName = Crane.ui.elements.newDirectoryNameInput.value;
        const error = await Crane.api.renameResource(Crane.ui.directoryBeingRenamed, Crane.api.path + newDirectoryName);
        if (error) {
            alert(error);
        } else {
            Crane.ui.closeRenameFileDialog();
            window.location.replace(window.location.origin + window.location.pathname + "?highlight-file=" + newDirectoryName)
        }
    },

    closeRenameDirectoryDialog() {
        Crane.ui.elements.newDirectoryNameInput.value = "";
        Crane.ui.elements.renameDirectoryDialog.close();
    },

    resourceBeingMoved: "",
    moveDestination: "",

    async showMoveFileDialog(button, fileName) {
        Crane.ui.resourceBeingMoved = fileName;
        // reset state
        Crane.ui.moveDestination = "";
        const root = document.querySelector("#move-file-dialog .file-tree .root");
        const destination = document.querySelector("#move-file-dialog .destination");
        destination.innerText = "";
        destination.classList.remove("visible");
        root.innerHTML = "";
        // add repository as root
        root.appendChild(Crane.ui.buildNode(Crane.api.repository, "/" + Crane.api.repository + "/"));
        // show dialog
        Crane.ui.elements.moveFileDialog.showModal()
    },

    buildNode(name, path) {
        const li = document.createElement('li');

        const dir = document.createElement('div');
        dir.className = 'dir';
        dir.innerHTML = `<span class="tree-icon"></span><span class="name"></span>`;
        dir.querySelector(".name").innerText = name;

        const children = document.createElement('ul');
        children.className = 'children';

        let loaded = false;
        dir.querySelector(".tree-icon").addEventListener('click', async () => {
            // expand node
            const isOpen = dir.classList.toggle('open');
            children.classList.toggle('visible', isOpen);

            if (isOpen && !loaded) {
                loaded = true;
                const entries = await Crane.api.listDirectories(path);
                entries.forEach(({name, path}) => {
                    children.appendChild(Crane.ui.buildNode(name, path));
                });
            }
        });

        dir.querySelector(".name").addEventListener('click', async () => {
            // select node
            document.querySelectorAll(".file-tree .selected").forEach(e => {
                e.classList.remove("selected");
            })
            dir.classList.add('selected');
            const destination = document.querySelector("#move-file-dialog .destination");
            destination.innerText = path + Crane.ui.resourceBeingMoved;
            destination.classList.add("visible");
            const moveFileButton = document.getElementById("move-file");
            moveFileButton.disabled = false;
            Crane.ui.moveDestination = path;
        });

        li.appendChild(dir);
        li.appendChild(children);
        return li;
    },

    async moveFile() {
        const destination = Crane.ui.moveDestination + Crane.ui.resourceBeingMoved;
        const error = await Crane.api.renameResource(Crane.ui.resourceBeingMoved, destination);
        if (error) {
            alert(error);
        } else {
            Crane.ui.closeMoveFileDialog();
            window.location.replace(Crane.api.buildURL(Crane.ui.moveDestination) + "?highlight-file=" + Crane.ui.resourceBeingMoved);
        }
    },

    closeMoveFileDialog() {
        Crane.ui.elements.moveFileDialog.close();
    },

    async copyLinkToFile(button, fileName) {
        await navigator.clipboard.writeText(window.location.pathname + fileName);
        const popover = button.querySelector('.copy-popover');
        popover.classList.add('visible');

        clearTimeout(popover._hideTimeout);
        popover._hideTimeout = setTimeout(() => popover.classList.remove('visible'), 1500)
    },

    async copyCurrentPath() {
        await navigator.clipboard.writeText(window.location.pathname);
        const popover = document.querySelector('.copy-current-popover');
        popover.classList.add('visible');

        clearTimeout(popover._hideTimeout);
        popover._hideTimeout = setTimeout(() => popover.classList.remove('visible'), 1500)
    }
}

document.addEventListener('DOMContentLoaded', async () => {
    await Crane.internationalization.init();
    Crane.ui.init();
});
