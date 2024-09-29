/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.test.context.bean.override.mockito;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * {@code TestExecutionListener} that enables {@link MockitoBean @MockitoBean}
 * and {@link MockitoSpyBean @MockitoSpyBean} support. Also triggers Mockito
 * setup of a {@link Mockito#mockitoSession() session} for each test class that
 * uses these annotations (or any annotation in that package).
 *
 * <p>The {@link MockitoSession#setStrictness(Strictness) strictness} of the
 * session defaults to {@link Strictness#STRICT_STUBS}. Use
 * {@link MockitoBeanSettings @MockitoBeanSettings} to specify a different strictness.
 *
 * <p>The automatic reset support for {@code @MockBean} and {@code @SpyBean} is
 * handled by the {@link MockitoResetTestExecutionListener}.
 *
 * @author Simon Baslé
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Moritz Halbritter
 * @author Sam Brannen
 * @since 6.2
 * @see MockitoResetTestExecutionListener
 * @see MockitoBean @MockitoBean
 * @see MockitoSpyBean @MockitoSpyBean
 */
public class MockitoTestExecutionListener extends AbstractTestExecutionListener {

	private static final String MOCKS_ATTRIBUTE_NAME = MockitoTestExecutionListener.class.getName() + ".mocks";

	static final boolean mockitoPresent = ClassUtils.isPresent("org.mockito.MockSettings",
			MockitoTestExecutionListener.class.getClassLoader());


	/**
	 * Executes before {@link DependencyInjectionTestExecutionListener}.
	 */
	@Override
	public final int getOrder() {
		return 1950;
	}

	@Override
	public void prepareTestInstance(TestContext testContext) throws Exception {
		if (mockitoPresent) {
			closeMocks(testContext);
			initMocks(testContext);
		}
	}

	@Override
	public void beforeTestMethod(TestContext testContext) throws Exception {
		if (mockitoPresent && Boolean.TRUE.equals(
				testContext.getAttribute(DependencyInjectionTestExecutionListener.REINJECT_DEPENDENCIES_ATTRIBUTE))) {
			closeMocks(testContext);
			initMocks(testContext);
		}
	}

	@Override
	public void afterTestMethod(TestContext testContext) throws Exception {
		if (mockitoPresent) {
			closeMocks(testContext);
		}
	}

	@Override
	public void afterTestClass(TestContext testContext) throws Exception {
		if (mockitoPresent) {
			closeMocks(testContext);
		}
	}

	private void initMocks(TestContext testContext) {
		if (MockitoAnnotationDetector.hasMockitoAnnotations(testContext.getTestClass())) {
			Object testInstance = testContext.getTestInstance();
			MockitoBeanSettings annotation = AnnotationUtils.findAnnotation(testInstance.getClass(),
					MockitoBeanSettings.class);
			testContext.setAttribute(MOCKS_ATTRIBUTE_NAME, initMockitoSession(testInstance,
					annotation != null ? annotation.value() : Strictness.STRICT_STUBS));
		}
	}

	private MockitoSession initMockitoSession(Object testInstance, Strictness strictness) {
		return Mockito.mockitoSession()
				.initMocks(testInstance)
				.strictness(strictness)
				.startMocking();
	}

	private void closeMocks(TestContext testContext) throws Exception {
		Object mocks = testContext.getAttribute(MOCKS_ATTRIBUTE_NAME);
		if (mocks instanceof MockitoSession session) {
			session.finishMocking();
		}
	}


	/**
	 * Utility class that detects {@code org.mockito} annotations as well as the
	 * annotations in this package (like {@link MockitoBeanSettings @MockitoBeanSettings}).
	 */
	private static class MockitoAnnotationDetector {

		private static final String MOCKITO_BEAN_PACKAGE = MockitoBeanSettings.class.getPackageName();

		private static final String ORG_MOCKITO_PACKAGE = "org.mockito";

		private static final Predicate<Annotation> isMockitoAnnotation = annotation -> {
				String packageName = annotation.annotationType().getPackageName();
				return (packageName.startsWith(MOCKITO_BEAN_PACKAGE) ||
						packageName.startsWith(ORG_MOCKITO_PACKAGE));
			};

		static boolean hasMockitoAnnotations(Class<?> testClass) {
			if (isAnnotated(testClass)) {
				return true;
			}
			// TODO Ideally we should short-circuit the search once we've found a Mockito annotation,
			// since there's no need to continue searching additional fields or further up the class
			// hierarchy; however, that is not possible with ReflectionUtils#doWithFields. Plus, the
			// previous invocation of isAnnotated(testClass) only finds annotations declared directly
			// on the test class. So, we'll likely need a completely different approach that combines
			// the "test class/interface is annotated?" and "field is annotated?" checks in a single
			// search algorithm.
			AtomicBoolean found = new AtomicBoolean();
			ReflectionUtils.doWithFields(testClass, field -> found.set(true), MockitoAnnotationDetector::isAnnotated);
			return found.get();
		}

		private static boolean isAnnotated(AnnotatedElement annotatedElement) {
			return Arrays.stream(annotatedElement.getAnnotations()).anyMatch(isMockitoAnnotation);
		}
	}

}
