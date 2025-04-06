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

import org.springframework.boot.context.properties.source.ConfigurationPropertyName;

public class HandleBind {

	public <T> T handleBindResult(ConfigurationPropertyName name, Bindable<T> target, BindHandler handler,
			Context context, Object result, boolean create, Binder binder) throws Exception {
		if (result != null) {
			result = handler.onSuccess(name, target, context, result);
			result = context.getConverter().convert(result, target);
		}
		if (result == null && create) {
			result = binder.getBinderUtils().fromDataObjectBinders(target.getBindMethod(),
					(dataObjectBinder) -> dataObjectBinder.create(target, context), binder);
			result = handler.onCreate(name, target, context, result);
			result = context.getConverter().convert(result, target);
			if (result == null) {
				IllegalStateException ex = new IllegalStateException(
						"Unable to create instance for " + target.getType());
				binder.getDataObjectBinders().get(target.getBindMethod())
						.forEach((dataObjectBinder) -> dataObjectBinder.onUnableToCreateInstance(target, context, ex));
				throw ex;
			}
		}
		handler.onFinish(name, target, context, result);
		return context.getConverter().convert(result, target);
	}

	public <T> T handleBindError(ConfigurationPropertyName name, Bindable<T> target, BindHandler handler,
			Context context, Exception error) {
		try {
			Object result = handler.onFailure(name, target, context, error);
			return context.getConverter().convert(result, target);
		}
		catch (Exception ex) {
			if (ex instanceof BindException bindException) {
				throw bindException;
			}
			throw new BindException(name, target, context.getConfigurationProperty(), ex);
		}
	}
}
