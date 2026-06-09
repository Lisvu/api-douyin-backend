package com.douyin.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class RedisDockerAutoStarter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisDockerAutoStarter.class);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(20);

    @Value("${app.redis.auto-start.enabled:true}")
    private boolean enabled;

    @Value("${app.redis.auto-start.container-name:douyin-redis}")
    private String containerName;

    @Value("${app.redis.auto-start.image:redis:7}")
    private String image;

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        if (!isLocalRedisHost(redisHost)) {
            log.info("Redis auto-start skipped because redis host is not local: {}", redisHost);
            return;
        }

        if (isRedisReachable()) {
            log.info("Redis is already running at {}:{}", redisHost, redisPort);
            return;
        }

        try {
            if (containerExists()) {
                runCommand("docker", "start", containerName);
                log.info("Started existing Redis container: {}", containerName);
            } else {
                runCommand("docker", "run",
                        "--name", containerName,
                        "-p", redisPort + ":6379",
                        "-d", image);
                log.info("Created and started Redis container: {} ({})", containerName, image);
            }
        } catch (Exception e) {
            log.warn("Redis auto-start failed: {}. Start it manually with: docker start {}",
                    e.getMessage(), containerName);
        }
    }

    private boolean isLocalRedisHost(String host) {
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
    }

    private boolean isRedisReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(redisHost, redisPort), 300);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean containerExists() {
        CommandResult result = runCommandAllowFailure("docker", "container", "inspect", containerName);
        return result.exitCode() == 0;
    }

    private void runCommand(String... command) {
        CommandResult result = runCommandAllowFailure(command);
        if (result.exitCode() != 0) {
            throw new IllegalStateException(result.output().isBlank()
                    ? String.join(" ", command) + " exited with code " + result.exitCode()
                    : result.output());
        }
    }

    private CommandResult runCommandAllowFailure(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            String output = readOutput(process);
            boolean finished = process.waitFor(COMMAND_TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(124, "Command timed out: " + String.join(" ", command));
            }
            return new CommandResult(process.exitValue(), output.trim());
        } catch (IOException e) {
            return new CommandResult(127, "Docker command unavailable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(130, "Interrupted while running: " + String.join(" ", command));
        }
    }

    private String readOutput(Process process) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return String.join(System.lineSeparator(), lines);
    }

    private record CommandResult(int exitCode, String output) {
    }
}
