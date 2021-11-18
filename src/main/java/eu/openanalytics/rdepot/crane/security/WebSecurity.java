package eu.openanalytics.rdepot.crane.security;

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EnableWebSecurity
public class WebSecurity extends WebSecurityConfigurerAdapter {

    private final CraneConfig config;

    private final Environment environment;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final static String PROP_OPENID_ROLES_CLAIM = "app.openid-roles-claim";

    public WebSecurity(CraneConfig config, Environment environment) {
        this.config = config;
        this.environment = environment;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        //@formatter:off
        http
            .csrf().disable()
            .authorizeRequests()
                .antMatchers("/.well-known/configured-openid-configuration").permitAll()
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
                .userAuthoritiesMapper(createAuthoritiesMapper())
                .and()
            .and()
                .oauth2Client();
        // @formatter:on

   }

    /**
     * Authorities mapper when an Oauth2 JWT is used.
     * I.e. when the user is authenticated by passing an OAuth2 Access token as Bearer token in the Authorization header.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        String rolesClaimName = environment.getProperty(PROP_OPENID_ROLES_CLAIM);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            if (rolesClaimName == null || rolesClaimName.isEmpty()) {
                return new ArrayList<>();
            }
            Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
            Object claimValue = jwt.getClaims().get(rolesClaimName);
            mapAuthorities(mappedAuthorities, rolesClaimName, claimValue);
            return mappedAuthorities;
        });
        return converter;
    }

    /**
     * Authorities mapper when OpenID is used.
     * I.e. when the user is authenticated in the browser, using a Java session.
     */
    private GrantedAuthoritiesMapper createAuthoritiesMapper() {
        String rolesClaimName = environment.getProperty(PROP_OPENID_ROLES_CLAIM);
        if (rolesClaimName == null || rolesClaimName.isEmpty()) {
            return authorities -> authorities;
        } else {
            return authorities -> {
                Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
                for (GrantedAuthority auth: authorities) {
                    if (auth instanceof OidcUserAuthority) {
                        OidcIdToken idToken = ((OidcUserAuthority) auth).getIdToken();

                        Object claimValue = idToken.getClaims().get(rolesClaimName);
                        mapAuthorities(mappedAuthorities, rolesClaimName, claimValue);
                    }
                }
                return mappedAuthorities;
            };
        }
    }

    /**
     * Maps the roles provided in the claimValue to {@link GrantedAuthority}.
     */
    private void mapAuthorities(Set<GrantedAuthority> mappedAuthorities, String rolesClaimName, Object claimValue) {
        for (String role: parseRolesClaim(rolesClaimName, claimValue)) {
            String mappedRole = role.toUpperCase().startsWith("ROLE_") ? role : "ROLE_" + role;
            mappedAuthorities.add(new SimpleGrantedAuthority(mappedRole.toUpperCase()));
        }
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
