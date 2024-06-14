package eu.openanalytics.rdepot.crane.security.auditing;

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class NoAuditEventRepository implements AuditEventRepository {
    public NoAuditEventRepository(CraneConfig craneConfig) {
    }

    @Override
    public void add(AuditEvent event) {

    }

    @Override
    public List<AuditEvent> find(String principal, Instant after, String type) {
        return new ArrayList<>();
    }
}
