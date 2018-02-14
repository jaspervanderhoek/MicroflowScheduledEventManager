package scheduler.impl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;

import scheduler.proxies.ActionMonitor;
import scheduler.proxies.Group;
import scheduler.proxies.RuntimeInstance;
import scheduler.proxies.ScheduledAction;
import scheduler.proxies.YesNo;
import scheduler.proxies.microflows.Microflows;

import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.logging.ILogNode;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IMendixObject;

import aQute.bnd.service.action.Action;

public class ScheduleManager {

	private static ScheduleManager _instance;
	private Scheduler sch = null;

	public static final ILogNode _logNodeCore = Core.getLogger("Scheduler_Core");
	public static final ILogNode _logNodeRuntime = Core.getLogger("Scheduler_Runtime");


	public enum RunningStatus {
		started, running, stopped, standby
	}

	protected HashMap<Long, ActionConfig> actionConfigMap = new HashMap<Long, ActionConfig>();

	private class ActionConfig {

		private String jobName;
		private String groupName;
		private String microflowName;
		private boolean allowConcurrent = false;
		private boolean hasGroupAssignment = false;

		private JobKey jobKey = null;
		private TriggerKey triggerKey = null;
		private Long internalId;

		ScheduledAction actionObject;

		public ActionConfig( ScheduledAction action ) throws CoreException {
			updateInfo(action);
		}

		public void updateInfo( ScheduledAction action ) throws CoreException {
			this.actionObject = action;

			this.jobName = "Action" + action.getInternalId();
			this.allowConcurrent = action.getAllowConcurrentExecution() == YesNo.Yes;
			this.internalId = action.getInternalId();

			Group group = action.getScheduledAction_Group();
			if ( group == null ) {
				this.groupName = "AutoCreatedGroup" + action.getInternalId();
				this.hasGroupAssignment = false;
			}
			else {
				this.groupName = "Group" + group.getNr();
				this.hasGroupAssignment = true;
			}

			mxmodelreflection.proxies.Microflows mf = action.getScheduledAction_Microflows();
			if ( mf == null )
				throw new CoreException("No microflow assigned to action: " + action.getInternalId() + "-" + action.getName());

			this.microflowName = mf.getCompleteName();
		}


		public boolean allowConcurrent() {
			if ( this.hasGroupAssignment )
				return false;

			return this.allowConcurrent;
		}

		public Long getInternalId() {
			return this.internalId;
		}

		public String getMicroflowName() {
			return this.microflowName;
		}

		protected JobKey getJobKey() {
			if ( this.jobKey == null )
				this.jobKey = JobKey.jobKey(this.jobName, this.groupName);

			return this.jobKey;
		}

		protected TriggerKey getTriggerKey() {
			if ( this.triggerKey == null )
				this.triggerKey = TriggerKey.triggerKey(this.jobName, this.groupName);

			return this.triggerKey;
		}

		public Date getStartDateTime() {
			return this.actionObject.getStartDateTime();
		}

		public ScheduledAction getAction() {
			return this.actionObject;
		}
	}

	private ActionConfig getActionConfig( ScheduledAction action ) throws CoreException {
		ActionConfig config;
		if ( !this.actionConfigMap.containsKey(action.getInternalId()) ) {
			config = new ActionConfig(action);
			this.actionConfigMap.put(action.getInternalId(), config);
		}

		else {
			config = this.actionConfigMap.get(action.getInternalId());
			config.updateInfo(action);
		}

		return config;
	}


	private ScheduleManager() {

	}

	private void intitialize() {
		_logNodeCore.info("Scheduling Scheduler.SE_SchedulerMaintenance every 5 minutes, starting now");
		
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.SECOND, 6);
		
		Core.scheduleAtFixedRate("Scheduler.SE_SchedulerMaintenance", cal.getTime(), 5, TimeUnit.MINUTES, "SchedulerMaintenance",
				"This action runs every 5 minutes and evaluates and cleans up all action information");

		cal.add(Calendar.SECOND, 2);
		_logNodeCore.info("Scheduling Scheduler.SE_EvaluateOpenInstructions every 10 seconds, starting now");
		Core.scheduleAtFixedRate(
				"Scheduler.SE_EvaluateOpenInstructions",
				cal.getTime(),
				10,
				TimeUnit.SECONDS,
				"EvaluateJobInstruction",
				"Evaluate all action instructions and make sure that clustermanager executes all actions, or in case of an instance specific action it will also trigger that event.");

		
		
		/*
		 * Peace of code that is never going to be executed (running status is never null),
		 * this is just here to get compile errors incase somebody renames the actions
		 */
		if ( this.sch == null && this.sch != null ) {
			Microflows.sE_SchedulerMaintenance(Core.createSystemContext());
			Microflows.sE_EvaluateOpenInstructions(Core.createSystemContext());
		}
	}

	public final static ScheduleManager getInstance() {
		if ( _instance == null ) {
			_instance = new ScheduleManager();
			_instance.intitialize();
		}

		return _instance;
	}

	public RunningStatus evaluateRunning() throws SchedulerException, CoreException {
		if ( this.sch == null ) {
			SchedulerFactory schFactory = new StdSchedulerFactory();
			this.sch = schFactory.getScheduler();
			this.sch.getListenerManager().addJobListener(new MxSchedulerListener());

			IContext context = Core.createSystemContext();
			List<IMendixObject> result = Core.retrieveXPathQueryEscaped(context, "//%s[%s='%s']", RuntimeInstance.getType(),
					RuntimeInstance.MemberNames.XASId.toString(), Core.getXASId());
			if ( result.size() > 0 ) {
				IMendixObject obj = result.get(0);
				obj.setValue(context, RuntimeInstance.MemberNames.RequiresRestart.toString(), false);
				Core.commit(context, obj);
			}
		}

		if ( !this.sch.isStarted() ) {
			// Start the schedule
			this.sch.start();
			_logNodeCore.debug("Started");

			return RunningStatus.started;
		}
		else if ( this.sch.isInStandbyMode() ) {
			_logNodeCore.debug("Standby");
			return RunningStatus.standby;
		}
		else if ( this.sch.isShutdown() ) {
			_logNodeCore.debug("Stopped");
			return RunningStatus.stopped;
		}
		else
			return RunningStatus.running;

	}

	public void startAction( ScheduledAction actionObj ) throws SchedulerException, CoreException, ParseException {
		if ( this.sch == null )
			this.evaluateRunning();

		ActionConfig config = this.getActionConfig(actionObj);
		JobKey jobKey = config.getJobKey();

		if ( this.sch.getJobDetail(jobKey) == null ) {
			_logNodeCore.trace("Scheduling new Job " + jobKey.toString());

			// define the job and tie it to our HelloJob class
			JobBuilder jobBuilder;
			if ( config.allowConcurrent() )
				jobBuilder = JobBuilder.newJob(ScheduledJob.class);
			else
				jobBuilder = JobBuilder.newJob(SingularScheduledJob.class);

			IMendixObject monitor = prepareActionMonitor(config.getAction());

			JobDataMap jdm = new JobDataMap();
			jdm.put(ScheduledJob.Microflow, config.getMicroflowName());
			jdm.put(ScheduledJob.Action, config.getAction().getMendixObject());
			jdm.put(ScheduledJob.Monitor, monitor);
			jdm.put(ScheduledJob.ActionInternalId, config.getInternalId());

			jobBuilder.withIdentity(jobKey).usingJobData(jdm);

			JobDetail job = jobBuilder.build();

			Trigger trigger = setupActionTrigger(config, monitor);

			// Tell quartz to schedule the job using our trigger
			this.sch.scheduleJob(job, trigger);

			_logNodeCore.trace("Job successfully scheduled \r\nJob: " + job.toString() + "\r\nTrigger: " + trigger.toString());
		}
		else
			_logNodeCore.trace("Job " + jobKey.toString() + " was already running");
	}

	public void shutDown( boolean waitForJobsToComplete ) throws SchedulerException {
		if ( this.sch != null ) {
			_logNodeCore.info("Shutting down all running actions");
			this.sch.shutdown(waitForJobsToComplete);

			_logNodeCore.info("Shutdown completed");
		}
		else
			_logNodeCore.info("Shutdown cannot be executed, Scheduler isn't running");
	}

	public boolean restartScheduler() throws SchedulerException, CoreException {
		this.shutDown(true);

		// Reset the scheduler variable so a new scheduler is initialized and started
		this.sch = null;
		this.evaluateRunning();

		// Send an instruction to evaluate all actions
		Microflows.evaluateAllActions(Core.createSystemContext());

		return false;
	}

	protected Trigger setupActionTrigger( ActionConfig config, IMendixObject monitor ) throws ParseException, CoreException {
		String expression = getCronExpression(config);

		try {
			TriggerKey triggerKey = config.getTriggerKey();
			Date startDate = config.getStartDateTime(), curDate = new Date();
			boolean actionMissed = false;

			if ( monitor != null ) {
				Date nextFireTime = monitor.getValue(Core.createSystemContext(), ActionMonitor.MemberNames.NextFireTime.toString());

				if ( nextFireTime != null && nextFireTime.before(curDate) ) {
					actionMissed = true;
					startDate = nextFireTime;

					_logNodeCore.trace("Action: " + triggerKey + " missed execution at: " + nextFireTime);
				}
			}

			CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule(new CronExpression(expression)).inTimeZone(
					Core.createSystemContext().getSession().getTimeZone());

			TriggerBuilder<CronTrigger> triggerBuilder = TriggerBuilder.newTrigger()
					.withIdentity(triggerKey)
					.startAt(startDate)
					.withSchedule(cronScheduleBuilder);

			// Pre-build the trigger so it will calculate all fire times
			Trigger trigger = triggerBuilder.build();

			// If the Start date is in the past we will have to recalculate the fire times
			if ( !actionMissed && config.getStartDateTime().before(curDate) ) {

				if ( _logNodeCore.isTraceEnabled() )
					_logNodeCore
							.trace("Rescheduling trigger: " + triggerKey + " to start at: " + trigger.getFireTimeAfter(curDate) + " original time: " + trigger
									.getStartTime());

				// Now re-set the start time with the calculated fire time
				triggerBuilder.startAt(trigger.getFireTimeAfter(curDate));
				trigger = triggerBuilder.build();
			}

			// Update the action monitor with the latest action trigger details
			updateMonitorInformation(monitor, trigger.getPreviousFireTime(), trigger.getNextFireTime(), trigger.getStartTime(),
					trigger.getStartTime(), trigger);


			return trigger;
		}
		catch( Exception e ) {
			_logNodeCore.error("Error while scheduling action: " + config.getInternalId() + " with schedule " + expression + ", error: " + e.getMessage());
			throw new CoreException(e);
		}
	}

	public boolean rescheduleAction( ScheduledAction action ) throws CoreException, ParseException, SchedulerException {
		if ( this.sch != null ) {
			ActionConfig config = getActionConfig(action);

			_logNodeCore.info("Stopping: Job" + config.getInternalId());

			Trigger trigger = setupActionTrigger(config, prepareActionMonitor(config.getAction()));
			this.sch.rescheduleJob(config.getTriggerKey(), trigger);
			_logNodeCore.trace("Job successfully Re-Scheduled \r\nTrigger: " + trigger.toString());

			return true;
		}
		else
			_logNodeCore.info("No scheduler running, can't update the configuration: Job" + action.getInternalId());

		return false;
	}

	public void stopAction( ScheduledAction action ) throws SchedulerException, CoreException {
		if ( this.sch != null ) {
			ActionConfig config = getActionConfig(action);
			_logNodeCore.info("Stopping: Job" + config.getInternalId());

			this.sch.unscheduleJob(config.getTriggerKey());

			// Schedule the job with the trigger
			this.sch.deleteJob(config.getJobKey());
			
			ActionMonitor monitor = action.getActionMonitor_ScheduledAction();
			monitor.setNextFireTime(null);
			monitor.setScheduledFireTime(null);
			Core.commit(Core.createSystemContext(), monitor.getMendixObject());
			
			_logNodeCore.trace("Job successfully Stopped \r\nJob: " + config.getJobKey() + "\r\nTrigger: " + config.getTriggerKey());
		}
		else
			_logNodeCore.info("No scheduler running, can't stop: Job" + action.getInternalId());
	}

	protected String getCronExpression( ActionConfig config ) throws ParseException, CoreException {
		ScheduledAction action = config.getAction();
		String expression = action.getCronExpression();

		if ( action.getAdvanced() )
			return expression;

		return buildCronExpression(action);
	}
	
	public static String buildCronExpression( ScheduledAction action ) throws CoreException, ParseException { 
		if ( action.getStartDateTime() == null )
			throw new CoreException("Please specifiy the start date and time");

		if ( action.getInterval() == null )
			throw new CoreException("Please specify the Interval");

		String second = "0", minute = "0", hour = "0", day = "*", month = "*", dayOfWeek = "?", year = "";

		SimpleDateFormat format;
		format = new SimpleDateFormat("ss");
		second = format.format(action.getStartDateTime());

		format = new SimpleDateFormat("mm");
		minute = format.format(action.getStartDateTime());

		format = new SimpleDateFormat("HH");
		hour = format.format(action.getStartDateTime());

		switch (action.getInterval()) {
		case Per_Minute:
			// Minute
			if ( action.getAmount() > 1 )
				minute = minute + "/" + action.getAmount();
			else
				minute = minute + "/1";

			if ( Integer.valueOf(hour) > 0 )
				hour = hour + "/1";
			else
				hour = "*"; // Hour


			dayOfWeek = setupDayOfWeek(action);
			if ( dayOfWeek.length() > 2 )
				day = "?";

			break;

		case Hourly:
			if ( action.getAmount() > 1 )
				hour = hour + "/" + action.getAmount();
			else
				hour = hour + "/1";

			dayOfWeek = setupDayOfWeek(action);
			if ( dayOfWeek.length() > 2 )
				day = "?";

			break;

		case Weekly:
			dayOfWeek = setupDayOfWeek(action);
			if ( dayOfWeek.length() > 2 )
				day = "?";

			break;

		case Monthly:
			if ( action.getAmount2() > 1 )
				month = "*/" + action.getAmount2();
			else
				month = "*";

			if ( action.getFixedDate() )
				day = String.valueOf(action.getAmount());

			else {
				// Day
				day = "?";

				if ( action.getDayOfWeek() == null || action.getDayOfMonth() == null )
					throw new CoreException("Invalid action configuration, please specify on which day of the month the action should run");

				// Day of week
				switch (action.getDayOfWeek()) {
				case Monday:
					dayOfWeek = "2";
					break;
				case Tuesday:
					dayOfWeek = "3";
					break;
				case Wednesday:
					dayOfWeek = "4";
					break;
				case Thursday:
					dayOfWeek = "5";
					break;
				case Friday:
					dayOfWeek = "6";
					break;
				case Saturday:
					dayOfWeek = "7";
					break;
				case Sunday:
					dayOfWeek = "1";
					break;
				}

				switch (action.getDayOfMonth()) {
				case First:
					dayOfWeek += "#1";
					break;
				case Second:
					dayOfWeek += "#2";
					break;
				case Third:
					dayOfWeek += "#3";
					break;
				case Fourth:
					dayOfWeek += "#4";
					break;
				case Last:
					dayOfWeek += "#L";
					break;
				}
			}

			break;

		default:
			throw new ParseException("Not implemented ", 2);
		}

		String expression = second + " " + minute + " " + hour + " " + day + " " + month + " " + dayOfWeek + " " + year;
		_logNodeCore.debug("Created expression " + expression + " for Job " + action.getInternalId());

		return expression;
	}

	protected static String setupDayOfWeek( ScheduledAction action ) throws CoreException {
		boolean everyDay = false;
		if ( action.getMon() && action.getTue() && action.getWed() && action.getThu() && action.getFri() && action.getSat() && action.getSun() ) {
			everyDay = true;
		}

		String days = "";

		if ( everyDay ) {
			days = " ?";
		}
		else {
			if ( action.getMon() )
				days += ",MON";
			if ( action.getTue() )
				days += ",TUE";
			if ( action.getWed() )
				days += ",WED";
			if ( action.getThu() )
				days += ",THU";
			if ( action.getFri() )
				days += ",FRI";
			if ( action.getSat() )
				days += ",SAT";
			if ( action.getSun() )
				days += ",SUN";

			if ( days.length() == 0 )
				throw new CoreException("Please specify at least 1 day");

			days = days.substring(1);
		}

		return days;
	}

	private static IMendixObject prepareActionMonitor( ScheduledAction action ) throws CoreException {

		IContext sysContext = Core.createSystemContext();
		ActionMonitor monitor = action.getActionMonitor_ScheduledAction(sysContext);
		if ( monitor == null ) {
			monitor = ActionMonitor.initialize(sysContext, Core.instantiate(sysContext, ActionMonitor.entityName));
			monitor.setActionMonitor_ScheduledAction(sysContext, action);
			action.setActionMonitor_ScheduledAction(sysContext, monitor);
			Core.commit(sysContext, monitor.getMendixObject());
			Core.commit(sysContext, action.getMendixObject());
		}


		return monitor.getMendixObject();
	}

	protected static void updateMonitorInformation( IMendixObject monitor, Date previousFireTime, Date scheduledFireTime, Date fireTime, Date nextFireTime, Trigger trigger ) {
		IContext context = Core.createSystemContext();
		try {
			if ( monitor != null ) {
				monitor.setValue(context, ActionMonitor.MemberNames.FireTime.toString(), fireTime);
				monitor.setValue(context, ActionMonitor.MemberNames.NextFireTime.toString(), nextFireTime);
				monitor.setValue(context, ActionMonitor.MemberNames.PreviousFireTime.toString(), previousFireTime);
				monitor.setValue(context, ActionMonitor.MemberNames.ScheduledFireTime.toString(), scheduledFireTime);
				monitor.setValue(context, ActionMonitor.MemberNames.TriggerKey.toString(), trigger.getKey().toString());
				Core.commit(context, monitor);
			}
			else
				_logNodeCore.error("No Action Monitor available for Action: " + trigger.getKey());
		}
		catch( Exception e ) {
			_logNodeCore.error("Unable to process Action Monitor details for Action: " + trigger.getKey() + ", error: " + e.getMessage(), e);
		}
	}
}
