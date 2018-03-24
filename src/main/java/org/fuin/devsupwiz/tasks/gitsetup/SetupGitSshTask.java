/**
 * Copyright (C) 2015 Michael Schnell. All rights reserved. 
 * http://www.fuin.org/
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.devsupwiz.tasks.gitsetup;

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.fuin.devsupwiz.common.DevSupWizUtils.MDC_TASK_KEY;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.Security;

import javax.enterprise.inject.Vetoed;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.io.FileUtils;
import org.fuin.devsupwiz.common.AbstractSetupTask;
import org.fuin.devsupwiz.common.DevSupWizUtils;
import org.fuin.devsupwiz.common.MultipleInstancesSetupTask;
import org.fuin.devsupwiz.common.UserInput;
import org.fuin.utils4j.Utils4J;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Generates a key pair and adds it to the "~/.ssh/config" file. The public key
 * is also submitted to the git provider (Bitbucket, Github) using a REST API.
 */
@Vetoed
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = SetupGitSshTask.KEY)
public final class SetupGitSshTask extends AbstractSetupTask
        implements MultipleInstancesSetupTask {

    /** Unique normalized name of the task (for example used for FXML file). */
    static final String KEY = "setup-git-ssh";

    private static final Logger LOG = LoggerFactory
            .getLogger(SetupGitSshTask.class);

    @NotEmpty
    @XmlAttribute(name = "id")
    private String id;

    @NotEmpty(message = "{setup-git-ssh.name.empty}", groups = {
            UserInput.class })
    @XmlAttribute(name = "name")
    private String name;

    @NotEmpty(message = "{setup-git-ssh.host.empty}", groups = {
            UserInput.class })
    @XmlAttribute(name = "host")
    private String host;

    @XmlAttribute(name = "public-key")
    private String publicKey;

    @NotNull(message = "sshDir==null")
    private transient File sshDir;

    /**
     * Default constructor for JAXB.
     */
    protected SetupGitSshTask() {
        super();
        sshDir = getDefaultSshDir();
    }

    /**
     * Constructor with predefined values.
     * 
     * @param id
     *            Unique task identifier.
     * @param name
     *            User's name.
     * @param host
     *            Host name (Domain without "www").
     */
    public SetupGitSshTask(@NotEmpty final String id,
            @NotEmpty final String name, @NotEmpty final String host) {
        this(id, name, host, getDefaultSshDir());
    }

    /**
     * Constructor for tests.
     * 
     * @param id
     *            Unique task identifier.
     * @param name
     *            User's name.
     * @param host
     *            Host name (Domain without "www").
     * @param sshDir
     *            SSH directory.
     */
    SetupGitSshTask(@NotEmpty final String id, @NotEmpty final String name,
            @NotEmpty final String host, @NotNull final File sshDir) {
        super();
        this.id = id;
        this.name = name;
        this.host = host;
        this.sshDir = sshDir;
    }

    /**
     * Returns user's name.
     * 
     * @return First name and last name.
     */
    public final String getName() {
        return name;
    }

    /**
     * Sets user's name.
     * 
     * @param name
     *            First name and last name.
     */
    public final void setName(@NotEmpty final String name) {
        this.name = name;
    }

    /**
     * Returns host name.
     * 
     * @return Host name (Domain without "www").
     */
    public final String getHost() {
        return host;
    }

    /**
     * Sets host name.
     * 
     * @param host
     *            Host name (Domain without "www" like "github.com" or
     *            "bitbucket.org").
     */
    public final void setHost(@NotEmpty final String host) {
        this.host = host;
    }

    /**
     * Returns the public key that was generated.
     * 
     * @return SSH public key.
     */
    public final String getPublicKey() {
        return publicKey;
    }

    @Override
    public final void execute() {

        MDC.put(MDC_TASK_KEY, getTypeId());
        try {

            if (alreadyExecuted()) {
                LOG.debug("Task already executed: " + getTypeId());

            } else {
                LOG.debug("Task not executed: " + getTypeId());

                init();

                final SshKeyPairGenerator generator = generateKeys(sshDir,
                        getPrivateKeyFile(), getPublicKeyFile());
                appendToConfig(getPrivateKeyFile());
                publicKey = generator.getPublicKey();

                // Only add in productiion mode (not test)
                if (sshDir.equals(getDefaultSshDir())) {
                    DevSupWizUtils.addToSshKnownHosts(host);
                }

                success();
                LOG.info("Successfully finished SSH git setup");

            }

        } finally {
            MDC.remove(MDC_TASK_KEY);
        }

    }

    @Override
    public final String getResource() {
        return this.getClass().getPackage().getName().replace('.', '/') + "/"
                + KEY;
    }

    @Override
    public final String getFxml() {
        return "/" + getResource() + ".fxml";
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final String getType() {
        return KEY;
    }

    @Override
    public final String getTypeId() {
        return getType() + "[" + getId() + "]";
    }

    private final String getKeyFilename() {
        return name + "-" + host;
    }

    final File getConfigFile() {
        return new File(sshDir, "config");
    }

    private File getPrivateKeyFile() {
        return new File(sshDir, getKeyFilename() + ".prv");
    }

    private File getPublicKeyFile() {
        return new File(sshDir, getKeyFilename() + ".pub");
    }

    private void init() {

        // Make sure BC is available
        if (Security.getProvider("BC") == null) {
            Security.addProvider(
                    new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }

        // Ensure SSH directory exists
        if (!sshDir.exists()) {
            LOG.debug("Directory does not exist: " + sshDir);
            if (sshDir.mkdirs()) {
                LOG.debug("Created directory: " + sshDir);
            } else {
                throw new IllegalStateException(
                        "Failed to create directory: " + sshDir);
            }
        }

    }

    private SshKeyPairGenerator generateKeys(final File sshDir,
            final File prvKeyFile, final File pubKeyFile) {
        try {
            final SshKeyPairGenerator generator = new SshKeyPairGenerator(name);

            FileUtils.writeStringToFile(prvKeyFile, generator.getPrivateKey(),
                    Charset.forName("us-ascii"));
            // Only owner is allowed to access private key
            DevSupWizUtils.setFilePermissions(prvKeyFile, OWNER_READ,
                    OWNER_WRITE);

            FileUtils.writeStringToFile(pubKeyFile, generator.getPublicKey(),
                    Charset.forName("us-ascii"));

            LOG.info("Successfully generated and saved ssh keys: {} {}",
                    prvKeyFile, pubKeyFile);

            return generator;
        } catch (final IOException ex) {
            throw new RuntimeException(
                    "Wasn't able to write ssh keys to: " + sshDir, ex);
        }
    }

    private void appendToConfig(final File prvKeyFile) {
        try {

            final String lf = System.lineSeparator();
            final String str = "Host " + host + lf + "    User " + name + lf
                    + "    HostName " + host + lf + "    IdentityFile "
                    + prvKeyFile + lf;

            try (final FileWriter fw = new FileWriter(getConfigFile(), true)) {
                fw.write(str);
            }

            LOG.info("Successfully added entry to ssh config: {}",
                    getConfigFile());

        } catch (final IOException ex) {
            throw new RuntimeException(
                    "Wasn't able to write ssh config: " + getConfigFile(), ex);
        }
    }

    private static File getDefaultSshDir() {
        return new File(Utils4J.getUserHomeDir(), ".ssh");
    }

}
