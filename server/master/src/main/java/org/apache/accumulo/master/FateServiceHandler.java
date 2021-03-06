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
package org.apache.accumulo.master;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.client.NamespaceNotFoundException;
import org.apache.accumulo.core.client.admin.TableOperationsImpl;
import org.apache.accumulo.core.client.admin.TimeType;
import org.apache.accumulo.core.client.impl.Namespaces;
import org.apache.accumulo.core.client.impl.Tables;
import org.apache.accumulo.core.client.impl.thrift.SecurityErrorCode;
import org.apache.accumulo.core.client.impl.thrift.TableOperation;
import org.apache.accumulo.core.client.impl.thrift.TableOperationExceptionType;
import org.apache.accumulo.core.client.impl.thrift.ThriftSecurityException;
import org.apache.accumulo.core.client.impl.thrift.ThriftTableOperationException;
import org.apache.accumulo.core.iterators.IteratorUtil;
import org.apache.accumulo.core.master.thrift.FateOperation;
import org.apache.accumulo.core.master.thrift.FateService;
import org.apache.accumulo.core.security.thrift.TCredentials;
import org.apache.accumulo.core.util.ArgumentChecker.Validator;
import org.apache.accumulo.core.util.ByteBufferUtil;
import org.apache.accumulo.fate.TStore.TStatus;
import org.apache.accumulo.master.tableOps.BulkImport;
import org.apache.accumulo.master.tableOps.CancelCompactions;
import org.apache.accumulo.master.tableOps.ChangeTableState;
import org.apache.accumulo.master.tableOps.CloneTable;
import org.apache.accumulo.master.tableOps.CompactRange;
import org.apache.accumulo.master.tableOps.CreateNamespace;
import org.apache.accumulo.master.tableOps.CreateTable;
import org.apache.accumulo.master.tableOps.DeleteNamespace;
import org.apache.accumulo.master.tableOps.DeleteTable;
import org.apache.accumulo.master.tableOps.ExportTable;
import org.apache.accumulo.master.tableOps.ImportTable;
import org.apache.accumulo.master.tableOps.RenameNamespace;
import org.apache.accumulo.master.tableOps.RenameTable;
import org.apache.accumulo.master.tableOps.TableRangeOp;
import org.apache.accumulo.master.tableOps.TraceRepo;
import org.apache.accumulo.server.client.ClientServiceHandler;
import org.apache.accumulo.server.master.state.MergeInfo;
import org.apache.accumulo.server.util.TablePropUtil;
import org.apache.accumulo.trace.thrift.TInfo;
import org.apache.hadoop.io.Text;
import org.apache.log4j.Logger;

/**
 * 
 */
class FateServiceHandler implements FateService.Iface {

  protected final Master master;
  protected static final Logger log = Master.log;

  public FateServiceHandler(Master master) {
    this.master = master;
  }

  @Override
  public long beginFateOperation(TInfo tinfo, TCredentials credentials) throws ThriftSecurityException {
    authenticate(credentials);
    return master.fate.startTransaction();
  }

  @Override
  public void executeFateOperation(TInfo tinfo, TCredentials c, long opid, FateOperation op, List<ByteBuffer> arguments, Map<String,String> options,
      boolean autoCleanup) throws ThriftSecurityException, ThriftTableOperationException {
    authenticate(c);

    switch (op) {
      case NAMESPACE_CREATE: {
        TableOperation tableOp = TableOperation.CREATE;
        String namespace = validateNamespaceArgument(arguments.get(0), tableOp, null);

        if (!master.security.canCreateNamespace(c, namespace))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        master.fate.seedTransaction(opid, new TraceRepo<Master>(new CreateNamespace(c.getPrincipal(), namespace, options)), autoCleanup);
        break;
      }
      case NAMESPACE_RENAME: {
        TableOperation tableOp = TableOperation.RENAME;
        String oldName = validateNamespaceArgument(arguments.get(0), tableOp, Namespaces.NOT_DEFAULT.and(Namespaces.NOT_ACCUMULO));
        String newName = validateNamespaceArgument(arguments.get(1), tableOp, null);

        String namespaceId = ClientServiceHandler.checkNamespaceId(master.getInstance(), oldName, tableOp);
        if (!master.security.canRenameNamespace(c, namespaceId, oldName, newName))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        master.fate.seedTransaction(opid, new TraceRepo<Master>(new RenameNamespace(namespaceId, oldName, newName)), autoCleanup);
        break;
      }
      case NAMESPACE_DELETE: {
        TableOperation tableOp = TableOperation.DELETE;
        String namespace = validateNamespaceArgument(arguments.get(0), tableOp, Namespaces.NOT_DEFAULT.and(Namespaces.NOT_ACCUMULO));

        String namespaceId = ClientServiceHandler.checkNamespaceId(master.getInstance(), namespace, tableOp);
        if (!master.security.canDeleteNamespace(c, namespaceId))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        master.fate.seedTransaction(opid, new TraceRepo<Master>(new DeleteNamespace(namespaceId)), autoCleanup);
        break;
      }
      case TABLE_CREATE: {
        TableOperation tableOp = TableOperation.CREATE;
        String tableName = validateTableNameArgument(arguments.get(0), tableOp, Tables.NOT_SYSTEM);
        TimeType timeType = TimeType.valueOf(ByteBufferUtil.toString(arguments.get(1)));

        if (!master.security.canCreateTable(c, tableName))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        try {
          master.fate.seedTransaction(opid, new TraceRepo<Master>(new CreateTable(c.getPrincipal(), tableName, timeType, options)), autoCleanup);
        } catch (NamespaceNotFoundException e) {
          throw new ThriftTableOperationException(null, tableName, tableOp, TableOperationExceptionType.NAMESPACE_NOTFOUND, "");
        }
        break;
      }
      case TABLE_RENAME: {
        TableOperation tableOp = TableOperation.RENAME;
        final String oldTableName = validateTableNameArgument(arguments.get(0), tableOp, Tables.NOT_SYSTEM);
        String newTableName = validateTableNameArgument(arguments.get(1), tableOp, new Validator<String>() {

          @Override
          public boolean isValid(String argument) {
            // verify they are in the same namespace
            String oldNamespace = Tables.qualify(oldTableName).getFirst();
            return oldNamespace.equals(Tables.qualify(argument, oldNamespace).getFirst());
          }

          @Override
          public String invalidMessage(String argument) {
            return "Cannot move tables to a new namespace by renaming. The namespace for " + oldTableName + " does not match " + argument;
          }

        });

        String tableId = ClientServiceHandler.checkTableId(master.getInstance(), oldTableName, tableOp);

        if (!master.security.canRenameTable(c, tableId, oldTableName, newTableName))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        try {
          master.fate.seedTransaction(opid, new TraceRepo<Master>(new RenameTable(tableId, oldTableName, newTableName)), autoCleanup);
        } catch (NamespaceNotFoundException e) {
          throw new ThriftTableOperationException(null, oldTableName, tableOp, TableOperationExceptionType.NAMESPACE_NOTFOUND, "");
        }

        break;
      }
      case TABLE_CLONE: {
        TableOperation tableOp = TableOperation.CLONE;
        String srcTableId = validateTableIdArgument(arguments.get(0), tableOp, null);
        String tableName = validateTableNameArgument(arguments.get(1), tableOp, Tables.NOT_SYSTEM);

        if (!master.security.canCloneTable(c, srcTableId, tableName))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        Map<String,String> propertiesToSet = new HashMap<String,String>();
        Set<String> propertiesToExclude = new HashSet<String>();

        for (Entry<String,String> entry : options.entrySet()) {
          if (entry.getKey().startsWith(TableOperationsImpl.CLONE_EXCLUDE_PREFIX)) {
            propertiesToExclude.add(entry.getKey().substring(TableOperationsImpl.CLONE_EXCLUDE_PREFIX.length()));
            continue;
          }

          if (!TablePropUtil.isPropertyValid(entry.getKey(), entry.getValue())) {
            throw new ThriftTableOperationException(null, tableName, tableOp, TableOperationExceptionType.OTHER, "Property or value not valid "
                + entry.getKey() + "=" + entry.getValue());
          }

          propertiesToSet.put(entry.getKey(), entry.getValue());
        }

        master.fate.seedTransaction(opid, new TraceRepo<Master>(new CloneTable(c.getPrincipal(), srcTableId, tableName, propertiesToSet, propertiesToExclude)),
            autoCleanup);

        break;
      }
      case TABLE_DELETE: {
        TableOperation tableOp = TableOperation.DELETE;
        String tableName = validateTableNameArgument(arguments.get(0), tableOp, Tables.NOT_SYSTEM);

        final String tableId = ClientServiceHandler.checkTableId(master.getInstance(), tableName, tableOp);
        if (!master.security.canDeleteTable(c, tableId))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);
        master.fate.seedTransaction(opid, new TraceRepo<Master>(new DeleteTable(tableId)), autoCleanup);
        break;
      }
      case TABLE_ONLINE: {
        TableOperation tableOp = TableOperation.ONLINE;
        final String tableId = validateTableIdArgument(arguments.get(0), tableOp, Tables.NOT_ROOT_ID);

        if (!master.security.canOnlineOfflineTable(c, tableId, op))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        master.fate.seedTransaction(opid, new TraceRepo<Master>(new ChangeTableState(tableId, tableOp)), autoCleanup);
        break;
      }
      case TABLE_OFFLINE: {
        TableOperation tableOp = TableOperation.OFFLINE;
        final String tableId = validateTableIdArgument(arguments.get(0), tableOp, Tables.NOT_ROOT_ID);

        if (!master.security.canOnlineOfflineTable(c, tableId, op))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        master.fate.seedTransaction(opid, new TraceRepo<Master>(new ChangeTableState(tableId, tableOp)), autoCleanup);
        break;
      }
      case TABLE_MERGE: {
        TableOperation tableOp = TableOperation.MERGE;
        String tableName = validateTableNameArgument(arguments.get(0), tableOp, null);
        Text startRow = ByteBufferUtil.toText(arguments.get(1));
        Text endRow = ByteBufferUtil.toText(arguments.get(2));

        final String tableId = ClientServiceHandler.checkTableId(master.getInstance(), tableName, tableOp);
        if (!master.security.canMerge(c, tableId))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        Master.log.debug("Creating merge op: " + tableId + " " + startRow + " " + endRow);
        master.fate.seedTransaction(opid, new TraceRepo<Master>(new TableRangeOp(MergeInfo.Operation.MERGE, tableId, startRow, endRow)), autoCleanup);
        break;
      }
      case TABLE_DELETE_RANGE: {
        TableOperation tableOp = TableOperation.DELETE_RANGE;
        String tableName = validateTableNameArgument(arguments.get(0), tableOp, Tables.NOT_SYSTEM);
        Text startRow = ByteBufferUtil.toText(arguments.get(1));
        Text endRow = ByteBufferUtil.toText(arguments.get(2));

        final String tableId = ClientServiceHandler.checkTableId(master.getInstance(), tableName, tableOp);
        if (!master.security.canDeleteRange(c, tableId, tableName, startRow, endRow))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        master.fate.seedTransaction(opid, new TraceRepo<Master>(new TableRangeOp(MergeInfo.Operation.DELETE, tableId, startRow, endRow)), autoCleanup);
        break;
      }
      case TABLE_BULK_IMPORT: {
        TableOperation tableOp = TableOperation.BULK_IMPORT;
        String tableName = validateTableNameArgument(arguments.get(0), tableOp, Tables.NOT_SYSTEM);
        String dir = ByteBufferUtil.toString(arguments.get(1));
        String failDir = ByteBufferUtil.toString(arguments.get(2));
        boolean setTime = Boolean.parseBoolean(ByteBufferUtil.toString(arguments.get(3)));

        final String tableId = ClientServiceHandler.checkTableId(master.getInstance(), tableName, tableOp);
        if (!master.security.canBulkImport(c, tableId, tableName, dir, failDir))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        master.fate.seedTransaction(opid, new TraceRepo<Master>(new BulkImport(tableId, dir, failDir, setTime)), autoCleanup);
        break;
      }
      case TABLE_COMPACT: {
        TableOperation tableOp = TableOperation.COMPACT;
        String tableId = validateTableIdArgument(arguments.get(0), tableOp, null);
        byte[] startRow = ByteBufferUtil.toBytes(arguments.get(1));
        byte[] endRow = ByteBufferUtil.toBytes(arguments.get(2));
        List<IteratorSetting> iterators = IteratorUtil.decodeIteratorSettings(ByteBufferUtil.toBytes(arguments.get(3)));

        if (!master.security.canCompact(c, tableId))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        master.fate.seedTransaction(opid, new TraceRepo<Master>(new CompactRange(tableId, startRow, endRow, iterators)), autoCleanup);
        break;
      }
      case TABLE_CANCEL_COMPACT: {
        TableOperation tableOp = TableOperation.COMPACT_CANCEL;
        String tableId = validateTableIdArgument(arguments.get(0), tableOp, null);

        if (!master.security.canCompact(c, tableId))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        master.fate.seedTransaction(opid, new TraceRepo<Master>(new CancelCompactions(tableId)), autoCleanup);
        break;
      }
      case TABLE_IMPORT: {
        TableOperation tableOp = TableOperation.IMPORT;
        String tableName = validateTableNameArgument(arguments.get(0), tableOp, Tables.NOT_SYSTEM);
        String exportDir = ByteBufferUtil.toString(arguments.get(1));

        if (!master.security.canImport(c, tableName, exportDir))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        try {
          master.fate.seedTransaction(opid, new TraceRepo<Master>(new ImportTable(c.getPrincipal(), tableName, exportDir)), autoCleanup);
        } catch (NamespaceNotFoundException e) {
          throw new ThriftTableOperationException(null, tableName, tableOp, TableOperationExceptionType.NAMESPACE_NOTFOUND, "");
        }
        break;
      }
      case TABLE_EXPORT: {
        TableOperation tableOp = TableOperation.EXPORT;
        String tableName = validateTableNameArgument(arguments.get(0), tableOp, Tables.NOT_SYSTEM);
        String exportDir = ByteBufferUtil.toString(arguments.get(1));

        String tableId = ClientServiceHandler.checkTableId(master.getInstance(), tableName, tableOp);
        if (!master.security.canExport(c, tableId, tableName, exportDir))
          throw new ThriftSecurityException(c.getPrincipal(), SecurityErrorCode.PERMISSION_DENIED);

        master.fate.seedTransaction(opid, new TraceRepo<Master>(new ExportTable(tableName, tableId, exportDir)), autoCleanup);
        break;
      }
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public String waitForFateOperation(TInfo tinfo, TCredentials credentials, long opid) throws ThriftSecurityException, ThriftTableOperationException {
    authenticate(credentials);

    TStatus status = master.fate.waitForCompletion(opid);
    if (status == TStatus.FAILED) {
      Exception e = master.fate.getException(opid);
      if (e instanceof ThriftTableOperationException)
        throw (ThriftTableOperationException) e;
      else if (e instanceof ThriftSecurityException)
        throw (ThriftSecurityException) e;
      else if (e instanceof RuntimeException)
        throw (RuntimeException) e;
      else
        throw new RuntimeException(e);
    }

    String ret = master.fate.getReturn(opid);
    if (ret == null)
      ret = ""; // thrift does not like returning null
    return ret;
  }

  @Override
  public void finishFateOperation(TInfo tinfo, TCredentials credentials, long opid) throws ThriftSecurityException {
    authenticate(credentials);
    master.fate.delete(opid);
  }

  protected void authenticate(TCredentials credentials) throws ThriftSecurityException {
    // this is a bit redundant, the credentials of the caller (the first arg) will throw an exception if it fails to authenticate
    // before the second arg is checked (which would return true or false)
    if (!master.security.authenticateUser(credentials, credentials))
      throw new ThriftSecurityException(credentials.getPrincipal(), SecurityErrorCode.BAD_CREDENTIALS);
  }

  // Verify table name arguments are valid, and match any additional restrictions
  private String validateTableIdArgument(ByteBuffer tableIdArg, TableOperation op, Validator<String> userValidator) throws ThriftTableOperationException {
    String tableId = tableIdArg == null ? null : ByteBufferUtil.toString(tableIdArg);
    try {
      return Tables.VALID_ID.and(userValidator).validate(tableId);
    } catch (IllegalArgumentException e) {
      String why = e.getMessage();
      log.warn(why);
      throw new ThriftTableOperationException(tableId, null, op, TableOperationExceptionType.INVALID_NAME, why);
    }
  }

  // Verify table name arguments are valid, and match any additional restrictions
  private String validateTableNameArgument(ByteBuffer tableNameArg, TableOperation op, Validator<String> userValidator) throws ThriftTableOperationException {
    String tableName = tableNameArg == null ? null : ByteBufferUtil.toString(tableNameArg);
    return _validateArgument(tableName, op, Tables.VALID_NAME.and(userValidator));
  }

  // Verify namespace arguments are valid, and match any additional restrictions
  private String validateNamespaceArgument(ByteBuffer namespaceArg, TableOperation op, Validator<String> userValidator) throws ThriftTableOperationException {
    String namespace = namespaceArg == null ? null : ByteBufferUtil.toString(namespaceArg);
    return _validateArgument(namespace, op, Namespaces.VALID_NAME.and(userValidator));
  }

  // helper to handle the exception
  private <T> T _validateArgument(T arg, TableOperation op, Validator<T> validator) throws ThriftTableOperationException {
    try {
      return validator.validate(arg);
    } catch (IllegalArgumentException e) {
      String why = e.getMessage();
      log.warn(why);
      throw new ThriftTableOperationException(null, String.valueOf(arg), op, TableOperationExceptionType.INVALID_NAME, why);
    }
  }
}
