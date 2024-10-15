package eu.openanalytics.crane.upload;

import eu.openanalytics.crane.security.auditing.AuditingService;
import eu.openanalytics.crane.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class UploadAuditing extends AuditingService {
    public UploadAuditing(UserService userService, AuditEventRepository auditEventRepository) {
        super(userService, auditEventRepository);
    }

    public void createUploadAuditEvent(HttpServletRequest request) {
        createAuditEvent("UPLOAD", createData(request, HttpStatus.OK));
    }

    public void createAuthorizationDeniedEvent(Authentication auth) {
        auditEventRepository.add(new AuditEvent(auth.getName(), "AUTHORIZATION_FAILURE", createData()));
    }

}
