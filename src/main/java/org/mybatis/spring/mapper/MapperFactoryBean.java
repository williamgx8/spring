/**
 * Copyright 2010-2018 the original author or authors.
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

package org.mybatis.spring.mapper;

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.beans.factory.FactoryBean;

/**
 * BeanFactory that enables injection of MyBatis mapper interfaces. It can be set up with a
 * SqlSessionFactory or a pre-configured SqlSessionTemplate.
 * 为每一个Mapper配置一个对应的FactoryBean，这是一种spring和mybatis结合的方式，Mapper接口的代理实现
 * 使用的是Mybatis生成的MapperProxy，只不过为了让mybatis和spring整合，在MapperProxy外面套了一层spring
 * 提供的FactoryBean接口实现
 * <p>
 * Sample configuration:
 *
 * <pre class="code">
 * {@code
 *   <bean id="baseMapper" class="org.mybatis.spring.mapper.MapperFactoryBean" abstract="true" lazy-init="true">
 *     <property name="sqlSessionFactory" ref="sqlSessionFactory" />
 *   </bean>
 *
 *   <bean id="oneMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyMapperInterface" />
 *   </bean>
 *
 *   <bean id="anotherMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyAnotherMapperInterface" />
 *   </bean>
 * }
 * </pre>
 * <p>
 * Note that this factory can only inject <em>interfaces</em>, not concrete classes.
 *
 * @author Eduardo Macarron
 * @see SqlSessionTemplate
 */
public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {

	//对应Mapper接口类型
	private Class<T> mapperInterface;

	private boolean addToConfig = true;

	public MapperFactoryBean() {
		//intentionally empty
	}

	public MapperFactoryBean(Class<T> mapperInterface) {
		this.mapperInterface = mapperInterface;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void checkDaoConfig() {
		//校验sqlSessionTemplate非空
		super.checkDaoConfig();

		//必须配置FactoryBean对应的Mapper
		notNull(this.mapperInterface, "Property 'mapperInterface' is required");

		//获取Configuration
		Configuration configuration = getSqlSession().getConfiguration();
		//如果允许将Mapper配置加入到Configuration，并且Configuration中确实没有该Mapper
		if (this.addToConfig && !configuration.hasMapper(this.mapperInterface)) {
			try {
				//将Mapper加入到Configuration中
				configuration.addMapper(this.mapperInterface);
			} catch (Exception e) {
				logger.error("Error while adding the mapper '" + this.mapperInterface
					+ "' to configuration.", e);
				throw new IllegalArgumentException(e);
			} finally {
				ErrorContext.instance().reset();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T getObject() throws Exception {
		//从sqlSession中(实际是从Configuration中)获得对应接口的Mapper代理类
		return getSqlSession().getMapper(this.mapperInterface);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class<T> getObjectType() {
		//就是Mapper接口类型
		return this.mapperInterface;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isSingleton() {
		return true;
	}

	//------------- mutators --------------

	/**
	 * Sets the mapper interface of the MyBatis mapper
	 *
	 * @param mapperInterface class of the interface
	 */
	public void setMapperInterface(Class<T> mapperInterface) {
		this.mapperInterface = mapperInterface;
	}

	/**
	 * Return the mapper interface of the MyBatis mapper
	 *
	 * @return class of the interface
	 */
	public Class<T> getMapperInterface() {
		return mapperInterface;
	}

	/**
	 * If addToConfig is false the mapper will not be added to MyBatis. This means
	 * it must have been included in mybatis-config.xml.
	 * <p>
	 * If it is true, the mapper will be added to MyBatis in the case it is not already
	 * registered.
	 * <p>
	 * By default addToConfig is true.
	 *
	 * @param addToConfig a flag that whether add mapper to MyBatis or not
	 */
	public void setAddToConfig(boolean addToConfig) {
		this.addToConfig = addToConfig;
	}

	/**
	 * Return the flag for addition into MyBatis config.
	 *
	 * @return true if the mapper will be added to MyBatis in the case it is not already
	 * registered.
	 */
	public boolean isAddToConfig() {
		return addToConfig;
	}
}
