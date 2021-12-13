# Microflow Scheduled Event Manager

## Description
This module allows you to schedule microflows at regular intervals.  The module allows you to configure the interval and will use 'cron' expressions to schedule the actions.

Configure what happens if an action is still running at the next schedule, if concurrent execution is allowed the action will run in parallel. If concurrent execution isn't allowed the schedule will be skipped. 

 The actions will automatically be executed on the 'cluster manager' only. Sometimes the platform needs a couple of minutes to determine the cluster manager, so it could happen that it takes up to 5 minutes before the scheduled events start.
<br><br>

### Known CVEs:

There are currently 4 CVEs on the Log4j library which is included in this module. The primary library used by this module is the [Quartz Scheduler](http://www.quartz-scheduler.org/) which depends on log4j. Specifically this module includes Log4j-1.2.16.

[2021-44228](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2021-44228). This CVE only applies on versions 2-beta - 2.15 and is not relevant to v1.2.16<br>
[2021-44228](https://security.snyk.io/vuln/SNYK-JAVA-LOG4J-2316893) The exploit on V1.x has only been found when leveraging the JMSAppender and having access to the topic binding name. Neither are implemented, even in combination with using the Ldap module the exact exploit can not be leveraged.<br>
[2020-9488](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2020-9488) is specific to a MitM attach when using the SMTP log appender, which is not implemented for this library or module. This CVE is therefore not exploitable.  <br>
[2019-17571](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2019-17571) Deserialization of untrusted data, the only data passes from the module through Quartz through info/debug/trace messages into log4j is the 'InternalId' attribute, all other logging happens through standard Mendix logging. This CVE is therefore not exploitable.  <br>
**None of these vulnerabilities pose any risk to your application or environment**
<br><br>

## Typical usage scenario
Schedule Microflows, compatible with an High Availability architecture, if you want to run your microflows only once and allow for fail-over you can use this module to configure your actions.
<br>
If you want to have more control on when your actions are execute or how to handle overlapping executing use this module. 

## Configuration 
Microflows can be scheduled through the UI, or using the microflow in the Use Me folder. Just setup your action in your startup microflow, the module will evaluate that the action is configured with the correct parameters.
<br><br>
For starting and stopping the module correctly you'll also need the after startup and before shutdown microflow. Those microflow will initialize the Schedule Manager and shut all actions down cleanly without any errors. 
By using the Scheduled Microflow interface you can setup you actions to run at specific intervals. The module will translate this configuration back to Cron expressions. If the UI does not offer you with the options you want you can also write your own Cron expression. The module uses Quartz 2.0, on this page you can find the documentation on writing Cron expressions using Quartz.

<b>Interval types:</b>
- Minute:
  Schedule an action on a regular interval. You can schedule the actions with an x-minute interval. The maximum interval that can be used is 60 minutes. For larger intervals you should create multiple schedules. 
- Hour:    
  Schedule an action on a regular hourly interval. You can schedule the actions on an x-hourly bases, the maximum interval that can be used is 24 hours. 
- Month:  
  Schedule an action on a specific date of the month, or at a specific day of the week, such as the 'first/second/third/fourth/last' Monday every 2 months.
- Year:
  (Not Implemented)  Will offer the same ability as the monthly schedule, but with the difference that you can select a specific month, and allow it to run every x-year.

<br><br>
### Still to be implemented:
- Yearly recurring interval
- Exclusive execution of a microflow,  allow a user to configure that an action cannot start if another action is running. 
