package org.xdi.oxauth.service.external.internal;

import org.xdi.model.SimpleCustomProperty;
import org.xdi.model.custom.script.type.auth.DummyPersonAuthenticationType;
import org.xdi.model.security.Credentials;
import org.xdi.oxauth.service.AuthenticationService;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;

/**
 * Wrapper to call internal authentication method
 *
 * @author Yuriy Movchan Date: 06/04/2015
 */
@Stateless
@Named
public class InternalDefaultPersonAuthenticationType extends DummyPersonAuthenticationType {

	@Inject
	private AuthenticationService authenticationService;

	@Inject
	private Credentials credentials;

	public InternalDefaultPersonAuthenticationType() {
	}

	@Override
	public boolean authenticate(Map<String, SimpleCustomProperty> configurationAttributes, Map<String, String[]> requestParameters, int step) {
		if (!credentials.isSet()) {
			return false;
		}

		return authenticationService.authenticate(credentials.getUsername(), credentials.getPassword());
	}

	@Override
	public boolean prepareForStep(Map<String, SimpleCustomProperty> configurationAttributes, Map<String, String[]> requestParameters, int step) {
		if (step == 1) {
			return true;
		}
		
		return super.prepareForStep(configurationAttributes, requestParameters, step);
	}

	@Override
	public int getCountAuthenticationSteps(Map<String, SimpleCustomProperty> configurationAttributes) {
		return 1;
	}

	@Override
	public boolean logout(Map<String, SimpleCustomProperty> configurationAttributes, Map<String, String[]> requestParameters) {
		return true;
	}

}
