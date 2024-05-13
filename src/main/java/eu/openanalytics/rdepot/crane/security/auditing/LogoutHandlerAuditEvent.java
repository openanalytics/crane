package eu.openanalytics.rdepot.crane.security.auditing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LogoutHandlerAuditEvent extends AuditApplicationEvent {

    public LogoutHandlerAuditEvent(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        super(principal(request, authentication), "LOGOUT_HANDLER", info(request, response));
    }

    private static String principal(HttpServletRequest request, Authentication authentication) {
        return Optional.ofNullable(request.getUserPrincipal()).map(Principal::getName).orElse(authentication.getName());
    }

    private static Map<String, Object> info(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> information = new HashMap<>();
        information.put("request_method", request.getMethod());
        information.put("request_path", request.getRequestURI());
        information.put("response_status", response.getStatus());
        return information;
    }
}
