package eu.openanalytics.rdepot.crane.service;

import eu.openanalytics.rdepot.crane.model.config.PathComponent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

@Service
public class PosixAccessControlService {
public boolean canAccess(Authentication auth, PathComponent pathComponent) {
    if (auth == null || pathComponent == null) {
        return false;
    }

    PosixFileAttributes attributes = null;
    try {
        attributes = pathComponent.getPosixFileAttributeView();
    } catch (IOException e) {
        throw new RuntimeException(e);
    }

    Set<PosixFilePermission> permissionSet = attributes.permissions();
    if (attributes.owner().getName().equals(auth.getName()) && permissionSet.contains(PosixFilePermission.OWNER_READ)) {
        return true;
    } else if (permissionSet.contains(PosixFilePermission.GROUP_READ) && isMember(auth, attributes.group().getName())){
        return true;
    } else if (permissionSet.contains(PosixFilePermission.OTHERS_READ)) {
        return true;
    } else {
        return false;
    }
}

    private boolean isMember(Authentication auth, String group) {
        for (GrantedAuthority grantedAuthority: auth.getAuthorities()) {
            String groupName = grantedAuthority.getAuthority().toUpperCase();
            if (groupName.startsWith("ROLE_")) {
                groupName = groupName.substring(5);
            }
            if (groupName.equalsIgnoreCase(group)) {
                return true;
            }
        }
        return false
    }
}
