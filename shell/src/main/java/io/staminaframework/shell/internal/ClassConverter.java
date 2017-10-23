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

/**
 * {@link Converter} implementation related to {@link Class} instances.
 *
 * @author Stamina Framework developers
 */
@Component(service = Converter.class,
        property = Converter.CONVERTER_CLASSES + "=java.lang.Class")
public class ClassConverter implements Converter {
    @Override
    public Object convert(Class<?> desiredType, Object in) throws Exception {
        if (desiredType == Class.class) {
            try {
                Class.forName(in.toString());
            } catch (ClassNotFoundException ignore) {
            }
        }
        return null;
    }

    @Override
    public CharSequence format(Object in, int type, Converter converter) throws Exception {
        if (in instanceof Class) {
            final Class<?> clazz = (Class) in;
            if (type == INSPECT || type == LINE) {
                return clazz.getName();
            } else if (type == PART) {
                return clazz.getSimpleName();
            }
        }
        return null;
    }
}
