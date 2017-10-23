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

import org.apache.felix.gogo.runtime.CommandProxy;
import org.apache.felix.gogo.runtime.CommandSessionImpl;
import org.apache.felix.gogo.runtime.Reflective;
import org.apache.felix.service.command.*;
import org.osgi.service.component.annotations.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Shell related commands.
 *
 * @author Apache Software Foundation
 */
@Component(service = ShellCommands.class,
        property = {
                CommandProcessor.COMMAND_SCOPE + "=shell",
                CommandProcessor.COMMAND_FUNCTION + "=help"
        })
public class ShellCommands {
    // Methods from this class are extracted from class Shell in bundle org.apache.felix.jline.

    private static Set<String> getCommands(CommandSession session) {
        return (Set<String>) session.get(CommandSessionImpl.COMMANDS);
    }

    private Map<String, List<Method>> getReflectionCommands(CommandSession session) {
        Map<String, List<Method>> commands = new TreeMap<>();
        Set<String> names = getCommands(session);
        for (String name : names) {
            Function function = (Function) session.get(name);
            if (function instanceof CommandProxy) {
                Object target = ((CommandProxy) function).getTarget();
                List<Method> methods = new ArrayList<>();
                String func = name.substring(name.indexOf(':') + 1).toLowerCase();
                List<String> funcs = new ArrayList<>();
                funcs.add("is" + func);
                funcs.add("get" + func);
                funcs.add("set" + func);
                if (Reflective.KEYWORDS.contains(func)) {
                    funcs.add("_" + func);
                } else {
                    funcs.add(func);
                }
                for (Method method : target.getClass().getMethods()) {
                    if (funcs.contains(method.getName().toLowerCase())) {
                        methods.add(method);
                    }
                }
                commands.put(name, methods);
                ((CommandProxy) function).ungetTarget();
            }
        }
        return commands;
    }

    private static <T extends Annotation> T findAnnotation(Annotation[] anns,
                                                           Class<T> clazz) {
        for (int i = 0; (anns != null) && (i < anns.length); i++) {
            if (clazz.isInstance(anns[i])) {
                return clazz.cast(anns[i]);
            }
        }
        return null;
    }

    @Descriptor("Display available commands")
    public void help(CommandSession session) {
        Map<String, List<Method>> commands = getReflectionCommands(session);
        commands.keySet().forEach(System.out::println);
    }

    @Descriptor("Displays information about a specific command")
    public void help(CommandSession session, @Descriptor("target command") String name) {
        Map<String, List<Method>> commands = getReflectionCommands(session);

        List<Method> methods = null;

        // If the specified command doesn't have a scope, then
        // search for matching methods by ignoring the scope.
        int scopeIdx = name.indexOf(':');
        if (scopeIdx < 0) {
            for (Map.Entry<String, List<Method>> entry : commands.entrySet()) {
                String k = entry.getKey().substring(entry.getKey().indexOf(':') + 1);
                if (name.equals(k)) {
                    name = entry.getKey();
                    methods = entry.getValue();
                    break;
                }
            }
        }
        // Otherwise directly look up matching methods.
        else {
            methods = commands.get(name);
        }

        if ((methods != null) && (methods.size() > 0)) {
            for (Method m : methods) {
                Descriptor d = m.getAnnotation(Descriptor.class);
                if (d == null) {
                    System.out.println("\n" + m.getName());
                } else {
                    System.out.println("\n" + m.getName() + " - " + d.value());
                }

                System.out.println("   scope: " + name.substring(0, name.indexOf(':')));

                // Get flags and options.
                Class<?>[] paramTypes = m.getParameterTypes();
                Map<String, Parameter> flags = new TreeMap<>();
                Map<String, String> flagDescs = new TreeMap<>();
                Map<String, Parameter> options = new TreeMap<>();
                Map<String, String> optionDescs = new TreeMap<>();
                List<String> params = new ArrayList<>();
                Annotation[][] anns = m.getParameterAnnotations();
                for (int paramIdx = 0; paramIdx < anns.length; paramIdx++) {
                    Class<?> paramType = m.getParameterTypes()[paramIdx];
                    if (paramType == CommandSession.class) {
                        /* Do not bother the user with a CommandSession. */
                        continue;
                    }
                    Parameter p = findAnnotation(anns[paramIdx], Parameter.class);
                    d = findAnnotation(anns[paramIdx], Descriptor.class);
                    if (p != null) {
                        if (p.presentValue().equals(Parameter.UNSPECIFIED)) {
                            options.put(p.names()[0], p);
                            if (d != null) {
                                optionDescs.put(p.names()[0], d.value());
                            }
                        } else {
                            flags.put(p.names()[0], p);
                            if (d != null) {
                                flagDescs.put(p.names()[0], d.value());
                            }
                        }
                    } else if (d != null) {
                        params.add(paramTypes[paramIdx].getSimpleName());
                        params.add(d.value());
                    } else {
                        params.add(paramTypes[paramIdx].getSimpleName());
                        params.add("");
                    }
                }

                // Print flags and options.
                if (flags.size() > 0) {
                    System.out.println("   flags:");
                    for (Map.Entry<String, Parameter> entry : flags.entrySet()) {
                        // Print all aliases.
                        String[] names = entry.getValue().names();
                        System.out.print("      " + names[0]);
                        for (int aliasIdx = 1; aliasIdx < names.length; aliasIdx++) {
                            System.out.print(", " + names[aliasIdx]);
                        }
                        System.out.println("   " + flagDescs.get(entry.getKey()));
                    }
                }
                if (options.size() > 0) {
                    System.out.println("   options:");
                    for (Map.Entry<String, Parameter> entry : options.entrySet()) {
                        // Print all aliases.
                        String[] names = entry.getValue().names();
                        System.out.print("      " + names[0]);
                        for (int aliasIdx = 1; aliasIdx < names.length; aliasIdx++) {
                            System.out.print(", " + names[aliasIdx]);
                        }
                        System.out.println("   "
                                + optionDescs.get(entry.getKey())
                                + ((entry.getValue().absentValue() == null) ? ""
                                : " [optional]"));
                    }
                }
                if (params.size() > 0) {
                    System.out.println("   parameters:");
                    for (Iterator<String> it = params.iterator(); it.hasNext(); ) {
                        System.out.println("      " + it.next() + "   " + it.next());
                    }
                }
            }
        }
    }
}
