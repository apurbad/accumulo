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
package org.apache.accumulo.core.security;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulo namespace permissions. Each permission has an associated byte ID.
 */
public enum NamespacePermission {
  /*
   * One may add new permissions, but new permissions must use new numbers.
   * Current numbers in use must not be changed.
   */
  READ((byte) 0),
  WRITE((byte) 1),
  ALTER_NAMESPACE((byte) 2),
  GRANT((byte) 3),
  ALTER_TABLE((byte) 4),
  CREATE_TABLE((byte) 5),
  DROP_TABLE((byte) 6);

  final private byte permID;

  final private static NamespacePermission mapping[] = new NamespacePermission[8];
  static {
    for (NamespacePermission perm : NamespacePermission.values())
      mapping[perm.permID] = perm;
  }

  private NamespacePermission(byte id) {
    this.permID = id;
  }

  /**
   * Gets the byte ID of this permission.
   *
   * @return byte ID
   */
  public byte getId() {
    return this.permID;
  }

  /**
   * Returns a list of printable permission values.
   *
   * @return list of namespace permission values, as "Namespace." + permission name
   */
  public static List<String> printableValues() {
    NamespacePermission[] a = NamespacePermission.values();

    List<String> list = new ArrayList<String>(a.length);

    for (NamespacePermission p : a)
      list.add("Namespace." + p);

    return list;
  }

  /**
   * Gets the permission matching the given byte ID.
   *
   * @param id byte ID
   * @return system permission
   * @throws IndexOutOfBoundsException if the byte ID is invalid
   */
  public static NamespacePermission getPermissionById(byte id) {
    NamespacePermission result = mapping[id];
    if (result != null)
      return result;
    throw new IndexOutOfBoundsException("No such permission");
  }

}
