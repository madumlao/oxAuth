/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.client;

import org.xdi.oxauth.model.crypto.PublicKey;
import org.xdi.oxauth.model.crypto.signature.ECDSAPublicKey;
import org.xdi.oxauth.model.crypto.signature.RSAPublicKey;
import org.xdi.oxauth.model.crypto.signature.SignatureAlgorithm;
import org.xdi.oxauth.model.crypto.signature.SignatureAlgorithmFamily;
import org.xdi.oxauth.model.jwk.JSONWebKey;
import org.xdi.oxauth.model.jwk.JSONWebKeySet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a JSON Web Key (JWK) received from the authorization server.
 *
 * @author Javier Rojas Blum
 * @version June 25, 2016
 */
public class JwkResponse extends BaseResponse {

    private JSONWebKeySet jwks;

    /**
     * Constructs a JWK response.
     *
     * @param status The response status code.
     */
    public JwkResponse(int status) {
        super(status);
    }

    public JSONWebKeySet getJwks() {
        return jwks;
    }

    public void setJwks(JSONWebKeySet jwks) {
        this.jwks = jwks;
    }

    /**
     * Search and returns a {@link org.xdi.oxauth.model.jwk.JSONWebKey} given its <code>keyId</code>.
     *
     * @param keyId The key id.
     * @return The JSONWebKey if found, otherwise <code>null</code>.
     */
    @Deprecated
    public JSONWebKey getKeyValue(String keyId) {
        for (JSONWebKey JSONWebKey : jwks.getKeys()) {
            if (JSONWebKey.getKid().equals(keyId)) {
                return JSONWebKey;
            }
        }

        return null;
    }

    @Deprecated
    public PublicKey getPublicKey(String keyId) {
        PublicKey publicKey = null;
        JSONWebKey JSONWebKey = getKeyValue(keyId);

        if (JSONWebKey != null) {
            switch (JSONWebKey.getKty()) {
                case RSA:
                    publicKey = new RSAPublicKey(
                            JSONWebKey.getN(),
                            JSONWebKey.getE());
                    break;
                case EC:
                    publicKey = new ECDSAPublicKey(
                            JSONWebKey.getAlg(),
                            JSONWebKey.getX(),
                            JSONWebKey.getY());
                    break;
                default:
                    break;
            }
        }

        return publicKey;
    }

    public List<JSONWebKey> getKeys(SignatureAlgorithm algorithm) {
        List<JSONWebKey> jsonWebKeys = new ArrayList<JSONWebKey>();

        if (SignatureAlgorithmFamily.RSA.equals(algorithm.getFamily())) {
            for (JSONWebKey jsonWebKey : jwks.getKeys()) {
                if (jsonWebKey.getAlg().equals(algorithm)) {
                    jsonWebKeys.add(jsonWebKey);
                }
            }
        } else if (SignatureAlgorithmFamily.EC.equals(algorithm.getFamily())) {
            for (JSONWebKey jsonWebKey : jwks.getKeys()) {
                if (jsonWebKey.getAlg().equals(algorithm)) {
                    jsonWebKeys.add(jsonWebKey);
                }
            }
        }

        Collections.sort(jsonWebKeys);
        return jsonWebKeys;
    }

    public String getKeyId(SignatureAlgorithm signatureAlgorithm) {
        List<JSONWebKey> jsonWebKeys = getKeys(signatureAlgorithm);
        if (jsonWebKeys.size() > 0) {
            return jsonWebKeys.get(0).getKid();
        } else {
            return null;
        }
    }
}