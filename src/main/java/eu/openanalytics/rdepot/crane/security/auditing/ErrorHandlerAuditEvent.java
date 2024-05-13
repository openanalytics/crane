package eu.openanalytics.rdepot.crane.security.auditing;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ErrorHandlerAuditEvent extends AuditApplicationEvent {

    public ErrorHandlerAuditEvent(HttpServletRequest request, int status) {
        super(principal(request), "ERROR_HANDLER", info(request, status));
    }

    private static String principal(HttpServletRequest request) {
        return Optional.ofNullable(request.getUserPrincipal()).map(Principal::getName).orElse("anonymousUser");
    }

    private static Map<String, Object> info(HttpServletRequest request, int status) {
        Map<String, Object> information = new HashMap<>();
        information.put("request_method", request.getMethod());
        information.put("request_path", request.getRequestURI());
        information.put("response_status", status);
        return information;
    }
}
