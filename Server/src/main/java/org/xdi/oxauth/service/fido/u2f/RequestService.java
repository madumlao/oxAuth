/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.service.fido.u2f;

import java.util.Date;
import java.util.List;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;

import org.gluu.persist.ldap.impl.LdapEntryManager;
import org.gluu.persist.model.BatchOperation;
import org.gluu.persist.model.SearchScope;
import org.gluu.search.filter.Filter;
import org.slf4j.Logger;
import org.xdi.oxauth.model.config.StaticConfiguration;
import org.xdi.oxauth.model.fido.u2f.RequestMessageLdap;

/**
 * Provides generic operations with U2F requests
 *
 * @author Yuriy Movchan Date: 05/19/2015
 */
@Stateless
@Named("u2fRequestService")
public class RequestService {

	@Inject
	private Logger log;

	@Inject
	private LdapEntryManager ldapEntryManager;

	@Inject
	private StaticConfiguration staticConfiguration;

	public List<RequestMessageLdap> getExpiredRequestMessages(BatchOperation<RequestMessageLdap> batchOperation, Date expirationDate, String[] returnAttributes, int sizeLimit, int chunkSize) {
		final String u2fBaseDn = staticConfiguration.getBaseDn().getU2fBase(); // ou=u2f,o=@!1111,o=gluu
		Filter expirationFilter = Filter.createLessOrEqualFilter("creationDate", ldapEntryManager.encodeGeneralizedTime(expirationDate));

		List<RequestMessageLdap> requestMessageLdap = ldapEntryManager.findEntries(u2fBaseDn, RequestMessageLdap.class, expirationFilter, SearchScope.SUB, returnAttributes, batchOperation, 0, sizeLimit, chunkSize);

		return requestMessageLdap;
	}

	public void removeRequestMessage(RequestMessageLdap requestMessageLdap) {
		ldapEntryManager.remove(requestMessageLdap);
	}

}
