package eu.openanalytics.rdepot.crane;

import eu.openanalytics.rdepot.crane.config.CraneConfig;
import eu.openanalytics.rdepot.crane.service.UserService;
import org.springframework.ui.ModelMap;

public class BaseUIController {

    protected final UserService userService;
    protected final CraneConfig config;

    public BaseUIController(UserService userService, CraneConfig craneConfig) {
        this.userService = userService;
        this.config = craneConfig;
    }

    protected void prepareMap(ModelMap map) {
        boolean authenticated = userService.isAuthenticated();
        map.put("logo", config.getLogoUrl());
        map.put("authenticated", authenticated);
        if (authenticated) {
            map.put("username", userService.getUser().getName());
        }
    }
}
