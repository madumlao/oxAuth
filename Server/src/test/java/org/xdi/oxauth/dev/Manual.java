/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.dev;

import java.io.File;
import java.util.Date;
import java.util.Properties;

import org.gluu.persist.ldap.impl.LdapEntryManager;
import org.gluu.persist.ldap.impl.LdapEntryManagerFactory;
import org.gluu.persist.ldap.operation.impl.LdapConnectionProvider;
import org.gluu.persist.ldap.operation.impl.LdapOperationsServiceImpl;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.xdi.oxauth.model.registration.Client;
import org.xdi.util.properties.FileConfiguration;
import org.xdi.util.security.PropertiesDecrypter;
import org.xdi.util.security.StringEncrypter;

/**
 * Test for manual run. Used for development purpose ONLY. Must not be run in
 * suite. ATTENTION : To make life easier must not have dependency on embedded
 * server.
 *
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 26/07/2012
 */

public class Manual {

	public static String LDAP_CONF_FILE_NAME = "oxauth-ldap.properties";
	public static final String CONF_FOLDER = "conf";

	private static final String LDAP_FILE_PATH = CONF_FOLDER + File.separator + LDAP_CONF_FILE_NAME;

	public static LdapEntryManager MANAGER = null;

	@BeforeClass
	public void init() {
		final FileConfiguration fileConfiguration = new FileConfiguration(LDAP_FILE_PATH);
		final Properties props = PropertiesDecrypter.decryptProperties(fileConfiguration.getProperties(), "passoword");
		final LdapEntryManagerFactory ldapEntryManagerFactory = new LdapEntryManagerFactory(); 
		final LdapConnectionProvider connectionProvider = new LdapConnectionProvider(props);
		MANAGER = ldapEntryManagerFactory.createEntryManager(props);
	}

	@AfterClass
	public void destroy() {
		MANAGER.getOperationService().getConnectionPool().close();
	}

	@Test
	public void getGroupsFromClient() {
		final Client client = MANAGER.find(Client.class, "inum=@!0000!0008!7652.0000,ou=clients,o=@!1111,o=gluu");
		System.out.println(client);
	}
}
