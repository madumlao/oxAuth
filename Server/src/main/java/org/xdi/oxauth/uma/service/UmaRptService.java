/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.uma.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang.ArrayUtils;
import org.gluu.persist.ldap.impl.LdapEntryManager;
import org.gluu.persist.model.BatchOperation;
import org.gluu.persist.model.ProcessBatchOperation;
import org.gluu.persist.model.SearchScope;
import org.gluu.persist.model.base.SimpleBranch;
import org.gluu.search.filter.Filter;
import org.slf4j.Logger;
import org.xdi.oxauth.model.common.AuthorizationGrantList;
import org.xdi.oxauth.model.config.StaticConfiguration;
import org.xdi.oxauth.model.configuration.AppConfiguration;
import org.xdi.oxauth.model.registration.Client;
import org.xdi.oxauth.model.uma.persistence.UmaPermission;
import org.xdi.oxauth.model.util.Util;
import org.xdi.oxauth.service.CleanerTimer;
import org.xdi.oxauth.service.ClientService;
import org.xdi.oxauth.service.token.TokenService;
import org.xdi.oxauth.uma.authorization.UmaRPT;
import org.xdi.util.INumGenerator;

import com.google.common.base.Preconditions;

/**
 * RPT manager component
 *
 * @author Yuriy Zabrovarnyy
 * @author Javier Rojas Blum
 * @version June 28, 2017
 */
@Stateless
@Named
public class UmaRptService {

    private static final String ORGUNIT_OF_RPT = "uma_rpt";

    public static final int DEFAULT_RPT_LIFETIME = 3600;

    @Inject
    private Logger log;

    @Inject
    private LdapEntryManager ldapEntryManager;

    @Inject
    private TokenService tokenService;

    @Inject
    private AuthorizationGrantList authorizationGrantList;

    @Inject
    private AppConfiguration appConfiguration;

    @Inject
    private StaticConfiguration staticConfiguration;

    @Inject
    private ClientService clientService;

    public static String getDn(String clientDn, String uniqueIdentifier) {
        return String.format("uniqueIdentifier=%s,%s", uniqueIdentifier, branchDn(clientDn));
    }

    public static String branchDn(String clientDn) {
        return String.format("ou=%s,%s", ORGUNIT_OF_RPT, clientDn);
    }

    public void persist(UmaRPT rpt) {
        try {
            Preconditions.checkNotNull(rpt.getClientId());

            Client client = clientService.getClient(rpt.getClientId());

            addBranchIfNeeded(client.getDn());
            String id = UUID.randomUUID().toString();
            rpt.setId(id);
            rpt.setDn(getDn(client.getDn(), id));
            ldapEntryManager.persist(rpt);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public UmaRPT getRPTByCode(String rptCode) {
        try {
            final Filter filter = Filter.create(String.format("&(oxAuthTokenCode=%s)", rptCode));
            final String baseDn = staticConfiguration.getBaseDn().getClients();
            final List<UmaRPT> entries = ldapEntryManager.findEntries(baseDn, UmaRPT.class, filter);
            if (entries != null && !entries.isEmpty()) {
                return entries.get(0);
            } else {
                log.error("Failed to find RPT by code: " + rptCode);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public void deleteByCode(String rptCode) {
        try {
            final UmaRPT t = getRPTByCode(rptCode);
            if (t != null) {
                ldapEntryManager.remove(t);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void cleanup(final Date now) {
    	BatchOperation<UmaRPT> rptBatchService = new ProcessBatchOperation<UmaRPT>() {
            @Override
            public void performAction(List<UmaRPT> entries) {
                for (UmaRPT p : entries) {
                    try {
                        ldapEntryManager.remove(p);
                    } catch (Exception e) {
                        log.error("Failed to remove entry", e);
                    }
                }
            }
        };
        ldapEntryManager.findEntries(staticConfiguration.getBaseDn().getClients(), UmaRPT.class, getExpiredUmaRptFilter(now), SearchScope.SUB, new String[] { "" }, rptBatchService, 0, 0, CleanerTimer.BATCH_SIZE);
    }

    private Filter getExpiredUmaRptFilter(Date date) {
        return Filter.createLessOrEqualFilter("oxAuthExpiration", ldapEntryManager.encodeGeneralizedTime(date));
    }

    public void addPermissionToRPT(UmaRPT rpt, Collection<UmaPermission> permissions) {
        addPermissionToRPT(rpt, permissions.toArray(new UmaPermission[permissions.size()]));
    }

    public void addPermissionToRPT(UmaRPT rpt, UmaPermission... permission) {
        if (ArrayUtils.isEmpty(permission)) {
            return;
        }

        final List<String> permissions = new ArrayList<String>();
        if (rpt.getPermissions() != null) {
            permissions.addAll(rpt.getPermissions());
        }

        for (UmaPermission p : permission) {
            permissions.add(p.getDn());
        }

        rpt.setPermissions(permissions);

        try {
            ldapEntryManager.merge(rpt);
            log.trace("Persisted RPT: " + rpt);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public List<UmaPermission> getRptPermissions(UmaRPT p_rpt) {
        final List<UmaPermission> result = new ArrayList<UmaPermission>();
        try {
            if (p_rpt != null && p_rpt.getPermissions() != null) {
                final List<String> permissionDns = p_rpt.getPermissions();
                for (String permissionDn : permissionDns) {
                    final UmaPermission permissionObject = ldapEntryManager.find(UmaPermission.class, permissionDn);
                    if (permissionObject != null) {
                        result.add(permissionObject);
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return result;
    }

    public UmaRPT createRPT(String clientId) {
        try {
            String code = UUID.randomUUID().toString() + "_" + INumGenerator.generate(8);
            return new UmaRPT(code, new Date(), rptExpirationDate(), null, clientId);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException("Failed to generate RPT, clientId: " + clientId, e);
        }
    }

    public Date rptExpirationDate() {
        int lifeTime = appConfiguration.getUmaRptLifetime();
        if (lifeTime <= 0) {
            lifeTime = DEFAULT_RPT_LIFETIME;
        }

        final Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, lifeTime);
        return calendar.getTime();
    }

    public UmaRPT createRPTAndPersist(String clientId) {
        UmaRPT rpt = createRPT(clientId);
        persist(rpt);
        return rpt;
    }

    public UmaPermission getPermissionFromRPTByResourceId(UmaRPT rpt, String resourceId) {
        try {
            if (Util.allNotBlank(resourceId)) {
                 for (UmaPermission permission : getRptPermissions(rpt)) {
                    if (resourceId.equals(permission.getResourceId())) {
                        return permission;
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public void addBranch(String clientDn) {
        final SimpleBranch branch = new SimpleBranch();
        branch.setOrganizationalUnitName(ORGUNIT_OF_RPT);
        branch.setDn(branchDn(clientDn));
        ldapEntryManager.persist(branch);
    }

    public void addBranchIfNeeded(String clientDn) {
        if (!containsBranch(clientDn)) {
            addBranch(clientDn);
        }
    }

    public boolean containsBranch(String clientDn) {
        return ldapEntryManager.contains(SimpleBranch.class, branchDn(clientDn));
    }

//    private JsonWebResponse createJwr(UmaRPT rpt, String authorization, List<String> gluuAccessTokenScopes) throws Exception {
//        final AuthorizationGrant grant = tokenService.getAuthorizationGrant(authorization);
//
//        JwtSigner jwtSigner = JwtSigner.newJwtSigner(appConfiguration, webKeysConfiguration, grant.getClient());
//        Jwt jwt = jwtSigner.newJwt();
//
//        jwt.getClaims().setExpirationTime(rpt.getExpirationDate());
//        jwt.getClaims().setIssuedAt(rpt.getCreationDate());
//
//        if (!gluuAccessTokenScopes.isEmpty()) {
//            jwt.getClaims().setClaim("scopes", gluuAccessTokenScopes);
//        }
//
//        return jwtSigner.sign();
//    }

//    UmaRPT rpt = rptService.createRPT(authorization);
//
//    String rptResponse = rpt.getCode();
//    final Boolean umaRptAsJwt = appConfiguration.getUmaRptAsJwt();
//    if (umaRptAsJwt != null && umaRptAsJwt) {
//        rptResponse = createJwr(rpt, authorization, Lists.<String>newArrayList()).asString();
//    }

}
