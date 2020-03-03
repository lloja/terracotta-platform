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
package org.terracotta.dynamic_config.server.nomad.persistence;

import org.terracotta.nomad.server.ChangeRequest;
import org.terracotta.nomad.server.ChangeRequestState;
import org.terracotta.nomad.server.NomadServerMode;
import org.terracotta.nomad.server.NomadServerRequest;
import org.terracotta.nomad.server.state.NomadStateChange;
import org.terracotta.persistence.sanskrit.MutableSanskritObject;
import org.terracotta.persistence.sanskrit.Sanskrit;
import org.terracotta.persistence.sanskrit.SanskritException;
import org.terracotta.persistence.sanskrit.SanskritObject;
import org.terracotta.persistence.sanskrit.change.SanskritChange;
import org.terracotta.persistence.sanskrit.change.SanskritChangeBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.CHANGE_CREATION_HOST;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.CHANGE_CREATION_TIMESTAMP;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.CHANGE_CREATION_USER;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.CHANGE_OPERATION;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.CHANGE_RESULT_HASH;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.CHANGE_STATE;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.CHANGE_VERSION;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.CURRENT_VERSION;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.HIGHEST_VERSION;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.LAST_MUTATION_HOST;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.LAST_MUTATION_TIMESTAMP;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.LAST_MUTATION_USER;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.LATEST_CHANGE_UUID;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.MODE;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.PREV_CHANGE_UUID;
import static org.terracotta.dynamic_config.server.nomad.persistence.NomadSanskritKeys.REQUEST;

public class SanskritNomadStateChange<T> implements NomadStateChange<T> {
  private final Sanskrit sanskrit;
  private final SanskritChangeBuilder changeBuilder;
  private final HashComputer<T> hashComputer;
  private volatile Long changeVersion;
  private volatile T changeResult;

  public SanskritNomadStateChange(Sanskrit sanskrit, SanskritChangeBuilder changeBuilder, HashComputer<T> hashComputer) {
    this.sanskrit = sanskrit;
    this.changeBuilder = changeBuilder;
    this.hashComputer = hashComputer;
  }

  @Override
  public NomadStateChange<T> setInitialized() {
    setMode(NomadServerMode.ACCEPTING);
    return this;
  }

  @Override
  public NomadStateChange<T> setMode(NomadServerMode mode) {
    changeBuilder.setString(MODE, mode.name());
    return this;
  }

  @Override
  public NomadStateChange<T> setRequest(NomadServerRequest request) {
    changeBuilder.setString(REQUEST, request.name());
    return this;
  }

  @Override
  public NomadStateChange<T> setLatestChangeUuid(UUID changeUuid) {
    changeBuilder.setString(LATEST_CHANGE_UUID, changeUuid.toString());
    return this;
  }

  @Override
  public NomadStateChange<T> setCurrentVersion(long versionNumber) {
    changeBuilder.setLong(CURRENT_VERSION, versionNumber);
    return this;
  }

  @Override
  public NomadStateChange<T> setHighestVersion(long versionNumber) {
    changeBuilder.setLong(HIGHEST_VERSION, versionNumber);
    return this;
  }

  @Override
  public NomadStateChange<T> setLastMutationHost(String lastMutationHost) {
    changeBuilder.setString(LAST_MUTATION_HOST, lastMutationHost);
    return this;
  }

  @Override
  public NomadStateChange<T> setLastMutationUser(String lastMutationUser) {
    changeBuilder.setString(LAST_MUTATION_USER, lastMutationUser);
    return this;
  }

  @Override
  public NomadStateChange<T> setLastMutationTimestamp(Instant lastMutationTimestamp) {
    changeBuilder.setString(LAST_MUTATION_TIMESTAMP, lastMutationTimestamp.toString());
    return this;
  }

  @Override
  public NomadStateChange<T> createChange(UUID changeUuid, ChangeRequest<T> changeRequest) {
    changeVersion = changeRequest.getVersion();
    changeResult = changeRequest.getChangeResult();
    String resultHash = hashComputer.computeHash(changeResult);

    MutableSanskritObject child = sanskrit.newMutableSanskritObject();
    child.setString(CHANGE_STATE, changeRequest.getState().name());
    child.setLong(CHANGE_VERSION, changeRequest.getVersion());
    if (changeRequest.getPrevChangeId() != null) {
      child.setString(PREV_CHANGE_UUID, changeRequest.getPrevChangeId());
    }
    child.setExternal(CHANGE_OPERATION, changeRequest.getChange());
    child.setString(CHANGE_RESULT_HASH, resultHash);
    child.setString(CHANGE_CREATION_HOST, changeRequest.getCreationHost());
    child.setString(CHANGE_CREATION_USER, changeRequest.getCreationUser());
    child.setString(CHANGE_CREATION_TIMESTAMP, changeRequest.getCreationTimestamp().toString());

    changeBuilder.setObject(changeUuid.toString(), child);

    return this;
  }

  @Override
  public NomadStateChange<T> updateChangeRequestState(UUID changeUuid, ChangeRequestState newState) {
    String uuidString = changeUuid.toString();
    SanskritObject existing = getObject(uuidString);
    MutableSanskritObject updated = sanskrit.newMutableSanskritObject();
    existing.accept(updated);

    updated.setString(CHANGE_STATE, newState.name());
    changeBuilder.setObject(uuidString, updated);
    return this;
  }

  public SanskritChange getSanskritChange() {
    return changeBuilder.build();
  }

  public Long getChangeVersion() {
    return changeVersion;
  }

  public T getChangeResult() {
    return changeResult;
  }

  private SanskritObject getObject(String key) {
    try {
      return sanskrit.getObject(key);
    } catch (SanskritException e) {
      throw new RuntimeException(e);
    }
  }
}
