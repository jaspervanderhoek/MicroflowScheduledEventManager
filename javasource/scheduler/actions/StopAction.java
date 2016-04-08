// This file was generated by Mendix Business Modeler.
//
// WARNING: Only the following code will be retained when actions are regenerated:
// - the import list
// - the code between BEGIN USER CODE and END USER CODE
// - the code between BEGIN EXTRA CODE and END EXTRA CODE
// Other code you write will be lost the next time you deploy the project.
// Special characters, e.g., é, ö, à, etc. are supported in comments.

package scheduler.actions;

import scheduler.impl.ScheduleManager;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.webui.CustomJavaAction;
import com.mendix.systemwideinterfaces.core.IMendixObject;

/**
 * 
 */
public class StopAction extends CustomJavaAction<Boolean>
{
	private IMendixObject __ScheduledActionParameter1;
	private scheduler.proxies.ScheduledAction ScheduledActionParameter1;

	public StopAction(IContext context, IMendixObject ScheduledActionParameter1)
	{
		super(context);
		this.__ScheduledActionParameter1 = ScheduledActionParameter1;
	}

	@Override
	public Boolean executeAction() throws Exception
	{
		this.ScheduledActionParameter1 = __ScheduledActionParameter1 == null ? null : scheduler.proxies.ScheduledAction.initialize(getContext(), __ScheduledActionParameter1);

		// BEGIN USER CODE

		ScheduleManager.getInstance().stopAction( this.ScheduledActionParameter1);
		
		return true;
		// END USER CODE
	}

	/**
	 * Returns a string representation of this action
	 */
	@Override
	public String toString()
	{
		return "StopAction";
	}

	// BEGIN EXTRA CODE
	// END EXTRA CODE
}
