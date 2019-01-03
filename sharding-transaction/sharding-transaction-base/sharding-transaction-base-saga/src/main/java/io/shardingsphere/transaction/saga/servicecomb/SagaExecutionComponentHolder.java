/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.transaction.saga.servicecomb;

import com.google.common.util.concurrent.MoreExecutors;
import io.shardingsphere.api.config.SagaConfiguration;
import io.shardingsphere.core.executor.ShardingThreadFactoryBuilder;
import io.shardingsphere.transaction.saga.servicecomb.transport.ShardingTransportFactory;
import org.apache.servicecomb.saga.core.EventEnvelope;
import org.apache.servicecomb.saga.core.PersistentStore;
import org.apache.servicecomb.saga.core.SagaDefinition;
import org.apache.servicecomb.saga.core.SagaEvent;
import org.apache.servicecomb.saga.core.application.SagaExecutionComponent;
import org.apache.servicecomb.saga.core.application.interpreter.FromJsonFormat;
import org.apache.servicecomb.saga.core.dag.GraphBasedSagaFactory;
import org.apache.servicecomb.saga.format.ChildrenExtractor;
import org.apache.servicecomb.saga.format.JacksonFromJsonFormat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service comb saga execution component holder.
 *
 * @author yangyi
 */
public class SagaExecutionComponentHolder {
    
    private final Map<String, SagaExecutionComponent> sagaCaches = new HashMap<>();
    
    private final Map<String, ExecutorService> executorCaches = new HashMap<>();
    
    /**
     * Get saga execution component from caches if exist.
     *
     * @param sagaConfiguration saga configuration
     * @return saga execution component
     */
    public SagaExecutionComponent getSagaExecutionComponent(final SagaConfiguration sagaConfiguration) {
        SagaExecutionComponent result;
        synchronized (sagaCaches) {
            if (!sagaCaches.containsKey(sagaConfiguration.getAlias())) {
                sagaCaches.put(sagaConfiguration.getAlias(), createSagaExecutionComponent(sagaConfiguration, createExecutors(sagaConfiguration)));
            }
            result = sagaCaches.get(sagaConfiguration.getAlias());
        }
        return result;
    }
    
    /**
     * Remove saga execution component from caches if exist.
     *
     * @param config saga configuration
     */
    public void removeSagaExecutionComponent(final SagaConfiguration config) {
        synchronized (sagaCaches) {
            if (sagaCaches.containsKey(config.getAlias())) {
                SagaExecutionComponent coordinator = sagaCaches.remove(config.getAlias());
                try {
                    coordinator.terminate();
                    // CHECKSTYLE:OFF
                } catch (Exception ignored) {
                    // CHECKSTYLE:ON
                }
            }
            if (executorCaches.containsKey(config.getAlias())) {
                executorCaches.remove(config.getAlias()).shutdown();
            }
        }
    }
    
    private SagaExecutionComponent createSagaExecutionComponent(final SagaConfiguration config, final ExecutorService executors) {
        EmptyPersistentStore persistentStore = new EmptyPersistentStore();
        FromJsonFormat<SagaDefinition> fromJsonFormat = new JacksonFromJsonFormat(ShardingTransportFactory.getInstance());
        GraphBasedSagaFactory sagaFactory = new GraphBasedSagaFactory(config.getCompensationRetryDelay(), persistentStore, new ChildrenExtractor(), executors);
        return new SagaExecutionComponent(persistentStore, fromJsonFormat, null, sagaFactory);
    }
    
    private ExecutorService createExecutors(final SagaConfiguration config) {
        ExecutorService result = MoreExecutors.listeningDecorator(config.getExecutorSize() <= 0
            ? Executors.newCachedThreadPool(ShardingThreadFactoryBuilder.build("Saga-%d")) : Executors.newFixedThreadPool(config.getExecutorSize(), ShardingThreadFactoryBuilder.build("Saga-%d")));
        MoreExecutors.addDelayedShutdownHook(result, 60, TimeUnit.SECONDS);
        executorCaches.put(config.getAlias(), result);
        return result;
    }
    
    private final class EmptyPersistentStore implements PersistentStore {
        
        @Override
        public Map<String, List<EventEnvelope>> findPendingSagaEvents() {
            return new HashMap<>(1);
        }
    
        @Override
        public void offer(final SagaEvent sagaEvent) {
        
        }
    }
    
}
