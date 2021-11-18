package eu.openanalytics.rdepot.crane.model;

import java.util.List;

public class Repository {

    private String name;
    private List<String> accessGroups;
    private List<String> accessUsers;
    private String accessExpression;

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

}
