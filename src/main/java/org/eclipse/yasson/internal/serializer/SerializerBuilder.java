/*******************************************************************************
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/

package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.ComponentMatcher;
import org.eclipse.yasson.internal.JsonbContext;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.SerializerBinding;

import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.bind.config.BinaryDataStrategy;
import javax.json.bind.serializer.JsonbSerializer;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Builder for serializers.
 *
 * @author Roman Grigoriadi
 */
public class SerializerBuilder extends AbstractSerializerBuilder<SerializerBuilder> {

    private Class<?> objectClass;

    /**
     * Creates a new builder.
     *
     * @param jsonbContext JSON-B context.
     */
    public SerializerBuilder(JsonbContext jsonbContext) {
        super(jsonbContext);
    }

    /**
     * Adds object class.
     *
     * @param objectClass object class
     * @return Builder.
     */
    public SerializerBuilder withObjectClass(Class<?> objectClass) {
        this.objectClass = objectClass;
        return this;
    }

    /**
     * Builds a {@link JsonbSerializer}.
     *
     * @return JsonbSerializer.
     */
    public JsonbSerializer<?> build() {
        runtimeType = resolveRuntimeType();

        //First check if user deserializer is registered for such type
        final ComponentMatcher componentMatcher = jsonbContext.getComponentMatcher();
        Optional<SerializerBinding<?>> userSerializer = componentMatcher.getSerializerBinding(getRuntimeType(), getModel());
        if (userSerializer.isPresent()  && !jsonbContext.containsProcessedType(userSerializer.get().getBindingType())) {
            return new UserSerializerSerializer<>(model, userSerializer.get().getJsonbSerializer());
        }

        //Second user components is registered.
        final Optional<AdapterBinding> adapterInfoOptional = componentMatcher.getAdapterBinding(getRuntimeType(), getModel());
        if (adapterInfoOptional.isPresent() && !jsonbContext.containsProcessedType(adapterInfoOptional.get().getBindingType())) {
            return new AdaptedObjectSerializer<>(getModel(), adapterInfoOptional.get());
        }

        final Optional<AbstractValueTypeSerializer<?>> supportedTypeSerializer = getSupportedTypeSerializer(objectClass);
        if (supportedTypeSerializer.isPresent()) {
            return supportedTypeSerializer.get();
        }

        if (Collection.class.isAssignableFrom(objectClass)) {
            return new CollectionSerializer<>(this);
        } else if (Map.class.isAssignableFrom(objectClass)) {
            return new MapSerializer<>(this);
        } else if (isByteArray(objectClass)) {
            String strategy = jsonbContext.getConfigProperties().getBinaryDataStrategy();
            switch (strategy) {
                case BinaryDataStrategy.BYTE:
                    return new ByteArraySerializer(this);
                default:
                    return new ByteArrayBase64Serializer(byte[].class, getModel());
            }
        } else if (objectClass.isArray() || getRuntimeType() instanceof GenericArrayType) {
            return createArrayItem(objectClass.getComponentType());

        } else if (JsonValue.class.isAssignableFrom(objectClass)) {
            if(JsonObject.class.isAssignableFrom(objectClass)) {
                return new JsonObjectSerializer(this);
            } else {
                return new JsonArraySerializer(this);
            }
        } else if (Optional.class.isAssignableFrom(objectClass)) {
            return new OptionalObjectSerializer<>(this);
        } else {
            jsonbContext.getMappingContext().addSerializerProvider(objectClass, new ObjectSerializerProvider());
            return new ObjectSerializer<>(this);
        }

    }

    private boolean isByteArray(Class<?> rawType) {
        return rawType.isArray() && rawType.getComponentType() == Byte.TYPE;
    }

    /**
     * Instance is not created in case of array items, because, we don't know how long it should be
     * till parser ends parsing.
     */
    private JsonbSerializer<?> createArrayItem(Class<?> componentType) {
        if (componentType == byte.class) {
            return new ByteArraySerializer(this);
        } else if (componentType == short.class) {
            return new ShortArraySerializer(this);
        } else if (componentType == int.class) {
            return new IntArraySerializer(this);
        } else if (componentType == long.class) {
            return new LongArraySerializer(this);
        } else if (componentType == float.class) {
            return new FloatArraySerializer(this);
        } else if (componentType == double.class) {
            return new DoubleArraySerializer(this);
        } else {
            return new ObjectArraySerializer(this);
        }
    }

    private Optional<AbstractValueTypeSerializer<?>> getSupportedTypeSerializer(Class<?> rawType) {
        final Optional<? extends SerializerProviderWrapper> supportedTypeSerializerOptional = DefaultSerializers.getInstance().findValueSerializerProvider(rawType);
        if (supportedTypeSerializerOptional.isPresent()) {
            return Optional.of(supportedTypeSerializerOptional.get().getSerializerProvider().provideSerializer(getModel()));
        }
        return Optional.empty();
    }

    private Type resolveRuntimeType() {
        if (genericType != null && genericType != Object.class) {
            return genericType;
        }
        if (getModel() != null && ! (getModel().getType() instanceof TypeVariable)) {
            return getModel().getType();
        }
        return objectClass;
    }
}
