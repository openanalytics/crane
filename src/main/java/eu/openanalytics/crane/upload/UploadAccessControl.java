package eu.openanalytics.crane.upload;

import eu.openanalytics.crane.model.config.AccessControl;

public class UploadAccessControl extends AccessControl {
    @Override
    public void validate(String name) {
        if (isAnyAuthenticatedUser() && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess())) {
            throw new IllegalArgumentException(String.format("PathComponent %s is invalid, cannot add user-based write access control properties to a repo that allows any authenticated user", name));
        }

        if (!hasGroupAccess() && !hasUserAccess() && !hasExpressionAccess() && !hasNetworkAccess()) {
            anyAuthenticatedUser = false;
        }

        if (isPublic && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess() || hasNetworkAccess())) {
            throw new IllegalArgumentException(String.format("Repository %s is invalid, cannot add write access control properties to a public repo", name));
        }
    }
}
