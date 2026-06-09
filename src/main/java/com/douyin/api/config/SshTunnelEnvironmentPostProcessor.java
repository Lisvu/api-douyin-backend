package com.douyin.api.config;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;

public class SshTunnelEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Duration START_TIMEOUT = Duration.ofSeconds(12);
    private static final Duration HEALTH_CHECK_INTERVAL = Duration.ofSeconds(5);
    private static Session tunnelSession;
    private static volatile boolean monitorStarted;
    private static volatile boolean shutdownHookRegistered;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean enabled = environment.getProperty("app.ssh-tunnel.enabled", Boolean.class, false);
        if (!enabled) {
            return;
        }

        String localHost = environment.getProperty("app.ssh-tunnel.local-host", "127.0.0.1");
        int localPort = environment.getProperty("app.ssh-tunnel.local-port", Integer.class, 5432);
        String remoteHost = environment.getProperty("app.ssh-tunnel.remote-host", "127.0.0.1");
        int remotePort = environment.getProperty("app.ssh-tunnel.remote-port", Integer.class, 5432);
        String sshHost = environment.getRequiredProperty("app.ssh-tunnel.ssh-host");
        int sshPort = environment.getProperty("app.ssh-tunnel.ssh-port", Integer.class, 22);
        String sshUsername = environment.getRequiredProperty("app.ssh-tunnel.ssh-username");
        String sshPassword = environment.getRequiredProperty("app.ssh-tunnel.ssh-password");

        if (databaseIsReady(environment)) {
            System.out.printf("PostgreSQL is already available through %s:%d%n", localHost, localPort);
            startTunnelMonitor(environment, localHost, localPort, remoteHost, remotePort, sshHost, sshPort, sshUsername, sshPassword);
            return;
        }

        startTunnel(environment, localHost, localPort, remoteHost, remotePort, sshHost, sshPort, sshUsername, sshPassword);
        startTunnelMonitor(environment, localHost, localPort, remoteHost, remotePort, sshHost, sshPort, sshUsername, sshPassword);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private synchronized void startTunnel(ConfigurableEnvironment environment,
                                          String localHost,
                                          int localPort,
                                          String remoteHost,
                                          int remotePort,
                                          String sshHost,
                                          int sshPort,
                                          String sshUsername,
                                          String sshPassword) {
        System.out.printf("Starting SSH tunnel: localhost:%d -> %s:%d via %s%n",
                localPort, remoteHost, remotePort, sshHost);

        try {
            stopTunnel();
            JSch jsch = new JSch();
            tunnelSession = jsch.getSession(sshUsername, sshHost, sshPort);
            tunnelSession.setPassword(sshPassword);
            tunnelSession.setConfig("StrictHostKeyChecking", "no");
            tunnelSession.setServerAliveInterval(30_000);
            tunnelSession.setServerAliveCountMax(3);
            tunnelSession.connect((int) START_TIMEOUT.toMillis());
            tunnelSession.setPortForwardingL(localHost, localPort, remoteHost, remotePort);
            registerShutdownHook();
            waitUntilReady(environment);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to start SSH tunnel for PostgreSQL", e);
        } catch (JSchException e) {
            throw new IllegalStateException("Failed to start SSH tunnel for PostgreSQL", e);
        }
    }

    private void waitUntilReady(ConfigurableEnvironment environment) throws InterruptedException {
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (tunnelSession == null || !tunnelSession.isConnected()) {
                throw new IllegalStateException("SSH tunnel disconnected before the database was ready");
            }
            if (databaseIsReady(environment)) {
                System.out.println("SSH tunnel ready for PostgreSQL");
                return;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Timed out waiting for PostgreSQL through SSH tunnel");
    }

    private boolean databaseIsReady(ConfigurableEnvironment environment) {
        String url = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        if (url == null || username == null || password == null) {
            return false;
        }
        try {
            DriverManager.setLoginTimeout(2);
            try (var ignored = DriverManager.getConnection(url, username, password)) {
                return true;
            }
        } catch (SQLException ignored) {
            return false;
        }
    }

    private void stopTunnel() {
        if (tunnelSession != null && tunnelSession.isConnected()) {
            tunnelSession.disconnect();
        }
    }

    private void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopTunnel, "ssh-tunnel-shutdown"));
    }

    private void startTunnelMonitor(ConfigurableEnvironment environment,
                                    String localHost,
                                    int localPort,
                                    String remoteHost,
                                    int remotePort,
                                    String sshHost,
                                    int sshPort,
                                    String sshUsername,
                                    String sshPassword) {
        if (monitorStarted) {
            return;
        }
        monitorStarted = true;
        Thread monitor = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(HEALTH_CHECK_INTERVAL.toMillis());
                    if (!databaseIsReady(environment)) {
                        System.out.println("PostgreSQL tunnel is unavailable, reconnecting SSH tunnel...");
                        startTunnel(environment, localHost, localPort, remoteHost, remotePort, sshHost, sshPort, sshUsername, sshPassword);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("SSH tunnel health check failed: " + e.getMessage());
                }
            }
        }, "ssh-tunnel-health-check");
        monitor.setDaemon(true);
        monitor.start();
    }
}
