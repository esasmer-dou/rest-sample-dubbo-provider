package com.reactor.sample.dubbo.provider.dubbo;

import org.apache.dubbo.common.serialize.hessian2.Hessian2FactoryManager;
import org.apache.dubbo.common.ssl.CertManager;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.common.threadpool.manager.FrameworkExecutorRepository;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.apache.dubbo.rpc.protocol.PermittedSerializationKeeper;

final class DubboProviderRuntimeModel {

    private DubboProviderRuntimeModel() {}

    static ModuleModel module() {
        ApplicationModel application = ApplicationModel.defaultModel();
        FrameworkModel framework = application.getFrameworkModel();
        framework.getBeanFactory().getOrRegisterBean(FrameworkExecutorRepository.class);
        framework.getBeanFactory().getOrRegisterBean(PermittedSerializationKeeper.class);
        if (framework.getBeanFactory().getBean(CertManager.class) == null) {
            framework.getBeanFactory().registerBean(new CertManager(framework));
        }
        if (framework.getBeanFactory().getBean(Hessian2FactoryManager.class) == null) {
            framework.getBeanFactory().registerBean(new Hessian2FactoryManager(framework));
        }
        ExecutorRepository.getInstance(application);
        return application.getDefaultModule();
    }
}
