/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.xdi.model.security.Identity;
import org.xdi.oxauth.model.authorize.AuthorizeRequestParam;
import org.xdi.oxauth.model.configuration.AppConfiguration;
import org.xdi.oxauth.model.util.Util;

/**
 * @author Yuriy Movchan
 * @author Javier Rojas Blum
 * 
 * @version November 24, 2017
 */
@Stateless
@Named
public class RequestParameterService {

	// use only "acr" instead of "acr_values" #334
    public static final List<String> ALLOWED_PARAMETER = Collections.unmodifiableList(Arrays.asList(
            AuthorizeRequestParam.SCOPE,
            AuthorizeRequestParam.RESPONSE_TYPE,
            AuthorizeRequestParam.CLIENT_ID,
            AuthorizeRequestParam.REDIRECT_URI,
            AuthorizeRequestParam.STATE,
            AuthorizeRequestParam.RESPONSE_MODE,
            AuthorizeRequestParam.NONCE,
            AuthorizeRequestParam.DISPLAY,
            AuthorizeRequestParam.PROMPT,
            AuthorizeRequestParam.MAX_AGE,
            AuthorizeRequestParam.UI_LOCALES,
            AuthorizeRequestParam.ID_TOKEN_HINT,
            AuthorizeRequestParam.LOGIN_HINT,
            AuthorizeRequestParam.ACR_VALUES,
            AuthorizeRequestParam.SESSION_ID,
            AuthorizeRequestParam.REQUEST,
            AuthorizeRequestParam.REQUEST_URI,
            AuthorizeRequestParam.ORIGIN_HEADERS,
            AuthorizeRequestParam.CODE_CHALLENGE,
            AuthorizeRequestParam.CODE_CHALLENGE_METHOD,
            AuthorizeRequestParam.CUSTOM_RESPONSE_HEADERS,
            AuthorizeRequestParam.CLAIMS));

    @Inject
    private Logger log;

    @Inject
    private Identity identity;

    @Inject
    private AppConfiguration appConfiguration;


    public Map<String, String> getAllowedParameters(@Nonnull final Map<String, String> requestParameterMap) {
        Set<String> authorizationRequestCustomAllowedParameters = appConfiguration.getAuthorizationRequestCustomAllowedParameters();
        if (authorizationRequestCustomAllowedParameters == null) {
        	authorizationRequestCustomAllowedParameters = new HashSet<String>(0);
        }

        final Map<String, String> result = new HashMap<String, String>();
        if (!requestParameterMap.isEmpty()) {
            final Set<Map.Entry<String, String>> set = requestParameterMap.entrySet();
            for (Map.Entry<String, String> entry : set) {
                if (ALLOWED_PARAMETER.contains(entry.getKey()) || authorizationRequestCustomAllowedParameters.contains(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return result;
    }

    public Map<String, String> getCustomParameters(@Nonnull final Map<String, String> requestParameterMap) {
        Set<String> authorizationRequestCustomAllowedParameters = appConfiguration.getAuthorizationRequestCustomAllowedParameters();

        final Map<String, String> result = new HashMap<String, String>();
        if (authorizationRequestCustomAllowedParameters == null) {
        	return result;
        }

        if (!requestParameterMap.isEmpty()) {
            final Set<Map.Entry<String, String>> set = requestParameterMap.entrySet();
            for (Map.Entry<String, String> entry : set) {
                if (authorizationRequestCustomAllowedParameters.contains(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return result;
    }

    public String parametersAsString(final Map<String, String> parameterMap) throws UnsupportedEncodingException {
        final StringBuilder sb = new StringBuilder();
        final Set<Entry<String, String>> set = parameterMap.entrySet();
        for (Map.Entry<String, String> entry : set) {
            final String value = (String) entry.getValue();
            if (StringUtils.isNotBlank(value)) {
                sb.append(entry.getKey()).append("=").append(URLEncoder.encode(value, Util.UTF8_STRING_ENCODING)).append("&");
            }
        }

        String result = sb.toString();
        if (result.endsWith("&")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public Map<String, String> getParametersMap(List<String> extraParameters, final Map<String, String> parameterMap) {
        final List<String> allowedParameters = new ArrayList<String>(ALLOWED_PARAMETER);

        if (extraParameters != null) {
            for (String extraParameter : extraParameters) {
                putInMap(parameterMap, extraParameter);
            }

            allowedParameters.addAll(extraParameters);
        }

        for (Iterator<Entry<String, String>> it = parameterMap.entrySet().iterator(); it.hasNext(); ) {
            Entry<String, String> entry = it.next();
            if (!allowedParameters.contains(entry.getKey())) {
                it.remove();
            }
        }

        return parameterMap;
    }

    private void putInMap(Map<String, String> map, String p_name) {
        if (map == null) {
            return;
        }

        String value = getParameterValue(p_name);

        map.put(p_name, value);
    }

    public String getParameterValue(String p_name) {
        final Object o = identity.getWorkingParameter(p_name);
        if (o instanceof String) {
            final String s = (String) o;
            return s;
        } else if (o instanceof Integer) {
            final Integer i = (Integer) o;
            return i.toString();
        } else if (o instanceof Boolean) {
            final Boolean b = (Boolean) o;
            return b.toString();
        }

        return null;
    }

}
