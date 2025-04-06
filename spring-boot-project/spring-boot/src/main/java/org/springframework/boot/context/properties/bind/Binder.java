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
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable.BindRestriction;
import org.springframework.boot.context.properties.bind.Context;
import org.springframework.boot.context.properties.bind.BindConverter;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertyState;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.env.Environment;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.util.Assert;

/**
 * A container object which Binds objects from one or more
 * {@link ConfigurationPropertySource ConfigurationPropertySources}.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @since 2.0.0
 */
public class Binder {

	private final Iterable<ConfigurationPropertySource> sources;

	private final PlaceholdersResolver placeholdersResolver;

	private final BindConverter bindConverter;

	private final BindHandler defaultBindHandler;

	private final Map<BindMethod, List<DataObjectBinder>> dataObjectBinders;

	private final BinderUtils binderUtils = new BinderUtils();

	private final HandleBind handleBind = new HandleBind();
	/**
	 * Create a new {@link Binder} instance for the specified sources. A
	 * {@link DefaultFormattingConversionService} will be used for all conversion.
	 * @param sources the sources used for binding
	 */
	public Binder(ConfigurationPropertySource... sources) {
		this((sources != null) ? Arrays.asList(sources) : null, null, null, null);
	}

	/**
	 * Create a new {@link Binder} instance for the specified sources. A
	 * {@link DefaultFormattingConversionService} will be used for all conversion.
	 * @param sources the sources used for binding
	 */
	public Binder(Iterable<ConfigurationPropertySource> sources) {
		this(sources, null, null, null);
	}

	/**
	 * Create a new {@link Binder} instance for the specified sources.
	 * @param sources the sources used for binding
	 * @param placeholdersResolver strategy to resolve any property placeholders
	 */
	public Binder(Iterable<ConfigurationPropertySource> sources, PlaceholdersResolver placeholdersResolver) {
		this(sources, placeholdersResolver, null, null);
	}

	/**
	 * Create a new {@link Binder} instance for the specified sources.
	 * @param sources the sources used for binding
	 * @param placeholdersResolver strategy to resolve any property placeholders
	 * @param conversionService the conversion service to convert values (or {@code null}
	 * to use {@link ApplicationConversionService})
	 */
	public Binder(Iterable<ConfigurationPropertySource> sources, PlaceholdersResolver placeholdersResolver,
			ConversionService conversionService) {
		this(sources, placeholdersResolver, conversionService, null);
	}

	/**
	 * Create a new {@link Binder} instance for the specified sources.
	 * @param sources the sources used for binding
	 * @param placeholdersResolver strategy to resolve any property placeholders
	 * @param conversionService the conversion service to convert values (or {@code null}
	 * to use {@link ApplicationConversionService})
	 * @param propertyEditorInitializer initializer used to configure the property editors
	 * that can convert values (or {@code null} if no initialization is required). Often
	 * used to call {@link ConfigurableListableBeanFactory#copyRegisteredEditorsTo}.
	 */
	public Binder(Iterable<ConfigurationPropertySource> sources, PlaceholdersResolver placeholdersResolver,
			ConversionService conversionService, Consumer<PropertyEditorRegistry> propertyEditorInitializer) {
		this(sources, placeholdersResolver, conversionService, propertyEditorInitializer, null);
	}

	/**
	 * Create a new {@link Binder} instance for the specified sources.
	 * @param sources the sources used for binding
	 * @param placeholdersResolver strategy to resolve any property placeholders
	 * @param conversionService the conversion service to convert values (or {@code null}
	 * to use {@link ApplicationConversionService})
	 * @param propertyEditorInitializer initializer used to configure the property editors
	 * that can convert values (or {@code null} if no initialization is required). Often
	 * used to call {@link ConfigurableListableBeanFactory#copyRegisteredEditorsTo}.
	 * @param defaultBindHandler the default bind handler to use if none is specified when
	 * binding
	 * @since 2.2.0
	 */
	public Binder(Iterable<ConfigurationPropertySource> sources, PlaceholdersResolver placeholdersResolver,
			ConversionService conversionService, Consumer<PropertyEditorRegistry> propertyEditorInitializer,
			BindHandler defaultBindHandler) {
		this(sources, placeholdersResolver, conversionService, propertyEditorInitializer, defaultBindHandler, null);
	}

	/**
	 * Create a new {@link Binder} instance for the specified sources.
	 * @param sources the sources used for binding
	 * @param placeholdersResolver strategy to resolve any property placeholders
	 * @param conversionService the conversion service to convert values (or {@code null}
	 * to use {@link ApplicationConversionService})
	 * @param propertyEditorInitializer initializer used to configure the property editors
	 * that can convert values (or {@code null} if no initialization is required). Often
	 * used to call {@link ConfigurableListableBeanFactory#copyRegisteredEditorsTo}.
	 * @param defaultBindHandler the default bind handler to use if none is specified when
	 * binding
	 * @param constructorProvider the constructor provider which provides the bind
	 * constructor to use when binding
	 * @since 2.2.1
	 */
	public Binder(Iterable<ConfigurationPropertySource> sources, PlaceholdersResolver placeholdersResolver,
			ConversionService conversionService, Consumer<PropertyEditorRegistry> propertyEditorInitializer,
			BindHandler defaultBindHandler, BindConstructorProvider constructorProvider) {
		this(sources, placeholdersResolver,
				(conversionService != null) ? Collections.singletonList(conversionService)
						: (List<ConversionService>) null,
				propertyEditorInitializer, defaultBindHandler, constructorProvider);
	}

	/**
	 * Create a new {@link Binder} instance for the specified sources.
	 * @param sources the sources used for binding
	 * @param placeholdersResolver strategy to resolve any property placeholders
	 * @param conversionServices the conversion services to convert values (or
	 * {@code null} to use {@link ApplicationConversionService})
	 * @param propertyEditorInitializer initializer used to configure the property editors
	 * that can convert values (or {@code null} if no initialization is required). Often
	 * used to call {@link ConfigurableListableBeanFactory#copyRegisteredEditorsTo}.
	 * @param defaultBindHandler the default bind handler to use if none is specified when
	 * binding
	 * @param constructorProvider the constructor provider which provides the bind
	 * constructor to use when binding
	 * @since 2.5.0
	 */
	public Binder(Iterable<ConfigurationPropertySource> sources, PlaceholdersResolver placeholdersResolver,
			List<ConversionService> conversionServices, Consumer<PropertyEditorRegistry> propertyEditorInitializer,
			BindHandler defaultBindHandler, BindConstructorProvider constructorProvider) {
		Assert.notNull(sources, "'sources' must not be null");
		for (ConfigurationPropertySource source : sources) {
			Assert.notNull(source, "'sources' must not contain null elements");
		}
		this.sources = sources;
		this.placeholdersResolver = (placeholdersResolver != null) ? placeholdersResolver : PlaceholdersResolver.NONE;
		this.bindConverter = BindConverter.get(conversionServices, propertyEditorInitializer);
		this.defaultBindHandler = (defaultBindHandler != null) ? defaultBindHandler : BindHandler.DEFAULT;
		if (constructorProvider == null) {
			constructorProvider = BindConstructorProvider.DEFAULT;
		}
		ValueObjectBinder valueObjectBinder = new ValueObjectBinder(constructorProvider);
		JavaBeanBinder javaBeanBinder = JavaBeanBinder.INSTANCE;
		Map<BindMethod, List<DataObjectBinder>> dataObjectBinders = new HashMap<>();
		dataObjectBinders.put(BindMethod.VALUE_OBJECT, List.of(valueObjectBinder));
		dataObjectBinders.put(BindMethod.JAVA_BEAN, List.of(javaBeanBinder));
		dataObjectBinders.put(null, List.of(valueObjectBinder, javaBeanBinder));
		this.dataObjectBinders = Collections.unmodifiableMap(dataObjectBinders);
	}

	public BindConverter getBindConverter(){
		return this.bindConverter;
	}

	public PlaceholdersResolver getPlaceholdersResolver() {
		return this.placeholdersResolver;
	}

	public Iterable<ConfigurationPropertySource> getSources(){
		return this.sources;
	}

	public Map<BindMethod, List<DataObjectBinder>> getDataObjectBinders(){
		return this.dataObjectBinders;
	}

	public BinderUtils getBinderUtils() {
		return this.binderUtils;
	}


	/**
	 * Bind the specified target {@link Class} using this binder's
	 * {@link ConfigurationPropertySource property sources}.
	 * @param name the configuration property name to bind
	 * @param target the target class
	 * @param <T> the bound type
	 * @return the binding result (never {@code null})
	 * @see #bind(ConfigurationPropertyName, Bindable, BindHandler)
	 */
	public <T> BindResult<T> bind(String name, Class<T> target) {
		return bind(name, Bindable.of(target));
	}

	/**
	 * Bind the specified target {@link Bindable} using this binder's
	 * {@link ConfigurationPropertySource property sources}.
	 * @param name the configuration property name to bind
	 * @param target the target bindable
	 * @param <T> the bound type
	 * @return the binding result (never {@code null})
	 * @see #bind(ConfigurationPropertyName, Bindable, BindHandler)
	 */
	public <T> BindResult<T> bind(String name, Bindable<T> target) {
		return bind(ConfigurationPropertyName.of(name), target, null);
	}

	/**
	 * Bind the specified target {@link Bindable} using this binder's
	 * {@link ConfigurationPropertySource property sources}.
	 * @param name the configuration property name to bind
	 * @param target the target bindable
	 * @param <T> the bound type
	 * @return the binding result (never {@code null})
	 * @see #bind(ConfigurationPropertyName, Bindable, BindHandler)
	 */
	public <T> BindResult<T> bind(ConfigurationPropertyName name, Bindable<T> target) {
		return bind(name, target, null);
	}

	/**
	 * Bind the specified target {@link Bindable} using this binder's
	 * {@link ConfigurationPropertySource property sources}.
	 * @param name the configuration property name to bind
	 * @param target the target bindable
	 * @param handler the bind handler (may be {@code null})
	 * @param <T> the bound type
	 * @return the binding result (never {@code null})
	 */
	public <T> BindResult<T> bind(String name, Bindable<T> target, BindHandler handler) {
		return bind(ConfigurationPropertyName.of(name), target, handler);
	}

	/**
	 * Bind the specified target {@link Bindable} using this binder's
	 * {@link ConfigurationPropertySource property sources}.
	 * @param name the configuration property name to bind
	 * @param target the target bindable
	 * @param handler the bind handler (may be {@code null})
	 * @param <T> the bound type
	 * @return the binding result (never {@code null})
	 */
	public <T> BindResult<T> bind(ConfigurationPropertyName name, Bindable<T> target, BindHandler handler) {
		T bound = bind(name, target, handler, false);
		return BindResult.of(bound);
	}

	/**
	 * Bind the specified target {@link Class} using this binder's
	 * {@link ConfigurationPropertySource property sources} or create a new instance of
	 * the specified target {@link Class} if the result of the binding is {@code null}.
	 * @param name the configuration property name to bind
	 * @param target the target class
	 * @param <T> the bound type
	 * @return the bound or created object
	 * @since 2.2.0
	 * @see #bind(ConfigurationPropertyName, Bindable, BindHandler)
	 */
	public <T> T bindOrCreate(String name, Class<T> target) {
		return bindOrCreate(name, Bindable.of(target));
	}

	/**
	 * Bind the specified target {@link Bindable} using this binder's
	 * {@link ConfigurationPropertySource property sources} or create a new instance using
	 * the type of the {@link Bindable} if the result of the binding is {@code null}.
	 * @param name the configuration property name to bind
	 * @param target the target bindable
	 * @param <T> the bound type
	 * @return the bound or created object
	 * @since 2.2.0
	 * @see #bindOrCreate(ConfigurationPropertyName, Bindable, BindHandler)
	 */
	public <T> T bindOrCreate(String name, Bindable<T> target) {
		return bindOrCreate(ConfigurationPropertyName.of(name), target, null);
	}

	/**
	 * Bind the specified target {@link Bindable} using this binder's
	 * {@link ConfigurationPropertySource property sources} or create a new instance using
	 * the type of the {@link Bindable} if the result of the binding is {@code null}.
	 * @param name the configuration property name to bind
	 * @param target the target bindable
	 * @param handler the bind handler
	 * @param <T> the bound type
	 * @return the bound or created object
	 * @since 2.2.0
	 * @see #bindOrCreate(ConfigurationPropertyName, Bindable, BindHandler)
	 */
	public <T> T bindOrCreate(String name, Bindable<T> target, BindHandler handler) {
		return bindOrCreate(ConfigurationPropertyName.of(name), target, handler);
	}

	/**
	 * Bind the specified target {@link Bindable} using this binder's
	 * {@link ConfigurationPropertySource property sources} or create a new instance using
	 * the type of the {@link Bindable} if the result of the binding is {@code null}.
	 * @param name the configuration property name to bind
	 * @param target the target bindable
	 * @param handler the bind handler (may be {@code null})
	 * @param <T> the bound or created type
	 * @return the bound or created object
	 * @since 2.2.0
	 */
	public <T> T bindOrCreate(ConfigurationPropertyName name, Bindable<T> target, BindHandler handler) {
		return bind(name, target, handler, true);
	}

	public <T> T bind(ConfigurationPropertyName name, Bindable<T> target, BindHandler handler, boolean create) {
		Assert.notNull(name, "'name' must not be null");
		Assert.notNull(target, "'target' must not be null");
		handler = (handler != null) ? handler : this.defaultBindHandler;
		Context context = new Context();
		return bind(name, target, handler, context, false, create);
	}

	public <T> T bind(ConfigurationPropertyName name, Bindable<T> target, BindHandler handler, Context context,
			boolean allowRecursiveBinding, boolean create) {
		try {
			Bindable<T> replacementTarget = handler.onStart(name, target, context);
			if (replacementTarget == null) {
				return this.handleBind.handleBindResult(name, target, handler, context, null, create, this);
			}
			target = replacementTarget;
			Object bound = this.binderUtils.bindObject(name, target, handler, context, allowRecursiveBinding, this);
			return this.handleBind.handleBindResult(name, target, handler, context, bound, create, this);
		}
		catch (Exception ex) {
			return this.handleBind.handleBindError(name, target, handler, context, ex);
		}
	}



	/**
	 * Create a new {@link Binder} instance from the specified environment.
	 * @param environment the environment source (must have attached
	 * {@link ConfigurationPropertySources})
	 * @return a {@link Binder} instance
	 */
	public static Binder get(Environment environment) {
		return get(environment, null);
	}

	/**
	 * Create a new {@link Binder} instance from the specified environment.
	 * @param environment the environment source (must have attached
	 * {@link ConfigurationPropertySources})
	 * @param defaultBindHandler the default bind handler to use if none is specified when
	 * binding
	 * @return a {@link Binder} instance
	 * @since 2.2.0
	 */
	public static Binder get(Environment environment, BindHandler defaultBindHandler) {
		Iterable<ConfigurationPropertySource> sources = ConfigurationPropertySources.get(environment);
		PropertySourcesPlaceholdersResolver placeholdersResolver = new PropertySourcesPlaceholdersResolver(environment);
		return new Binder(sources, placeholdersResolver, null, null, defaultBindHandler);
	}
}
