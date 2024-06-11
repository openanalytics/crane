package eu.openanalytics.rdepot.crane.security.auditing.event;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

public class LogoutHandlerAuditEvent extends AuditApplicationEvent {

    public LogoutHandlerAuditEvent(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        super(principal(request, authentication.getName()), "LOGOUT_HANDLER", info(request, response.getStatus()));
    }
}
