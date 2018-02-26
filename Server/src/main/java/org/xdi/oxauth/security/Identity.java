/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.security;

import org.xdi.oxauth.model.common.SessionId;
import org.xdi.oxauth.model.common.User;
import org.xdi.oxauth.model.session.SessionClient;

import javax.enterprise.context.RequestScoped;
import javax.inject.Named;

/**
 * @version August 9, 2017
 */
@RequestScoped
@Named
public class Identity extends org.xdi.model.security.Identity {

    private static final long serialVersionUID = 2751659008033189259L;

    private SessionId sessionId;

    private User user;
    private SessionClient sessionClient;

    public SessionId getSessionId() {
        return sessionId;
    }

    public void setSessionId(SessionId sessionId) {
        this.sessionId = sessionId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setSessionClient(SessionClient sessionClient) {
        this.sessionClient = sessionClient;
    }

	public SessionClient getSessionClient() {
		return sessionClient;
	}

}
