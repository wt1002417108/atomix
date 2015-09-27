/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.copycat.atomic;

import io.atomix.catalogue.client.Command;
import io.atomix.catalogue.client.Query;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Distributed atomic long.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class DistributedAtomicLong extends DistributedAtomicValue<Long> {
  private Long value;

  @Override
  public DistributedAtomicLong withDefaultCommandConsistency(Command.ConsistencyLevel consistency) {
    super.setDefaultCommandConsistency(consistency);
    return this;
  }

  @Override
  public DistributedAtomicLong withDefaultQueryConsistency(Query.ConsistencyLevel consistency) {
    super.setDefaultQueryConsistency(consistency);
    return this;
  }

  /**
   * Adds a delta to the long and returns the updated value.
   *
   * @param delta The delta to add.
   * @return A completable future to be completed with the result.
   */
  public CompletableFuture<Long> addAndGet(long delta) {
    return updateValue(v -> v + delta);
  }

  /**
   * Adds a delta to the value and returns the previous value.
   *
   * @param delta The delta to add.
   * @return A completable future to be completed with the result.
   */
  public CompletableFuture<Long> getAndAdd(long delta) {
    return updateValue(v -> v + delta).thenApply(v -> v - delta);
  }

  /**
   * Increments the value and returns the updated value.
   *
   * @return A completable future to be completed with the result.
   */
  public CompletableFuture<Long> incrementAndGet() {
    return updateValue(v -> v + 1);
  }

  /**
   * Decrements the value and returns the updated value.
   *
   * @return A completable future to be completed with the result.
   */
  public CompletableFuture<Long> decrementAndGet() {
    return updateValue(v -> v - 1);
  }

  /**
   * Increments the value and returns the previous value.
   *
   * @return A completable future to be completed with the result.
   */
  public CompletableFuture<Long> getAndIncrement() {
    return updateValue(v -> v + 1).thenApply(v -> v - 1);
  }

  /**
   * Decrements the value and returns the previous value.
   *
   * @return A completable future to be completed with the result.
   */
  public CompletableFuture<Long> getAndDecrement() {
    return updateValue(v -> v - 1).thenApply(v -> v + 1);
  }

  /**
   * Returns the current value.
   *
   * @return A completable future to be completed with the current value.
   */
  private CompletableFuture<Long> getValue() {
    if (value != null)
      return CompletableFuture.completedFuture(value);
    return get();
  }

  /**
   * Recursively attempts to update the atomic value.
   */
  private CompletableFuture<Long> updateValue(Function<Long, Long> updater) {
    return updateValue(updater, new CompletableFuture<>());
  }

  /**
   * Recursively attempts to update the atomic value.
   */
  private CompletableFuture<Long> updateValue(Function<Long, Long> updater, CompletableFuture<Long> future) {
    getValue().whenComplete((expectedResult, expectedError) -> {
      if (expectedError == null) {
        long updatedValue = updater.apply(expectedResult);
        compareAndSet(expectedResult, updatedValue).whenComplete((updateResult, updateError) -> {
          if (updateError == null) {
            if (updateResult) {
              value = updatedValue;
              future.complete(updatedValue);
            } else {
              value = null;
              updateValue(updater, future);
            }
          } else {
            future.completeExceptionally(updateError);
          }
        });
      } else {
        future.completeExceptionally(expectedError);
      }
    });
    return future;
  }

}
