/*
 * Copyright (c) 2017 Stamina Framework developers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.staminaframework.shell.internal;

import io.staminaframework.boot.CommandLine;
import io.staminaframework.command.Command;
import io.staminaframework.command.CommandConstants;
import org.apache.felix.service.command.*;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.*;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.log.LogService;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shell component.
 *
 * @author Stamina Framework developers
 */
@Component(immediate = true, configurationPid = "io.staminaframework.shell")
public class Shell {
    @interface Config {
        /**
         * Get command prompt.
         */
        String prompt() default "$ ";

        /**
         * Get Message Of The Day: banner displayed on shell startup.
         */
        String motd() default "";
    }

    @Reference
    private CommandProcessor processor;
    @Reference
    private LogService logService;
    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private CommandLine commandLine;
    private CommandSession session;
    private Terminal terminal;
    private Thread shellThread;
    private final AtomicBoolean running = new AtomicBoolean();

    @Activate
    void activate(BundleContext bundleContext, Config config) throws IOException {
        // Most of this code is taken from the Apache Felix Gogo JLine bundle.

        terminal = TerminalBuilder.builder()
                .name("gogo")
                .system(true)
                .nativeSignals(true)
                .signalHandler(Terminal.SignalHandler.SIG_IGN)
                .build();
        session = processor.createSession(
                terminal.input(), terminal.output(), terminal.output());
        session.put(".terminal", terminal);
        session.put(".processor", processor);
        session.put("SCOPE", "bundle:*");
        session.put("#TERM", (Function) (s, arguments) -> terminal.getType());
        session.put("#COLUMNS", (Function) (s, arguments) -> terminal.getWidth());
        session.put("#LINES", (Function) (s, arguments) -> terminal.getHeight());
        session.put("#PWD", (Function) (s, arguments) -> s.currentDir().toString());

        final LineReader lineReader = LineReaderBuilder.builder()
                .appName("stamina")
                .terminal(terminal)
                .parser(new DefaultParser())
                .build();
        lineReader.setOpt(LineReader.Option.AUTO_FRESH_LINE);
        session.put(".reader", lineReader);
        running.set(true);

        final Terminal.SignalHandler intHandler = terminal.handle(Terminal.Signal.INT, s -> {
            Job current = session.foregroundJob();
            if (current != null) {
                current.interrupt();
            }
        });
        final Terminal.SignalHandler suspHandler = terminal.handle(Terminal.Signal.TSTP, s -> {
            Job current = session.foregroundJob();
            if (current != null) {
                current.suspend();
            }
        });

        final boolean interactive = commandLine == null || !commandLine.command().equals("shell:exec");

        logService.log(LogService.LOG_INFO, "Starting command-line shell");
        final Runnable shellHandler = () -> {
            Thread.currentThread().setUncaughtExceptionHandler((t, e) -> {
                // Do nothing.
            });

            try {
                while (running.get()) {
                    ParsedLine parsedLine;
                    if (!interactive && commandLine.command().equals("shell:exec")) {
                        final String cmdLine = String.join(" ", commandLine.arguments());
                        logService.log(LogService.LOG_INFO, "Executing Shell command-line: $ " + cmdLine);

                        // Register an empty command, since we are doing the hard work.
                        final Dictionary<String, Object> cmdProps = new Hashtable<>(1);
                        cmdProps.put(CommandConstants.COMMAND, "shell:exec");
                        bundleContext.registerService(Command.class, new Command() {
                            @Override
                            public void help(PrintStream printStream) {
                            }

                            @Override
                            public boolean execute(Context context) throws Exception {
                                return false;
                            }
                        }, cmdProps);

                        // Shell is stopped once command has been executed.
                        running.set(false);

                        // Pretend an user just typed a command.
                        parsedLine = new ParsedLine() {
                            @Override
                            public String word() {
                                return "";
                            }

                            @Override
                            public int wordCursor() {
                                return 0;
                            }

                            @Override
                            public int wordIndex() {
                                return 0;
                            }

                            @Override
                            public List<String> words() {
                                return Collections.emptyList();
                            }

                            @Override
                            public String line() {
                                return cmdLine;
                            }

                            @Override
                            public int cursor() {
                                return 0;
                            }
                        };

                        // Workaround for a weird bug in command processor:
                        // if a command is executed too soon, an exception is raised
                        // and a stacktrace is printed on console...
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignore) {
                        }
                    } else {
                        lineReader.readLine(config.prompt());
                        parsedLine = lineReader.getParsedLine();
                    }
                    if (parsedLine == null) {
                        throw new EndOfFileException();
                    } else {
                        logService.log(LogService.LOG_DEBUG,
                                "Executing command: " + parsedLine.line());
                        try {
                            final Object result = session.execute(parsedLine.line());

                            // Store last command result in variable $_.
                            session.put("_", result);

                            if (result != null) {
                                System.out.println(session.format(result, Converter.INSPECT));
                            }
                        } catch (Exception e) {
                            final int type;
                            if (e instanceof IllegalArgumentException) {
                                type = Converter.PART;
                            } else {
                                type = Converter.INSPECT;
                            }
                            terminal.writer().println(session.format(e, type));
                            terminal.flush();
                            session.put("exception", e);
                        }

                        while (true) {
                            Job job = session.foregroundJob();
                            if (job != null) {
                                synchronized (job) {
                                    if (job.status() == Job.Status.Foreground) {
                                        job.wait();
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }
            } catch (UserInterruptException e) {
                logService.log(LogService.LOG_DEBUG, "Shell interrupted by user");
                running.set(false);
            } catch (InterruptedException e) {
                logService.log(LogService.LOG_DEBUG, "Shell interrupted");
                running.set(false);
            } catch (EndOfFileException e) {
                logService.log(LogService.LOG_DEBUG, "Shell closed");
                running.set(false);
            } finally {
                terminal.handle(Terminal.Signal.INT, intHandler);
                terminal.handle(Terminal.Signal.TSTP, suspHandler);
            }

            session.close();
            try {
                terminal.close();
            } catch (IOException ignore) {
            }

            try {
                final Bundle sys = bundleContext.getBundle(Constants.SYSTEM_BUNDLE_LOCATION);
                try {
                    sys.stop();
                } catch (BundleException ignore) {
                }
            } catch (IllegalStateException ignore) {
                // The platform is already shutting down.
            }
        };
        shellThread = new Thread(shellHandler, "Stamina Shell");
        shellThread.setDaemon(false);
        shellThread.setPriority(Thread.MIN_PRIORITY);
        shellThread.start();
    }

    @Deactivate
    void deactivate() {
        running.set(false);
        if (shellThread != null) {
            shellThread.interrupt();
            try {
                shellThread.join(4000);
            } catch (InterruptedException ignore) {
            }
            shellThread = null;
        }
    }
}
