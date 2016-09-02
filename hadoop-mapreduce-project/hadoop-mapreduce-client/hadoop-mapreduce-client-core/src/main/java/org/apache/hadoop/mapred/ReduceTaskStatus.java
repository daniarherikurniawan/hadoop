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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.task.reduce.FetchRateReport;
import org.apache.hadoop.mapreduce.task.reduce.ShuffleData;



class ReduceTaskStatus extends TaskStatus {

  private long shuffleFinishTime; 
  private long sortFinishTime; 
  private List<TaskAttemptID> failedFetchTasks = new ArrayList<TaskAttemptID>(1);

  //huanke
  private DatanodeInfo[] DNpath = DatanodeInfo.createDatanodeInfo();

  //huanke
  @Override
  public void setDNpath(DatanodeInfo[] DNpath) {
    if(DNpath!=null){
      //LOG.info("@huanke setDNpath ReduceTaskStatus: "+Arrays.toString(DNPath));
        this.DNpath=DNpath;
    }
  }

  //huanke

  //huanke  if taskAttemptListenerImpl get 0000 something, it means DNPath is not set at all
  public DatanodeInfo[] getDNpath(){
    //LOG.info("@huanke getDNpath ReduceTaskStatus: "+Arrays.toString(DNPath));
    return this.DNpath;
  }
  
  //@Cesar: Keep shuffle info here
  private FetchRateReport reportedFetchRates = new FetchRateReport();
  
  public ReduceTaskStatus() {}

  public ReduceTaskStatus(TaskAttemptID taskid, float progress, int numSlots,
                          State runState, String diagnosticInfo, String stateString, 
                          String taskTracker, Phase phase, Counters counters) {
    super(taskid, progress, numSlots, runState, diagnosticInfo, stateString, 
          taskTracker, phase, counters);
  }

  @Override
  public Object clone() {
    ReduceTaskStatus myClone = (ReduceTaskStatus)super.clone();
    myClone.failedFetchTasks = new ArrayList<TaskAttemptID>(failedFetchTasks);
    return myClone;
  }

  @Override
  public boolean getIsMap() {
    return false;
  }

  @Override
  void setFinishTime(long finishTime) {
    if (shuffleFinishTime == 0) {
      this.shuffleFinishTime = finishTime; 
    }
    if (sortFinishTime == 0){
      this.sortFinishTime = finishTime;
    }
    super.setFinishTime(finishTime);
  }

  @Override
  public long getShuffleFinishTime() {
    return shuffleFinishTime;
  }

  @Override
  void setShuffleFinishTime(long shuffleFinishTime) {
    this.shuffleFinishTime = shuffleFinishTime;
  }

  @Override
  public long getSortFinishTime() {
    return sortFinishTime;
  }

  @Override
  void setSortFinishTime(long sortFinishTime) {
    this.sortFinishTime = sortFinishTime;
    if (0 == this.shuffleFinishTime){
      this.shuffleFinishTime = sortFinishTime;
    }
  }

  @Override
  public long getMapFinishTime() {
    throw new UnsupportedOperationException(
        "getMapFinishTime() not supported for ReduceTask");
  }

  @Override
  void setMapFinishTime(long shuffleFinishTime) {
    throw new UnsupportedOperationException(
        "setMapFinishTime() not supported for ReduceTask");
  }

  @Override
  public List<TaskAttemptID> getFetchFailedMaps() {
    return failedFetchTasks;
  }
  
  @Override
  public void addFetchFailedMap(TaskAttemptID mapTaskId) {
    failedFetchTasks.add(mapTaskId);
  }
  
  @Override
  synchronized void statusUpdate(TaskStatus status) {
    super.statusUpdate(status);
    
    if (status.getShuffleFinishTime() != 0) {
      this.shuffleFinishTime = status.getShuffleFinishTime();
    }
    
    if (status.getSortFinishTime() != 0) {
      sortFinishTime = status.getSortFinishTime();
    }
    
    List<TaskAttemptID> newFetchFailedMaps = status.getFetchFailedMaps();
    if (failedFetchTasks == null) {
      failedFetchTasks = newFetchFailedMaps;
    } else if (newFetchFailedMaps != null){
      failedFetchTasks.addAll(newFetchFailedMaps);
    }
  }

  @Override
  synchronized void clearStatus() {
    super.clearStatus();
    failedFetchTasks.clear();
    reportedFetchRates.getFetchRateReport().clear();
  }

  //@Cesar: fetch rate maps
  public FetchRateReport  getReportedFetchRates(){
	  return this.reportedFetchRates;
  }
 
  public void setReportedFetchRates(String mapperHost, ShuffleData shuffleData){
	  this.reportedFetchRates.addReport(mapperHost, shuffleData);
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    super.readFields(in);
    shuffleFinishTime = in.readLong(); 
    sortFinishTime = in.readLong();
    // huanke
    for(int i=0; i< 2;i++){
        DNpath[i].readFields(in);
      }
    
    int noFailedFetchTasks = in.readInt();
    failedFetchTasks = new ArrayList<TaskAttemptID>(noFailedFetchTasks);
    for (int i=0; i < noFailedFetchTasks; ++i) {
      TaskAttemptID id = new TaskAttemptID();
      id.readFields(in);
      failedFetchTasks.add(id);
    }
    
    // @Cesar: Read fetch rate report
    reportedFetchRates = FetchRateReport.readFrom(in);
  }

  @Override
  public void write(DataOutput out) throws IOException {
    super.write(out);
    out.writeLong(shuffleFinishTime);
    out.writeLong(sortFinishTime);

    // huanke
    for(int i=0; i<2;i++){
        DNpath[i].write(out);
    }
    
    out.writeInt(failedFetchTasks.size());
    for (TaskAttemptID taskId : failedFetchTasks) {
      taskId.write(out);
    }
    
    // @Cesar: Add fetch rate reports
    reportedFetchRates.writeTo(out);
  
  }
  
}
