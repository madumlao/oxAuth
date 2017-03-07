/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.model.common;

import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.RDN;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.*;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.log.Log;
import org.jboss.seam.log.Logging;
import org.xdi.oxauth.model.authorize.JwtAuthorizationRequest;
import org.xdi.oxauth.model.configuration.AppConfiguration;
import org.xdi.oxauth.model.ldap.TokenLdap;
import org.xdi.oxauth.model.registration.Client;
import org.xdi.oxauth.model.util.Util;
import org.xdi.oxauth.service.ClientService;
import org.xdi.oxauth.service.GrantService;
import org.xdi.oxauth.service.UserService;
import org.xdi.service.CacheService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Component to hold in memory authorization grant objects.
 *
 * @author Javier Rojas Blum Date: 09.29.2011
 */
@Name("authorizationGrantList")
@AutoCreate
@Scope(ScopeType.APPLICATION)
@Startup
public class AuthorizationGrantList implements IAuthorizationGrantList {

    private static final Log LOGGER = Logging.getLog(AuthorizationGrantList.class);

    @In
    private GrantService grantService;

    @In
    private UserService userService;

    @In
    private ClientService clientService;

    @In
	private AppConfiguration appConfiguration;

    @In
    private CacheService cacheService;

    @Override
    public void removeAuthorizationGrants(List<AuthorizationGrant> authorizationGrants) {
        if (authorizationGrants != null && !authorizationGrants.isEmpty()) {
            for (AuthorizationGrant r : authorizationGrants) {
                grantService.remove(r);
            }
        }
    }

    @Override
    public AuthorizationGrant createAuthorizationGrant(User user, Client client, Date authenticationTime) {
        return new AuthorizationGrant(user, null, client, authenticationTime, appConfiguration);
    }

    @Override
    public AuthorizationCodeGrant createAuthorizationCodeGrant(User user, Client client, Date authenticationTime) {
        final AuthorizationCodeGrant grant = new AuthorizationCodeGrant(user, client, authenticationTime, appConfiguration);
        MemcachedGrant memcachedGrant = new MemcachedGrant(grant);
        cacheService.put(Integer.toString(grant.getAuthorizationCode().getExpiresIn()), memcachedGrant.cacheKey(), memcachedGrant);
        LOGGER.trace("Put authorization grant in cache, code: " + grant.getAuthorizationCode().getCode() + ", clientId: " + grant.getClientId());
        return grant;
    }

    @Override
    public ImplicitGrant createImplicitGrant(User user, Client client, Date authenticationTime) {
        return new ImplicitGrant(user, client, authenticationTime, appConfiguration);
    }

    @Override
    public ClientCredentialsGrant createClientCredentialsGrant(User user, Client client) {
        return new ClientCredentialsGrant(user, client, appConfiguration);
    }

    @Override
    public ResourceOwnerPasswordCredentialsGrant createResourceOwnerPasswordCredentialsGrant(User user, Client client) {
        return new ResourceOwnerPasswordCredentialsGrant(user, client, appConfiguration);
    }

    @Override
    public AuthorizationCodeGrant getAuthorizationCodeGrant(String clientId, String authorizationCode) {
        Object cachedGrant = cacheService.get(null, MemcachedGrant.cacheKey(clientId, authorizationCode));
        if (cachedGrant == null) {
            // retry one time : sometimes during high load cache client may be not fast enough
            cachedGrant = cacheService.get(null, MemcachedGrant.cacheKey(clientId, authorizationCode));
            LOGGER.trace("Failed to fetch authorization grant from cache, code: " + authorizationCode + ", clientId: " + clientId);
        }
        return cachedGrant instanceof MemcachedGrant ? ((MemcachedGrant) cachedGrant).asCodeGrant(appConfiguration) : null;
    }

    @Override
    public AuthorizationGrant getAuthorizationGrantByRefreshToken(String clientId, String refreshTokenCode) {
        return load(clientId, refreshTokenCode);
    }

    @Override
    public List<AuthorizationGrant> getAuthorizationGrant(String clientId) {
        final List<AuthorizationGrant> result = new ArrayList<AuthorizationGrant>();
        try {
            final List<TokenLdap> entries = grantService.getGrantsOfClient(clientId);
            if (entries != null && !entries.isEmpty()) {
                for (TokenLdap t : entries) {
                    final AuthorizationGrant grant = asGrant(t);
                    if (grant != null) {
                        result.add(grant);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.trace(e.getMessage(), e);
        }
        return result;
    }

    @Override
    public AuthorizationGrant getAuthorizationGrantByAccessToken(String accessToken) {
        final TokenLdap tokenLdap = grantService.getGrantsByCode(accessToken);
        if (tokenLdap != null && (tokenLdap.getTokenTypeEnum() == org.xdi.oxauth.model.ldap.TokenType.ACCESS_TOKEN || tokenLdap.getTokenTypeEnum() == org.xdi.oxauth.model.ldap.TokenType.LONG_LIVED_ACCESS_TOKEN)) {
            return asGrant(tokenLdap);
        }
        return null;
    }

    @Override
    public AuthorizationGrant getAuthorizationGrantByIdToken(String idToken) {
        TokenLdap tokenLdap = grantService.getGrantsByCode(idToken);
        if (tokenLdap != null && (tokenLdap.getTokenTypeEnum() == org.xdi.oxauth.model.ldap.TokenType.ID_TOKEN)) {
            return asGrant(tokenLdap);
        }
        return null;
    }

    public AuthorizationGrant load(String clientId, String p_code) {
        return asGrant(grantService.getGrantsByCodeAndClient(p_code, clientId));
    }

    public static String extractClientIdFromTokenDn(String p_dn) {
        try {
            if (StringUtils.isNotBlank(p_dn)) {
                final RDN[] rdNs = DN.getRDNs(p_dn);
                if (ArrayUtils.isNotEmpty(rdNs)) {
                    for (RDN r : rdNs) {
                        final String[] names = r.getAttributeNames();
                        if (ArrayUtils.isNotEmpty(names) && Arrays.asList(names).contains("inum")) {
                            final String[] values = r.getAttributeValues();
                            if (ArrayUtils.isNotEmpty(values)) {
                                return values[0];
                            }
                        }
                    }
                }
            }
        } catch (LDAPException e) {
            LOGGER.trace(e.getMessage(), e);
        }

        return "";
    }

    public AuthorizationGrant asGrant(TokenLdap tokenLdap) {
        if (tokenLdap != null) {
            final AuthorizationGrantType grantType = AuthorizationGrantType.fromString(tokenLdap.getGrantType());
            if (grantType != null) {
                final User user = userService.getUser(tokenLdap.getUserId());
                final Client client = clientService.getClient(extractClientIdFromTokenDn(tokenLdap.getDn()));
                final Date authenticationTime = tokenLdap.getAuthenticationTime();
                final String nonce = tokenLdap.getNonce();

                AuthorizationGrant result;
                switch (grantType) {
                    case AUTHORIZATION_CODE:
                        result = new AuthorizationCodeGrant(user, client, authenticationTime, appConfiguration);
                        break;
                    case CLIENT_CREDENTIALS:
                        result = new ClientCredentialsGrant(user, client, appConfiguration);
                        break;
                    case IMPLICIT:
                        result = new ImplicitGrant(user, client, authenticationTime, appConfiguration);
                        break;
                    case RESOURCE_OWNER_PASSWORD_CREDENTIALS:
                        result = new ResourceOwnerPasswordCredentialsGrant(user, client, appConfiguration);
                        break;
                    default:
                        return null;
                }

                final String grantId = tokenLdap.getGrantId();
                final String jwtRequest = tokenLdap.getJwtRequest();
                final String authMode = tokenLdap.getAuthMode();
                final String sessionDn = tokenLdap.getSessionDn();

                result.setNonce(nonce);
                result.setTokenLdap(tokenLdap);
                if (StringUtils.isNotBlank(grantId)) {
                    result.setGrantId(grantId);
                }
                result.setScopes(Util.splittedStringAsList(tokenLdap.getScope(), " "));

                result.setCodeChallenge(tokenLdap.getCodeChallenge());
                result.setCodeChallengeMethod(tokenLdap.getCodeChallengeMethod());

                if (StringUtils.isNotBlank(jwtRequest)) {
                    try {
                        result.setJwtAuthorizationRequest(new JwtAuthorizationRequest(appConfiguration, jwtRequest, client));
                    } catch (Exception e) {
                        LOGGER.trace(e.getMessage(), e);
                    }
                }

                result.setAcrValues(authMode);
                result.setSessionDn(sessionDn);

                if (tokenLdap.getTokenTypeEnum() != null) {
                    switch (tokenLdap.getTokenTypeEnum()) {
                        case AUTHORIZATION_CODE:
                            if (result instanceof AuthorizationCodeGrant) {
                                final AuthorizationCode code = new AuthorizationCode(tokenLdap.getTokenCode(), tokenLdap.getCreationDate(), tokenLdap.getExpirationDate());
                                final AuthorizationCodeGrant g = (AuthorizationCodeGrant) result;
                                g.setAuthorizationCode(code);
                            }
                            break;
                        case REFRESH_TOKEN:
                            final RefreshToken refreshToken = new RefreshToken(tokenLdap.getTokenCode(), tokenLdap.getCreationDate(), tokenLdap.getExpirationDate());
                            result.setRefreshTokens(Arrays.asList(refreshToken));
                            break;
                        case ACCESS_TOKEN:
                            final AccessToken accessToken = new AccessToken(tokenLdap.getTokenCode(), tokenLdap.getCreationDate(), tokenLdap.getExpirationDate());
                            result.setAccessTokens(Arrays.asList(accessToken));
                            break;
                        case ID_TOKEN:
                            final IdToken idToken = new IdToken(tokenLdap.getTokenCode(), tokenLdap.getCreationDate(), tokenLdap.getExpirationDate());
                            result.setIdToken(idToken);
                            break;
                        case LONG_LIVED_ACCESS_TOKEN:
                            final AccessToken longLivedAccessToken = new AccessToken(tokenLdap.getTokenCode(), tokenLdap.getCreationDate(), tokenLdap.getExpirationDate());
                            result.setLongLivedAccessToken(longLivedAccessToken);
                            break;
                    }
                }
                return result;
            }
        }
        return null;
    }
}
