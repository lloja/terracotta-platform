/*
 * Copyright Terracotta, Inc.
 *
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
 */
package org.terracotta.dynamic_config.server.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.dynamic_config.api.service.IParameterSubstitutor;

import java.nio.file.Path;
import java.util.Optional;

public class ConfigRepoStarter implements NodeStarter {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigRepoStarter.class);

  private final Options options;
  private final NodeStarter nextStarter;
  private final StartupManager startupManager;
  private final IParameterSubstitutor parameterSubstitutor;

  ConfigRepoStarter(Options options, StartupManager startupManager, NodeStarter nextStarter, IParameterSubstitutor parameterSubstitutor) {
    this.options = options;
    this.nextStarter = nextStarter;
    this.startupManager = startupManager;
    this.parameterSubstitutor = parameterSubstitutor;
  }

  @Override
  public boolean startNode() {
    Path repositoryDir = startupManager.getOrDefaultRepositoryDir(options.getNodeRepositoryDir());
    Optional<String> nodeName = startupManager.findNodeName(repositoryDir);
    if (nodeName.isPresent()) {
      return startupManager.startUsingConfigRepo(repositoryDir, nodeName.get(), options.wantsDiagnosticMode());
    }

    LOGGER.info("Did not find config repository at: " + parameterSubstitutor.substitute(repositoryDir));
    // Couldn't start node - pass the responsibility to the next starter
    return nextStarter.startNode();
  }
}
