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


}

document.addEventListener('DOMContentLoaded', () => {
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
    })
});
