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
package org.apache.accumulo.test.randomwalk.concurrent;

import java.util.Properties;

import org.apache.accumulo.core.client.Instance;
import org.apache.accumulo.core.client.impl.MasterClient;
import org.apache.accumulo.core.master.thrift.MasterClientService.Client;
import org.apache.accumulo.core.master.thrift.MasterGoalState;
import org.apache.accumulo.core.util.UtilWaitThread;
import org.apache.accumulo.master.state.SetGoalState;
import org.apache.accumulo.server.client.HdfsZooInstance;
import org.apache.accumulo.server.security.SystemCredentials;
import org.apache.accumulo.test.randomwalk.State;
import org.apache.accumulo.test.randomwalk.Test;
import org.apache.accumulo.trace.instrument.Tracer;

public class Shutdown extends Test {
  
  @Override
  public void visit(State state, Properties props) throws Exception {
    log.debug("shutting down");
    SetGoalState.main(new String[] {MasterGoalState.CLEAN_STOP.name()});
    
    while (!state.getConnector().instanceOperations().getTabletServers().isEmpty()) {
      UtilWaitThread.sleep(1000);
    }
    
    while (true) {
      try {
        Instance instance = HdfsZooInstance.getInstance();
        Client client = MasterClient.getConnection(instance);
        client.getMasterStats(Tracer.traceInfo(), SystemCredentials.get().toThrift(instance));
      } catch (Exception e) {
        // assume this is due to server shutdown
        break;
      }
      UtilWaitThread.sleep(1000);
    }
    
    log.debug("tablet servers stopped");
  }
  
}
