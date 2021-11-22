package eu.openanalytics.rdepot.crane.service;

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import eu.openanalytics.rdepot.crane.model.Repository;
import eu.openanalytics.rdepot.crane.service.spel.SpecExpressionContext;
import eu.openanalytics.rdepot.crane.service.spel.SpecExpressionResolver;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AccessControlService {

    private final CraneConfig craneConfig;
    private final SpecExpressionResolver specExpressionResolver;

    public AccessControlService(CraneConfig craneConfig, SpecExpressionResolver specExpressionResolver) {
        this.craneConfig = craneConfig;
        this.specExpressionResolver = specExpressionResolver;
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

        if (allowedByExpression(auth, repository)) {
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

    public boolean allowedByExpression(Authentication auth, Repository repository) {
        if (!repository.hasExpressionAccess()) {
            // no expression defined -> this user has no access based on the expression
            return false;
        }
        SpecExpressionContext context = SpecExpressionContext.create(auth, auth.getPrincipal(), auth.getCredentials(), repository);
        return specExpressionResolver.evaluateToBoolean(repository.getAccessExpression(), context);
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

    public static List<String> getGroups(Authentication auth) {
        List<String> groups = new ArrayList<>();
        if (auth != null) {
            for (GrantedAuthority grantedAuth: auth.getAuthorities()) {
                String authName = grantedAuth.getAuthority().toUpperCase();
                if (authName.startsWith("ROLE_")) authName = authName.substring(5);
                groups.add(authName);
            }
        }
        return groups;
    }

}
