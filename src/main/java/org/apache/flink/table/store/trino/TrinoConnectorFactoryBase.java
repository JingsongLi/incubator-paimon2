/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.store.trino;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.store.file.catalog.CatalogFactory;
import org.apache.flink.table.store.filesystem.FileSystems;

import java.util.Map;

import io.airlift.bootstrap.Bootstrap;
import io.trino.plugin.base.CatalogName;
import io.trino.plugin.base.jmx.ConnectorObjectNameGeneratorModule;
import io.trino.spi.NodeManager;
import io.trino.spi.PageIndexerFactory;
import io.trino.spi.classloader.ThreadContextClassLoader;
import io.trino.spi.connector.Connector;
import io.trino.spi.connector.ConnectorContext;
import io.trino.spi.connector.ConnectorFactory;
import io.trino.spi.type.TypeManager;

/** Trino {@link ConnectorFactory}. */
public abstract class TrinoConnectorFactoryBase implements ConnectorFactory {
    @Override
    public String getName() {
        return "tablestore";
    }

    @Override
    public Connector create(
            String catalogName, Map<String, String> config, ConnectorContext context) {
        ClassLoader classLoader = TrinoConnectorFactoryBase.class.getClassLoader();
        try (ThreadContextClassLoader ignored = new ThreadContextClassLoader(classLoader)) {
            Bootstrap app = new Bootstrap(
                    new TrinoSecurityModule(),
                    binder -> {
                        binder.bind(NodeManager.class).toInstance(context.getNodeManager());
                        binder.bind(TypeManager.class).toInstance(context.getTypeManager());
                        binder.bind(PageIndexerFactory.class).toInstance(context.getPageIndexerFactory());
                        binder.bind(CatalogName.class).toInstance(new CatalogName(catalogName));
                    });
            app.doNotInitializeLogging().setOptionalConfigurationProperties(config).initialize();

            Configuration configuration = Configuration.fromMap(config);
            // initialize file system
            FileSystems.initialize(CatalogFactory.warehouse(configuration), configuration);
            return new TrinoConnector(
                    new TrinoMetadata(configuration),
                    new TrinoSplitManager(),
                    new TrinoPageSourceProvider());
        }
    }
}
