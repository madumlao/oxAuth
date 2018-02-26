/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.uma.ws.rs;

import com.wordnik.swagger.annotations.Api;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.xdi.model.GluuImage;
import org.xdi.oxauth.model.error.ErrorResponseFactory;
import org.xdi.oxauth.model.uma.UmaConstants;
import org.xdi.oxauth.model.uma.UmaErrorResponseType;
import org.xdi.oxauth.model.uma.persistence.UmaScopeDescription;
import org.xdi.oxauth.uma.service.UmaScopeService;
import org.xdi.service.XmlService;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

/**
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 02/05/2013
 */

@Path("/uma/scopes/icons")
@Api(value= "/uma/scopes/icons", description = "UMA Scope Icon endpoint provides scope icon by scope id.")
public class UmaScopeIconWS {

    @Inject
    private Logger log;

    @Inject
    private ErrorResponseFactory errorResponseFactory;

    @Inject
    private UmaScopeService umaScopeService;

    @Inject
    private XmlService xmlService;

    @GET
    @Path("{id}")
    @Produces({UmaConstants.JSON_MEDIA_TYPE})
    public Response getScopeDescription(@PathParam("id") String id) {
        log.trace("UMA - get scope's icon : id: {}", id);
        try {
            if (StringUtils.isNotBlank(id)) {
                final UmaScopeDescription scope = umaScopeService.getScope(id);
                if (scope != null && StringUtils.isNotBlank(scope.getFaviconImageAsXml())) {
                    final GluuImage gluuImage = xmlService.getGluuImageFromXML(scope.getFaviconImageAsXml());

                    if (gluuImage != null && ArrayUtils.isNotEmpty(gluuImage.getData())) {
                        // todo yuriyz : it must be clarified how exactly content of image must be shared between oxTrust and oxAuth
                        // currently oxTrust save content on disk however oxAuth expects it in ldap as we must support clustering!

                        // send non-streamed image as it's anyway picked up in memory (i know it's not nice...)

                        return Response.status(Response.Status.OK).entity(gluuImage.getData()).build();
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.SERVER_ERROR)).build());
        }
        throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                .entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.NOT_FOUND)).build());
    }
}
