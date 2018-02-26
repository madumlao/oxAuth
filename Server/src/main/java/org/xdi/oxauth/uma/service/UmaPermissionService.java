/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.uma.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang.StringUtils;
import org.gluu.persist.ldap.impl.LdapEntryManager;
import org.gluu.persist.model.BatchOperation;
import org.gluu.persist.model.ProcessBatchOperation;
import org.gluu.persist.model.SearchScope;
import org.gluu.persist.model.base.SimpleBranch;
import org.gluu.search.filter.Filter;
import org.slf4j.Logger;
import org.xdi.oxauth.model.config.StaticConfiguration;
import org.xdi.oxauth.model.uma.UmaPermissionList;
import org.xdi.oxauth.model.uma.persistence.UmaPermission;
import org.xdi.oxauth.service.CleanerTimer;
import org.xdi.util.INumGenerator;

/**
 * Holds permission tokens and permissions
 *
 * @author Yuriy Zabrovarnyy
 */
@Stateless
@Named
public class UmaPermissionService {

    private static final String ORGUNIT_OF_RESOURCE_PERMISSION = "uma_permission";

    @Inject
    private Logger log;

    @Inject
    private LdapEntryManager ldapEntryManager;

    @Inject
    private StaticConfiguration staticConfiguration;

    @Inject
    private UmaScopeService scopeService;

    public static String getDn(String clientDn, String ticket) {
        return String.format("oxTicket=%s,%s", ticket, getBranchDn(clientDn));
    }

    public static String getBranchDn(String clientDn) {
        return String.format("ou=%s,%s", ORGUNIT_OF_RESOURCE_PERMISSION, clientDn);
    }

    private List<UmaPermission> createPermissions(UmaPermissionList permissions, Date expirationDate) {
        final String configurationCode = INumGenerator.generate(8) + "." + System.currentTimeMillis();

        List<UmaPermission> result = new ArrayList<UmaPermission>();
        for (org.xdi.oxauth.model.uma.UmaPermission permission : permissions) {
            result.add(new UmaPermission(permission.getResourceId(), scopeService.getScopeDNsByIdsAndAddToLdapIfNeeded(permission.getScopes()),
                    generateNewTicket(), configurationCode, expirationDate));
        }

        return result;
    }

    public String generateNewTicket() {
       return UUID.randomUUID().toString();
    }

    public String addPermission(UmaPermissionList permissionList, Date expirationDate, String clientDn) throws Exception {
        try {
            List<UmaPermission> created = createPermissions(permissionList, expirationDate);
            for (UmaPermission permission : created) {
                addPermission(permission, clientDn);
            }
            return created.get(0).getTicket();
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
            throw e;
        }
    }

    public void addPermission(UmaPermission permission, String clientDn) {
        try {
            addBranchIfNeeded(clientDn);
            permission.setDn(getDn(clientDn, permission.getTicket()));
            ldapEntryManager.persist(permission);
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
        }
    }

    public void merge(UmaPermission permission) {
        ldapEntryManager.merge(permission);
    }

    public void mergeSilently(UmaPermission permission) {
        try {
            ldapEntryManager.merge(permission);
        } catch (Exception e) {
            log.error("Failed to persist permission: " + permission, e);
        }
    }

    public List<UmaPermission> getPermissionsByTicket(String ticket) {
        try {
            final String baseDn = staticConfiguration.getBaseDn().getClients();
            final Filter filter = Filter.create(String.format("&(oxTicket=%s)", ticket));
            return ldapEntryManager.findEntries(baseDn, UmaPermission.class, filter);
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
        }
        return null;
    }

    public String getPermissionTicketByConfigurationCode(String configurationCode, String clientDn) {
        final UmaPermission permission = getPermissionByConfigurationCode(configurationCode, clientDn);
        if (permission != null) {
            return permission.getTicket();
        }
        return null;
    }

    public UmaPermission getPermissionByConfigurationCode(String p_configurationCode, String clientDn) {
        try {
            final Filter filter = Filter.create(String.format("&(oxConfigurationCode=%s)", p_configurationCode));
            final List<UmaPermission> entries = ldapEntryManager.findEntries(clientDn, UmaPermission.class, filter);
            if (entries != null && !entries.isEmpty()) {
                return entries.get(0);
            }
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
        }
        return null;
    }

    public void deletePermission(String ticket) {
        try {
            final List<UmaPermission> permissions = getPermissionsByTicket(ticket);
            for (UmaPermission p : permissions) {
                ldapEntryManager.remove(p);
            }
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
        }
    }

    public void cleanup(final Date now) {
    	BatchOperation<UmaPermission> batchService = new ProcessBatchOperation<UmaPermission>() {
            @Override
            public void performAction(List<UmaPermission> entries) {
                for (UmaPermission p : entries) {
                    try {
                        ldapEntryManager.remove(p);
                    } catch (Exception e) {
                        log.error("Failed to remove entry", e);
                    }
                }
            }

        };
        ldapEntryManager.findEntries(staticConfiguration.getBaseDn().getClients(), UmaPermission.class, getExpiredUmaPermissionFilter(now), SearchScope.SUB, new String[] { "" }, batchService, 0, 0, CleanerTimer.BATCH_SIZE);
    }

    private Filter getExpiredUmaPermissionFilter(Date date) {
        return Filter.createLessOrEqualFilter("oxAuthExpiration", ldapEntryManager.encodeGeneralizedTime(date));
    }

    public void addBranch(String clientDn) {
        final SimpleBranch branch = new SimpleBranch();
        branch.setOrganizationalUnitName(ORGUNIT_OF_RESOURCE_PERMISSION);
        branch.setDn(getBranchDn(clientDn));
        ldapEntryManager.persist(branch);
    }

    public void addBranchIfNeeded(String clientDn) {
        if (!containsBranch(clientDn)) {
            addBranch(clientDn);
        }
    }

    public boolean containsBranch(String clientDn) {
        return ldapEntryManager.contains(SimpleBranch.class, getBranchDn(clientDn));
    }

    public String changeTicket(List<UmaPermission> permissions, Map<String, String> attributes) {
        String newTicket = generateNewTicket();

        for (UmaPermission permission : permissions) {
            ldapEntryManager.remove(permission);

            String dn = String.format("oxTicket=%s,%s", newTicket, StringUtils.substringAfter(permission.getDn(), ","));
            permission.setTicket(newTicket);
            permission.setDn(dn);
            permission.setAttributes(attributes);
            ldapEntryManager.persist(permission);
        }
        return newTicket;
    }
}
