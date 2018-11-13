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

package io.shardingsphere.transaction.base.manager.servicecomb;

import io.shardingsphere.core.event.transaction.base.SagaTransactionEvent;
import io.shardingsphere.core.exception.ShardingException;
import io.shardingsphere.spi.NewInstanceServiceLoader;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.servicecomb.saga.transports.SQLTransport;

import java.util.Collection;

/**
 * SQL transport factory SPI loader.
 *
 * @author yangyi
 */
@Slf4j
public final class ShardingTransportFactorySPILoader {
    
    private static final ShardingTransportFactorySPILoader INSTANCE = new ShardingTransportFactorySPILoader();
    
    @Getter
    private final ShardingTransportFactory transportFactory;
    
    private ShardingTransportFactorySPILoader() {
        transportFactory = load();
    }
    
    private ShardingTransportFactory load() {
        try {
            Collection<ShardingTransportFactory> transportFactorys = NewInstanceServiceLoader.load(ShardingTransportFactory.class);
            if (transportFactorys.isEmpty()) {
                log.warn("There are no SQLTransport configured. BASE Saga Transaction cannot be used.");
                return new ShardingTransportFactory() {
        
                    @Override
                    public void cacheTransport(final SagaTransactionEvent event) {
                    }
        
                    @Override
                    public void remove() {
                    }
        
                    @Override
                    public SQLTransport getTransport() {
                        return new EmptySQLTransport();
                    }
                };
            }
            if (transportFactorys.size() > 1) {
                log.warn("There are more than one SQLTransport implement existing, chosen first one by default.");
            }
            return transportFactorys.iterator().next();
            // CHECKSTYLE:OFF
        } catch (Exception ex) {
            // CHECKSTYLE:ON
            throw new ShardingException("Can not initialize the SQLTransport failed with " + ex);
        }
    }
    
    /**
     * Get instance of Transport factory SPI loader.
     *
     * @return instance of Transport factory SPI loader
     */
    public static ShardingTransportFactorySPILoader getInstance() {
        return INSTANCE;
    }
    
}
