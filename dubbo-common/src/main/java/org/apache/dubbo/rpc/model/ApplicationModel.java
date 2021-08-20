/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.model;

import org.apache.dubbo.common.config.Environment;
import org.apache.dubbo.common.context.FrameworkExt;
import org.apache.dubbo.common.extension.ExtensionDirector;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.ExtensionScope;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.context.ConfigManager;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link ExtensionLoader}, {@code DubboBootstrap} and this class are at present designed to be
 * singleton or static (by itself totally static or uses some static fields). So the instances
 * returned from them are of process scope. If you want to support multiple dubbo servers in one
 * single process, you may need to refactor those three classes.
 * <p>
 * Represent a application which is using Dubbo and store basic metadata info for using
 * during the processing of RPC invoking.
 * <p>
 * ApplicationModel includes many ProviderModel which is about published services
 * and many Consumer Model which is about subscribed services.
 * <p>
 */

public class ApplicationModel {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ApplicationModel.class);
    public static final String NAME = "application";
    private static volatile ApplicationModel defaultInstance;

    private AtomicBoolean initFlag = new AtomicBoolean(false);
    private Environment environment;
    private ConfigManager configManager;
    private ServiceRepository serviceRepository;

    private FrameworkModel frameworkModel;
    private ExtensionDirector extensionDirector;

    public ApplicationModel(FrameworkModel frameworkModel) {
        this.frameworkModel = frameworkModel;
        extensionDirector = new ExtensionDirector(frameworkModel.getExtensionDirector(), ExtensionScope.APPLICATION);
        extensionDirector.addExtensionPostProcessor(new ModelAwarePostProcessor(this));
    }

    public static ApplicationModel defaultModel() {
        if (defaultInstance == null) {
            synchronized (ApplicationModel.class) {
                if (defaultInstance == null) {
                    defaultInstance = new ApplicationModel(FrameworkModel.defaultModel());
                }
            }
        }
        return defaultInstance;
    }

    public void init() {
        if (initFlag.compareAndSet(false, true)) {
            ExtensionLoader<ApplicationInitListener> extensionLoader = ExtensionLoader.getExtensionLoader(ApplicationInitListener.class);
            Set<String> listenerNames = extensionLoader.getSupportedExtensions();
            for (String listenerName : listenerNames) {
                extensionLoader.getExtension(listenerName).init();
            }
        }
    }

    public void destroy() {
        // TODO destroy application resources
    }

    public FrameworkModel getFrameworkModel() {
        return frameworkModel;
    }

    public ExtensionDirector getExtensionDirector() {
        return extensionDirector;
    }

    public <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        return extensionDirector.getExtensionLoader(type);
    }

    public Collection<ConsumerModel> allConsumerModels() {
        return getServiceRepository().getReferredServices();
    }

    public Collection<ProviderModel> allProviderModels() {
        return getServiceRepository().getExportedServices();
    }

    public ProviderModel getProviderModel(String serviceKey) {
        return getServiceRepository().lookupExportedService(serviceKey);
    }

    public ConsumerModel getConsumerModel(String serviceKey) {
        return getServiceRepository().lookupReferredService(serviceKey);
    }

    public void initFrameworkExts() {
        Set<FrameworkExt> exts = extensionDirector.getExtensionLoader(FrameworkExt.class).getSupportedExtensionInstances();
        for (FrameworkExt ext : exts) {
            ext.initialize();
        }
    }

    public Environment getEnvironment() {
        if (environment == null) {
            environment = (Environment) extensionDirector.getExtensionLoader(FrameworkExt.class)
                .getExtension(Environment.NAME);
        }
        return environment;
    }

    public ConfigManager getConfigManager() {
        if (configManager == null) {
            configManager = (ConfigManager) extensionDirector.getExtensionLoader(FrameworkExt.class)
                .getExtension(ConfigManager.NAME);
        }
        return configManager;
    }

    public ServiceRepository getServiceRepository() {
        if (serviceRepository == null) {
            serviceRepository = (ServiceRepository) extensionDirector.getExtensionLoader(FrameworkExt.class)
                .getExtension(ServiceRepository.NAME);
        }
        return serviceRepository;
    }

    public ExecutorRepository getExecutorRepository() {
        return extensionDirector.getExtensionLoader(ExecutorRepository.class).getDefaultExtension();
    }

    public ApplicationConfig getApplicationConfig() {
        return getConfigManager().getApplicationOrElseThrow();
    }

    public String getName() {
        return getApplicationConfig().getName();
    }

    // only for unit test
    @Deprecated
    public static void reset() {
        if (defaultInstance != null) {
            defaultInstance.destroy();
            defaultInstance = null;
        }
    }

}
