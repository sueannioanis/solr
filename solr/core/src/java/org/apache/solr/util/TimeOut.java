/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.util;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.apache.solr.common.util.TimeSource;

/** Timeout tool to ease checking time left, time elapsed, and waiting on a condition. */
public class TimeOut {

  // Internally, the time unit is nanosecond.
  private final long timeoutAt, startTime;
  private final TimeSource timeSource;

  /**
   * @param timeout after this maximum time, {@link #hasTimedOut()} will return true.
   * @param unit the time unit of the timeout argument.
   * @param timeSource the source of the time.
   */
  public TimeOut(long timeout, TimeUnit unit, TimeSource timeSource) {
    // Since timeout is stored in nanoseconds, it cannot track more than Long.MAX_VALUE nanoseconds.
    // Depending on the time unit selected in this constructor, large timeout can be truncated when
    // converting to Long.MAX_VALUE nanoseconds.
    this.timeSource = timeSource;
    startTime = timeSource.getTimeNs();
    // Consider negative interval as 0.
    timeout = Math.max(timeout, 0L);
    // Detect long addition overflow.
    long timeoutAt = startTime + NANOSECONDS.convert(timeout, unit);
    this.timeoutAt = timeoutAt < startTime ? Long.MAX_VALUE : timeoutAt;
  }

  public boolean hasTimedOut() {
    return timeSource.getTimeNs() > timeoutAt;
  }

  public void sleep(long ms) throws InterruptedException {
    timeSource.sleep(ms);
  }

  public long timeLeft(TimeUnit unit) {
    return unit.convert(timeoutAt - timeSource.getTimeNs(), NANOSECONDS);
  }

  public long timeElapsed(TimeUnit unit) {
    return unit.convert(timeSource.getTimeNs() - startTime, NANOSECONDS);
  }

  /**
   * Wait until the given {@link Supplier} returns true or the timeout expires which ever happens
   * first
   *
   * @param messageOnTimeOut the exception message to be used in case a TimeoutException is thrown
   * @param supplier a {@link Supplier} that returns a {@link Boolean} value
   * @throws InterruptedException if any thread has interrupted the current thread
   * @throws TimeoutException if the timeout expires
   */
  public void waitFor(String messageOnTimeOut, Supplier<Boolean> supplier)
      throws InterruptedException, TimeoutException {
    while (!supplier.get() && !hasTimedOut()) {
      timeSource.sleep(250);
    }
    if (hasTimedOut()) throw new TimeoutException(messageOnTimeOut);
  }

  @Override
  public String toString() {
    return "TimeOut [timeoutAt="
        + timeoutAt
        + ", startTime="
        + startTime
        + ", timeSource="
        + timeSource
        + "]";
  }
}
