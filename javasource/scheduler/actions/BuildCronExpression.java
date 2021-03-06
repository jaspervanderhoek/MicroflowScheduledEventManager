// This file was generated by Mendix Studio Pro.
//
// WARNING: Only the following code will be retained when actions are regenerated:
// - the import list
// - the code between BEGIN USER CODE and END USER CODE
// - the code between BEGIN EXTRA CODE and END EXTRA CODE
// Other code you write will be lost the next time you deploy the project.
// Special characters, e.g., é, ö, à, etc. are supported in comments.

package scheduler.actions;

import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.webui.CustomJavaAction;
import scheduler.impl.ScheduleManager;
import com.mendix.systemwideinterfaces.core.IMendixObject;

public class BuildCronExpression extends CustomJavaAction<java.lang.String>
{
	private IMendixObject __action;
	private scheduler.proxies.ScheduledAction action;

	public BuildCronExpression(IContext context, IMendixObject action)
	{
		super(context);
		this.__action = action;
	}

	@java.lang.Override
	public java.lang.String executeAction() throws Exception
	{
		this.action = __action == null ? null : scheduler.proxies.ScheduledAction.initialize(getContext(), __action);

		// BEGIN USER CODE
		return ScheduleManager.buildCronExpression(action);
		// END USER CODE
	}

	/**
	 * Returns a string representation of this action
	 */
	@java.lang.Override
	public java.lang.String toString()
	{
		return "BuildCronExpression";
	}

	// BEGIN EXTRA CODE
	// END EXTRA CODE
}
