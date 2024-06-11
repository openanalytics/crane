package eu.openanalytics.rdepot.crane.security.auditing.event;

import jakarta.servlet.http.HttpServletRequest;

public class ErrorHandlerAuditEvent extends AuditApplicationEvent {

    public ErrorHandlerAuditEvent(HttpServletRequest request, int status) {
        super(principal(request), "ERROR_HANDLER", info(request, status));
    }

}
