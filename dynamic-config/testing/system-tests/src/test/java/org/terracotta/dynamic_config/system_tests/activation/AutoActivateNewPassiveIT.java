/*
 * Copyright (c) 2011-2019 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG.
 */
package org.terracotta.dynamic_config.system_tests.activation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.terracotta.dynamic_config.system_tests.ClusterDefinition;
import org.terracotta.dynamic_config.system_tests.DynamicConfigIT;

import java.nio.file.Path;

import static com.tc.util.Assert.fail;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

@ClusterDefinition(stripes = 2, nodesPerStripe = 2, autoStart = false)
public class AutoActivateNewPassiveIT extends DynamicConfigIT {

  @Rule public final SystemOutRule out = new SystemOutRule().enableLog();
  @Rule public final SystemErrRule err = new SystemErrRule().enableLog();

  @Test
  public void test_auto_activation_failure_for_2x1_cluster() throws Exception {
    try {
      startNode(1, 1,
          "-f", copyConfigProperty("/config-property-files/2x1.properties").toString(),
          "-s", "localhost", "-p", String.valueOf(getNodePort()),
          "--node-repository-dir", "repository/stripe1/node-1-1");
      fail();
    } catch (Exception e) {
      assertThat(out.getLog(), containsString("Cannot start a pre-activated multistripe cluster"));
    }
  }

  @Test
  public void test_auto_activation_failure_for_2x2_cluster() throws Exception {
    try {
      startNode(1, 1,
          "-f", copyConfigProperty("/config-property-files/2x2.properties").toString(),
          "-s", "localhost", "-p", String.valueOf(getNodePort()),
          "--node-repository-dir", "repository/stripe1/node-1-1");
      fail();
    } catch (Exception e) {
      assertThat(out.getLog(), containsString("Cannot start a pre-activated multistripe cluster"));
    }
  }

  @Test
  public void test_auto_activation_success_for_1x1_cluster() throws Exception {
    Path configurationFile = copyConfigProperty("/config-property-files/1x1.properties");
    startNode(1, 1, "-f", configurationFile.toString(), "-s", "localhost", "-p", String.valueOf(getNodePort()), "--node-repository-dir", "repository/stripe1/node-1-1");
    waitUntil(out::getLog, containsString("Moved to State[ ACTIVE-COORDINATOR ]"));
  }

  @Test
  public void test_auto_activation_failure_for_different_1x2_cluster() throws Exception {
    startNode(1, 1, "-f", copyConfigProperty("/config-property-files/1x2.properties").toString(), "-s", "localhost", "-p", String.valueOf(getNodePort(1, 1)), "--node-repository-dir", "repository/stripe1/node-1-1");
    waitUntil(out::getLog, containsString("Moved to State[ ACTIVE-COORDINATOR ]"));

    err.clearLog();
    try {
      startNode(1, 2,
          "-f", copyConfigProperty("/config-property-files/1x2-diff.properties").toString(),
          "-s", "localhost", "-p", String.valueOf(getNodePort(1, 2)),
          "--node-repository-dir", "repository/stripe1/node-1-2");
      fail();
    } catch (Exception e) {
      assertThat(err.getLog(), containsString("Unable to find any change in active node matching the topology used to activate this passive node"));
    }
  }

  @Test
  public void test_auto_activation_success_for_1x2_cluster() throws Exception {
    Path configurationFile = copyConfigProperty("/config-property-files/1x2.properties");
    startNode(1, 1, "-f", configurationFile.toString(), "-s", "localhost", "-p", String.valueOf(getNodePort(1, 1)), "--node-repository-dir", "repository/stripe1/node-1-1");
    waitUntil(out::getLog, containsString("Moved to State[ ACTIVE-COORDINATOR ]"));

    startNode(1, 2, "-f", configurationFile.toString(), "-s", "localhost", "-p", String.valueOf(getNodePort(1, 2)), "--node-repository-dir", "repository/stripe1/node-1-2");
    waitUntil(out::getLog, containsString("Moved to State[ PASSIVE-STANDBY ]"));
  }
}