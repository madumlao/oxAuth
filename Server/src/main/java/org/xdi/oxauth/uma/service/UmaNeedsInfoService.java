package org.xdi.oxauth.uma.service;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.xdi.model.custom.script.conf.CustomScriptConfiguration;
import org.xdi.model.uma.ClaimDefinition;
import org.xdi.oxauth.model.configuration.AppConfiguration;
import org.xdi.oxauth.model.registration.Client;
import org.xdi.oxauth.model.uma.UmaConstants;
import org.xdi.oxauth.model.uma.UmaNeedInfoResponse;
import org.xdi.oxauth.model.uma.persistence.UmaPermission;
import org.xdi.oxauth.model.uma.persistence.UmaScopeDescription;
import org.xdi.oxauth.service.AttributeService;
import org.xdi.oxauth.service.UserService;
import org.xdi.oxauth.service.external.ExternalUmaRptPolicyService;
import org.xdi.oxauth.uma.authorization.*;
import org.xdi.oxauth.util.ServerUtil;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * @author yuriyz on 06/16/2017.
 */
@Stateless
@Named
public class UmaNeedsInfoService {

    @Inject
    private Logger log;
    @Inject
    private AppConfiguration appConfiguration;
    @Inject
    private UmaPermissionService permissionService;
    @Inject
    private AttributeService attributeService;
    @Inject
    private UmaResourceService resourceService;
    @Inject
    private ExternalUmaRptPolicyService policyService;
    @Inject
    private UmaSessionService sessionService;
    @Inject
    private UserService userService;

    public Map<UmaScriptByScope, UmaAuthorizationContext> checkNeedsInfo(Claims claims, Map<UmaScopeDescription, Boolean> requestedScopes,
                                                                                  List<UmaPermission> permissions, UmaPCT pct, HttpServletRequest httpRequest,
                                                                                  Client client) {

        Map<UmaScriptByScope, UmaAuthorizationContext> scriptMap = new HashMap<UmaScriptByScope, UmaAuthorizationContext>();
        Map<String, String> ticketAttributes = new HashMap<String, String>();

        List<ClaimDefinition> missedClaims = new ArrayList<ClaimDefinition>();

        UmaAuthorizationContextBuilder contextBuilder = new UmaAuthorizationContextBuilder(appConfiguration,
                attributeService, resourceService, permissions, requestedScopes, claims, httpRequest,
                sessionService, userService, client);


        for (UmaScopeDescription scope : requestedScopes.keySet()) {
            List<String> authorizationPolicies = scope.getAuthorizationPolicies();
            if (authorizationPolicies != null && !authorizationPolicies.isEmpty()) {
                for (String scriptDN : authorizationPolicies) { //log.trace("Loading UMA script: " + scriptDN + ", scope: " + scope + " ...");
                    CustomScriptConfiguration script = policyService.getScriptByDn(scriptDN);
                    if (script != null) {
                        UmaAuthorizationContext context = contextBuilder.build(script);
                        scriptMap.put(new UmaScriptByScope(scope, script), context);

                        List<ClaimDefinition> requiredClaims = policyService.getRequiredClaims(script, context);
                        if (requiredClaims != null && !requiredClaims.isEmpty()) {
                            for (ClaimDefinition definition : requiredClaims) {
                                if (!claims.has(definition.getName())) {
                                    missedClaims.add(definition);
                                }
                            }
                        }

                        String claimsGatheringScriptName = policyService.getClaimsGatheringScriptName(script, context);
                        if (StringUtils.isNotBlank(claimsGatheringScriptName)) {
                            ticketAttributes.put(UmaConstants.GATHERING_ID, constructGatheringScriptNameValue(ticketAttributes.get(UmaConstants.GATHERING_ID), claimsGatheringScriptName));
                        } else {
                            log.error("External 'getClaimsGatheringScriptName' script method return null or blank value, script: " + script.getName());
                        }
                    } else {
                        log.error("Unable to load UMA script dn: '{}'", scriptDN);
                    }
                }
            } else {
                log.trace("No policies defined for scope: " + scope.getId() + ", scopeDn: " + scope.getDn());
            }
        }

        if (!missedClaims.isEmpty()) {
            ticketAttributes.put(UmaPermission.PCT, pct.getCode());
            String newTicket = permissionService.changeTicket(permissions, ticketAttributes);

            UmaNeedInfoResponse needInfoResponse = new UmaNeedInfoResponse();
            needInfoResponse.setTicket(newTicket);
            needInfoResponse.setError("need_info");
            needInfoResponse.setRedirectUser(buildClaimsGatheringRedirectUri(scriptMap.values(), client, newTicket));
            needInfoResponse.setRequiredClaims(missedClaims);

            throw new WebApplicationException(Response.status(Response.Status.FORBIDDEN).entity(ServerUtil.asJsonSilently(needInfoResponse)).build());
        }

        return scriptMap;
    }

    private String constructGatheringScriptNameValue(String existingValue, String claimsGatheringScriptName) {
        if (StringUtils.isBlank(existingValue)) {
            return claimsGatheringScriptName;
        }
        return existingValue + " " + claimsGatheringScriptName;
    }

    private String buildClaimsGatheringRedirectUri(Collection<UmaAuthorizationContext> contexts, Client client, String newTicket) {
        String queryParameters = "";

        for (UmaAuthorizationContext context : contexts) {
            queryParameters += context.getRedirectUserParameters().buildQueryString() + "&";
        }
        queryParameters = StringUtils.removeEnd(queryParameters, "&");

        String result = appConfiguration.getBaseEndpoint() + "/uma/gather_claims";
        if (StringUtils.isNotBlank(queryParameters)) {
            result += "?" + queryParameters;
        }
        result += "&client_id=" + client.getClientId() + "&ticket=" + newTicket;
        return result;
    }

    public static Set<String> getScriptDNs(List<UmaScopeDescription> scopes) {
        HashSet<String> result = new HashSet<String>();

        for (UmaScopeDescription scope : scopes) {
            List<String> authorizationPolicies = scope.getAuthorizationPolicies();
            if (authorizationPolicies != null) {
                result.addAll(authorizationPolicies);
            }
        }

        return result;
    }
}

