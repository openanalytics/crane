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
        modalOverlay: document.getElementById("crane-modal-overlay"),
        popupOverlay: document.getElementById("crane-popup-overlay"),
        createDirectoryForm: document.getElementById("create-directory-form"),
        createDirectoryButton: document.getElementById("create-directory"),
        renameFileForm: document.getElementById("rename-file-form"),
        renameDirectoryForm: document.getElementById("rename-directory-form"),
        newFileNameInput: document.getElementById("new-file-name"),
        newDirectoryNameInput: document.getElementById("new-directory-name"),
        table: $("table"),
        datatable: null,
        uppy: null,
    },

    _popupResolve: null,

    showModal(modalId, focusSelector) {
        const overlay = Crane.ui.elements.modalOverlay;
        overlay.querySelectorAll('.crane-popup').forEach(p => p.style.display = 'none');
        const panel = document.getElementById(modalId);
        panel.style.display = 'flex';
        overlay.style.display = 'flex';
        setTimeout(function () {
            let target;
            if (focusSelector) {
                target = panel.querySelector(focusSelector);
            } else {
                const buttons = panel.querySelectorAll(
                    '.crane-popup__footer .crane-popup__btn-cancel:not(:disabled), ' +
                    '.crane-popup__footer .crane-popup__btn-submit:not(:disabled)'
                );
                target = buttons.length ? buttons[buttons.length - 1] : null;
            }
            if (target) {
                target.focus({ focusVisible: true });
            }
        }, 50);
    },

    hideModal() {
        const overlay = Crane.ui.elements.modalOverlay;
        overlay.style.display = 'none';
        overlay.querySelectorAll('.crane-popup').forEach(p => p.style.display = 'none');
    },

    showPopup(popupSelector) {
        return new Promise((resolve) => {
            Crane.ui._popupResolve = resolve;
            const overlay = Crane.ui.elements.popupOverlay;
            overlay.querySelectorAll('.crane-popup').forEach(p => p.style.display = 'none');
            const popup = document.querySelector(popupSelector);
            popup.style.display = 'flex';
            overlay.style.display = 'flex';
            setTimeout(function () {
                let btn;
                if (popup.classList.contains('crane-popup--destructive')) {
                    btn = popup.querySelector('.crane-popup__footer .crane-popup-dismiss');
                } else {
                    const buttons = popup.querySelectorAll(
                        '.crane-popup__footer .crane-popup-confirm, .crane-popup__footer .crane-popup-dismiss'
                    );
                    btn = buttons.length ? buttons[buttons.length - 1] : null;
                }
                if (btn) {
                    btn.focus({ focusVisible: true });
                }
            }, 50);
        });
    },

    hidePopup(result) {
        const overlay = Crane.ui.elements.popupOverlay;
        overlay.style.display = 'none';
        overlay.querySelectorAll('.crane-popup').forEach(p => p.style.display = 'none');
        if (Crane.ui._popupResolve) {
            Crane.ui._popupResolve(result);
            Crane.ui._popupResolve = null;
        }
    },

    showError(message) {
        document.getElementById("errorPopupMessage").textContent = message;
        return Crane.ui.showPopup('#errorPopup');
    },

    closeCreateDirectoryModal() {
        const cancelButton = document.getElementById("cancel-create-directory");
        cancelButton.disabled = false;
        if (Crane.ui.elements.createDirectoryButton.classList.contains("uploading")) {
            Crane.ui.elements.createDirectoryButton.lastElementChild.textContent = Crane.internationalization.t("index.create.button");
            Crane.ui.elements.createDirectoryButton.classList.remove("uploading");
        }
        Crane.ui.hideModal();
        Crane.ui.elements.createDirectoryForm.reset();
    },

    createDirectory(e) {
        e.preventDefault();
        const dirNameField = document.getElementById("directory-name");
        const cancelButton = document.getElementById("cancel-create-directory");
        cancelButton.disabled = true;
        Crane.ui.elements.createDirectoryButton.disabled = true;
        Crane.ui.elements.createDirectoryButton.lastElementChild.textContent = Crane.internationalization.t("index.creating.button") + "...";
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
            Crane.ui.closeCreateDirectoryModal();
            Crane.ui.showError(error);
        }).catch((error) => {
            Crane.ui.closeCreateDirectoryModal();
            Crane.ui.showError(error);
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
        Crane.ui.initDropdowns();
        Crane.ui.initHamburger();
        Crane.ui.initLanguageSelector();
        Crane.ui.initTable();
        Crane.ui.initPopupEvents();
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
        if (document.getElementById("crane-modal-overlay")) {
            document.getElementById("cancel-edit-metadata").addEventListener("click", Crane.ui.hideModal);
            document.getElementById("save-edit-metadata").addEventListener("click", Crane.ui.saveEditMetadataDialog);
            document.getElementById("cancel-rename-file").addEventListener("click", Crane.ui.closeRenameFileDialog);
            document.getElementById("rename-file").addEventListener("click", Crane.ui.renameFile);
            document.getElementById("cancel-rename-directory").addEventListener("click", Crane.ui.closeRenameDirectoryDialog);
            document.getElementById("rename-directory").addEventListener("click", Crane.ui.renameDirectory);
            document.getElementById("move-file").addEventListener("click", Crane.ui.moveFile);
            document.getElementById("cancel-move-file").addEventListener("click", Crane.ui.hideModal);
            document.getElementById("directory-name").addEventListener("input", Crane.ui.disableCreateDirectory);
            document.getElementById("cancel-create-directory").addEventListener("click", Crane.ui.closeCreateDirectoryModal);
            document.getElementById("create-directory").addEventListener("click", Crane.ui.createDirectory);
        }
        document.getElementById("upload-button")?.addEventListener("click", () => Crane.ui.showModal('uploadModal'));
        document.getElementById("create-directory-button")?.addEventListener("click", () => Crane.ui.showModal('createDirectoryModal', '#directory-name'));
        document.getElementById("search-button")?.addEventListener("click", Crane.ui.showSearchDialog);
        document.getElementById("upload-instructions-button")?.addEventListener("click", () => Crane.ui.showModal('uploadInstructionsModal'));

        const searchInput = document.getElementById("toolbar-search-input") || document.getElementById("repositories-search-input");
        if (searchInput) {
            searchInput.addEventListener("input", function () {
                if (Crane.ui.elements.datatable) {
                    Crane.ui.elements.datatable.search(this.value).draw();
                    Crane.ui.updateTableFooter();
                }
            });
        }

        document.querySelectorAll(".copy-current-button").forEach(btn =>
            btn.addEventListener("click", Crane.ui.copyCurrentPath)
        );
        document.querySelectorAll("#upload-instructions-body code").forEach(c => {
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

    initPopupEvents() {
        $(document).on('click', '.crane-modal-close', function() {
            Crane.ui.hideModal();
        });

        $(document).on('click', '#crane-modal-overlay', function(e) {
            if (e.target === this) {
                Crane.ui.hideModal();
            }
        });

        $(document).on('click', '.crane-popup-dismiss', function() {
            Crane.ui.hidePopup(false);
        });

        $(document).on('click', '.crane-popup-confirm', function() {
            Crane.ui.hidePopup(true);
        });

        $(document).on('click', '#crane-popup-overlay', function(e) {
            if (e.target === this) {
                Crane.ui.hidePopup(false);
            }
        });

        $(document).on('keydown', function(e) {
            if (e.key === 'Escape') {
                if ($('#crane-popup-overlay').is(':visible')) {
                    Crane.ui.hidePopup(false);
                } else if ($('#crane-modal-overlay').is(':visible')) {
                    Crane.ui.hideModal();
                }
            }
        });
    },

    initTable() {
        if (Crane.ui.elements.table.length === 0) {
            return;
        }
        const isRepositoriesPage = Crane.ui.elements.table.hasClass('repositories-table');
        const dataTableLanguageUrl = document.querySelector("meta[name='_dataTableLanguageUrl']")?.content;
        let dataTableOptions = {
            paging: false,
            aaSorting: [[0, 'asc'], [1, 'asc']],
            language: {
                url: dataTableLanguageUrl
            },
            searching: !Crane.search.enabled,
            initComplete: function () {
                Crane.ui.elements.datatable = this.api();
                if (!isRepositoriesPage && Crane.ui.elements.table.data("has-write-access")) {
                    Crane.ui.initUppy();
                }
                Crane.ui.elements.table.css("visibility", "visible");
                Crane.ui.updateTableFooter();
            }
        };
        if (!isRepositoriesPage) {
            dataTableOptions.columnDefs = [
                {
                    targets: 0,
                    visible: false,
                    searchable: false
                },
                {
                    targets: 2,
                    render: function (data, type, row) {
                        if (type === 'sort' || type === 'filter') {
                            return data;
                        }

                        if (data && data.trim()) {
                            const d = new Date(data);
                            if (!isNaN(d.getTime())) {
                                return d.toLocaleString(document.documentElement.lang, {
                                    dateStyle: 'long',
                                    timeStyle: 'short'
                                });
                            }
                        }
                        return "\u2014";
                    }
                },
                {
                    targets: 3,
                    render: function (data, type, row) {
                        if (type === 'sort' || type === 'filter') {
                            return data;
                        }
                        if (data && data.trim()) {
                            return data;
                        }
                        return "\u2014";
                    }
                }
            ];
        }
        Crane.ui.elements.table.DataTable(dataTableOptions);

        if (Crane.ui.elements.createDirectoryForm) {
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
        }

        const deleteButtons = document.querySelectorAll("button.delete-button");
        for (let deleteButton of deleteButtons) {
            deleteButton.addEventListener("click", async () => {
                const objectName = deleteButton.dataset.filename !== undefined ? deleteButton.dataset.filename : deleteButton.dataset.dirname;
                const action = deleteButton.dataset.ispathcomponent === 'true' ? 'empty' : 'delete';
                const fileType = deleteButton.dataset.filename ? 'file' : 'directory';
                const message = Crane.internationalization.f(`index.${action}_${fileType}.confirmation`, objectName);

                const titleKey = action === 'empty' ? 'index.empty.title' : 'index.delete.title';
                document.getElementById("deleteConfirmTitle").textContent = Crane.internationalization.t(titleKey);
                document.getElementById("deleteConfirmMessage").textContent = message;

                const confirmed = await Crane.ui.showPopup('#deleteConfirmPopup');
                if (confirmed) {
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


        const dashboardEl = document.querySelector('#uppy .uppy-Dashboard-innerWrap');
        if (dashboardEl) {
            dashboardEl.style.cursor = 'pointer';
            dashboardEl.addEventListener('click', (e) => {
                const browseBtn = dashboardEl.querySelector('.uppy-Dashboard-browse');
                const isInteractive = e.target.closest('a, button, input, label, .uppy-DashboardContent-addMore');
                if (browseBtn && !isInteractive) {
                    browseBtn.click();
                }
            });
        }

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
            uploadButton.lastElementChild.textContent = Crane.internationalization.t("index.retry.button");
            uploadButton.classList.remove("uploading");
            uploadButton.classList.add("retry");

            const addMore = document.getElementsByClassName("uppy-DashboardContent-addMore")[0];
            addMore.disabled = true;
        })

        document.getElementById("cancel-uppy-upload").addEventListener("click", Crane.ui.closeUploadModal);

        document.querySelector('#uploadModal .crane-popup__close').addEventListener("click", Crane.ui.closeUploadModal);

        document.getElementById("uppy-upload").addEventListener("click", () => {
            const button = document.getElementById("uppy-upload");
            button.classList.add("uploading");
            button.lastElementChild.textContent = Crane.internationalization.t("index.uploading") + "...";
            if (!button.classList.contains("retry")) {
                Crane.ui.elements.uppy.upload();
            } else {
                button.classList.remove("retry");
                Crane.ui.elements.uppy.retryAll();
            }
        });
        Crane.ui.uppyUploadButtonDisable();
    },

    closeUploadModal() {
        const uploadButton = document.getElementById("uppy-upload");
        uploadButton.classList.remove("uploading");
        uploadButton.classList.remove("retry");
        uploadButton.lastElementChild.textContent = Crane.internationalization.t("index.upload.button");

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
    },

    initDropdowns() {
        document.addEventListener('click', function(e) {
            const toggle = e.target.closest('.dropdown-toggle');
            if (toggle) {
                const dropdown = toggle.closest('.crane-dropdown');
                document.querySelectorAll('.crane-dropdown.open').forEach(d => {
                    if (d !== dropdown) d.classList.remove('open');
                });
                dropdown.classList.toggle('open');
                e.stopPropagation();
            } else {
                document.querySelectorAll('.crane-dropdown.open').forEach(d => d.classList.remove('open'));
            }
        });
    },

    initHamburger() {
        document.getElementById('hamburger-btn').addEventListener('click', function() {
            document.getElementById('header-actions').classList.toggle('mobile-open');
        });
    },

    initLanguageSelector() {
        document.querySelectorAll('#language-selector li').forEach(li => {
            li.addEventListener('click', async () => {
                if (await Crane.api.changeLanguage(li.dataset.language)) {
                    window.location.replace(window.location.origin + window.location.pathname);
                } else {
                    Crane.ui.showError("Failed to change language!");
                }
                document.querySelector('#language-selector').classList.remove("open");
            });
        });
    },

    async showEditMetadataDialog(button, fileName) {
        button.classList.add("uploading");
        const response = await Crane.api.getFile(".crane/metadata/schema.json")
        if (response === null) {
            Crane.ui.showError("Failed to get metadata schema");
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
        Crane.ui.showModal('editMetadataModal');
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

    async submitMetadataDialog(filename, result) {
        const success = await Crane.api.uploadStringAsFile(
            ".crane/metadata/" + filename + ".yaml",
            JSON.stringify(result.formData)
        );
        if (!success) {
            Crane.ui.showError("Failed to update metadata");
        } else {
            Crane.ui.hideModal();
        }
    },

    saveEditMetadataDialog() {
        document.querySelector("#edit-metadata-form button[type=submit]").click();
    },

    async showSearchDialog() {
        Crane.ui.showModal('searchModal', '.ais-SearchBox-input');
        await Crane.search.init();
    },

    fileBeingRenamed: null,

    async showRenameFileDialog(button, fileName) {
        Crane.ui.fileBeingRenamed = fileName;
        Crane.ui.elements.newFileNameInput.value = fileName;
        Crane.ui.showModal('renameFileModal', '#new-file-name');
    },

    async renameFile() {
        const newFilename = Crane.ui.elements.newFileNameInput.value;
        const error = await Crane.api.renameResource(Crane.ui.fileBeingRenamed, Crane.api.path + newFilename);
        if (error) {
            Crane.ui.showError(error);
        } else {
            Crane.ui.closeRenameFileDialog();
            window.location.replace(window.location.origin + window.location.pathname + "?highlight-file=" + newFilename)
        }
    },

    closeRenameFileDialog() {
        Crane.ui.elements.newFileNameInput.value = "";
        Crane.ui.hideModal();
    },

    directoryBeingRenamed: null,

    async showRenameDirectoryDialog(button, directoryName) {
        Crane.ui.directoryBeingRenamed = directoryName;
        Crane.ui.elements.newDirectoryNameInput.value = directoryName;
        Crane.ui.showModal('renameDirectoryModal', '#new-directory-name');
    },

    async renameDirectory() {
        const newDirectoryName = Crane.ui.elements.newDirectoryNameInput.value;
        const error = await Crane.api.renameResource(Crane.ui.directoryBeingRenamed, Crane.api.path + newDirectoryName);
        if (error) {
            Crane.ui.showError(error);
        } else {
            Crane.ui.closeRenameDirectoryDialog();
            window.location.replace(window.location.origin + window.location.pathname + "?highlight-file=" + newDirectoryName)
        }
    },

    closeRenameDirectoryDialog() {
        Crane.ui.elements.newDirectoryNameInput.value = "";
        Crane.ui.hideModal();
    },

    resourceBeingMoved: "",
    moveDestination: "",

    async showMoveFileDialog(button, fileName) {
        Crane.ui.resourceBeingMoved = fileName;
        // reset state
        Crane.ui.moveDestination = "";
        const root = document.querySelector("#moveFileModal .file-tree .root");
        const destination = document.querySelector("#moveFileModal .destination");
        destination.innerText = "";
        destination.classList.remove("visible");
        root.innerHTML = "";
        // add repository as root
        root.appendChild(Crane.ui.buildNode(Crane.api.repository, "/" + Crane.api.repository + "/"));
        // show modal
        Crane.ui.showModal('moveFileModal');
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
            const destination = document.querySelector("#moveFileModal .destination");
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
            Crane.ui.showError(error);
        } else {
            Crane.ui.hideModal();
            window.location.replace(Crane.api.buildURL(Crane.ui.moveDestination) + "?highlight-file=" + Crane.ui.resourceBeingMoved);
        }
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
    },

    updateTableFooter() {
        const tableContainer = document.querySelector('.table-container');
        if (!tableContainer) return;
        let footer = document.querySelector('.table-footer') || document.querySelector('.repositories-footer');
        if (!footer) {
            footer = document.createElement('div');
            footer.className = 'table-footer';
            tableContainer.parentNode.insertBefore(footer, tableContainer.nextSibling);
        }
        if (Crane.ui.elements.datatable) {
            const total = Crane.ui.elements.datatable.rows().count();
            const visible = Crane.ui.elements.datatable.rows({search: 'applied'}).count();
            if (visible === total) {
                footer.textContent = `Showing 1 to ${total} of ${total} entries`;
            } else {
                footer.textContent = `Showing ${visible} of ${total} entries`;
            }
        }
    }
}

document.addEventListener('DOMContentLoaded', async () => {
    await Crane.internationalization.init();
    Crane.ui.init();
});
