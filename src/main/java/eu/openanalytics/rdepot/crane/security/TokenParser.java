/**
 * Crane
 *
 * Copyright (C) 2021-2024 Open Analytics
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
package eu.openanalytics.rdepot.crane.security;

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TokenParser {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CraneConfig config;

    public TokenParser(CraneConfig config) {
        this.config = config;
    }

    /**
     * Maps the groups provided in the claimValue to {@link GrantedAuthority}.
     *
     * @return
     */
    public Set<GrantedAuthority> parseAuthorities(Map<String, Object> claims) {
        if (!config.hasOpenidGroupsClaim() || !claims.containsKey(config.getOpenidGroupsClaim())) {
            return Set.of();
        }
        Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
        for (String role : parseGroupsClaim(claims.get(config.getOpenidGroupsClaim()))) {
            String mappedRole = role.toUpperCase().startsWith("ROLE_") ? role : "ROLE_" + role;
            mappedAuthorities.add(new SimpleGrantedAuthority(mappedRole.toUpperCase()));
        }
        return mappedAuthorities;
    }

    public int parseUID(Map<String, Object> claims) {
        if (config.getOpenidPosixUIDClaim() == null) {
            return -1;
        }
        Object UID = claims.get(config.getOpenidPosixUIDClaim());
        if (UID instanceof String stringUID) {
            try {
                return Integer.parseInt(stringUID);
            } catch (NumberFormatException ignored) {
                logger.warn("User identifier could not be parsed as an integer {}", stringUID);
                return -1;
            }
        } else if (UID instanceof Integer intUID) {
            return intUID;
        }
        return -1;
    }

    public List<Integer> parseGIDS(Map<String, Object> claims) {
        if (config.getOpenidPosixGIDSClaim() == null) {
            return List.of();
        }
        Object GIDs = claims.get(config.getOpenidPosixGIDSClaim());
        if (GIDs instanceof String gid) {
            try {
                return List.of(Integer.parseInt(gid));
            } catch (NumberFormatException ignored) {
                logger.warn("Group identifier could not be parsed as an integer {}", gid);
                return List.of();
            }
        }
        if (GIDs instanceof Integer gid) {
            return List.of(gid);
        }
        if (GIDs instanceof List<?> listGIDS) {
            // TODO
            if (!listGIDS.isEmpty() && listGIDS.get(0) instanceof String) {
                return ((List<String>) listGIDS).stream().map(gid -> {
                    try {
                        return Integer.parseInt(gid);
                    } catch (NumberFormatException ignored) {
                        logger.warn("Group identifier could not be parsed as an integer {}", gid);
                        return -1;
                    }
                }).filter(gid -> gid != -1).toList();
            } else if (!listGIDS.isEmpty() && listGIDS.get(0) instanceof Integer) {
                return (List<Integer>) listGIDS;
            }
        }
        return List.of();
    }


    /**
     * Parses the claim containing the groups to a List of Strings.
     */
    private List<String> parseGroupsClaim(Object claimValue) {
        String groupsClaimName = config.getOpenidGroupsClaim();
        if (claimValue == null) {
            logger.debug(String.format("No groups claim with name %s found", groupsClaimName));
            return new ArrayList<>();
        } else {
            logger.debug(String.format("Matching claim found: %s -> %s (%s)", groupsClaimName, claimValue, claimValue.getClass()));
        }

        if (claimValue instanceof Collection) {
            List<String> result = new ArrayList<>();
            for (Object object : ((Collection<?>) claimValue)) {
                if (object != null) {
                    result.add(object.toString());
                }
            }
            logger.debug(String.format("Parsed groups claim as Java Collection: %s -> %s (%s)", groupsClaimName, result, result.getClass()));
            return result;
        }

        if (claimValue instanceof String) {
            List<String> result = new ArrayList<>();
            try {
                Object value = new JSONParser(JSONParser.MODE_PERMISSIVE).parse((String) claimValue);
                if (value instanceof List valueList) {
                    valueList.forEach(o -> result.add(o.toString()));
                }
            } catch (ParseException e) {
                // Unable to parse JSON
                logger.debug(String.format("Unable to parse claim as JSON: %s -> %s (%s)", groupsClaimName, claimValue, claimValue.getClass()));
            }
            logger.debug(String.format("Parsed groups claim as JSON: %s -> %s (%s)", groupsClaimName, result, result.getClass()));
            return result;
        }

        logger.debug(String.format("No parser found for groups claim (unsupported type): %s -> %s (%s)", groupsClaimName, claimValue, claimValue.getClass()));
        return new ArrayList<>();
    }

}
