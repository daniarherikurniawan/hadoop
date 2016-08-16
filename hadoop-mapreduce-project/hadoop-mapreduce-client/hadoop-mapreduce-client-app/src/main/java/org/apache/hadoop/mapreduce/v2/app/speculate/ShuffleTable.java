package org.apache.hadoop.mapreduce.v2.app.speculate;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.mapreduce.v2.api.records.TaskAttemptId;
import org.apache.hadoop.mapreduce.v2.api.records.TaskId;

// @Cesar: Contains all shuffle info (central entity)
public class ShuffleTable {
	
	private static final Log LOG = LogFactory.getLog(ShuffleTable.class);
	
	// @Cesar: Remove port from a host
	public static String parseHost(String host){
		return host != null? (host.split(":").length == 2? 
							  host.split(":")[0] : host) : null;
	}
	
	// @Cesar: This is host -> reports
	private Map<ShuffleHost, Set<ShuffleRateInfo>> shuffleReports = new TreeMap<>();
	// @Cesar: Count the number of reports
	private Map<ShuffleRateInfo, Long> shuffleReportCount = new TreeMap<>();
	// @Cesar: This map task were speculated
	private Set<TaskId> alreadySpeculated = new TreeSet<>();
	// @Cesar: Do not receive more reports from this guys
	private Set<ShuffleRateInfo> bannedReports = new TreeSet<>();
	// @Cesar: Store all attempts for a given host
	private Map<String, Set<MapAttemptInfo>> attemptsSucceededPerHost = new TreeMap<>();
	// @Cesar: Store all tasks started for a given host
	private Map<String, Set<TaskId>> tasksStartedPerHost = new TreeMap<>();
	// @Cesar: Store all successful started for a given host
	private Map<String, Set<TaskId>> tasksSuccessfulPerHost = new TreeMap<>();
		
	
	private void updateReportCount(ShuffleRateInfo info){
		long oldCount = 0L;
		if(shuffleReportCount.containsKey(info)){
			oldCount = shuffleReportCount.get(info);
			
		}
		shuffleReportCount.put(info, oldCount + 1);
	}
	
	private void updateShuffleReports(ShuffleHost host, ShuffleRateInfo info){
		Set<ShuffleRateInfo> newInfo = null;
		if(shuffleReports.containsKey(host)){
			newInfo = shuffleReports.get(host);
			// @Cesar: Remove it for its update
			newInfo.remove(info);
		}
		else{
			newInfo = new TreeSet<>();
		}
		boolean addedTo = newInfo.add(info);
		LOG.info(addedTo? "Added new fetch rate report for host " + host.getMapHost() : 
						  "Nothing added for host " + host.getMapHost());
		shuffleReports.put(host, newInfo);
	}
	
	private boolean reportSuccessfulTask(String host, TaskId mapTask){
		Set<TaskId> newInfo = null;
		if(tasksSuccessfulPerHost.containsKey(host)){
			newInfo = tasksSuccessfulPerHost.get(host);
		}
		else{
			newInfo = new TreeSet<>();
		}
		boolean added = newInfo.add(mapTask);
		tasksSuccessfulPerHost.put(host, newInfo);
		return added;
	}
	
	public boolean wasSpeculated(TaskId mapTask){
		return alreadySpeculated.contains(mapTask);
	}
	
	public boolean isReportBanned(ShuffleRateInfo info){
		return bannedReports.contains(info);
	}
	
	public synchronized void bannReportersAndCleanHost(Set<ShuffleRateInfo> infos, String host){
		LOG.info("@Cesar: Cleaning host " + host + " for " + infos.size() + " reports");
		ShuffleHost shuffleHost = new ShuffleHost(host);
		bannedReports.addAll(infos);
		if(shuffleReports.containsKey(shuffleHost)){
			Iterator<ShuffleRateInfo> infosIt = infos.iterator();
			while(infosIt.hasNext()){
				ShuffleRateInfo info = infosIt.next();
				LOG.info("@Cesar: Report was be banned for host " + host + " : " + info);
				// @Cesar: lets remove this attempt cause is not successful anymore
				if(attemptsSucceededPerHost.get(host) != null){
					attemptsSucceededPerHost.get(host).remove(new MapAttemptInfo(info.getMapTaskAttempId().getTaskId(),
																				 info.getMapTaskAttempId()));
					LOG.info("@Cesar: Removed successful attempt at host " + host + " : " + info.getMapTaskAttempId());
				}
		
			}
			// @Cesar: Clear if empty
			if(attemptsSucceededPerHost.get(host) != null && attemptsSucceededPerHost.get(host).size() == 0)
				attemptsSucceededPerHost.remove(host);
		}
	}
	
	// @Cesar: Mark task as speculated
	public synchronized boolean bannMapTask(TaskId task){
		return alreadySpeculated.add(task);
	}
	
	// @Cesar: Add a new report
	public synchronized boolean reportRate(ShuffleHost host, ShuffleRateInfo info){
		// @Cesar: Is banned?
		if(isReportBanned(info)) return false;
		// @Cesar: not banned?, well, then insert
		updateShuffleReports(host, info);
		updateReportCount(info);
		return true;
	}
	
	public synchronized boolean reportSuccessfullAttempt(String host, TaskAttemptId mapTaskAttempt){
		Set<MapAttemptInfo> newInfo = null;
		if(attemptsSucceededPerHost.containsKey(host)){
			newInfo = attemptsSucceededPerHost.get(host);
		}
		else{
			newInfo = new TreeSet<>();
		}
		boolean added = newInfo.add(new MapAttemptInfo(mapTaskAttempt.getTaskId(), mapTaskAttempt));
		attemptsSucceededPerHost.put(host, newInfo);
		// @Cesar: Also, report the task as successful
		reportSuccessfulTask(host, mapTaskAttempt.getTaskId());
		return added;
	}
	
	public synchronized boolean reportStartedTask(String host, TaskId mapTask){
		Set<TaskId> newInfo = null;
		if(tasksStartedPerHost.containsKey(host)){
			newInfo = tasksStartedPerHost.get(host);
		}
		else{
			newInfo = new TreeSet<>();
		}
		boolean added = newInfo.add(mapTask);
		tasksStartedPerHost.put(host, newInfo);
		return added;
	}
	
	public synchronized int countSuccessfulTaskPerHost(String mapHost){
		Set<TaskId> sTasks = tasksSuccessfulPerHost.get(mapHost);
		return sTasks != null? sTasks.size() : 0;
	}
	
	public synchronized int countStartedTaskPerHost(String mapHost){
		Set<TaskId> sTasks = tasksStartedPerHost.get(mapHost);
		return sTasks != null? sTasks.size() : 0;
	}
	
	public synchronized Map<ShuffleHost, Set<ShuffleRateInfo>> getReports(){
		return shuffleReports;
	}
	
	// @Cesar: Get all successful maps from a given host
	public synchronized Set<TaskAttemptId> getAllSuccessfullMapTaskAttemptsFromHost(String host){
		Set<MapAttemptInfo> allAttemptsPerHost = attemptsSucceededPerHost.get(host);
		Set<TaskAttemptId> allAttempts = new TreeSet<>();
		if(allAttemptsPerHost != null){
			Iterator<MapAttemptInfo> attIt = allAttemptsPerHost.iterator();
			while(attIt.hasNext()){
				allAttempts.add(attIt.next().mapTaskAttemptId);
			}
		}
		return allAttempts;
	}
	
	// @Cesar: Count the number of different successful attempts in a given host
	public synchronized void unsucceedTaskAtHost(String host, TaskId task){
		if(tasksSuccessfulPerHost.containsKey(host)){
			tasksSuccessfulPerHost.get(host).remove(task);
		}
	}
	
	// @Cesar: Can a host be speculated on, given the number of reports
	public synchronized boolean canSpeculate(String host){
		if(attemptsSucceededPerHost.get(host) != null && shuffleReports.get(new ShuffleHost(host)) != null)
			return attemptsSucceededPerHost.get(host).size() <= shuffleReports.get(new ShuffleHost(host)).size(); 
		return false;
	}
	
	


	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ShuffleTable [shuffleReports=").append(shuffleReports).append(", shuffleReportCount=")
				.append(shuffleReportCount).append(", alreadySpeculated=").append(alreadySpeculated)
				.append(", bannedReports=").append(bannedReports).append(", attemptsSucceededPerHost=")
				.append(attemptsSucceededPerHost).append(", tasksStartedPerHost=").append(tasksStartedPerHost)
				.append(", tasksSuccessfulPerHost=").append(tasksSuccessfulPerHost).append("]");
		return builder.toString();
	}




	// @Cesar: Utility class
	private static class MapAttemptInfo implements Comparable<MapAttemptInfo>{
		
		private TaskId mapTaskId = null;
		private TaskAttemptId mapTaskAttemptId = null;
		
		public MapAttemptInfo(TaskId mapTaskId, TaskAttemptId mapTaskAttemptId) {
			this.mapTaskId = mapTaskId;
			this.mapTaskAttemptId = mapTaskAttemptId;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((mapTaskAttemptId == null) ? 0 : mapTaskAttemptId.hashCode());
			result = prime * result + ((mapTaskId == null) ? 0 : mapTaskId.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MapAttemptInfo other = (MapAttemptInfo) obj;
			if (mapTaskAttemptId == null) {
				if (other.mapTaskAttemptId != null)
					return false;
			} else if (!mapTaskAttemptId.equals(other.mapTaskAttemptId))
				return false;
			if (mapTaskId == null) {
				if (other.mapTaskId != null)
					return false;
			} else if (!mapTaskId.equals(other.mapTaskId))
				return false;
			return true;
		}


		@Override
		public int compareTo(MapAttemptInfo other) {
			if(mapTaskId == null){
				return other.mapTaskId == null? 0 : -1;
			}
			else if(mapTaskId.compareTo(other.mapTaskId) == 0){
				if(mapTaskAttemptId == null){
					if(other.mapTaskAttemptId == null){
						return 0;
					}
					return -1;
				}
				else if(other.mapTaskAttemptId == null){
					return 1;
				}
				return mapTaskAttemptId.compareTo(other.mapTaskAttemptId);

			}
			return 1;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("MapAttemptInfo [mapTaskId=").append(mapTaskId).append(", mapTaskAttemptId=")
					.append(mapTaskAttemptId).append("]");
			return builder.toString();
		}
		
		
	}
	
}
