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
package eu.openanalytics.crane.service;

import org.springframework.stereotype.Service;

@Service
public class ThymeleafService {

    private final String iconBasePath;

    public ThymeleafService(IdentifierService identifierService) {
        iconBasePath = "/__assets/" + identifierService.instanceId + "/webjars/mdi__svg/7.4.47/svg/";
    }

    /**
     * Returns path to an icon.
     * Search for icons on <a href="https://pictogrammers.com/library/mdi/">pictogrammers.com</a>
     * @param name name of the icon
     * @return path to the icon
     */
    public String icon(String name) {
        return iconBasePath + name + ".svg";
    }

}
