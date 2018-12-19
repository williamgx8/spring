/**
 * Copyright 2010-2017 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mybatis.spring.annotation;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * mybatis和spring整合的一种方式，Mapper接口的代理对象仍然是mybatis生成的MapperProxy，用spring提供的
 * {@code @Import}和ImportBeanDefinitionRegistrar将所有包下Mapper解析成BeanDefinition，在使用
 * ClassPathMapperScanner扫描并生成BeanDefinition之后，会将每一个BeanDefinition的beanClass属性
 * 设为MapperFactoryBean，当真正注入Mapper实例时创建Mapper对象，spring会检测到该对象为FactoryBean，
 * 从而调用自定义的MapperFactoryBean#getObject 创建出Mapper的代理对象
 * <p>
 * A {@link ImportBeanDefinitionRegistrar} to allow annotation configuration of
 * MyBatis mapper scanning. Using an @Enable annotation allows beans to be
 * registered via @Component configuration, whereas implementing
 * {@code BeanDefinitionRegistryPostProcessor} will work for XML configuration.
 *
 * @author Michael Lanyon
 * @author Eduardo Macarron
 * @author Putthiphong Boonphong
 * @see MapperFactoryBean
 * @see ClassPathMapperScanner
 * @since 1.2.0
 */
public class MapperScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

	private ResourceLoader resourceLoader;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	/**
	 * 根据@MapperScan中配置的待扫描的包，将包中接口生成代理对象，并放入spring的beanFactory中交由spring管理
	 *
	 * @param importingClassMetadata 当前标注@MapperScan类对应的AnnotataionMetadata对象
	 * @param registry spring的bean注册中心
	 * {@inheritDoc}
	 */
	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
		BeanDefinitionRegistry registry) {
		//@MapperScan中的所有属性对象
		AnnotationAttributes mapperScanAttrs = AnnotationAttributes
			.fromMap(importingClassMetadata.getAnnotationAttributes(MapperScan.class.getName()));
		if (mapperScanAttrs != null) {
			//创建bean并注册
			registerBeanDefinitions(mapperScanAttrs, registry);
		}
	}

	void registerBeanDefinitions(AnnotationAttributes annoAttrs, BeanDefinitionRegistry registry) {

		//根路径Mapper文件扫描器
		ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry);

		// this check is needed in Spring 3.1
		//由于实现了ResourceLoaderAware，可以从spring中直接得到当前的资源加载器
		if (resourceLoader != null) {
			scanner.setResourceLoader(resourceLoader);
		}

		//注解中的annotationClass属性
		Class<? extends Annotation> annotationClass = annoAttrs.getClass("annotationClass");
		if (!Annotation.class.equals(annotationClass)) {
			scanner.setAnnotationClass(annotationClass);
		}

		//注解中的markerInterface属性
		Class<?> markerInterface = annoAttrs.getClass("markerInterface");
		if (!Class.class.equals(markerInterface)) {
			scanner.setMarkerInterface(markerInterface);
		}

		//注解中的nameGenerator属性，创建对应类的对象放入扫描器中
		Class<? extends BeanNameGenerator> generatorClass = annoAttrs.getClass("nameGenerator");
		if (!BeanNameGenerator.class.equals(generatorClass)) {
			scanner.setBeanNameGenerator(BeanUtils.instantiateClass(generatorClass));
		}

		//factoryBean属性，创建对应类的对象放入扫描器
		Class<? extends MapperFactoryBean> mapperFactoryBeanClass = annoAttrs
			.getClass("factoryBean");
		//必须是MapperFactoryBean的实例
		if (!MapperFactoryBean.class.equals(mapperFactoryBeanClass)) {
			scanner.setMapperFactoryBean(BeanUtils.instantiateClass(mapperFactoryBeanClass));
		}

		//引用的SqlSessionTemplate
		scanner.setSqlSessionTemplateBeanName(annoAttrs.getString("sqlSessionTemplateRef"));
		//引用的SqlSessionFactoryRef
		scanner.setSqlSessionFactoryBeanName(annoAttrs.getString("sqlSessionFactoryRef"));

		//将配置的所有待扫描的包统一存放
		List<String> basePackages = new ArrayList<>();
		basePackages.addAll(
			Arrays.stream(annoAttrs.getStringArray("value"))
				.filter(StringUtils::hasText)
				.collect(Collectors.toList()));

		basePackages.addAll(
			Arrays.stream(annoAttrs.getStringArray("basePackages"))
				.filter(StringUtils::hasText)
				.collect(Collectors.toList()));

		basePackages.addAll(
			Arrays.stream(annoAttrs.getClassArray("basePackageClasses"))
				.map(ClassUtils::getPackageName)
				.collect(Collectors.toList()));

		//设置需要处理的和需要过滤的，上面可以同过addIncludeFilter等方法添加各种过滤器
		scanner.registerFilters();
		//扫包、创建对象并注册进spring
		scanner.doScan(StringUtils.toStringArray(basePackages));
	}

	/**
	 * A {@link MapperScannerRegistrar} for {@link MapperScans}.
	 *
	 * @since 2.0.0
	 */
	static class RepeatingRegistrar extends MapperScannerRegistrar {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
			BeanDefinitionRegistry registry) {
			AnnotationAttributes mapperScansAttrs = AnnotationAttributes
				.fromMap(
					importingClassMetadata.getAnnotationAttributes(MapperScans.class.getName()));
			if (mapperScansAttrs != null) {
				Arrays.stream(mapperScansAttrs.getAnnotationArray("value"))
					.forEach(mapperScanAttrs -> registerBeanDefinitions(mapperScanAttrs, registry));
			}
		}
	}

}
