package eu.openanalytics.rdepot.crane.service;

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import eu.openanalytics.rdepot.crane.model.Repository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService {

    private final CraneConfig craneConfig;

    public AccessControlService(CraneConfig craneConfig) {
        this.craneConfig = craneConfig;
    }

    public boolean canAccess(Authentication auth, String repositoryName) {
        if (auth == null || repositoryName == null) {
            return false;
        }

        Repository repository = craneConfig.getRepository(repositoryName);
        if (repository == null) {
            return false;
        }

        return canAccess(auth, repository);
    }

    public boolean canAccess(Authentication auth, Repository repository) {
        if (auth == null || repository == null) {
            return false;
        }

        if (repository.getPublic()) {
            return true;
        }

        if (auth instanceof AnonymousAuthenticationToken) {
            // no anonymous users allowed beyond this stage
            return false;
        }

        if (repositoryAllowsAnyLoggedInUser(repository)) {
            return true;
        }

        if (allowedByGroups(auth, repository)) {
            return true;
        }

        if (allowedByUsers(auth, repository)) {
            return true;
        }

        return false;
    }

    public boolean repositoryAllowsAnyLoggedInUser(Repository repository) {
        return !repository.hasGroupAccess()
            && !repository.hasUserAccess()
            && !repository.hasExpressionAccess();
    }

    public boolean allowedByGroups(Authentication auth, Repository spec) {
        if (!spec.hasGroupAccess()) {
            // no groups defined -> this user has no access based on the groups
            return false;
        }
        for (String group : spec.getAccessGroups()) {
            if (isMember(auth, group)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowedByUsers(Authentication auth, Repository repository) {
        if (!repository.hasUserAccess()) {
            // no users defined -> this user has no access based on the users
            return false;
        }
        for (String user : repository.getAccessUsers()) {
            if (auth.getName().equals(user)) {
                return true;
            }
        }
        return false;
    }

    public boolean isMember(Authentication auth, String group) {
        for (GrantedAuthority grantedAuth: auth.getAuthorities()) {
            String groupName = grantedAuth.getAuthority().toUpperCase();
            if (groupName.startsWith("ROLE_")) {
                groupName = groupName.substring(5);
            }
            if (groupName.equalsIgnoreCase(group)) {
                return true;
            }
        }
        return false;
    }



}
