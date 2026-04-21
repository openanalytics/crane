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
Crane.search = {

    async init() {
        const apiInfo = await Crane.api._getResponseJson(await fetch(Crane.api.buildURL('__search/api-info')));
        const searchEndpoint = Crane.api.buildURL("__search/" + Crane.api.repository);
        const urlPrefix = Crane.api.buildURL("");
        const {searchClient} = instantMeiliSearch(
            searchEndpoint.toString(),
            "",
            {
                requestInit: {
                    headers: {
                        [Crane.api.csrfHeader]: Crane.api.csrfToken,
                    },
                },
                meiliSearchParams: {
                    "distinct": "webUri"
                }
            }
        );

        const search = instantsearch({
            indexName: apiInfo.data.indexUid,
            searchClient
        });

        search.addWidgets([
            instantsearch.widgets.searchBox({
                container: '.search-bar'
            }),
            instantsearch.widgets.infiniteHits({
                container: '.result-list',
                templates: {
                    item: (hit, {html, components}) => {
                        return html`
                                        <a href="${Crane.api.buildURL(hit.webUri)}" class="result-item hit">
                                            <p class="result-title">${components.Highlight({hit, attribute: 'extractedMetadata.title'})}</p>
                                            <p class="result-snippet">${components.Highlight({hit, attribute: 'chunk'})}</p>
                                        </a>
                                `
                    }
                },
                transformItems(items) {
                    return items.filter((item) => {
                        if (item.id === "dummy") {
                            return false;
                        }
                        return true;
                    });
                },
            }),
            instantsearch.widgets.refinementList({
                container: ".facet-content-type",
                attribute: "extractedMetadata.humanReadableMediaType",
                sortBy: ["name"]
            })
        ]);

        search.on('render', () => {
            if (search.status === "idle") {
                if (document.querySelectorAll(".hit").length === 0) {
                    if (document.querySelectorAll(".ais-InfiniteHits-loadMore")[0] && !document.querySelectorAll(".ais-InfiniteHits-loadMore")[0].disabled) {
                        document.querySelectorAll(".ais-InfiniteHits-loadMore")[0].click();
                    }
                }
            }
        });

        search.start();
    }


};
