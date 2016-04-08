package scheduler.impl;

import java.util.HashMap;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.mendix.core.Core;
import com.mendix.systemwideinterfaces.core.IContext;


public class ScheduledJob implements Job {

	@SuppressWarnings("unused")
	private static final long serialVersionUID = 367747155463687864L;
	public static final String Microflow = "MF";
	public static final String Action = "Action";
	public static final String ActionInternalId = "ActionInternalId";
	public static final String NonConcurrentList = "NonConcurrentList";
	public static final String Monitor = "Monitor";
	public static final String ExcutionAttempts = "ExcutionAttempts";

	public ScheduledJob() {
		super();

	}

	@Override
	public void execute( JobExecutionContext jeContext ) throws JobExecutionException {

		JobDataMap dataMap = jeContext.getJobDetail().getJobDataMap();
		// String jobKey = jeContext.getJobDetail().getKey().getName();

		String mfName = dataMap.getString(Microflow);
		Object action = dataMap.get(Action);
		// Long actionId = dataMap.getLong(ActionInternalId);

		IContext context = Core.createSystemContext();
		try {
			dataMap.putAsString(ExcutionAttempts, 0);


			context.startTransaction();
			HashMap<String, Object> params = new HashMap<String, Object>();
			params.put("ScheduledAction", action);

			Core.execute(context, mfName, params);
			context.endTransaction();
		}
		catch( Throwable t ) {
			context.rollbackTransAction();
			JobExecutionException je = new JobExecutionException("An error occured while executing Microflow: " + mfName, t, false);
			je.setUnscheduleAllTriggers(false);
			throw je;
		}
	}

}
