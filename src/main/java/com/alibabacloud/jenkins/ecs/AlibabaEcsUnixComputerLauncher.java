package com.alibabacloud.jenkins.ecs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.alibabacloud.credentials.plugin.auth.AlibabaPrivateKey;
import com.alibabacloud.jenkins.ecs.exception.AlibabaEcsException;
import com.alibabacloud.jenkins.ecs.util.LogHelper;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.Session;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.slaves.CommandLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

/**
 * Created by kunlun.ykl on 2020/8/24.
 */
@Slf4j
public class AlibabaEcsUnixComputerLauncher extends AlibabaEcsComputerLauncher {

    @Override
    protected void launchScript(AlibabaEcsComputer computer, TaskListener listener) throws AlibabaEcsException, IOException, InterruptedException {
        log.info("launchScript start...");
        AlibabaEcsSpotFollower node = computer.getNode();
        if (node == null) {
            LogHelper.error(log, listener, "node is null", null);
            throw new IllegalStateException("node is null");
        }
        String remoteFs = node.getRemoteFS();
        String initScript = node.getInitScript();
        PrintStream remoteLogger = listener.getLogger();
        Connection conn = null;
        try {
            // 1. ssh connect to follower
            File identityKeyFile = createIdentityKeyFile(listener, computer);
            conn = connectToSsh(computer, listener);

            String tmpDir = "/tmp";
            conn.exec("mkdir -p " + tmpDir, remoteLogger);

            // 2. install jdk and scp
            installJdk(node, computer, conn ,remoteLogger, listener);

            //executeRemote(computer, conn, "git --version", "sudo yum install -y git", remoteLogger, listener);

            // 3. run initScript
            SCPClient scp = conn.createSCPClient();
            if (initScript != null && initScript.trim().length() > 0
                    && conn.exec("test -e ~/.hudson-run-init", remoteLogger) != 0) {
                boolean initScriptSuccess = executeInitScript(listener, initScript, conn, tmpDir, scp);
                if (!initScriptSuccess) {
                    return;
                }
            }

            // 3. scp remoting.jar
            scp.put(Jenkins.get().getJnlpJars("remoting.jar").readFully(), "remoting.jar", tmpDir);

            // 4. 启动follower的jenkins进程
            String workDir = StringUtils.isNotBlank(remoteFs) ? remoteFs : tmpDir;
            String hostIp = getHostIp(node, listener);

            if (hostIp == null) {
                listener.getLogger().println("Host IP is null. Aborting launch.");
                return; // or throw an exception if this is inside a method that returns void
            }
            
            String launchString
                = "java -jar " + tmpDir + "/remoting.jar -workDir " + workDir + " -jar-cache "
                + workDir + "/remoting/jarCache";
            String sshClientLaunchString = String.format("ssh -o StrictHostKeyChecking=no -i %s %s@%s -p %d %s",
                identityKeyFile.getAbsolutePath(), node.getRemoteAdmin(),
                hostIp,
                22,
                launchString);
            CommandLauncher commandLauncher = new CommandLauncher(sshClientLaunchString, null);
            commandLauncher.launch(computer, listener);
        } finally {
            if (null != conn) {
                conn.close();
            }
        }
    }

    private boolean executeInitScript(TaskListener listener, String initScript,
                                      Connection conn, String tmpDir, SCPClient scp)
            throws IOException, InterruptedException {
        scp.put(initScript.getBytes("UTF-8"), "init.sh", tmpDir, "0700");

        Session sess = conn.openSession();
        sess.requestDumbPTY(); // so that the remote side bundles stdout and stderr
        sess.execCommand(tmpDir + "/init.sh");

        sess.getStdin().close(); // nothing to write here
        sess.getStderr().close(); // we are not supposed to get anything from stderr
        IOUtils.copy(sess.getStdout(), listener.getLogger());

        int exitStatus = waitCompletion(sess);
        if (exitStatus != 0) {
            LogHelper.warn(log, listener, "init script failed: exit code=" + exitStatus, null);
            return false;
        }
        sess.close();

        sess = conn.openSession();
        sess.requestDumbPTY(); // so that the remote side bundles stdout
        // and stderr
        sess.execCommand("touch ~/.hudson-run-init");

        sess.getStdin().close(); // nothing to write here
        sess.getStderr().close(); // we are not supposed to get anything from stderr
        IOUtils.copy(sess.getStdout(), listener.getLogger());

        exitStatus = waitCompletion(sess);
        if (exitStatus != 0) {
            LogHelper.warn(log, listener, "init script failed: exit code=" + exitStatus, null);
            return false;
        }
        sess.close();
        return true;
    }

    private Connection connectToSsh(AlibabaEcsComputer computer, TaskListener listener)
            throws IOException {
        final AlibabaEcsSpotFollower node = computer.getNode();

        final long timeout = node == null ? 0L : node.getLaunchTimeoutInMillis();
        final long startTime = System.currentTimeMillis();
        while (true) {
            long waitTime = System.currentTimeMillis() - startTime;
            if (timeout > 0 && waitTime > timeout) {
                throw new IOException("Timed out after " + (waitTime / 1000)
                        + " seconds of waiting for ssh to become available. (maximum timeout configured is "
                        + (timeout / 1000) + ")");
            }
            try {
                LogHelper.info(log, listener, "try to connect to ssh.", null);
                AlibabaEcsSpotFollower follower = computer.getNode();
                if (null == follower) {
                    throw new AlibabaEcsException("alibabaEcsSpotFollower is null");
                }

                // 优先使用公网IP, 没有的话, 则降级到内网IP.
                String privateIp = follower.getPrivateIp();
                String hostIp = follower.getPublicIp();
                if (StringUtils.isEmpty(hostIp)) {
                    LogHelper.warn(log, listener,
                            "connectToSsh using privateIp " + privateIp + " publicIp is null. instanceId: " + follower.getEcsInstanceId(), null);
                    hostIp = privateIp;
                }
                Connection conn = new Connection(hostIp, 22);
                conn.connect(new ServerHostKeyVerifierImpl(computer, listener), 10000, 10000);

                LogHelper.info(log, listener, "connectToSsh success. cost: " + (System.currentTimeMillis() - startTime) + "ms", null);

                boolean b = false;
                AlibabaCloud alibabaCloud = computer.getCloud();
                if (null != alibabaCloud) {
                    AlibabaPrivateKey key = alibabaCloud.resolvePrivateKey();
                    if (null != key) {
                        b = conn.authenticateWithPublicKey(follower.getRemoteAdmin(),
                            key.getPrivateKey().toCharArray(), "");
                    }

                }

                if (!b) {
                    LogHelper.info(log, listener, "ssh auth failed. retry.", null);
                } else {
                    LogHelper.info(log, listener,
                            "ssh auth success", null);
                    return conn;
                }
            } catch (Exception e) {
                LogHelper.error(log, listener, "connectToSsh error. cost: " + waitTime + "ms timeout threshold: " + timeout + "ms  Retrying...", e);
            }
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private File createIdentityKeyFile(TaskListener listener, AlibabaEcsComputer computer) throws IOException, AlibabaEcsException {
        AlibabaCloud cloud = computer.getCloud();
        if (null == cloud) {
            throw new AlibabaEcsException("cloud is null");
        }
        AlibabaPrivateKey tempKey = cloud.resolvePrivateKey();
        if (null == tempKey) {
            throw new AlibabaEcsException("AlibabaPrivateKey is null");
        }

        String privateKey = tempKey.getPrivateKey();

        File tempFile = Files.createTempFile("ecs_", ".pem").toFile();
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
            OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
            try {
                writer.write(privateKey);
                writer.flush();
            } finally {
                writer.close();
                fileOutputStream.close();
            }
            FilePath filePath = new FilePath(tempFile);
            filePath.chmod(0400); // octal file mask - readonly by owner
            return tempFile;
        } catch (Exception e) {
            LogHelper.error(log, listener, "Failed to create identity key file " + tempFile.getName(), e);
            if (!tempFile.delete()) {
                LogHelper.error(log, listener, "Failed to delete identity key file " + tempFile.getName(), e);
            }
            throw new IOException("Error creating temporary identity key file for connecting to ECS agent.", e);
        }
    }

    private boolean executeRemote(AlibabaEcsComputer computer, Connection conn, String checkCommand, String command,
                                  PrintStream logger, TaskListener listener)
            throws IOException, InterruptedException {
        LogHelper.info(log, listener, "Verifying: " + checkCommand, null);
        if (conn.exec(checkCommand, logger) != 0) {
            LogHelper.info(log, listener, "Installing: " + command, null);
            if (conn.exec(command, logger) != 0) {
                LogHelper.warn(log, listener, "Failed to install: " + command, null);
                return false;
            }
        }
        return true;
    }

    private static class ServerHostKeyVerifierImpl implements ServerHostKeyVerifier {
        private final SlaveComputer computer;
        private final TaskListener listener;

        public ServerHostKeyVerifierImpl(final SlaveComputer computer, final TaskListener listener) {
            this.computer = computer;
            this.listener = listener;
        }

        @Override
        public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm,
                                           byte[] serverHostKey) throws Exception {
            return true;
        }
    }

    private int waitCompletion(Session session) throws InterruptedException {
        // I noticed that the exit status delivery often gets delayed. Wait up
        // to 1 sec.
        for (int i = 0; i < 10; i++) {
            Integer r = session.getExitStatus();
            if (r != null) {
                return r;
            }
            Thread.sleep(100);
        }
        return -1;
    }

    private String getHostIp(AlibabaEcsSpotFollower node, TaskListener listener) {
        String hostIp = null;
        // 10 because we sometimes need more time
        for (int i = 0; i < 10; i++) {
            hostIp = node.getPublicIp();
            if (StringUtils.isBlank(hostIp)) {
                // 优先使用公网IP, 没有的话, 则降级到内网IP.
                hostIp = node.getPrivateIp();

                LogHelper.warn(log, listener,
                    "launchScript using privateIp " + hostIp + " publicIp is null. instanceId: " + node
                        .getEcsInstanceId(), null);
            }
            if (StringUtils.isNotBlank(hostIp)) {
                break;

            }
            LogHelper.warn(log, listener,
                "privateIp is null. publicIp is null. instanceId: " + node
                    .getEcsInstanceId(), null);
            try {
                Thread.sleep(2000L); // also need more time 
            } catch (InterruptedException e) {
                LogHelper.error(log, listener, "getHostIp error instanceId: " + node.getEcsInstanceId(), e);
            }
        }
        return hostIp;
    }

    private void installJdk(AlibabaEcsSpotFollower node, AlibabaEcsComputer computer, Connection conn,
                            PrintStream remoteLogger, TaskListener listener) throws IOException, InterruptedException {
        int i = -1;
        VersionNumber version = Jenkins.getVersion();
        if (null != version && null != node.lastFetchInstance && StringUtils.isNotBlank(
            node.lastFetchInstance.getOSName())) {
            VersionNumber versionNumber = new VersionNumber("2.303.1");
            i = version.compareTo(versionNumber);
            String osName = node.lastFetchInstance.getOSName();
            String[] s = osName.split(" ");
            String os = s[0];
            if ("Ubuntu".equals(os)){
                aptInstallJdk(i, remoteLogger, conn, computer, listener);
            }else {
                yumInstallJdk(i, remoteLogger, conn, computer, listener);
            }
        }else {
            LogHelper.warn(log, listener, "jenkins version is null or ecs os is null" , null);
            executeRemote(computer, conn, "java -fullversion", "sudo yum install -y java-1.8.0-openjdk.x86_64",
                remoteLogger,
                listener);
            executeRemote(computer, conn, "which scp", "sudo yum install -y openssh-clients", remoteLogger,
                listener);
        }
    }

    private void aptInstallJdk( int i, PrintStream remoteLogger, Connection conn, AlibabaEcsComputer computer,
                            TaskListener listener) throws IOException, InterruptedException {
        if (0 <= i) {
            conn.exec("sudo apt-get update", remoteLogger);
            executeRemote(computer, conn, "java -fullversion", "sudo apt-get install -y openjdk-11-jdk",
                remoteLogger,
                listener);
        } else {
            conn.exec("sudo apt-get update", remoteLogger);
            executeRemote(computer, conn, "java -fullversion", "sudo apt-get install -y openjdk-8-jdk",
                remoteLogger,
                listener);
        }
    }

    private void yumInstallJdk(int i, PrintStream remoteLogger, Connection conn, AlibabaEcsComputer computer,
    TaskListener listener) throws IOException, InterruptedException {
        if (0 <= i) {
            executeRemote(computer, conn, "java -fullversion", "sudo yum install -y java-11-openjdk.x86_64",
                remoteLogger,
                listener);
            executeRemote(computer, conn, "which scp", "sudo yum install -y openssh-clients", remoteLogger,
                listener);
        }else {
            executeRemote(computer, conn, "java -fullversion", "sudo yum install -y java-1.8.0-openjdk.x86_64",
                remoteLogger,
                listener);
            executeRemote(computer, conn, "which scp", "sudo yum install -y openssh-clients", remoteLogger,
                listener);
        }
    }



}
