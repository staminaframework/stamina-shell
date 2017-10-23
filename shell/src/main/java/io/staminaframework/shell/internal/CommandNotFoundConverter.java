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

import org.apache.felix.gogo.runtime.CommandNotFoundException;
import org.apache.felix.service.command.Converter;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;

/**
 * {@link Converter} implementation used when a command is not found.
 *
 * @author Stamina Framework developers
 */
@Component(
        service = Converter.class,
        property = {
                Converter.CONVERTER_CLASSES + "=org.apache.felix.gogo.runtime.CommandNotFoundException",
                Constants.SERVICE_RANKING + ":Integer=100"
        })
public class CommandNotFoundConverter implements Converter {
    @Override
    public Object convert(Class<?> desiredType, Object in) throws Exception {
        return null;
    }

    @Override
    public CharSequence format(Object target, int level, Converter escape) throws Exception {
        if (target instanceof CommandNotFoundException) {
            final CommandNotFoundException e = (CommandNotFoundException) target;
            return "Command not found: " + e.getCommand();
        }
        return null;
    }
}
