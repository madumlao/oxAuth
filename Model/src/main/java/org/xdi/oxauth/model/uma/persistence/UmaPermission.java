/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.model.uma.persistence;

import com.google.common.collect.Maps;
import org.gluu.site.ldap.persistence.annotation.*;

import java.util.*;

/**
 * UMA permission
 *
 * @author Yuriy Zabrovarnyy
 * @version 2.0, date: 17/05/2017
 */
@LdapEntry
@LdapObjectClass(values = {"top", "oxUmaResourcePermission"})
public class UmaPermission {

    public static final String PCT = "pct";

    @LdapDN
    private String dn;
    @LdapAttribute(name = "oxStatus")
	private String status;
    @LdapAttribute(name = "oxTicket")
	private String ticket;
    @LdapAttribute(name = "oxConfigurationCode")
	private String configurationCode;
    @LdapAttribute(name = "oxAuthExpiration")
	private Date expirationDate;

    @LdapAttribute(name = "oxResourceSetId")
    private String resourceId;
    @LdapAttribute(name = "oxAuthUmaScope")
    private List<String> scopeDns;

    @LdapJsonObject
    @LdapAttribute(name = "oxAttributes")
    private Map<String, String> attributes;

    private boolean expired;

    public UmaPermission() {
    }

    public UmaPermission(String resourceId, List<String> scopes, String ticket,
                         String configurationCode, Date expirationDate) {
		this.resourceId = resourceId;
        this.scopeDns = scopes;
		this.ticket = ticket;
		this.configurationCode = configurationCode;
		this.expirationDate = expirationDate;

		checkExpired();
	}

    public String getDn() {
        return dn;
    }

    public void setDn(String p_dn) {
        dn = p_dn;
    }

    public void checkExpired() {
		checkExpired(new Date());
	}

	public void checkExpired(Date now) {
        if (now.after(expirationDate)) {
            expired = true;
        }
	}

	public boolean isValid() {
		return !expired;
	}

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConfigurationCode() {
		return configurationCode;
	}

	public void setConfigurationCode(String configurationCode) {
		this.configurationCode = configurationCode;
	}

	public String getTicket() {
		return ticket;
	}

	public void setTicket(String ticket) {
		this.ticket = ticket;
	}

	public Date getExpirationDate() {
		return expirationDate;
	}

	public void setExpirationDate(Date expirationDate) {
		this.expirationDate = expirationDate;
	}

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public List<String> getScopeDns() {
        if (scopeDns == null) {
            scopeDns = new ArrayList<String>();
        }
        return scopeDns;
    }

    public void setScopeDns(List<String> p_scopeDns) {
        scopeDns = p_scopeDns;
    }

    public Map<String, String> getAttributes() {
        if (attributes == null) {
            attributes = Maps.newHashMap();
        }
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes != null ? attributes : new HashMap<String, String>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UmaPermission that = (UmaPermission) o;

        return !(ticket != null ? !ticket.equals(that.ticket) : that.ticket != null);

    }

    @Override
    public int hashCode() {
        return ticket != null ? ticket.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "UmaPermission{" +
                "dn='" + dn + '\'' +
                ", status='" + status + '\'' +
                ", ticket='" + ticket + '\'' +
                ", configurationCode='" + configurationCode + '\'' +
                ", expirationDate=" + expirationDate +
                ", resourceId='" + resourceId + '\'' +
                ", scopeDns=" + scopeDns +
                ", expired=" + expired +
                '}';
    }
}
