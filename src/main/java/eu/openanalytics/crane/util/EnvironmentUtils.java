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

import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnvironmentUtils {

    /**
     * Reads a property from the environment (i.e. config file) and parse it as a list.
     * The following syntax's are supported:
     *
     * Single value:
     *      myProperty: abc
     *
     * Multiple comma separated values:
     *      myProperty: abc, xyz
     *
     * Single-line YAML array:
     *      myProperty: [abc, xyz]
     *
     * Multiline YAML array:
     *      myProperty:
     *       - abc
     *       - xyz
     * @param environment environment to read the property from
     * @param propertyName property name
     * @return list of values or null if the property does not exist or is empty
     */
    public static List<String> readList(Environment environment, String propertyName) {
        String[] singleValueOrCommaSeparated = environment.getProperty(propertyName, String[].class);
        if (singleValueOrCommaSeparated != null) {
            return List.of(singleValueOrCommaSeparated);
        }

        List<String> result = new ArrayList<>();
        int i = 0;
        String value = environment.getProperty(String.format(propertyName + "[%d]", i));
        while (value != null) {
            result.add(value);
            i++;
            value = environment.getProperty(String.format(propertyName + "[%d]", i));
        }

        if (result.isEmpty()) {
            return null;
        }

        return result;
    }

    /**
     * Reads a property from the environment (i.e. config file). Supports reading a fallback property.
     * This is useful to rename properties.
     * @param environment environment to read the property from
     * @param propertyName property name
     * @param fallbackPropertyName property name used when no value found
     * @param targetType the expected type of the property value
     * @param defaultValue the default value to return if no value is found
     * @return the property value or null
     */
    public static <T> T getProperty(Environment environment, String propertyName, String fallbackPropertyName, Class<T> targetType, T defaultValue) {
        T result = environment.getProperty(propertyName, targetType);
        if (result != null) {
            return result;
        }
        return environment.getProperty(fallbackPropertyName, targetType, defaultValue);
    }

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
