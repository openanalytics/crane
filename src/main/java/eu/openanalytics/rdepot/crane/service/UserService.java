package eu.openanalytics.rdepot.crane.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Service
public class UserService {

    private final String clientRegistrationId;

    public UserService(InMemoryClientRegistrationRepository clientRegistrationRepository) {
        clientRegistrationId = clientRegistrationRepository.iterator().next().getRegistrationId();
    }

    public String getLoginUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
            .path(OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI)
            .path("/")
            .path(clientRegistrationId)
            .build().toString();
    }

    public boolean isAuthenticated() {
        Authentication user = SecurityContextHolder.getContext().getAuthentication();
        return !(user instanceof AnonymousAuthenticationToken);
    }

}
