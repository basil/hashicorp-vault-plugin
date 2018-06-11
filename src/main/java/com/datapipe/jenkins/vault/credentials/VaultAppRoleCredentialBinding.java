package com.datapipe.jenkins.vault.credentials;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.datapipe.jenkins.vault.exception.VaultPluginException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.util.Secret;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/* example:

Exports VAULT_ADDR and VAULT_TOKEN variables to pipeline environment.

         withCredentials([[
            $class: 'VaultAppRoleCredentialBinding',
            credentialsId: 'approle',
            vaultAddr: 'http://vault:8200'
            ]]) {
            sh 'echo token=$VAULT_ADDR'
            sh 'echo token=$VAULT_TOKEN'
        }

The name of the exported variables can be chosen.
 
        withCredentials([[
            $class: 'VaultAppRoleCredentialBinding',
            addrVariable: 'VA',
            tokenVariable: 'VT',
            credentialsId: 'approle',
            vaultAddr: 'http://vault:8200'
            ]]) {
            sh 'echo token=$VT'
            sh 'echo addr=$VA'
        }

 *
 */

/**
 * @author <a href="mailto:gotcha@bubblenet.be">Godefroid Chapelle</a>
 */
public class VaultAppRoleCredentialBinding extends MultiBinding<VaultAppRoleCredential> {

    private final static String DEFAULT_VAULT_ADDR_VARIABLE_NAME = "VAULT_ADDR";
    private final static String DEFAULT_VAULT_TOKEN_VARIABLE_NAME = "VAULT_TOKEN";

    @NonNull
    private final String addrVariable;
    private final String tokenVariable;
    private final String vaultAddr;

    /**
     *
     * @param addrVariable if {@code null}, {@value DEFAULT_VAULT_ADDR_VARIABLE_NAME} will be used.
     * @param tokenVariable if {@code null}, {@value DEFAULT_VAULT_TOKEN_VARIABLE_NAME} will be used.
     * @param credentialsId
     * @param vaultAddr
     */
    @DataBoundConstructor
    public VaultAppRoleCredentialBinding(@Nullable String addrVariable, @Nullable String tokenVariable, String credentialsId, String vaultAddr) {
        super(credentialsId);
        this.vaultAddr = vaultAddr;
        this.addrVariable = StringUtils.defaultIfBlank(addrVariable, DEFAULT_VAULT_ADDR_VARIABLE_NAME);
        this.tokenVariable = StringUtils.defaultIfBlank(tokenVariable, DEFAULT_VAULT_TOKEN_VARIABLE_NAME);
    }

    @NonNull
    public String getAddrVariable() {
        return addrVariable;
    }

    @NonNull
    public String getTokenVariable() {
        return tokenVariable;
    }

    @Override
    protected Class<VaultAppRoleCredential> type() {
        return VaultAppRoleCredential.class;
    }

    @Override
    public MultiEnvironment bind(@Nonnull Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        VaultAppRoleCredential credentials = getCredentials(build);
        Map<String,String> m = new HashMap<String,String>();
        m.put(addrVariable, vaultAddr);
        m.put(tokenVariable, getToken(credentials));

        return new MultiEnvironment(m);
    }

    private String getToken(VaultAppRoleCredential credentials) {
        String token;
        try {
            VaultConfig config = new VaultConfig(vaultAddr).build();
            Vault vault = new Vault(config);
            token = vault.auth().loginByAppRole("approle", credentials.getRoleId(), Secret.toString(credentials.getSecretId())).getAuthClientToken();
        } catch (VaultException e) {
            throw new VaultPluginException("failed to connect to vault", e);
        }
        return token;
    }

    @Override
    public Set<String> variables() {
        return new HashSet<String>(Arrays.asList(addrVariable, tokenVariable));
    }

    @Extension
    public static class DescriptorImpl extends BindingDescriptor<VaultAppRoleCredential> {

        @Override protected Class<VaultAppRoleCredential> type() {
            return VaultAppRoleCredential.class;
        }

        @Override public String getDisplayName() {
            return "Vault App Role token";
        }
    }

}

