package eu.openanalytics.rdepot.crane.security.auditing.event;

import jakarta.servlet.http.HttpServletRequest;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AuditApplicationEvent extends org.springframework.boot.actuate.audit.listener.AuditApplicationEvent {
    public AuditApplicationEvent(String principal, String type, Map<String, Object> data) {
        super(principal, type, data);
    }

    static String principal(HttpServletRequest request, String defaultName) {
        return Optional.ofNullable(request.getUserPrincipal()).map(Principal::getName).orElse(defaultName);
    }

    static String principal(HttpServletRequest request) {
        return principal(request, "anonymousUser");
    }

    static Map<String, Object> info(HttpServletRequest request) {
        Map<String, Object> information = new HashMap<>();
        information.put("request_method", request.getMethod());
        information.put("request_path", request.getRequestURI());
        return information;
    }

    static Map<String, Object> info(HttpServletRequest request, int response_status) {
        Map<String, Object> information = info(request);
        information.put("response_status", response_status);
        return information;
    }
}
