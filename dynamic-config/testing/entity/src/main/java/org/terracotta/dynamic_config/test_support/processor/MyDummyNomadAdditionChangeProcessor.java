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
package org.terracotta.dynamic_config.test_support.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.dynamic_config.api.model.Cluster;
import org.terracotta.dynamic_config.api.model.NodeContext;
import org.terracotta.dynamic_config.api.model.nomad.NodeAdditionNomadChange;
import org.terracotta.dynamic_config.api.service.ClusterValidator;
import org.terracotta.dynamic_config.api.service.TopologyService;
import org.terracotta.dynamic_config.server.api.DynamicConfigEventFiring;
import org.terracotta.dynamic_config.server.api.NomadChangeProcessor;
import org.terracotta.monitoring.PlatformService;
import org.terracotta.nomad.server.NomadException;

import javax.management.JMException;
import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.stream.Stream;

import static com.tc.management.beans.L2MBeanNames.TOPOLOGY_MBEAN;
import static java.util.Objects.requireNonNull;

public class MyDummyNomadAdditionChangeProcessor implements NomadChangeProcessor<NodeAdditionNomadChange> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MyDummyNomadAdditionChangeProcessor.class);
  private static final String PLATFORM_MBEAN_OPERATION_NAME = "addPassive";
  private static final String failAtPrepare = "prepareAddition-failure";
  private static final String killAtPrepare = "killAddition-prepare";
  private static final String killAtCommit = "killAddition-commit";
  private static final String failoverKey = "failoverAddition";
  private static final String attachStatusKey = "attachStatus";
  private final TopologyService topologyService;
  private final DynamicConfigEventFiring dynamicConfigEventFiring;
  private final PlatformService platformService;
  private final MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

  public MyDummyNomadAdditionChangeProcessor(TopologyService topologyService, DynamicConfigEventFiring dynamicConfigEventFiring, PlatformService platformService) {
    this.topologyService = requireNonNull(topologyService);
    this.dynamicConfigEventFiring = requireNonNull(dynamicConfigEventFiring);
    this.platformService = platformService;
  }

  @Override
  public void validate(NodeContext baseConfig, NodeAdditionNomadChange change) throws NomadException {
    if (failAtPrepare.equals(topologyService.getUpcomingNodeContext().getNode().getTcProperties().orDefault().get(attachStatusKey))) {
      throw new NomadException("Invalid addition fail at prepare");
    }

    LOGGER.info("Validating change: {}", change.getSummary());
    if (baseConfig == null) {
      throw new NomadException("Existing config must not be null");
    }
    try {
      checkMBeanOperation();
      Cluster updated = change.apply(baseConfig.getCluster());
      new ClusterValidator(updated).validate();
    } catch (RuntimeException e) {
      throw new NomadException("Error when trying to apply: '" + change.getSummary() + "': " + e.getMessage(), e);
    }

    // cause failure when in prepare phase
    if (killAtPrepare.equals(topologyService.getUpcomingNodeContext().getNode().getTcProperties().orDefault().get(failoverKey))) {
      platformService.stopPlatform();
    }
  }

  @Override
  public void apply(NodeAdditionNomadChange change) throws NomadException {
    Cluster runtime = topologyService.getRuntimeNodeContext().getCluster();
    if (runtime.containsNode(change.getNodeAddress())) {
      return;
    }

    // cause failover when in commit phase
    if (killAtCommit.equals(topologyService.getUpcomingNodeContext().getNode().getTcProperties().orDefault().get(failoverKey))) {
      platformService.stopPlatform();
    }

    try {
      LOGGER.info("Adding node: {} to stripe ID: {}", change.getNode().getName(), change.getStripeId());

      InetSocketAddress addr = change.getNode().getInternalAddress();
      LOGGER.debug("Calling mBean {}#{}({}))", TOPOLOGY_MBEAN, PLATFORM_MBEAN_OPERATION_NAME, addr);
      mbeanServer.invoke(
          TOPOLOGY_MBEAN,
          PLATFORM_MBEAN_OPERATION_NAME,
          new Object[]{addr.toString()},
          new String[]{String.class.getName()}
      );

      dynamicConfigEventFiring.onNodeAddition(change.getStripeId(), change.getNode());
    } catch (RuntimeException | JMException e) {
      throw new NomadException("Error when applying: '" + change.getSummary() + "': " + e.getMessage(), e);
    }
  }

  private void checkMBeanOperation() {
    boolean canCall;
    try {
      canCall = Stream
          .of(mbeanServer.getMBeanInfo(TOPOLOGY_MBEAN).getOperations())
          .anyMatch(attr -> PLATFORM_MBEAN_OPERATION_NAME.equals(attr.getName()));
    } catch (JMException e) {
      LOGGER.error("MBeanServer::getMBeanInfo resulted in:", e);
      canCall = false;
    }
    if (!canCall) {
      throw new IllegalStateException("Unable to invoke MBean operation to attach a node");
    }
  }
}
