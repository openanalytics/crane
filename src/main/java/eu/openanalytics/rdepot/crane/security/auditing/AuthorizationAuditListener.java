package eu.openanalytics.rdepot.crane.security.auditing;

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.security.AbstractAuthorizationAuditListener;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.authorization.event.AuthorizationEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestWrapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;

@Component
public class AuthorizationAuditListener extends AbstractAuthorizationAuditListener {

    /**
     * Authorization failure event type.
     */
    public static final String AUTHORIZATION_FAILURE = "AUTHORIZATION_FAILURE";
    private final Set<String> ignorePaths = new HashSet<>(List.of("/favicon.ico"));

    @Override
    public void onApplicationEvent(AuthorizationEvent event) {
        if (event instanceof AuthorizationDeniedEvent<?> authorizationDeniedEvent) {
            onAuthorizationDeniedEvent(authorizationDeniedEvent);
        }
    }

    private void onAuthorizationDeniedEvent(AuthorizationDeniedEvent<?> event) {
        String name = getName(event.getAuthentication());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("remoteAddress", getRemoteAddress(event));
        if (name.equals("anonymousUser")) {
            return;
        }
        publish(new AuditEvent(name, AUTHORIZATION_FAILURE, data));
    }

    private String getRemoteAddress(AuthorizationDeniedEvent<?> event) {
        try {
            SecurityContextHolderAwareRequestWrapper wrapper = (SecurityContextHolderAwareRequestWrapper) event.getSource();
            return wrapper.getRemoteAddr();
        } catch (Exception e) {
            return "";
        }
    }

    private String getName(Supplier<Authentication> authentication) {
        try {
            return authentication.get().getName();
        }
        catch (Exception ex) {
            return "anonymousUser";
        }
    }

    private Object getDetails(Supplier<Authentication> authentication) {
        try {
            return authentication.get().getDetails();
        }
        catch (Exception ex) {
            return null;
        }
    }

}
