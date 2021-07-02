package eu.openanalytics.rdepot.crane.security;

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@EnableWebSecurity
public class WebSecurity extends WebSecurityConfigurerAdapter {

    private final CraneConfig config;

    public WebSecurity(CraneConfig config) {
        this.config = config;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .antMatcher("/repo/**")
            .csrf().disable()
            .authorizeRequests()
            .anyRequest().authenticated()
            .and()
            .oauth2ResourceServer()
            .jwt().jwkSetUri(config.getJwksUri());
    }

}
