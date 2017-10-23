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

import org.apache.felix.service.command.Converter;
import org.osgi.service.component.annotations.Component;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * {@link Converter} implementation used when an {@link Exception}
 * is raised by a command.
 */
@Component(
        service = Converter.class,
        property = Converter.CONVERTER_CLASSES + "=java.lang.Exception")
public class ExceptionConverter implements Converter {
    @Override
    public Object convert(Class<?> desiredType, Object in) throws Exception {
        return null;
    }

    @Override
    public CharSequence format(Object target, int level, Converter escape) throws Exception {
        if (target instanceof Exception) {
            final Exception e = (Exception) target;
            if (level == INSPECT) {
                final ByteArrayOutputStream buf = new ByteArrayOutputStream(512);
                e.printStackTrace(new PrintStream(buf, true));
                return buf.toString();
            } else if (level == LINE) {
                return e.toString();
            } else if (level == PART) {
                return e.getMessage();
            }
        }
        return null;
    }
}
