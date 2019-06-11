/*
 * Copyright (c) 2011-2019 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG.
 */
package com.terracottatech.dynamic_config.cli.command;

/**
 * @author Mathieu Carbou
 */
public interface DynamicConfigCommand extends Runnable {
  String name();

  /**
   * Run extra global validation after each parameter has been parsed, converted, injected and validated
   */
  void validate();
}
