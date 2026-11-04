package com.github.kornilova203.matlab.execution.console;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.*;

/**
 * Runs MATLAB through MathWorks' Java Engine API (engine.jar in the MATLAB installation) and connects it to
 * standard input and output, so the IDE can talk to it like to a terminal. Used on Windows, where
 * {@code matlab -nodesktop} opens its own window instead of reading standard input.
 * <p>
 * Each input line is one command. A line with only {@link #INTERRUPT} stops the running command.
 * {@code exit} or {@code quit}, or the end of the input, closes MATLAB.
 * <p>
 * Breakpoints: the engine gives no K>> prompt. When MATLAB stops at one, the command that runs the code just
 * doesn't finish. So commands are handed to the engine as they come, without waiting for the one before, and after
 * each one the bridge asks MATLAB (a small probe) whether it's paused in its debugger; if it is, the bridge prints
 * {@link #DEBUG_PROMPT}, and the commands that follow (dbstep, dbcont, the IDE's queries) run where MATLAB stopped.
 * Like a terminal, every command gets one prompt when it's done: {@code >> }, or {@code K>> } while MATLAB is paused.
 * <p>
 * Started as its own Java process with engine.jar on the class path and MATLAB's bin/win64 folder on PATH;
 * it uses only the JDK (no IntelliJ or Kotlin classes), and reaches the engine through reflection.
 */
public final class MatlabEngineBridge {
    /** Sent on a line of its own to interrupt the running command (Ctrl+C). */
    public static final String INTERRUPT = "\u0003";
    public static final String PROMPT = ">> ";
    public static final String DEBUG_PROMPT = "K>> ";
    /** Prints 1 when MATLAB is paused in its debugger. */
    static final String PROBE = "disp(double(feature('IsDebugMode')))";
    static final long PROBE_AGAIN_MS = 300;
    static final String ENGINE_CLASS = "com.mathworks.engine.MatlabEngine";

    private final Object engine;
    private final BufferedReader input;
    private final PrintStream out;
    private final PrintStream err;
    /** Waits for the engine's answers, one thread per command. */
    private final ExecutorService waiters = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "MATLAB command");
        thread.setDaemon(true);
        return thread;
    });
    /** Commands handed to the engine and not done yet, oldest first; the oldest is what MATLAB runs (or is paused in). */
    private final Deque<Future<?>> active = new ArrayDeque<>();
    private final Object lock = new Object();

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
        print(PROMPT);
        String line;
        while ((line = input.readLine()) != null) {
            if (line.equals(INTERRUPT)) {
                interrupt();
                continue;
            }
            String command = line.trim();
            if (command.equals("exit") || command.equals("quit")) break;
            if (command.isEmpty()) {
                // Like Enter in a terminal: a new prompt if MATLAB waits, nothing while it's busy
                String prompt = currentPrompt();
                if (prompt != null) print(prompt);
                continue;
            }
            submit(line);
        }
        waiters.shutdown();
        try {
            // Let the last commands finish before closing MATLAB
            waiters.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException ignored) {
        }
        close();
    }

    private void interrupt() {
        List<Future<?>> running;
        synchronized (lock) {
            running = new ArrayList<>(active);
        }
        boolean any = false;
        for (Future<?> future : running) any |= future.cancel(true);
        if (any) err.println("Interrupted");
    }

    private void submit(String command) {
        Writer output = new StreamWriter(out);
        Writer error = new StreamWriter(err);
        Future<?> future;
        boolean first;
        // In order: the engine runs requests in the order it gets them
        synchronized (lock) {
            try {
                future = evalAsync(command, output, error);
            } catch (Exception e) {
                err.println(e.getCause() != null ? e.getCause() : e);
                String prompt = currentPrompt();
                print(prompt != null ? prompt : PROMPT);
                return;
            }
            first = active.isEmpty();
            active.addLast(future);
            lastPrompt = null;
        }
        // A command that runs code may stop at a breakpoint: is MATLAB paused once it gets to it?
        if (first) probe();
        waiters.submit(() -> finish(future));
    }

    private void finish(Future<?> future) {
        try {
            future.get();
        } catch (CancellationException e) {
            // Interrupted by the user; already reported
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            err.println(cause.getMessage() != null ? cause.getMessage() : cause.toString());
        } catch (InterruptedException e) {
            return;
        }
        boolean oldest;
        synchronized (lock) {
            oldest = active.peekFirst() == future;
            active.remove(future);
        }
        if (oldest) {
            // Finished as usual (or MATLAB left the debugger and the code ran to its end)
            print(PROMPT);
        } else {
            // Ran while an older command is still going: MATLAB was paused, and may still be (dbstep) or not (dbcont)
            probe();
        }
    }

    /**
     * Asks MATLAB whether it's paused in the debugger. The engine answers only between other requests, so this
     * waits behind the running code; if MATLAB is paused while code is still running, prints K>>.
     */
    private void probe() {
        StringWriter answer = new StringWriter();
        Future<?> future;
        try {
            future = evalAsync(PROBE, answer, new StringWriter());
        } catch (Exception e) {
            return;
        }
        waiters.submit(() -> {
            boolean answered;
            try {
                future.get();
                answered = true;
            } catch (ExecutionException e) {
                // feature('IsDebugMode') missing: that it answered at all while code runs means it's paused
                answered = false;
            } catch (CancellationException | InterruptedException e) {
                return;
            }
            boolean running;
            synchronized (lock) {
                running = !active.isEmpty();
            }
            if (!running) return;
            boolean paused = answered ? answer.toString().trim().equals("1") : true;
            if (paused) {
                print(DEBUG_PROMPT);
            } else {
                // Answered between other work (the code calls pause or drawnow): ask again, it may stop later
                try {
                    Thread.sleep(PROBE_AGAIN_MS);
                } catch (InterruptedException e) {
                    return;
                }
                probe();
            }
        });
    }

    /** The prompt MATLAB waits at: >> when nothing runs, K>> when paused, null while it's busy. */
    private String currentPrompt() {
        synchronized (lock) {
            if (active.isEmpty()) return PROMPT;
            return DEBUG_PROMPT.equals(lastPrompt) ? DEBUG_PROMPT : null;
        }
    }

    private Future<?> evalAsync(String command, Writer output, Writer error) throws Exception {
        Method evalAsync = engine.getClass().getMethod("evalAsync", String.class, Writer.class, Writer.class);
        try {
            return (Future<?>) evalAsync.invoke(engine, command, output, error);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
        }
    }

    /** The last prompt printed, cleared when a command is handed to the engine. */
    private volatile String lastPrompt;

    private void print(String text) {
        if (text.equals(PROMPT) || text.equals(DEBUG_PROMPT)) lastPrompt = text;
        synchronized (out) {
            out.print(text);
            out.flush();
        }
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
            synchronized (stream) {
                stream.print(new String(buffer, offset, length));
                stream.flush();
            }
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
