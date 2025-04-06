/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.properties.bind;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;

/**
 * Context used when binding and the {@link BindContext} implementation.
 */

public class Context implements BindContext {

	private int depth;

	private final List<ConfigurationPropertySource> source = Arrays.asList((ConfigurationPropertySource) null);

	private int sourcePushCount;

	private final Deque<Class<?>> dataObjectBindings = new ArrayDeque<>();

	private final Deque<Class<?>> constructorBindings = new ArrayDeque<>();

	private ConfigurationProperty configurationProperty;

	private void increaseDepth() {
		this.depth++;
	}

	private void decreaseDepth() {
		this.depth--;
	}

	private Binder binder = new Binder();

	public <T> T withSource(ConfigurationPropertySource source, Supplier<T> supplier) {
		if (source == null) {
			return supplier.get();
		}
		this.source.set(0, source);
		this.sourcePushCount++;
		try {
			return supplier.get();
		}
		finally {
			this.sourcePushCount--;
		}
	}

	public <T> T withDataObject(Class<?> type, Supplier<T> supplier) {
		this.dataObjectBindings.push(type);
		try {
			return withIncreasedDepth(supplier);
		}
		finally {
			this.dataObjectBindings.pop();
		}
	}

	public boolean isBindingDataObject(Class<?> type) {
		return this.dataObjectBindings.contains(type);
	}

	public <T> T withIncreasedDepth(Supplier<T> supplier) {
		increaseDepth();
		try {
			return supplier.get();
		}
		finally {
			decreaseDepth();
		}
	}

	void setConfigurationProperty(ConfigurationProperty configurationProperty) {
		this.configurationProperty = configurationProperty;
	}

	void clearConfigurationProperty() {
		this.configurationProperty = null;
	}

	void pushConstructorBoundTypes(Class<?> value) {
		this.constructorBindings.push(value);
	}

	boolean isNestedConstructorBinding() {
		return !this.constructorBindings.isEmpty();
	}

	void popConstructorBoundTypes() {
		this.constructorBindings.pop();
	}

	PlaceholdersResolver getPlaceholdersResolver() {
		return this.binder.getPlaceholdersResolver();
	}

	BindConverter getConverter() {
		return this.binder.getBindConverter();
	}

	@Override
	public Binder getBinder() {
		return this.binder;
	}

	@Override
	public int getDepth() {
		return this.depth;
	}

	@Override
	public Iterable<ConfigurationPropertySource> getSources() {
		if (this.sourcePushCount > 0) {
			return this.source;
		}
		return this.binder.getSources();
	}

	@Override
	public ConfigurationProperty getConfigurationProperty() {
		return this.configurationProperty;
	}

}
