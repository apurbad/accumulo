/**
 * Autogenerated by Thrift Compiler (0.8.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package org.apache.accumulo.core.master.thrift;


import java.util.Map;
import java.util.HashMap;
import org.apache.thrift.TEnum;

@SuppressWarnings("all") public enum TableOperation implements org.apache.thrift.TEnum {
  CREATE(0),
  CLONE(1),
  DELETE(2),
  RENAME(3),
  ONLINE(4),
  OFFLINE(5),
  MERGE(6),
  DELETE_RANGE(7),
  BULK_IMPORT(8),
  COMPACT(9),
  IMPORT(10),
  EXPORT(11);

  private final int value;

  private TableOperation(int value) {
    this.value = value;
  }

  /**
   * Get the integer value of this enum value, as defined in the Thrift IDL.
   */
  public int getValue() {
    return value;
  }

  /**
   * Find a the enum type by its integer value, as defined in the Thrift IDL.
   * @return null if the value is not found.
   */
  public static TableOperation findByValue(int value) { 
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CLONE;
      case 2:
        return DELETE;
      case 3:
        return RENAME;
      case 4:
        return ONLINE;
      case 5:
        return OFFLINE;
      case 6:
        return MERGE;
      case 7:
        return DELETE_RANGE;
      case 8:
        return BULK_IMPORT;
      case 9:
        return COMPACT;
      case 10:
        return IMPORT;
      case 11:
        return EXPORT;
      default:
        return null;
    }
  }
}
