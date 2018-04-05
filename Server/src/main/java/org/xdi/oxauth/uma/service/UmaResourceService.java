/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.uma.service;

import com.google.common.base.Preconditions;
import org.apache.commons.lang.StringUtils;
import org.gluu.persist.ldap.impl.LdapEntryManager;
import org.gluu.persist.model.BatchOperation;
import org.gluu.persist.model.ProcessBatchOperation;
import org.gluu.persist.model.SearchScope;
import org.gluu.persist.model.base.SimpleBranch;
import org.gluu.search.filter.Filter;
import org.slf4j.Logger;
import org.xdi.oxauth.model.config.StaticConfiguration;
import org.xdi.oxauth.model.error.ErrorResponseFactory;
import org.xdi.oxauth.model.uma.persistence.UmaResource;
import org.xdi.oxauth.service.CleanerTimer;
import org.xdi.service.CacheService;
import org.xdi.util.StringHelper;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

/**
 * Provides operations with resource set descriptions
 *
 * @author Yuriy Movchan
 * @author Yuriy Zabrovarnyy
 *         Date: 10.05.2012
 */
@Stateless
@Named
public class UmaResourceService {

    private static final int RESOURCE_CACHE_EXPIRATION_IN_SECONDS = 120;

    @Inject
    private Logger log;

    @Inject
    private LdapEntryManager ldapEntryManager;

    @Inject
    private ErrorResponseFactory errorResponseFactory;

    @Inject
    private StaticConfiguration staticConfiguration;

    @Inject
    private CacheService cacheService;

    public void addBranch() {
        SimpleBranch branch = new SimpleBranch();
        branch.setOrganizationalUnitName("resources");
        branch.setDn(getDnForResource(null));

        ldapEntryManager.persist(branch);
    }

    /**
     * Add new resource description entry
     *
     * @param resource resource
     */
    public void addResource(UmaResource resource) {
        validate(resource);
        ldapEntryManager.persist(resource);
        putInCache(resource);
    }

    public void validate(UmaResource resource) {
        Preconditions.checkArgument(StringUtils.isNotBlank(resource.getName()), "Name is required for resource.");
        Preconditions.checkArgument(((resource.getScopes() != null && !resource.getScopes().isEmpty()) || StringUtils.isNotBlank(resource.getScopeExpression())), "Scope must be specified for resource.");
        Preconditions.checkState(!resource.isExpired(), "UMA Resource expired. It must not be expired.");
        prepareBranch();
    }

    /**
     * Update resource description entry
     *
     * @param resource resource
     */
    public void updateResource(UmaResource resource) {
        validate(resource);
        ldapEntryManager.merge(resource);
    }

    /**
     * Remove resource description entry
     *
     * @param resource resource
     */
    public void remove(UmaResource resource) {
        ldapEntryManager.remove(resource);
    }

    /**
     * Remove resource description entry by ID.
     *
     * @param rsid resource ID
     */
    public void remove(String rsid) {
        ldapEntryManager.remove(getResourceById(rsid));
    }

    public void remove(List<UmaResource> resources) {
        for (UmaResource resource : resources) {
            remove(resource);
        }
    }

    /**
     * Get all resource descriptions
     *
     * @return List of resource descriptions
     */
    public List<UmaResource> getResourcesByAssociatedClient(String associatedClientDn) {
        try {
            prepareBranch();

            if (StringUtils.isNotBlank(associatedClientDn)) {
                final Filter filter = Filter.create(String.format("&(oxAssociatedClient=%s)", associatedClientDn));
                return ldapEntryManager.findEntries(getBaseDnForResource(), UmaResource.class, filter);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    /**
     * Get resource descriptions by example.
     *
     * Do not expose it outside because we want to involve cache where possible.
     *
     * @param resource Resource
     * @return Resource which conform example
     */
    private List<UmaResource> findResources(UmaResource resource) {
        return ldapEntryManager.findEntries(resource);
    }

    /**
     * Check if LDAP server contains resource description with specified attributes
     *
     * @return True if resource description with specified attributes exist
     */
    public boolean containsResource(UmaResource resource) {
        return ldapEntryManager.contains(resource);
    }

    public Set<UmaResource> getResources(Set<String> ids) {
        Set<UmaResource> result = new HashSet<UmaResource>();
        if (ids != null) {
            for (String id : ids) {
                UmaResource resource = getResourceById(id);
                if (resource != null) {
                    result.add(resource);
                } else {
                    log.error("Failed to find resource by id: " + id);
                }
            }
        }
        return result;
    }

    public UmaResource getResourceById(String id) {

        UmaResource fromCache = fromCache(getDnForResource(id));
        if (fromCache != null) {
            log.trace("UMA Resource from cache, id: " + id);
            return fromCache;
        }

        prepareBranch();

        UmaResource ldapResource = new UmaResource();
        ldapResource.setDn(getBaseDnForResource());
        ldapResource.setId(id);

        final List<UmaResource> result = findResources(ldapResource);
        if (result.size() == 0) {
            log.error("Failed to find resource set with id: " + id);
            errorResponseFactory.throwUmaNotFoundException();
        } else if (result.size() > 1) {
            log.error("Multiple resource sets found with given id: " + id);
            errorResponseFactory.throwUmaInternalErrorException();
        }
        return result.get(0);
    }

    private void prepareBranch() {
        // Create resource description branch if needed
        if (!ldapEntryManager.contains(SimpleBranch.class, getDnForResource(null))) {
            addBranch();
        }
    }

    /**
     * Get resource description by DN
     *
     * @param dn Resource description DN
     * @return Resource description
     */
    public UmaResource getResourceByDn(String dn) {
        UmaResource fromCache = fromCache(dn);
        if (fromCache != null) {
            return fromCache;
        }
        return ldapEntryManager.find(UmaResource.class, dn);
    }

    /**
     * Build DN string for resource description
     */
    public String getDnForResource(String oxId) {
        if (StringHelper.isEmpty(oxId)) {
            return getBaseDnForResource();
        }
        return String.format("oxId=%s,%s", oxId, getBaseDnForResource());
    }

    public String getBaseDnForResource() {
        final String umaBaseDn = staticConfiguration.getBaseDn().getUmaBase(); // "ou=uma,o=@!1111,o=gluu"
        return String.format("ou=resources,%s", umaBaseDn);
    }

    private void putInCache(UmaResource resource) {
        if (resource == null) {
            return;
        }

        try {
            cacheService.put(Integer.toString(RESOURCE_CACHE_EXPIRATION_IN_SECONDS), resource.getDn(), resource);
        } catch (Exception e) {
            log.error("Failed to put client in cache, client:" + resource, e);
        }
    }

    private UmaResource fromCache(String dn) {
        try {
            return (UmaResource) cacheService.get(null, dn);
        } catch (Exception e) {
            log.error("Failed to fetch client from cache, dn: " + dn, e);
            return null;
        }
    }

    public boolean removeFromCache(UmaResource resource) {
        try {
            cacheService.remove(null, resource.getDn());
        } catch (Exception e) {
            log.error(e.getMessage(), e);

            return false;
        }
        return true;
    }

    public void cleanup(Date now) {
        prepareBranch();

        BatchOperation<UmaResource> batchService = new ProcessBatchOperation<UmaResource>() {
            @Override
            public void performAction(List<UmaResource> entries) {
                for (UmaResource p : entries) {
                    try {
                        remove(p);
                    } catch (Exception e) {
                        log.error("Failed to remove entry", e);
                    }
                }
            }

        };
        ldapEntryManager.findEntries(getBaseDnForResource(), UmaResource.class, Filter.createLessOrEqualFilter("oxAuthExpiration", ldapEntryManager.encodeGeneralizedTime(now)), SearchScope.SUB, new String[]{""}, batchService, 0, 0, CleanerTimer.BATCH_SIZE);
    }
}
