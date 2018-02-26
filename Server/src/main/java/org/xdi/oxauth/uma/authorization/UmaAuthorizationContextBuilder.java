package org.xdi.oxauth.uma.authorization;

import org.xdi.model.custom.script.conf.CustomScriptConfiguration;
import org.xdi.oxauth.model.configuration.AppConfiguration;
import org.xdi.oxauth.model.registration.Client;
import org.xdi.oxauth.model.uma.persistence.UmaPermission;
import org.xdi.oxauth.model.uma.persistence.UmaResource;
import org.xdi.oxauth.model.uma.persistence.UmaScopeDescription;
import org.xdi.oxauth.service.AttributeService;
import org.xdi.oxauth.service.UserService;
import org.xdi.oxauth.uma.service.UmaResourceService;
import org.xdi.oxauth.uma.service.UmaSessionService;

import javax.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author yuriyz on 06/06/2017.
 */
public class UmaAuthorizationContextBuilder {

    private final AttributeService attributeService;
    private final UmaResourceService resourceService;
    private final List<UmaPermission> permissions;
    private final Map<UmaScopeDescription, Boolean> scopes;
    private final Claims claims;
    private final HttpServletRequest httpRequest;
    private final AppConfiguration configuration;
    private final UmaSessionService sessionService;
    private final UserService userService;
    private final Client client;

    public UmaAuthorizationContextBuilder(AppConfiguration configuration, AttributeService attributeService, UmaResourceService resourceService,
                                          List<UmaPermission> permissions, Map<UmaScopeDescription, Boolean> scopes,
                                          Claims claims, HttpServletRequest httpRequest,
                                          UmaSessionService sessionService, UserService userService, Client client) {
        this.configuration = configuration;
        this.attributeService = attributeService;
        this.resourceService = resourceService;
        this.permissions = permissions;
        this.client = client;
        this.scopes = scopes;
        this.claims = claims;
        this.httpRequest = httpRequest;
        this.sessionService = sessionService;
        this.userService = userService;
    }

    public UmaAuthorizationContext build(CustomScriptConfiguration script) {
        return new UmaAuthorizationContext(configuration, attributeService, scopes, getResources(), claims,
                script.getCustomScript().getDn(), httpRequest, script.getConfigurationAttributes(),
                sessionService, userService, client);
    }

    public Set<String> getResourceIds() {
        Set<String> result = new HashSet<String>();
        for (UmaPermission permission : permissions) {
            result.add(permission.getResourceId());
        }
        return result;
    }

    public Set<UmaResource> getResources() {
        return resourceService.getResources(getResourceIds());
    }
}
