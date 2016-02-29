/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.service;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.*;
import org.jboss.seam.annotations.Observer;
import org.jboss.seam.annotations.async.Asynchronous;
import org.jboss.seam.async.TimerSchedule;
import org.jboss.seam.core.Events;
import org.jboss.seam.log.Log;
import org.xdi.model.ApplicationType;
import org.xdi.oxauth.model.common.AuthorizationGrant;
import org.xdi.oxauth.model.common.AuthorizationGrantList;
import org.xdi.oxauth.model.config.ConfigurationFactory;
import org.xdi.oxauth.model.fido.u2f.DeviceRegistration;
import org.xdi.oxauth.model.fido.u2f.RequestMessageLdap;
import org.xdi.oxauth.model.registration.Client;
import org.xdi.oxauth.service.fido.u2f.DeviceRegistrationService;
import org.xdi.oxauth.service.fido.u2f.RequestService;
import org.xdi.oxauth.service.uma.RPTManager;
import org.xdi.oxauth.service.uma.ResourceSetPermissionManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Yuriy Zabrovarnyy
 * @author Javier Rojas Blum
 * @version December 15, 2015
 */
@Name("cleanerTimer")
@AutoCreate
@Scope(ScopeType.APPLICATION)
public class CleanerTimer {

    private final static String EVENT_TYPE = "CleanerTimerEvent";
    private final static int DEFAULT_INTERVAL = 600; // 10 minutes

    @Logger
    private Log log;
    @In
    private AuthorizationGrantList authorizationGrantList;
    @In
    private ClientService clientService;
    @In
    private GrantService grantService;
    @In
    private RPTManager rptManager;
    @In
    private ResourceSetPermissionManager resourceSetPermissionManager;
    @In
    private SessionStateService sessionStateService;

    @In
    private RequestService u2fRequestService;

    @In
    private MetricService metricService;
	
	@In
	private DeviceRegistrationService deviceRegistrationService;

    private AtomicBoolean isActive;

    @Observer("org.jboss.seam.postInitialization")
    public void init() {
        log.debug("Initializing CleanerTimer");
        this.isActive = new AtomicBoolean(false);

        long interval = ConfigurationFactory.instance().getConfiguration().getCleanServiceInterval();
        if (interval <= 0) {
            interval = DEFAULT_INTERVAL;
        }
        interval = interval * 1000L;
        Events.instance().raiseTimedEvent(EVENT_TYPE, new TimerSchedule(interval, interval));
    }

    @Observer(EVENT_TYPE)
    @Asynchronous
    public void process() {
        if (this.isActive.get()) {
            return;
        }

        if (!this.isActive.compareAndSet(false, true)) {
            return;
        }

        try {
            processAuthorizationGrantList();
            processRegisteredClients();
            sessionStateService.cleanUpSessions(); // remove unused session ids

            Date now = new Date();
            this.rptManager.cleanupRPTs(now);
            this.resourceSetPermissionManager.cleanupResourceSetPermissions(now);

            processU2fRequests();
            processU2fDeviceRegistrations();

            processMetricEntries();
        } finally {
            this.isActive.set(false);
        }
    }

    private void processAuthorizationGrantList() {
        log.debug("Start AuthorizationGrant clean up");
        grantService.cleanUp();
        log.debug("End AuthorizationGrant clean up");
    }

    private void processRegisteredClients() {
        log.debug("Start Client clean up");

        List<Client> clientList = clientService.getClientsWithExpirationDate(new String[]{"inum", "oxAuthClientSecretExpiresAt"});

        if (clientList != null && !clientList.isEmpty()) {
            for (Client client : clientList) {
                GregorianCalendar now = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
                GregorianCalendar expirationDate = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
                expirationDate.setTime(client.getClientSecretExpiresAt());

                if (expirationDate.before(now)) {
                    List<AuthorizationGrant> toRemove = authorizationGrantList.getAuthorizationGrant(client.getClientId());
                    authorizationGrantList.removeAuthorizationGrants(toRemove);

                    log.debug("Removing Client: {0}, Expiration date: {1}",
                            client.getClientId(),
                            client.getClientSecretExpiresAt());
                    clientService.remove(client);
                }
            }
        }

        log.debug("End Client clean up");
    }

    private void processU2fRequests() {
        log.debug("Start U2F request clean up");

        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.add(Calendar.SECOND, -90);
        Date expirationDate = calendar.getTime();

        List<RequestMessageLdap> expiredRequestMessage = u2fRequestService.getExpiredRequestMessages(expirationDate);
        if ((expiredRequestMessage != null) && !expiredRequestMessage.isEmpty()) {
            for (RequestMessageLdap requestMessageLdap : expiredRequestMessage) {
                log.debug("Removing RequestMessageLdap: {0}, Creation date: {1}",
                        requestMessageLdap.getRequestId(),
                        requestMessageLdap.getCreationDate());
                u2fRequestService.removeRequestMessage(requestMessageLdap);
            }
        }

        log.debug("End U2F request clean up");
    }

    private void processU2fDeviceRegistrations() {
        log.debug("Start U2F request clean up");

        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.add(Calendar.SECOND, -90);
        Date expirationDate = calendar.getTime();

        List<DeviceRegistration> deviceRegistrations = deviceRegistrationService.getExpiredDeviceRegistrations(expirationDate);
        if ((deviceRegistrations != null) && !deviceRegistrations.isEmpty()) {
            for (DeviceRegistration deviceRegistration : deviceRegistrations) {
                log.debug("Removing DeviceRegistration: {0}, Creation date: {1}",
                		deviceRegistration.getId(),
                		deviceRegistration.getCreationDate());
                deviceRegistrationService.removeUserDeviceRegistration(deviceRegistration);
            }
        }

        log.debug("End U2F request clean up");
    }

    private void processMetricEntries() {
        log.debug("Start metric entries clean up");

        int keepDataDays = ConfigurationFactory.instance().getConfiguration().getMetricReporterKeepDataDays();

        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.add(Calendar.DATE, -keepDataDays);
        Date expirationDate = calendar.getTime();

        metricService.removeExpiredMetricEntries(expirationDate, ApplicationType.OX_AUTH, metricService.applianceInum());

        log.debug("End metric entries clean up");
    }

}