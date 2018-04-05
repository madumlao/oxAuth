/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.service;

import org.gluu.persist.ldap.impl.LdapEntryManager;
import org.gluu.persist.model.BatchOperation;
import org.gluu.persist.model.ProcessBatchOperation;
import org.slf4j.Logger;
import org.xdi.model.ApplicationType;
import org.xdi.oxauth.model.common.AuthorizationGrant;
import org.xdi.oxauth.model.common.AuthorizationGrantList;
import org.xdi.oxauth.model.configuration.AppConfiguration;
import org.xdi.oxauth.model.fido.u2f.DeviceRegistration;
import org.xdi.oxauth.model.fido.u2f.RequestMessageLdap;
import org.xdi.oxauth.model.registration.Client;
import org.xdi.oxauth.service.cdi.event.CleanerEvent;
import org.xdi.oxauth.service.fido.u2f.DeviceRegistrationService;
import org.xdi.oxauth.service.fido.u2f.RequestService;
import org.xdi.oxauth.uma.service.UmaPctService;
import org.xdi.oxauth.uma.service.UmaPermissionService;
import org.xdi.oxauth.uma.service.UmaResourceService;
import org.xdi.oxauth.uma.service.UmaRptService;
import org.xdi.service.cdi.async.Asynchronous;
import org.xdi.service.cdi.event.Scheduled;
import org.xdi.service.timer.event.TimerEvent;
import org.xdi.service.timer.schedule.TimerSchedule;

import javax.ejb.DependsOn;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Yuriy Zabrovarnyy
 * @author Javier Rojas Blum
 * @version August 9, 2017
 */
@ApplicationScoped
@DependsOn("appInitializer")
@Named
public class CleanerTimer {

    public final static int BATCH_SIZE = 100;
    private final static int DEFAULT_INTERVAL = 600; // 10 minutes

    @Inject
    private Logger log;

    @Inject
    private LdapEntryManager ldapEntryManager;

    @Inject
    private AuthorizationGrantList authorizationGrantList;

    @Inject
    private ClientService clientService;

    @Inject
    private GrantService grantService;

    @Inject
    private UmaRptService umaRptService;

    @Inject
    private UmaPctService umaPctService;

    @Inject
    private UmaPermissionService umaPermissionService;

    @Inject
    private UmaResourceService umaResourceService;

    @Inject
    private SessionIdService sessionIdService;

    @Inject
    @Named("u2fRequestService")
    private RequestService u2fRequestService;

    @Inject
    private MetricService metricService;

    @Inject
    private DeviceRegistrationService deviceRegistrationService;

    @Inject
    private AppConfiguration appConfiguration;

    @Inject
    private Event<TimerEvent> cleanerEvent;

    private AtomicBoolean isActive;

    public void initTimer() {
        log.debug("Initializing Cleaner Timer");
        this.isActive = new AtomicBoolean(false);

        int interval = appConfiguration.getCleanServiceInterval();
        if (interval <= 0) {
            interval = DEFAULT_INTERVAL;
        }

        cleanerEvent.fire(new TimerEvent(new TimerSchedule(interval, interval), new CleanerEvent(), Scheduled.Literal.INSTANCE));
    }

    @Asynchronous
    public void process(@Observes @Scheduled CleanerEvent cleanerEvent) {
        if (this.isActive.get()) {
            return;
        }

        if (!this.isActive.compareAndSet(false, true)) {
            return;
        }

        try {
            processAuthorizationGrantList();
            processRegisteredClients();

            Date now = new Date();
            this.umaRptService.cleanup(now);
            this.umaPermissionService.cleanup(now);
            this.umaPctService.cleanup(now);
            this.umaResourceService.cleanup(now);

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

        BatchOperation<Client> clientBatchService = new ProcessBatchOperation<Client>() {
            @Override
            public void performAction(List<Client> entries) {
                for (Client client : entries) {
                    try {
                        GregorianCalendar now = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
                        GregorianCalendar expirationDate = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
                        expirationDate.setTime(client.getClientSecretExpiresAt());
                        if (expirationDate.before(now)) {
                            List<AuthorizationGrant> toRemove = authorizationGrantList.getAuthorizationGrant(client.getClientId());
                            authorizationGrantList.removeAuthorizationGrants(toRemove);

                            log.debug("Removing Client: {}, Expiration date: {}",
                                    client.getClientId(),
                                    client.getClientSecretExpiresAt());
                            clientService.remove(client);
                        }
                    } catch (Exception e) {
                        log.error("Failed to remove entry", e);
                    }
                }
            }
        };

        clientService.getClientsWithExpirationDate(clientBatchService, new String[] {"inum", "oxAuthClientSecretExpiresAt"}, 0, BATCH_SIZE);

        log.debug("End Client clean up");
    }

    private void processU2fRequests() {
        log.debug("Start U2F request clean up");

        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.add(Calendar.SECOND, -90);
        final Date expirationDate = calendar.getTime();

        BatchOperation<RequestMessageLdap> requestMessageLdapBatchService = new ProcessBatchOperation<RequestMessageLdap>() {
            @Override
            public void performAction(List<RequestMessageLdap> entries) {
                for (RequestMessageLdap requestMessageLdap : entries) {
                    try {
                        log.debug("Removing RequestMessageLdap: {}, Creation date: {}",
                                requestMessageLdap.getRequestId(),
                                requestMessageLdap.getCreationDate());
                        u2fRequestService.removeRequestMessage(requestMessageLdap);
                    } catch (Exception e) {
                        log.error("Failed to remove entry", e);
                    }
                }
            }
        };

        u2fRequestService.getExpiredRequestMessages(requestMessageLdapBatchService, expirationDate, new String[] {"oxRequestId", "creationDate"}, 0, BATCH_SIZE);
        log.debug("End U2F request clean up");
    }

    private void processU2fDeviceRegistrations() {
        log.debug("Start U2F request clean up");

        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.add(Calendar.SECOND, -90);
        final Date expirationDate = calendar.getTime();

        BatchOperation<DeviceRegistration> deviceRegistrationBatchService = new ProcessBatchOperation<DeviceRegistration>() {
            @Override
            public void performAction(List<DeviceRegistration> entries) {
                for (DeviceRegistration deviceRegistration : entries) {
                    try {
                        log.debug("Removing DeviceRegistration: {}, Creation date: {}",
                                deviceRegistration.getId(),
                                deviceRegistration.getCreationDate());
                        deviceRegistrationService.removeUserDeviceRegistration(deviceRegistration);
                    } catch (Exception e) {
                        log.error("Failed to remove entry", e);
                    }
                }
            }
        };
        deviceRegistrationService.getExpiredDeviceRegistrations(deviceRegistrationBatchService, expirationDate, new String[] {"oxId", "creationDate"}, 0, BATCH_SIZE);

        log.debug("End U2F request clean up");
    }

    private void processMetricEntries() {
        log.debug("Start metric entries clean up");

        int keepDataDays = appConfiguration.getMetricReporterKeepDataDays();

        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.add(Calendar.DATE, -keepDataDays);
        Date expirationDate = calendar.getTime();

        metricService.removeExpiredMetricEntries(expirationDate, ApplicationType.OX_AUTH, metricService.applianceInum(), 0, BATCH_SIZE);

        log.debug("End metric entries clean up");
    }

}