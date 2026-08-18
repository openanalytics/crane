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
package eu.openanalytics.crane.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnvironmentUtils {

    /**
     * Converts the nested lists (in maps) to the proper lists instead of list-like maps.
     *
     * When reading a nested YAML object from the configuration file, Spring can convert it to a
     * {@code Map<String, Object>}. The value is always a Map, never a List. If an array is used in the nested
     * YAML, this is converted to map with (string) index "0", "1" etc. This function converts these maps
     * to proper lists (at any depth of the object).
     * @param value the value to convert
     * @return converted value
     */
    public static Object convertedNestedLists(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                .map(EnvironmentUtils::convertedNestedLists)
                .toList();
        }
        if (value instanceof Map<?, ?> map) {
            if (mapIsAlist(map)) {
                // convert to list, Spring uses LinkedHashMap so order should be correct
                return map.values().stream()
                    .map(EnvironmentUtils::convertedNestedLists)
                    .toList();
            } else {
                Map<Object, Object> result = new LinkedHashMap<>();
                map.forEach((key, value1) -> result.put(key, EnvironmentUtils.convertedNestedLists(value1)));
                return result;
            }
        }
        return value;
    }

    /**
     * Checks whether this map is actually representing a list.
     * This is a map with (string) index "0", "1" etc.
     *
     * @param map the map to check
     * @return whether this map is a list
     */
    private static boolean mapIsAlist(Map<?,?> map) {
        for (int i = 0; i < map.size(); i++) {
            if (!map.containsKey(Integer.toString(i))) {
                return false;
            }
        }
        return true;
    }

}
