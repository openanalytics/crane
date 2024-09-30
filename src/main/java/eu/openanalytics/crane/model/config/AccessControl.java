package eu.openanalytics.crane.model.config;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.List;
import java.util.stream.Collectors;

public class AccessControl {

    protected List<String> accessGroups;
    protected List<String> accessUsers;
    protected boolean isPublic = false;
    protected List<String> accessNetwork;
    protected boolean accessAnyAuthenticatedUser;
    protected List<IpAddressMatcher> accessNetworkMatchers;
    protected String accessExpression;

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

    public boolean hasGroupAccess() {
        return accessGroups != null && accessGroups.size() > 0;
    }

    public boolean hasUserAccess() {
        return accessUsers != null && accessUsers.size() > 0;
    }

    public boolean getPublic() {
        return isPublic;
    }

    public void setPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public List<String> getAccessNetwork() {
        return accessNetwork;
    }

    public void setAccessNetwork(List<String> accessIpRanges) {
        this.accessNetwork = accessIpRanges;
        this.accessNetworkMatchers = accessIpRanges.stream()
                .map(IpAddressMatcher::new).collect(Collectors.toList());
    }

    public List<IpAddressMatcher> getAccessNetworkMatchers() {
        return accessNetworkMatchers;
    }

    public String getAccessExpression() {
        return accessExpression;
    }

    public void setAccessExpression(String accessExpression) {
        this.accessExpression = accessExpression;
    }

    public boolean hasNetworkAccess() {
        return accessNetwork != null && accessNetwork.size() > 0;
    }

    public boolean hasExpressionAccess() {
        return accessExpression != null && accessExpression.length() > 0;
    }

    public boolean isAccessAnyAuthenticatedUser() {
        return accessAnyAuthenticatedUser;
    }

    public void setAccessAnyAuthenticatedUser(boolean accessAnyAuthenticatedUser) {
        this.accessAnyAuthenticatedUser = accessAnyAuthenticatedUser;
    }
}
