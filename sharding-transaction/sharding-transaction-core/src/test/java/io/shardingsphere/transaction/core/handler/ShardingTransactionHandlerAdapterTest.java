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

package io.shardingsphere.transaction.core.handler;

import io.shardingsphere.core.constant.DatabaseType;
import io.shardingsphere.transaction.api.TransactionType;
import io.shardingsphere.transaction.core.TransactionOperationType;
import io.shardingsphere.transaction.core.manager.ShardingTransactionManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ShardingTransactionHandlerAdapterTest {
    
    private FixedShardingTransactionHandler fixedShardingTransactionHandler = new FixedShardingTransactionHandler();
    
    private ShardingTransactionManager shardingTransactionManager = fixedShardingTransactionHandler.getShardingTransactionManager();
    
    @Test
    public void assertDoTransactionBegin() {
        fixedShardingTransactionHandler.doInTransaction(TransactionOperationType.BEGIN);
        verify(shardingTransactionManager).begin();
    }
    
    @Test
    public void assertDoTransactionCommit() {
        fixedShardingTransactionHandler.doInTransaction(TransactionOperationType.COMMIT);
        verify(shardingTransactionManager).commit();
    }
    
    @Test
    public void assertDoTransactionRollback() {
        fixedShardingTransactionHandler.doInTransaction(TransactionOperationType.ROLLBACK);
        verify(shardingTransactionManager).rollback();
    }
    
    @Test
    public void assertGetConnection() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        fixedShardingTransactionHandler.createConnection("test", dataSource);
        verify(dataSource).getConnection();
    }
    
    private static final class FixedShardingTransactionHandler extends ShardingTransactionHandlerAdapter {
        
        private ShardingTransactionManager shardingTransactionManager = mock(ShardingTransactionManager.class);
        
        @Override
        public ShardingTransactionManager getShardingTransactionManager() {
            return shardingTransactionManager;
        }
        
        @Override
        public TransactionType getTransactionType() {
            return null;
        }
        
        @Override
        public void registerTransactionalResource(final DatabaseType databaseType, final Map<String, DataSource> dataSourceMap) {
        
        }
        
        @Override
        public void clearTransactionalResource(final Map<String, DataSource> dataSourceMap) {
        
        }
    }
}
