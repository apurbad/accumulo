/**
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
package org.apache.accumulo.core.iterators.predicates;

import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.Predicate;

/**
 * TimestampRangeFilter is used to determine whether a Key/Value pair falls within a timestamp range
 */
public class TimestampRangePredicate implements Predicate<Key,Value> {
  
  private final long startTimestamp;
  private final long endTimestamp;
  
  public long getStartTimestamp() {
    return startTimestamp;
  }
  
  public long getEndTimestamp() {
    return endTimestamp;
  }
  
  /**
   * @param startTimestamp
   *          - inclusive first allowable timestamp
   * @param endTimestamp
   *          - inclusive last allowable timestamp
   */
  public TimestampRangePredicate(long startTimestamp, long endTimestamp) {
    super();
    this.startTimestamp = startTimestamp;
    this.endTimestamp = endTimestamp;
  }
  
  /**
   * return true IFF the key falls within the timestamp range
   */
  @Override
  public boolean evaluate(Key k, Value v) {
    long timestamp = k.getTimestamp();
    return timestamp >= startTimestamp && timestamp <= endTimestamp;
  }
  
  @Override
  public String toString() {
    return "{" + startTimestamp + "-" + endTimestamp + "}";
  }
}