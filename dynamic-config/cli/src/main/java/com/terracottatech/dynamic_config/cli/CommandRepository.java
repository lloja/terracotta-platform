/*
 * Copyright (c) 2011-2019 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG.
 */
package com.terracottatech.dynamic_config.cli;

import com.terracottatech.dynamic_config.cli.service.command.Command;
import com.terracottatech.dynamic_config.cli.service.command.MainCommand;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class CommandRepository {
  private static final Map<String, Command> commandMap = new LinkedHashMap<>();

  private CommandRepository() {}

  public static void addAll(Set<Command> commands) {
    commands.forEach(command -> commandMap.put(Metadata.getName(command), command));
  }

  public static Collection<Command> getCommands() {
    return Collections.unmodifiableCollection(commandMap.values());
  }

  public static Command getCommand(String name) {
    if (!commandMap.containsKey(name)) {
      throw new IllegalArgumentException("Command not found: " + name);
    }
    return commandMap.get(name);
  }

  public static MainCommand getMainCommand() {
    return (MainCommand) getCommand("main");
  }

  public static void inject(Object... services) {
    commandMap.values().forEach(command -> Injector.inject(command, services));
  }
}
