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

package io.shardingsphere.transaction.base.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import io.shardingsphere.api.config.SagaConfiguration;
import io.shardingsphere.core.constant.SagaRecoveryPolicy;
import io.shardingsphere.core.constant.transaction.TransactionType;
import io.shardingsphere.core.event.transaction.base.SagaSQLExecutionEvent;
import io.shardingsphere.core.event.transaction.base.SagaTransactionEvent;
import io.shardingsphere.core.exception.ShardingException;
import io.shardingsphere.transaction.base.manager.SagaTransactionManager;
import io.shardingsphere.transaction.base.servicecomb.definition.SagaDefinitionBuilder;
import io.shardingsphere.transaction.handler.ShardingTransactionHandlerAdapter;
import io.shardingsphere.transaction.manager.ShardingTransactionManager;
import io.shardingsphere.transaction.manager.base.BASETransactionManager;
import io.shardingsphere.transaction.revert.EmptyRevertEngine;
import io.shardingsphere.transaction.revert.RevertEngine;
import io.shardingsphere.transaction.revert.RevertEngineImpl;
import io.shardingsphere.transaction.revert.RevertResult;
import lombok.extern.slf4j.Slf4j;

import javax.transaction.Status;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saga transaction handler.
 *
 * @author yangyi
 */
@Slf4j
public final class SagaShardingTransactionHandler extends ShardingTransactionHandlerAdapter<SagaTransactionEvent> {
    
    private final BASETransactionManager<SagaTransactionEvent> transactionManager = SagaTransactionManager.getInstance();
    
    private final Map<String, SagaDefinitionBuilder> sagaDefinitionBuilderMap = new ConcurrentHashMap<>();
    
    private final Map<String, RevertEngine> revertEngineMap = new ConcurrentHashMap<>();
    
    private final SagaSQLExecutionEventHandler sagaSQLExecutionEventHandler = new SagaSQLExecutionEventHandler();
    
    @Override
    public TransactionType getTransactionType() {
        return TransactionType.BASE;
    }
    
    @Override
    protected ShardingTransactionManager getShardingTransactionManager() {
        return transactionManager;
    }
    
    @Override
    public void doInTransaction(final SagaTransactionEvent transactionEvent) {
        if (transactionEvent.isExecutionEvent()) {
            sagaSQLExecutionEventHandler.handleSQLExecutionEvent(transactionEvent.getSagaSQLExecutionEvent());
        }
//        if (null != transactionEvent.getSagaSQLExecutionEvent()) {
//            try {
//                handleSQLExecutionEvent(transactionEvent.getSagaSQLExecutionEvent());
//                return;
//            } catch (SQLException e) {
//                throw new ShardingException("Handler SQL Execution Event error: ", e);
//            }
//        }
        switch (transactionEvent.getOperationType()) {
            case COMMIT:
                if (Status.STATUS_ACTIVE != transactionManager.getStatus()) {
                    throw new ShardingException("No transaction begin in current thread connection");
                }
                try {
                    revertEngineMap.remove(transactionManager.getTransactionId());
                    transactionEvent.setSagaJson(sagaDefinitionBuilderMap.remove(transactionManager.getTransactionId()).build());
                    super.doInTransaction(transactionEvent);
                } catch (JsonProcessingException ignored) {
                    log.error("saga transaction", transactionManager.getTransactionId(), "commit failed, caused by json build exception: ", ignored);
                    return;
                }
                break;
            case ROLLBACK:
                sagaDefinitionBuilderMap.remove(transactionManager.getTransactionId());
                revertEngineMap.remove(transactionManager.getTransactionId());
                super.doInTransaction(transactionEvent);
                break;
            case BEGIN:
                if (Status.STATUS_NO_TRANSACTION == transactionManager.getStatus()) {
                    super.doInTransaction(transactionEvent);
                    SagaConfiguration config = transactionEvent.getSagaConfiguration();
                    sagaDefinitionBuilderMap.put(transactionManager.getTransactionId(),
                            new SagaDefinitionBuilder(config.getRecoveryPolicy().getName(), config.getTransactionMaxRetries(), config.getCompensationMaxRetries(), config.getTransactionRetryDelay()));
                    revertEngineMap.put(transactionManager.getTransactionId(), createRevertEngine(transactionEvent));
                }
                break;
            default:
        }
    }
    
    private RevertEngine createRevertEngine(final SagaTransactionEvent transactionEvent) {
        return SagaRecoveryPolicy.FORWARD == transactionEvent.getSagaConfiguration().getRecoveryPolicy() ? new EmptyRevertEngine() : new RevertEngineImpl(transactionEvent.getDataSourceMap());
    }
    
    private void handleSQLExecutionEvent(final SagaSQLExecutionEvent sqlExecutionEvent) throws SQLException {
        if (sqlExecutionEvent.isSameLogicSQL()) {
            //TODO generate revert sql by sql and params in event
            RevertResult result = revertEngineMap.get(sqlExecutionEvent.getTransactionId()).revert(sqlExecutionEvent.getRouteUnit().getDataSourceName(),
                sqlExecutionEvent.getRouteUnit().getSqlUnit().getSql(), sqlExecutionEvent.getRouteUnit().getSqlUnit().getParameterSets());
            sagaDefinitionBuilderMap.get(sqlExecutionEvent.getTransactionId()).addChildRequest(sqlExecutionEvent.getId(), sqlExecutionEvent.getRouteUnit().getDataSourceName(),
                sqlExecutionEvent.getRouteUnit().getSqlUnit().getSql(), copyList(sqlExecutionEvent.getRouteUnit().getSqlUnit().getParameterSets()),
                result.getRevertSQL(), result.getRevertSQLParams());
        } else {
            sagaDefinitionBuilderMap.get(sqlExecutionEvent.getTransactionId()).switchParents();
        }
    }
    
    private List<List<Object>> copyList(final List<List<Object>> origin) {
        List<List<Object>> result = new ArrayList<>();
        for (List<Object> each : origin) {
            result.add(Lists.newArrayList(each));
        }
        return result;
    }
}
