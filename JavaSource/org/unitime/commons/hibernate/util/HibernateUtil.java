/*
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * The Apereo Foundation licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
*/
package org.unitime.commons.hibernate.util;

import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;

import javax.naming.spi.NamingManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.MappingException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.cfgxml.spi.LoadedConfig;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.registry.internal.StandardServiceRegistryImpl;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.Oracle8iDialect;
import org.hibernate.dialect.PostgreSQL9Dialect;
import org.hibernate.dialect.function.SQLFunctionTemplate;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Selectable;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.spi.ServiceBinding;
import org.hibernate.type.IntegerType;
import org.hibernate.type.StandardBasicTypes;
import org.unitime.commons.LocalContext;
import org.unitime.commons.hibernate.connection.LoggingConnectionProvider;
import org.unitime.commons.hibernate.id.UniqueIdGenerator;
import org.unitime.timetable.ApplicationProperties;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.model.base._BaseRootDAO;
import org.unitime.timetable.model.dao._RootDAO;

/**
 * @author Tomas Muller
 */
public class HibernateUtil {
    private static Log sLog = LogFactory.getLog(HibernateUtil.class);
    private static SessionFactory sSessionFactory = null;
    
    public static void configureHibernate(String connectionUrl) throws Exception {
        Properties properties = ApplicationProperties.getProperties();
        properties.setProperty("connection.url", connectionUrl);
        configureHibernate(properties);
    }
    
    public static String getProperty(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value!=null) {
            sLog.debug("   -- " + name + "=" + value);
        	return value;
        }
        sLog.debug("   -- using application properties for " + name);
        value = ApplicationProperties.getProperty(name);
        sLog.debug("     -- " + name + "=" + value);
        return value;
    }
    
    public static void fixSchemaInFormulas(Metadata meta, String schema, Class dialect) throws ClassNotFoundException {
    	for (PersistentClass pc: meta.getEntityBindings()) {
    		for (Iterator j=pc.getPropertyIterator();j.hasNext();) {
                Property p = (Property)j.next();
                for (Iterator k=p.getColumnIterator();k.hasNext();) {
                    Selectable c = (Selectable)k.next();
                    if (c instanceof Formula) {
                        Formula f = (Formula)c;
                        boolean updated = false;
                        if (schema != null && f.getFormula() != null && f.getFormula().indexOf("%SCHEMA%")>=0) {
                            f.setFormula(f.getFormula().replaceAll("%SCHEMA%", schema));
                            sLog.debug("Schema updated in "+pc.getClassName()+"."+p.getName()+" to "+f.getFormula());
                        }
                        if (f.getFormula()!=null && (f.getFormula().indexOf("%TRUE%")>=0 || f.getFormula().indexOf("%FALSE%")>=0)) {
                        	if (isPostgress(dialect)) {
                        		f.setFormula(f.getFormula().replaceAll("%TRUE%", "'t'").replaceAll("%FALSE%", "'f'"));
                        	} else {
                        		f.setFormula(f.getFormula().replaceAll("%TRUE%", "1").replaceAll("%FALSE%", "0"));
                        	}
                        }
                        if (updated)
                        	sLog.debug("Schema updated in "+pc.getClassName()+"."+p.getName()+" to "+f.getFormula());
                    }
                }
            }
        }
    }
	public static void configureHibernate(Properties properties) throws Exception {
		if (sSessionFactory != null) {
			sSessionFactory.close();
			sSessionFactory = null;
		}
		
		if (!NamingManager.hasInitialContextFactoryBuilder())
			NamingManager.setInitialContextFactoryBuilder(new LocalContext(null));
		
		sLog.info("Connecting to "+getProperty(properties, "connection.url"));
		ClassLoader classLoader = HibernateUtil.class.getClassLoader();
		sLog.debug("  -- class loader retrieved");
		
		StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder();
		LoadedConfig config = registryBuilder.getConfigLoader().loadConfigXmlUrl(classLoader.getResource("hibernate.cfg.xml"));
		
        String dialect = ApplicationProperty.DatabaseDialect.value();
        if (dialect!=null)
        	config.getConfigurationValues().put("dialect", dialect);

        String idgen = getProperty(properties, "tmtbl.uniqueid.generator");
        if (idgen!=null)
        	config.getConfigurationValues().put("tmtbl.uniqueid.generator", idgen);

        if (ApplicationProperty.HibernateCacheConfiguration.value() != null)
        	config.getConfigurationValues().put("hibernate.cache.infinispan.cfg", ApplicationProperty.HibernateCacheConfiguration.value());
        else if (ApplicationProperty.HibernateClusterEnabled.isTrue())
        	config.getConfigurationValues().put("hibernate.cache.infinispan.cfg", "infinispan-cluster.xml");
        else if (ApplicationProperty.HibernateClusterEnabled.isFalse())
        	config.getConfigurationValues().put("hibernate.cache.infinispan.cfg", "infinispan-local.xml");
        config.getConfigurationValues().put("hibernate.cache.infinispan.jgroups_cfg", ApplicationProperty.HibernateClusterConfiguration.value());

        // Remove second level cache
        config.getConfigurationValues().put("hibernate.cache.use_second_level_cache", "false");
        config.getConfigurationValues().put("hibernate.cache.use_query_cache", "false");
        config.getConfigurationValues().remove("hibernate.cache.region.factory_class");

        for (Enumeration e=properties.propertyNames();e.hasMoreElements();) {
        	String name = (String)e.nextElement();
            if (name.startsWith("hibernate.") || name.startsWith("tmtbl.hibernate.")) {
				String value = ApplicationProperties.getProperty(name);
                if ("NULL".equals(value))
                	config.getConfigurationValues().remove(name);
                else
                	config.getConfigurationValues().put(name, value);
                if (!name.equals("connection.password"))
                    sLog.debug("  -- set "+name+": "+value);
                else
                    sLog.debug("  -- set "+name+": *****");
            }
            if (name.startsWith("connection.")) {
				String value = ApplicationProperties.getProperty(name);
                if ("NULL".equals(value)) {
                	config.getConfigurationValues().remove(name);
                	config.getConfigurationValues().remove("hibernate." + name);
                } else {
                	config.getConfigurationValues().put(name, value);
                	config.getConfigurationValues().put("hibernate." + name, value);
                }
                if (!name.equals("connection.password"))
                    sLog.debug("  -- set "+name+": "+value);
                else
                    sLog.debug("  -- set "+name+": *****");
            }
        }
        
        String default_schema = getProperty(properties, "default_schema");
        if (default_schema != null)
        	config.getConfigurationValues().put("default_schema", default_schema);
        
        UniqueIdGenerator.configure(config);
        
        registryBuilder.configure(config);
        
        ServiceRegistry registry = registryBuilder.build();
        
        if (ApplicationProperty.ConnectionLogging.isTrue()) {
        	ConnectionProvider cp = registry.getService(ConnectionProvider.class);
        	if (cp != null) {
        		ServiceBinding<ConnectionProvider> scp = ((StandardServiceRegistryImpl)registry).locateServiceBinding(ConnectionProvider.class);
            	if (scp != null)
            		scp.setService(new LoggingConnectionProvider(registry.getService(ConnectionProvider.class)));
        	}
        }

        MetadataBuilder metaBuild = new MetadataSources(registry).getMetadataBuilder();
        Class d = Class.forName((String)config.getConfigurationValues().get("dialect"));
        addOperations(metaBuild, d);
        
        Metadata meta = metaBuild.build();
        
        fixSchemaInFormulas(meta, default_schema, d);
        
        (new _BaseRootDAO() {
    		void setContext(HibernateContext cx) {
    			_BaseRootDAO.sContext = cx;
    		}
    		protected Class getReferenceClass() { return null; }
    	}).setContext(new HibernateContext(config, registry, meta, meta.buildSessionFactory()));
        
        DatabaseUpdate.update();
    }
    
    public static void closeHibernate() {
		if (sSessionFactory != null) {
			sSessionFactory.close();
			sSessionFactory=null;
		}
	}
    
    public static HibernateContext configureHibernateFromRootDAO() throws ClassNotFoundException {
    	sLog.info("Connecting to "+ApplicationProperty.ConnectionUrl.value());
		ClassLoader classLoader = HibernateUtil.class.getClassLoader();

        StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder();
        LoadedConfig config = registryBuilder.getConfigLoader().loadConfigXmlUrl(classLoader.getResource("hibernate.cfg.xml"));
        
        String dialect = ApplicationProperty.DatabaseDialect.value();
        if (dialect!=null) {
        	config.getConfigurationValues().put("dialect", dialect);
        	config.getConfigurationValues().put("hibernate.dialect", dialect);
        }

        String idgen = ApplicationProperty.DatabaseUniqueIdGenerator.value();
        if (idgen!=null)
        	config.getConfigurationValues().put("tmtbl.uniqueid.generator", idgen);

        if (ApplicationProperty.HibernateCacheConfiguration.value() != null)
        	config.getConfigurationValues().put("hibernate.cache.infinispan.cfg", ApplicationProperty.HibernateCacheConfiguration.value());
        else if (ApplicationProperty.HibernateClusterEnabled.isTrue())
        	config.getConfigurationValues().put("hibernate.cache.infinispan.cfg", "infinispan-cluster.xml");
        else if (ApplicationProperty.HibernateClusterEnabled.isFalse())
        	config.getConfigurationValues().put("hibernate.cache.infinispan.cfg", "infinispan-local.xml");
        config.getConfigurationValues().put("hibernate.cache.infinispan.jgroups_cfg", ApplicationProperty.HibernateClusterConfiguration.value());

        for (Enumeration e=ApplicationProperties.getProperties().propertyNames();e.hasMoreElements();) {
            String name = (String)e.nextElement();
            if (name.startsWith("hibernate.") || name.startsWith("tmtbl.hibernate.")) {
				String value = ApplicationProperties.getProperty(name);
                if ("NULL".equals(value))
                	config.getConfigurationValues().remove(name);
                else
                	config.getConfigurationValues().put(name, value);
                if (!name.equals("connection.password"))
                    sLog.debug("  -- set "+name+": "+value);
                else
                    sLog.debug("  -- set "+name+": *****");
            }
            if (name.startsWith("connection.")) {
				String value = ApplicationProperties.getProperty(name);
                if ("NULL".equals(value)) {
                	config.getConfigurationValues().remove(name);
                	config.getConfigurationValues().remove("hibernate." + name);
                } else {
                	config.getConfigurationValues().put(name, value);
                	config.getConfigurationValues().put("hibernate." + name, value);
                }
                if (!name.equals("connection.password"))
                    sLog.debug("  -- set "+name+": "+value);
                else
                    sLog.debug("  -- set "+name+": *****");
            }
        }

        String default_schema = ApplicationProperty.DatabaseSchema.value();
        if (default_schema != null)
        	config.getConfigurationValues().put("default_schema", default_schema);
        
        UniqueIdGenerator.configure(config);
        
        registryBuilder.configure(config);
        
        ServiceRegistry registry = registryBuilder.build();
        
        if (ApplicationProperty.ConnectionLogging.isTrue()) {
        	ConnectionProvider cp = registry.getService(ConnectionProvider.class);
        	if (cp != null) {
        		ServiceBinding<ConnectionProvider> scp = ((StandardServiceRegistryImpl)registry).locateServiceBinding(ConnectionProvider.class);
            	if (scp != null)
            		scp.setService(new LoggingConnectionProvider(registry.getService(ConnectionProvider.class)));
        	}
        }

        
        MetadataBuilder metaBuild = new MetadataSources(registry).getMetadataBuilder();
        Class d = Class.forName((String)config.getConfigurationValues().get("dialect"));
        addOperations(metaBuild, d);
        
        Metadata meta = metaBuild.build();
        
        fixSchemaInFormulas(meta, default_schema, d);
        
        return new HibernateContext(config, registry, meta, meta.buildSessionFactory());
    }
    
    private static String sConnectionUrl = null;
    
    public static String getConnectionUrl() {
        if (sConnectionUrl==null) {
            try {
                SessionImplementor session = (SessionImplementor)new _RootDAO().getSession();
                Connection connection = session.getJdbcConnectionAccess().obtainConnection();
                sConnectionUrl = connection.getMetaData().getURL();
                session.getJdbcConnectionAccess().releaseConnection(connection);
            } catch (Exception e) {
                sLog.error("Unable to get connection string, reason: "+e.getMessage(),e);
            }
        }
        return sConnectionUrl;
    }

    public static String getDatabaseName() {
    	String schema = (String)_RootDAO.getHibernateContext().getConfig().getConfigurationValues().get("default_schema");
        String url = getConnectionUrl();
        if (url==null) return "N/A";
        if (url.startsWith("jdbc:oracle:")) {
            return schema+"@"+url.substring(1+url.lastIndexOf(':'));
        }
        return schema;
    }
    
    public static void clearCache() {
        clearCache(null, true);
    }
    
    public static void clearCache(Class persistentClass) {
        clearCache(persistentClass, false);
    }

    public static void clearCache(Class persistentClass, boolean evictQueries) {
        _RootDAO dao = new _RootDAO();
        org.hibernate.Session hibSession = dao.getSession(); 
        SessionFactory hibSessionFactory = hibSession.getSessionFactory();
        if (persistentClass==null) {
            hibSessionFactory.getCache().evictEntityData();
            hibSessionFactory.getCache().evictCollectionData();
        } else {
            hibSessionFactory.getCache().evictEntityData(persistentClass);
            ClassMetadata classMetadata = null;
            try {
            	classMetadata = hibSessionFactory.getClassMetadata(persistentClass);
            } catch (MappingException e) {}
            if (classMetadata!=null) {
                for (int j=0;j<classMetadata.getPropertyNames().length;j++) {
                    if (classMetadata.getPropertyTypes()[j].isCollectionType()) {
                        try {
                            hibSessionFactory.getCache().evictCollectionData(persistentClass.getClass().getName()+"."+classMetadata.getPropertyNames()[j]);
                        } catch (MappingException e) {}
                    }
                }
            }
        }
        if (evictQueries) {
            hibSessionFactory.getCache().evictQueryRegions();
            hibSessionFactory.getCache().evictDefaultQueryRegion();
        }
    }
    
    public static Class<?> getDialect() {
    	try {
    		return Class.forName((String)_RootDAO.getHibernateContext().getConfig().getConfigurationValues().get("dialect"));
    	} catch (ClassNotFoundException e) {
    		return null;
    	}
    }
    
    public static boolean isMySQL() {
    	return MySQLDialect.class.isAssignableFrom(getDialect());
    }
    
    public static boolean isOracle() {
    	return Oracle8iDialect.class.isAssignableFrom(getDialect());
    }
    
    public static boolean isPostgress() {
    	return PostgreSQL9Dialect.class.isAssignableFrom(getDialect());
    }
    
    public static boolean isPostgress(Class dialect) {
    	return PostgreSQL9Dialect.class.isAssignableFrom(dialect);
    }
    
    public static String addDate(String dateSQL, String incrementSQL) {
        if (isMySQL() || isPostgress())
            return "adddate("+dateSQL+","+incrementSQL+")";
        else
            return dateSQL+(incrementSQL.startsWith("+")||incrementSQL.startsWith("-")?"":"+")+incrementSQL;
    }
    
    public static String dayOfWeek(String field) {
    	if (isOracle())
    		return "(trunc(" + field + ") - trunc(" + field + ", 'IW'))";
    	else if (isPostgress())
    		return "extract(isodow from " + field + ") - 1";
    	else
    		return "weekday(" + field + ")";
    }
    
    public static String date(Date date) {
    	if (isOracle() || isPostgress())
    		return "to_date('" + new SimpleDateFormat("yyyy-MM-dd").format(date) + "', 'YYYY-MM-DD')";
    	else
    		return "str_to_date('" + new SimpleDateFormat("yyyy-MM-dd").format(date) + "', '%Y-%m-%d')";
    }
    
    public static void addOperations(MetadataBuilder builder, Class dialect) {
    	if (Oracle8iDialect.class.isAssignableFrom(dialect)) {
    		builder.applySqlFunction(
    				"bit_and",
    				new StandardSQLFunction("bitand", StandardBasicTypes.INTEGER)
    				);
        } else if (PostgreSQL9Dialect.class.isAssignableFrom(dialect)) {
    		builder.applySqlFunction(
    				"bit_and",
    				new SQLFunctionTemplate(IntegerType.INSTANCE, "cast(?1 as int) & cast(?2 as int)")
    				);
    		builder.applySqlFunction(
    				"adddate",
    				new SQLFunctionTemplate(IntegerType.INSTANCE, "?1 + (?2) * interval '1 day'")
    				);	
        } else {
        	builder.applySqlFunction(
    				"bit_and",
    				new SQLFunctionTemplate(IntegerType.INSTANCE, "?1 & ?2")
    				);
        }
    	builder.applySqlFunction(
				"replace",
				new StandardSQLFunction("replace", StandardBasicTypes.STRING)
				);
    }    
    
    public static String escapeSql(String str) {
    	if (str == null) return null;
    	return StringUtils.replace(str, "'", "''");
    }
}
