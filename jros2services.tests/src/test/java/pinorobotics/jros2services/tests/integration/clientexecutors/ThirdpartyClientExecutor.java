/*
 * Copyright 2026 jrosservices project
 * 
 * Website: https://github.com/pinorobotics/jros2services
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pinorobotics.jros2services.tests.integration.clientexecutors;

import id.xfunction.lang.XExec;
import id.xfunction.lang.XProcess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import pinorobotics.jros2services.tests.integration.ClientExecutor;

/**
 * Executor that launches the third‑party client processes for a given ROS‑2 topic. Each executed
 * process writes its stderr to a temporary file; when the process terminates the captured log file
 * content is returned as a {@link Future}.
 *
 * @author lambdaprime intid@protonmail.com
 */
public class ThirdpartyClientExecutor implements ClientExecutor {

    private String serviceName;
    private int streamSize;
    private List<XProcess> procs = new ArrayList<XProcess>();

    public ThirdpartyClientExecutor(int streamSize, String serviceName) {
        this.streamSize = streamSize;
        this.serviceName = serviceName;
    }

    @Override
    public List<Future<Result>> executeClientsAsync() throws Exception {
        /* ---- 1. generate the same seed list that the original test used ---- */
        List<Integer> seeds =
                new Random()
                        .ints(streamSize, 0, 1_000)
                        .distinct()
                        .mapToObj(Integer::valueOf)
                        .collect(Collectors.toList());

        /* ---- 2. create a temporary directory that will hold the per‑process logs ---- */
        Path clientLogs = Files.createTempDirectory("test_sendRequest");

        List<Future<Result>> futures = new ArrayList<>();

        /* ---- 3. launch a client process for each seed ---------------------- */
        for (Integer seed : seeds) {
            String command =
                    String.format(
                            "ws2/out.%s/build/examples_rclcpp_minimal_client/client_main %s %d"
                                    + " --ros-args --log-level DEBUG",
                            System.getenv("ROS_DISTRO"), serviceName, seed);

            XProcess proc = new XExec(command).start();
            procs.add(proc);
            Path logFile = clientLogs.resolve(seed.toString());

            /* ---- 4. capture stderr into the temporary file ------------------- */
            proc.stderrAsync(
                    line -> {
                        try {
                            Files.writeString(
                                    logFile,
                                    line + System.lineSeparator(),
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.APPEND);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });

            /* ---- 5. when the process finishes, read the log and complete the future ---- */
            futures.add(
                    proc.code()
                            .thenApply(
                                    exitCode -> {
                                        try {
                                            var actualOutput =
                                                    Files.readAllLines(logFile).stream()
                                                            .filter(l -> l.startsWith("[INFO]"))
                                                            .filter(
                                                                    l ->
                                                                            !l.contains(
                                                                                    "waiting for"
                                                                                        + " service"
                                                                                        + " to appear"))
                                                            .map(l -> l.replaceAll("\\[.*\\]", ""))
                                                            .collect(Collectors.joining("\n"));
                                            if (exitCode != 0) {
                                                throw new RuntimeException(
                                                        "Process exited with code " + exitCode);
                                            }
                                            return new Result(seed, actualOutput);
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    }));
        }
        return futures; // each future implements {@link Future<String>}
    }

    @Override
    public void close() {
        procs.forEach(XProcess::destroyAllForcibly);
    }
}
