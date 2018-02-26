/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.service;

import java.util.List;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.xdi.model.GluuAttribute;
import org.xdi.oxauth.model.config.StaticConfiguration;
import org.xdi.service.CacheService;
import org.xdi.util.StringHelper;

/**
 * @author Javier Rojas Blum
 * @version 0.9 March 27, 2015
 */
@Stateless
@Named
public class AttributeService extends org.xdi.service.AttributeService {

    private static final String CACHE_ATTRIBUTE = "AttributeCache";

    @Inject
    private Logger log;

    @Inject
    private CacheService cacheService;

    @Inject
    private StaticConfiguration staticConfiguration;

    /**
     * returns GluuAttribute by Dn
     *
     * @return GluuAttribute
     */
    public GluuAttribute getAttributeByDn(String dn) {
        GluuAttribute gluuAttribute = (GluuAttribute) cacheService.get(CACHE_ATTRIBUTE, dn);

        if (gluuAttribute == null) {
            gluuAttribute = ldapEntryManager.find(GluuAttribute.class, dn);
            cacheService.put(CACHE_ATTRIBUTE, dn, gluuAttribute);
        } else {
            log.trace("Get attribute from cache by Dn '{}'", dn);
        }

        return gluuAttribute;
    }

    public GluuAttribute getByLdapName(String name) {
        List<GluuAttribute> gluuAttributes = getAttributesByAttribute("gluuAttributeName", name, staticConfiguration.getBaseDn().getAttributes());
        if (gluuAttributes.size() > 0) {
            for (GluuAttribute gluuAttribute : gluuAttributes) {
                if (gluuAttribute.getName() != null && gluuAttribute.getName().equals(name)) {
                    return gluuAttribute;
                }
            }
        }

        return null;
    }

    public GluuAttribute getByClaimName(String name) {
        List<GluuAttribute> gluuAttributes = getAttributesByAttribute("oxAuthClaimName", name, staticConfiguration.getBaseDn().getAttributes());
        if (gluuAttributes.size() > 0) {
            for (GluuAttribute gluuAttribute : gluuAttributes) {
                if (gluuAttribute.getOxAuthClaimName() != null && gluuAttribute.getOxAuthClaimName().equals(name)) {
                    return gluuAttribute;
                }
            }
        }

        return null;
    }

    public List<GluuAttribute> getAllAttributes() {
        return getAllAttributes(staticConfiguration.getBaseDn().getAttributes());
    }

	public String getDnForAttribute(String inum) {
		String attributesDn = staticConfiguration.getBaseDn().getAttributes();
		if (StringHelper.isEmpty(inum)) {
			return attributesDn;
		}

		return String.format("inum=%s,%s", inum, attributesDn);
	}

}