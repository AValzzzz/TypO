package com.example.model.code.run;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class CodeRunner {
    public static final int TIMEOUT_SECONDS = 10;
    public static final int MAX_OUTPUT_CHARS = 20_000;

    public enum Status {
        FINISHED, FAILED_TO_START, TIMEOUT, STOPPED
    }

    public record Result(Status status, int exitCode, boolean truncated) {
    }

    private record Interpreter(String name, String fileName, List<List<String>> commands,
            List<List<String>> compilers) {
        Interpreter(String name, String fileName, List<List<String>> commands) {
            this(name, fileName, commands, List.of());
        }
    }

    public static final class Execution {
        private volatile Process process;
        private volatile boolean stopped;

        public void stop() {
            stopped = true;
            Process p = process;
            if (p != null)
                kill(p);
        }
    }

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
            .contains("win");
    private static final Map<String, Interpreter> BY_TAG = new HashMap<>();

    static {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "java.exe" : "java").toString();

        Interpreter python = new Interpreter("Python", "script.py", WINDOWS
                ? List.of(List.of("python", "-u"), List.of("py", "-u"), List.of("python3", "-u"))
                : List.of(List.of("python3", "-u"), List.of("python", "-u")));
        Interpreter node = new Interpreter("JavaScript", "script.js", List.of(List.of("node")));
        Interpreter bash = new Interpreter("Bash", "script.sh", List.of(List.of("bash")));
        Interpreter java = new Interpreter("Java", "Main.java", List.of(
                List.of(javaBin, "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"),
                List.of("java", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")));
        Interpreter ocaml = new Interpreter("OCaml", "script.ml", List.of(List.of("ocaml")));
        Interpreter c = new Interpreter("C", "main.c", List.of(),
                List.of(List.of("gcc"), List.of("cc"), List.of("clang")));

        register(python, "python", "py");
        register(node, "javascript", "js", "mjs");
        register(bash, "bash", "sh", "shell", "zsh");
        register(java, "java");
        register(ocaml, "ocaml", "ml");
        register(c, "c");
    }

    private CodeRunner() {
    }

    private static void register(Interpreter interpreter, String... tags) {
        for (String t : tags)
            BY_TAG.put(t, interpreter);
    }

    private static Interpreter interpreterFor(String tag) {
        return tag == null ? null : BY_TAG.get(tag.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isRunnable(String tag) {
        return interpreterFor(tag) != null;
    }

    public static String displayName(String tag) {
        Interpreter i = interpreterFor(tag);
        return i == null ? null : i.name();
    }

    public static Execution run(String tag, String code, Consumer<String> onOutput, Consumer<Result> onDone) {
        Execution execution = new Execution();
        Interpreter interpreter = interpreterFor(tag);
        Thread thread = new Thread(() -> execute(interpreter, code, execution, onOutput, onDone), "code-runner");
        thread.setDaemon(true);
        thread.start();
        return execution;
    }

    private static void execute(Interpreter it, String code, Execution exec, Consumer<String> onOutput,
            Consumer<Result> onDone) {
        Path dir = null;
        Result result;
        try {
            if (it == null) {
                onOutput.accept("Langage non pris en charge.");
                onDone.accept(new Result(Status.FAILED_TO_START, -1, false));
                return;
            }

            dir = Files.createTempDirectory("typo-run-");
            Path file = dir.resolve(it.fileName());
            Files.writeString(file, code, StandardCharsets.UTF_8);

            List<List<String>> candidates = it.commands();
            boolean appendFile = true;
            if (!it.compilers().isEmpty()) {
                Path exe = dir.resolve(WINDOWS ? "program.exe" : "program");
                Result compiled = compile(it, file, exe, dir, exec, onOutput);
                if (compiled != null) {
                    onDone.accept(compiled);
                    return;
                }
                candidates = List.of(List.of(exe.toString()));
                appendFile = false;
            }

            Process started = null;
            for (List<String> prefix : candidates) {
                List<String> command = new ArrayList<>(prefix);
                if (appendFile)
                    command.add(file.toString());
                ProcessBuilder pb = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
                pb.environment().put("PYTHONIOENCODING", "utf-8");
                try {
                    started = pb.start();
                    break;
                } catch (IOException ignored) {
                }
            }

            if (started == null) {
                onOutput.accept("« " + it.name() + " » est introuvable. Installez-le et vérifiez qu'il est "
                        + "dans le PATH.");
                onDone.accept(new Result(Status.FAILED_TO_START, -1, false));
                return;
            }

            final Process process = started;
            exec.process = process;
            if (exec.stopped)
                kill(process);
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
            }

            AtomicBoolean truncated = new AtomicBoolean();
            Thread reader = new Thread(() -> pump(process.getInputStream(), onOutput, truncated), "code-runner-out");
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            boolean timedOut = !finished && !exec.stopped;
            if (!finished)
                kill(process);
            process.waitFor(2, TimeUnit.SECONDS);
            reader.join(2000);

            Status status = exec.stopped ? Status.STOPPED : timedOut ? Status.TIMEOUT : Status.FINISHED;
            int exit = process.isAlive() ? -1 : process.exitValue();
            result = new Result(status, exit, truncated.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = new Result(Status.STOPPED, -1, false);
        } catch (IOException e) {
            onOutput.accept("Impossible de préparer l'exécution : " + e.getMessage());
            result = new Result(Status.FAILED_TO_START, -1, false);
        } finally {
            deleteQuietly(dir);
        }
        onDone.accept(result);
    }

    private static Result compile(Interpreter it, Path file, Path exe, Path dir, Execution exec,
            Consumer<String> onOutput) throws InterruptedException {
        Process started = null;
        for (List<String> prefix : it.compilers()) {
            List<String> command = new ArrayList<>(prefix);
            command.add(file.toString());
            command.add("-o");
            command.add(exe.toString());
            command.add("-lm");
            try {
                started = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
                break;
            } catch (IOException ignored) {
            }
        }

        if (started == null) {
            onOutput.accept("Aucun compilateur C trouvé (gcc, cc ou clang). Installez-en un et vérifiez "
                    + "qu'il est dans le PATH.");
            return new Result(Status.FAILED_TO_START, -1, false);
        }

        final Process process = started;
        exec.process = process;
        if (exec.stopped)
            kill(process);
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
        }

        AtomicBoolean truncated = new AtomicBoolean();
        Thread reader = new Thread(() -> pump(process.getInputStream(), onOutput, truncated), "code-runner-out");
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished)
            kill(process);
        process.waitFor(2, TimeUnit.SECONDS);
        reader.join(2000);

        if (exec.stopped)
            return new Result(Status.STOPPED, -1, false);
        if (!finished)
            return new Result(Status.TIMEOUT, -1, truncated.get());
        int exit = process.exitValue();
        if (exit != 0)
            return new Result(Status.FINISHED, exit, truncated.get());
        return null;
    }

    private static void pump(InputStream in, Consumer<String> onOutput, AtomicBoolean truncated) {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buffer = new char[2048];
            int total = 0;
            int n;
            while ((n = reader.read(buffer)) >= 0) {
                if (total < MAX_OUTPUT_CHARS) {
                    int take = Math.min(n, MAX_OUTPUT_CHARS - total);
                    onOutput.accept(new String(buffer, 0, take));
                    total += take;
                    if (take < n)
                        truncated.set(true);
                } else if (n > 0) {
                    truncated.set(true);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void kill(Process p) {
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null)
            return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
