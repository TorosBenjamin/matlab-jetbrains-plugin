package com.github.kornilova203.matlab.execution.console;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * Runs MATLAB through MathWorks' Java Engine API (engine.jar in the MATLAB installation) and connects it to
 * standard input and output, so the IDE can talk to it like to a terminal. Used on Windows, where
 * {@code matlab -nodesktop} opens its own window instead of reading standard input.
 * <p>
 * Each input line is one command. A line with only {@link #INTERRUPT} stops the running command.
 * {@code exit} or {@code quit}, or the end of the input, closes MATLAB.
 * <p>
 * Started as its own Java process with engine.jar on the class path and MATLAB's bin/win64 folder on PATH;
 * it uses only the JDK (no IntelliJ or Kotlin classes), and reaches the engine through reflection.
 */
public final class MatlabEngineBridge {
    /** Sent on a line of its own to interrupt the running command (Ctrl+C). */
    public static final String INTERRUPT = "\u0003";
    public static final String PROMPT = ">> ";
    static final String ENGINE_CLASS = "com.mathworks.engine.MatlabEngine";

    private final Object engine;
    private final BufferedReader input;
    private final PrintStream out;
    private final PrintStream err;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MATLAB command");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Future<?> running;

    public MatlabEngineBridge(Object engine, Reader input, PrintStream out, PrintStream err) {
        this.engine = engine;
        this.input = new BufferedReader(input);
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, "UTF-8");
        out.println("Starting MATLAB...");
        Object engine;
        try {
            Class<?> engineClass = Class.forName(ENGINE_CLASS);
            engine = engineClass.getMethod("startMatlab").invoke(null);
        } catch (InvocationTargetException e) {
            err.println("Couldn't start MATLAB: " + e.getCause());
            System.exit(1);
            return;
        } catch (ClassNotFoundException e) {
            err.println("MATLAB's engine.jar wasn't found: " + e.getMessage());
            System.exit(1);
            return;
        }
        new MatlabEngineBridge(engine, new InputStreamReader(System.in, StandardCharsets.UTF_8), out, err).run();
        System.exit(0);
    }

    /** Reads and runs commands until exit or the end of the input, then closes MATLAB. */
    public void run() throws IOException {
        out.print(PROMPT);
        out.flush();
        // Commands run on the worker thread, so this thread can still read an interrupt while one runs.
        String line;
        while ((line = input.readLine()) != null) {
            if (line.equals(INTERRUPT)) {
                Future<?> current = running;
                if (current != null && current.cancel(true)) err.println("Interrupted");
                continue;
            }
            String command = line.trim();
            if (command.equals("exit") || command.equals("quit")) break;
            final String toRun = line;
            try {
                worker.submit(() -> execute(toRun));
            } catch (RejectedExecutionException e) {
                break;
            }
        }
        worker.shutdown();
        try {
            // Let the last command finish before closing MATLAB
            worker.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException ignored) {
        }
        close();
    }

    private void execute(String command) {
        if (!command.trim().isEmpty()) {
            Writer output = new StreamWriter(out);
            Writer error = new StreamWriter(err);
            try {
                Method evalAsync = engine.getClass().getMethod("evalAsync", String.class, Writer.class, Writer.class);
                Future<?> future = (Future<?>) evalAsync.invoke(engine, command, output, error);
                running = future;
                future.get();
            } catch (CancellationException e) {
                // Interrupted by the user; already reported
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                err.println(cause.getMessage() != null ? cause.getMessage() : cause.toString());
            } catch (InvocationTargetException e) {
                err.println(String.valueOf(e.getCause()));
            } catch (Exception e) {
                err.println(e);
            } finally {
                running = null;
            }
        }
        out.print(PROMPT);
        out.flush();
    }

    private void close() {
        try {
            engine.getClass().getMethod("close").invoke(engine);
        } catch (Exception e) {
            err.println("Couldn't close MATLAB: " + e);
        }
    }

    /** Writes MATLAB's output to the stream as it comes. */
    private static final class StreamWriter extends Writer {
        private final PrintStream stream;

        StreamWriter(PrintStream stream) {
            this.stream = stream;
        }

        @Override
        public void write(char[] buffer, int offset, int length) {
            stream.print(new String(buffer, offset, length));
            stream.flush();
        }

        @Override
        public void flush() {
            stream.flush();
        }

        @Override
        public void close() {
            stream.flush();
        }
    }
}
