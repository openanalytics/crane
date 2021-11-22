package eu.openanalytics.rdepot.crane.model;

import java.util.List;
import java.util.regex.Pattern;

public class Repository {

    private String name;
    private List<String> accessGroups;
    private List<String> accessUsers;
    private String accessExpression;
    private Boolean isPublic = false;

    private static final Pattern namePattern = Pattern.compile("^[a-zA-Z0-9_\\-]*$");

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAccessGroups() {
        return accessGroups;
    }

    public void setAccessGroups(List<String> accessGroups) {
        this.accessGroups = accessGroups;
    }

    public List<String> getAccessUsers() {
        return accessUsers;
    }

    public void setAccessUsers(List<String> accessUsers) {
        this.accessUsers = accessUsers;
    }

    public String getAccessExpression() {
        return accessExpression;
    }

    public void setAccessExpression(String accessExpression) {
        this.accessExpression = accessExpression;
    }

    public boolean hasGroupAccess() {
        return accessGroups != null && accessGroups.size() > 0;
    }

    public boolean hasUserAccess() {
        return accessUsers != null && accessUsers.size() > 0;
    }

    public boolean hasExpressionAccess() {
        return accessExpression != null && accessExpression.length() > 0;
    }

    public Boolean getPublic() {
        return isPublic;
    }

    public void setPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public void validate() {
        if (name == null) {
            throw new RuntimeException("Repository has no name");
        }

        // restrict Repository name in order to limit chances of path traversal
        if (!namePattern.matcher(name).matches()) {
            throw new RuntimeException(String.format("Repository name %s contains invalid characters", name));
        }

        if (isPublic && (hasGroupAccess() || hasUserAccess() || hasExpressionAccess())) {
            throw new IllegalArgumentException(String.format("Repository %s is invalid, cannot add access control properties to a public repo", name));
        }

    }
}
