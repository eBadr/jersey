/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.jersey.server.spi.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.internal.ProcessingException;
import org.glassfish.jersey.internal.inject.Providers;
import org.glassfish.jersey.message.internal.MessageBodyProviderNotFoundException;
import org.glassfish.jersey.server.internal.process.MappableException;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.model.Parameterized;

import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.ServiceLocator;

/**
 * Utility methods for retrieving values or value providers for the
 * {@link Parameterized parameterized} resource model components.
 *
 * @author Marek Potociar (marek.potociar at oracle.com)
 */
public final class ParameterValueHelper {

    /**
     * Get the array of parameter values.
     *
     * @param valueProviders a list of value providers.
     * @return array of parameter values provided by the value providers.
     */
    public static Object[] getParameterValues(List<Factory<?>> valueProviders) {
        final Object[] params = new Object[valueProviders.size()];
        try {
            int index = 0;
            for (Factory<?> valueProvider : valueProviders) {
                params[index++] = valueProvider.provide();
            }
            return params;
        } catch (WebApplicationException e) {
            throw e;
        } catch (MessageBodyProviderNotFoundException e) {
            throw new WebApplicationException(Status.UNSUPPORTED_MEDIA_TYPE);
        } catch (ProcessingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MappableException("Exception obtaining parameters", e);
        }
    }

    /**
     * Create list of parameter value providers for the given {@link Parameterized
     * parameterized} resource model component.
     *
     * @param locator       HK2 service locator.
     * @param parameterized parameterized resource model component.
     * @return list of parameter value providers for the parameterized component.
     */
    public static List<Factory<?>> createValueProviders(final ServiceLocator locator, final Parameterized parameterized) {
        if ((null == parameterized.getParameters()) || (0 == parameterized.getParameters().size())) {
            return Collections.emptyList();
        }

        List<ValueFactoryProvider> valueFactoryProviders = new ArrayList<ValueFactoryProvider>(
                Providers.getProviders(locator, ValueFactoryProvider.class));

        Collections.sort(valueFactoryProviders, new Comparator<ValueFactoryProvider>() {

            @Override
            public int compare(ValueFactoryProvider o1, ValueFactoryProvider o2) {
                return o2.getPriority().getWeight() - o1.getPriority().getWeight();
            }

        });

        boolean entityParamFound = false;
        final List<Factory<?>> providers = new ArrayList<Factory<?>>(parameterized.getParameters().size());
        for (final Parameter parameter : parameterized.getParameters()) {
            entityParamFound = entityParamFound || Parameter.Source.ENTITY == parameter.getSource();
            providers.add(getValueFactory(valueFactoryProviders, parameter));
        }

        if (!entityParamFound && Collections.frequency(providers, null) == 1) {
            // Try to find entity if there is one unresolved parameter and the annotations are unknown
            final int entityParamIndex = providers.lastIndexOf(null);
            final Parameter parameter = parameterized.getParameters().get(entityParamIndex);
            if (Parameter.Source.UNKNOWN == parameter.getSource() && !parameter.isQualified()) {
                providers.set(entityParamIndex, getValueFactory(
                        valueFactoryProviders,
                        Parameter.overrideSource(parameter, Parameter.Source.ENTITY)));
            }
        }

        return providers;
    }

    private static Factory<?> getValueFactory(Collection<ValueFactoryProvider> valueFactoryProviders, final Parameter parameter) {
        Factory<?> valueFactory = null;
        final Iterator<ValueFactoryProvider> vfpIterator = valueFactoryProviders.iterator();
        while (valueFactory == null && vfpIterator.hasNext()) {
            valueFactory = vfpIterator.next().getValueFactory(parameter);
        }
        return valueFactory;
    }

    /**
     * Prevents instantiation.
     */
    private ParameterValueHelper() {
    }
}
