package scheduler.impl;

import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.Trigger;
import org.quartz.Trigger.CompletedExecutionInstruction;
import org.quartz.TriggerListener;

import com.mendix.core.Core;
import com.mendix.logging.ILogNode;
// PBornier Update
import com.mendix.systemwideinterfaces.core.IMendixIdentifier;
import com.mendix.systemwideinterfaces.core.IMendixObject;

public class MxSchedulerListener implements JobListener, TriggerListener {

	private static final ILogNode _logNodeRuntime = Core.getLogger("Scheduler_Runtime");

	@Override
	public void triggerComplete( Trigger arg0, JobExecutionContext jobContext, CompletedExecutionInstruction arg2 ) {
		_logNodeRuntime.info("Trigger Comlete " + arg0 + "," + arg2 + "," + jobContext.getJobDetail().getKey() + "\r\n" + jobContext);

		evalMonitorInfo(jobContext);
	}

	@Override
	public void triggerFired( Trigger arg0, JobExecutionContext jobContext ) {
		_logNodeRuntime.info("Trigger Fired " + arg0 + "," + jobContext.getJobDetail().getKey() + "\r\n" + jobContext);

		evalMonitorInfo(jobContext);
	}

	@Override
	public void triggerMisfired( Trigger arg0 ) {
		_logNodeRuntime.info("Trigger Misfired " + arg0);

	}

	@Override
	public boolean vetoJobExecution( Trigger arg0, JobExecutionContext jobContext ) {
		_logNodeRuntime.info("Veto Job Execution " + arg0 + "," + jobContext.getJobDetail().getKey() + "\r\n" + jobContext);

		evalMonitorInfo(jobContext);

		return false;
	}

	@Override
	public String getName() {
		return "MxSchedulerListener";
	}

	@Override
	public void jobExecutionVetoed( JobExecutionContext jobContext ) {
		_logNodeRuntime.info("Job Execution Vetoed" + jobContext.getJobDetail().getKey() + "\r\n" + jobContext);

		evalMonitorInfo(jobContext);
	}

	/**
	 * Executed just before the Job is scheduled
	 */
	@Override
	public void jobToBeExecuted( JobExecutionContext jobContext ) {
		_logNodeRuntime.trace("Starting Job: " + jobContext.getJobDetail().getKey() + "\r\n" + jobContext);

		evalMonitorInfo(jobContext);
	}

	/**
	 * Executed just after the Job has been executed
	 */
	@Override
	public void jobWasExecuted( JobExecutionContext jobContext, JobExecutionException exception ) {
		if ( exception != null )
			_logNodeRuntime
					.error("Error while executing Job " + jobContext.getJobDetail().getKey() + ", error: " + exception.getMessage(), exception);

		_logNodeRuntime.debug("Completed Job " + jobContext.getJobDetail().getKey());

		evalMonitorInfo(jobContext);
	}

	protected void evalMonitorInfo( JobExecutionContext jobContext ) {
		JobDataMap dataMap = jobContext.getJobDetail().getJobDataMap();
		IMendixObject monitor = (IMendixObject) dataMap.get(ScheduledJob.Monitor);
		// PBornier Update
		IMendixIdentifier instance = (IMendixIdentifier) dataMap.get(ScheduledJob.InstanceId);

		ScheduleManager.updateMonitorInformation(monitor, jobContext.getPreviousFireTime(), jobContext.getScheduledFireTime(),
				jobContext.getFireTime(), jobContext.getNextFireTime(), jobContext.getTrigger());
	}

}
