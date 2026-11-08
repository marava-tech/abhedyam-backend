package com.abhedyam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("audit-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        // who made the request travels with the task, so rows it saves get added_by ("by Ravi")
        executor.setTaskDecorator(org.springframework.security.concurrent.DelegatingSecurityContextRunnable::new);
        executor.initialize();
        return executor;
    }
    
    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        // same: copy the caller's security context into the task (audit rows get added_by)
        return new org.springframework.security.concurrent.DelegatingSecurityContextExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }
}

