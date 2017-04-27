package org.wildfly.swarm.elytron.runtime;

import java.security.Principal;
import java.security.spec.AlgorithmParameterSpec;

import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.auth.server.event.RealmEvent;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.evidence.Evidence;

/**
 * Created by bob on 5/3/17.
 */
public class Realm implements SecurityRealm {

    @Override
    public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
        System.err.println("get realm identity: " + principal);
        return null;
    }

    @Override
    public RealmIdentity getRealmIdentity(Evidence evidence) throws RealmUnavailableException {
        System.err.println("get realm identity: " + evidence);
        return null;
    }

    @Override
    public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String s, AlgorithmParameterSpec algorithmParameterSpec) throws RealmUnavailableException {
        System.err.println("get credential acquire support: " + credentialType + " " + algorithmParameterSpec);
        return null;
    }

    @Override
    public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName) throws RealmUnavailableException {
        System.err.println("get evidence verify support: " + evidenceType + " " + algorithmName);
        return null;
    }

    @Override
    public void handleRealmEvent(RealmEvent event) {
        System.err.println("handle realm event: " + event);
    }
}
