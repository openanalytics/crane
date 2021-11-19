package eu.openanalytics.rdepot.crane.security;

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EnableWebSecurity
public class WebSecurity extends WebSecurityConfigurerAdapter {

    private final CraneConfig config;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public WebSecurity(CraneConfig config) {
        this.config = config;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        //@formatter:off
        http
            .csrf().disable()
            .authorizeRequests()
                .antMatchers("/.well-known/configured-openid-configuration").permitAll()
                .antMatchers("/repo/{repoName}/**").access("@accessControlService.canAccess(authentication, #repoName)")
                .anyRequest().authenticated()
            .and()
            .oauth2ResourceServer()
                .jwt()
                    .jwkSetUri(config.getJwksUri())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                .and()
            .and()
            .oauth2Login()
                .userInfoEndpoint()
                    .userAuthoritiesMapper(new NullAuthoritiesMapper())
                    .oidcUserService(oidcUserService())
                .and()
            .and()
                .oauth2Client();
        // @formatter:on
   }

    private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        // Use a custom UserService that respects our username attribute config and extract the authorities from the ID token
        return new OidcUserService() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
                Object claimValue = userRequest.getIdToken().getClaims().get(config.getOpenidRolesClaim());
                Set<GrantedAuthority> mappedAuthorities = mapAuthorities( config.getOpenidRolesClaim(), claimValue);

                return new DefaultOidcUser(mappedAuthorities,
                    userRequest.getIdToken(),
                    config.getOpenidUsernameClaim()
                );
            }
        };
    }

    /**
     * Authorities mapper when an Oauth2 JWT is used.
     * I.e. when the user is authenticated by passing an OAuth2 Access token as Bearer token in the Authorization header.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            if (!config.hasOpenidRolesClaim()) {
                return new ArrayList<>();
            }
            Object claimValue = jwt.getClaims().get(config.getOpenidRolesClaim());
            return mapAuthorities( config.getOpenidRolesClaim(), claimValue);
        });
        converter.setPrincipalClaimName(config.getOpenidUsernameClaim());
        return converter;
    }

    /**
     * Maps the roles provided in the claimValue to {@link GrantedAuthority}.
     * @return
     */
    private Set<GrantedAuthority> mapAuthorities(String rolesClaimName, Object claimValue) {
        Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
        for (String role: parseRolesClaim(rolesClaimName, claimValue)) {
            String mappedRole = role.toUpperCase().startsWith("ROLE_") ? role : "ROLE_" + role;
            mappedAuthorities.add(new SimpleGrantedAuthority(mappedRole.toUpperCase()));
        }
        return mappedAuthorities;
    }

    /**
     * Parses the claim containing the roles to a List of Strings.
     * See #25549 and TestOpenIdParseClaimRoles
     */
    private List<String> parseRolesClaim(String rolesClaimName, Object claimValue) {
        if (claimValue == null) {
            logger.debug(String.format("No roles claim with name %s found", rolesClaimName));
            return new ArrayList<>();
        } else {
            logger.debug(String.format("Matching claim found: %s -> %s (%s)", rolesClaimName, claimValue, claimValue.getClass()));
        }

        if (claimValue instanceof Collection) {
            List<String> result = new ArrayList<>();
            for (Object object : ((Collection<?>) claimValue)) {
                if (object != null) {
                    result.add(object.toString());
                }
            }
            logger.debug(String.format("Parsed roles claim as Java Collection: %s -> %s (%s)", rolesClaimName, result, result.getClass()));
            return result;
        }

        if (claimValue instanceof String) {
            List<String> result = new ArrayList<>();
            try {
                Object value = new JSONParser(JSONParser.MODE_PERMISSIVE).parse((String) claimValue);
                if (value instanceof List) {
                    List<?> valueList = (List<?>) value;
                    valueList.forEach(o -> result.add(o.toString()));
                }
            } catch (ParseException e) {
                // Unable to parse JSON
                logger.debug(String.format("Unable to parse claim as JSON: %s -> %s (%s)", rolesClaimName, claimValue, claimValue.getClass()));
            }
            logger.debug(String.format("Parsed roles claim as JSON: %s -> %s (%s)", rolesClaimName, result, result.getClass()));
            return result;
        }

        logger.debug(String.format("No parser found for roles claim (unsupported type): %s -> %s (%s)", rolesClaimName, claimValue, claimValue.getClass()));
        return new ArrayList<>();
    }

}
