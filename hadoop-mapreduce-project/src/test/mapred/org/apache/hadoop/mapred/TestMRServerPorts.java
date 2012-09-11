/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.mapred;

import java.io.IOException;

import javax.security.auth.login.LoginException;

import junit.framework.TestCase;
import org.apache.hadoop.hdfs.TestHDFSServerPorts;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.mapreduce.server.jobtracker.JTConfig;
import org.apache.hadoop.mapreduce.server.tasktracker.TTConfig;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;

/**
 * This test checks correctness of port usage by mapreduce components:
 * JobTracker, and TaskTracker.
 * 
 * The correct behavior is:<br> 
 * - when a specific port is provided the server must either start on that port 
 * or fail by throwing {@link java.net.BindException}.<br>
 * - if the port = 0 (ephemeral) then the server should choose 
 * a free port and start on it.
 */
public class TestMRServerPorts extends TestCase {
  static final String THIS_HOST = TestHDFSServerPorts.getFullHostName() + ":0";

  TestHDFSServerPorts hdfs = new TestHDFSServerPorts();

  // Runs the JT in a separate thread
  private static class JTRunner extends Thread {
    JobTracker jt;
    void setJobTracker(JobTracker jt) {
      this.jt = jt;
    }

    public void run() {
      if (jt != null) {
        try {
          jt.offerService();
        } catch (Exception ioe) {}
      }
    }
  }
  /**
   * Check whether the JobTracker can be started.
   */
  private JobTracker startJobTracker(JobConf conf, JTRunner runner) 
  throws IOException, LoginException {
    conf.set(JTConfig.JT_IPC_ADDRESS, "localhost:0");
    conf.set(JTConfig.JT_HTTP_ADDRESS, "0.0.0.0:0");
    JobTracker jt = null;
    try {
      jt = JobTracker.startTracker(conf);
      runner.setJobTracker(jt);
      runner.start();
      conf.set(JTConfig.JT_IPC_ADDRESS, "localhost:" + jt.getTrackerPort());
      conf.set(JTConfig.JT_HTTP_ADDRESS, 
                            "0.0.0.0:" + jt.getInfoPort());
    } catch(InterruptedException e) {
      throw new IOException(e.getLocalizedMessage());
    }
    return jt;
  }
  
  private void setDataNodePorts(Configuration conf) {
    conf.set("dfs.datanode.address", THIS_HOST);
    conf.set("dfs.datanode.http.address", THIS_HOST);
    conf.set("dfs.datanode.ipc.address", THIS_HOST);
  }

  /**
   * Check whether the JobTracker can be started.
   */
  private boolean canStartJobTracker(JobConf conf) 
  throws IOException, InterruptedException, LoginException {
    JobTracker jt = null;
    try {
      jt = JobTracker.startTracker(conf);
    } catch(IOException e) {
      if (e instanceof java.net.BindException)
        return false;
      throw e;
    }
    jt.fs.close();
    jt.stopTracker();
    return true;
  }

  /**
   * Check whether the TaskTracker can be started.
   */
  private boolean canStartTaskTracker(JobConf conf) 
  throws IOException, InterruptedException {
    TaskTracker tt = null;
    try {
      tt = new TaskTracker(conf);
    } catch(IOException e) {
      if (e instanceof java.net.BindException)
        return false;
      throw e;
    }
    tt.shutdown();
    return true;
  }

  /**
   * Verify JobTracker port usage.
   */
  public void testJobTrackerPorts() throws Exception {
    NameNode nn = null;
    DataNode dn = null;
    try {
      nn = hdfs.startNameNode();
      setDataNodePorts(hdfs.getConfig());
      dn = hdfs.startDataNode(1, hdfs.getConfig());

      // start job tracker on the same port as name-node
      JobConf conf2 = new JobConf(hdfs.getConfig());
      conf2.set(JTConfig.JT_IPC_ADDRESS,
                FileSystem.getDefaultUri(hdfs.getConfig()).toString());
      conf2.set(JTConfig.JT_HTTP_ADDRESS, THIS_HOST);
      boolean started = canStartJobTracker(conf2);
      assertFalse(started); // should fail

      // bind http server to the same port as name-node
      conf2.set(JTConfig.JT_IPC_ADDRESS, THIS_HOST);
      conf2.set(JTConfig.JT_HTTP_ADDRESS,
        hdfs.getConfig().get("dfs.http.address"));
      started = canStartJobTracker(conf2);
      assertFalse(started); // should fail again

      // both ports are different from the name-node ones
      conf2.set(JTConfig.JT_IPC_ADDRESS, THIS_HOST);
      conf2.set(JTConfig.JT_HTTP_ADDRESS, THIS_HOST);
      started = canStartJobTracker(conf2);
      assertTrue(started); // should start now

    } finally {
      hdfs.stopDataNode(dn);
      hdfs.stopNameNode(nn);
    }
  }

  /**
   * Verify JobTracker port usage.
   */
  public void testTaskTrackerPorts() throws Exception {
    NameNode nn = null;
    DataNode dn = null;
    JobTracker jt = null;
    JTRunner runner = null;
    try {
      nn = hdfs.startNameNode();
      setDataNodePorts(hdfs.getConfig());
      dn = hdfs.startDataNode(2, hdfs.getConfig());

      JobConf conf2 = new JobConf(hdfs.getConfig());
      runner = new JTRunner();
      jt = startJobTracker(conf2, runner);

      // start job tracker on the same port as name-node
      conf2.set(TTConfig.TT_REPORT_ADDRESS,
                FileSystem.getDefaultUri(hdfs.getConfig()).toString());
      conf2.set(TTConfig.TT_HTTP_ADDRESS, THIS_HOST);
      boolean started = canStartTaskTracker(conf2);
      assertFalse(started); // should fail

      // bind http server to the same port as name-node
      conf2.set(TTConfig.TT_REPORT_ADDRESS, THIS_HOST);
      conf2.set(TTConfig.TT_HTTP_ADDRESS,
        hdfs.getConfig().get("dfs.http.address"));
      started = canStartTaskTracker(conf2);
      assertFalse(started); // should fail again

      // both ports are different from the name-node ones
      conf2.set(TTConfig.TT_REPORT_ADDRESS, THIS_HOST);
      conf2.set(TTConfig.TT_HTTP_ADDRESS, THIS_HOST);
      started = canStartTaskTracker(conf2);
      assertTrue(started); // should start now
    } finally {
      if (jt != null) {
        jt.fs.close();
        jt.stopTracker();
        runner.interrupt();
        runner.join();
      }
      hdfs.stopDataNode(dn);
      hdfs.stopNameNode(nn);
    }
  }
}
