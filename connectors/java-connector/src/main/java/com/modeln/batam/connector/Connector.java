/**
 * The MIT License (MIT)
 * 
 * Copyright (c) 2014 Model N
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.modeln.batam.connector;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.jcabi.aspects.RetryOnFailure;
import com.modeln.batam.connector.exception.InvalidArgumentException;
import com.modeln.batam.connector.exception.NoConnectionFoundException;
import com.modeln.batam.connector.util.ConfigHelper;
import com.modeln.batam.connector.wrapper.BuildEntry;
import com.modeln.batam.connector.wrapper.Commit;
import com.modeln.batam.connector.wrapper.Pair;
import com.modeln.batam.connector.wrapper.Step;
import com.modeln.batam.connector.wrapper.TestEntry;
import com.modeln.batam.connector.wrapper.ReportEntry;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Main connector Singleton class.
 * Get a Connector instance using the following piece of code:
 * 
 * <PRE>
 * Connector connect = Connector.getInstance();
 * connect.beginConnection();
 * 
 * //Publish actions using your connector instance.
 * 
 * connector.endConnection();
 * </PRE>
 * 
 * Since the Connector instance is a singleton, you don't need to begin and end a connection for every actions you need to publish.
 * 
 * @author gzussa
 *
 */
public class Connector {
	
	private final static int RETRY_ON_FAILURE_ATTEMPTS = 3;
	private final static int RETRY_ON_FAILURE_DELAY = 1;
	
	private final static String ACTION_FIELD = "action";
	private final static String DATA_FIELD = "data";
	
	private final static String CREATE_BUILD_ACTION = "create_build";
	private final static String UPDATE_BUILD_ACTION = "update_build";
	private final static String CREATE_REPORT_ACTION = "create_report";
	private final static String UPDATE_REPORT_ACTION = "update_report";
	private final static String CREATE_TEST_ACTION = "create_test";
	private final static String UPDATE_TEST_ACTION = "update_test";
	private final static String RUN_ANALYSIS_ACTION = "run_analysis";
	
	private static Connector instance;
	
	private Boolean publish;

	private String host;
	private String username;
	private String password;
	private Integer port;
	private String vhost;
	private String queue;
	
	private Connection connection;
	private Channel channel;

	protected Connector() {
		// Exists only to defeat instantiation.
	}

	/**
	 * Connector Singleton constructor.
	 * @return a Connector Instance.
	 */
	public static Connector getInstance() {
		if(instance == null) {
			instance = new Connector();
		}
		return instance;
	}

	/**
	 * Begin a connection using property file configurations located in <i>batam.property</i> file.
	 * 
	 * <pre>
	 * com.modeln.batam.host=localhost : specify the message broker host.
	 * com.modeln.batam.username=username : specify the message broker user name.
	 * com.modeln.batam.password=password : specify the message broker password.
	 * com.modeln.batam.port=5672 : specify the message broker port.
	 * com.modeln.batam.vhost=batam : specify the message broker VHost.
	 * com.modeln.batam.queue=batam : specify the message broker queue the connector publish data to.
	 * com.modeln.batam.publisher=on : when set to **off**, it prints messages in your console (stdout) instead of publishing them to the message broker. 
	 * </pre>
	 * 
	 * or/and System properties
	 * <pre>
	 * -Dbatam.host=localhost : specify the message broker host.
	 * -Dbatam.username=username : specify the message broker user name.
	 * -Dbatam.password=password : specify the message broker password.
	 * -Dbatam.port=5672 : specify the message broker port.
	 * -Dbatam.vhost=batam : specify the message broker VHost.
	 * -Dbatam.queue=batam : specify the message broker queue the connector publish data to.
	 * -Dbatam.publisher=on : when set to **off**, it prints messages in your console (stdout) instead of publishing them to the message broker. 
	 * </pre>
	 * 
	 * @throws IOException
	 */
	public void beginConnection() throws IOException{
		beginConnection(null, null, null, null, null, null, null);
	}
	
	/**
	 * Begin a connection using parameters. If some or all parameters are null, the connector will use values loaded either from property files or System properties.
	 * 
	 * If no system property have been defined using {@see System#setProperty(String, String) setProperty}, This method will use configuration located in the batam.properties file by default.
	 * Non null parameters will override default property file values.
	 * 
	 * To use external property file properties, call {@see com.modeln.batam.connector.Connector#loadProperties(String) loadProperties} function specifying the external property file location before to call this method with null parameters. 
	 * 
	 * If System properties are defined, they will override values defined in property files (default or external). Non null parameters will also override System properties.
	 * 
	 * @param host : message broker host configuration.
	 * @param username : message broker user name configuration.
	 * @param password : message broker password configuration. 
	 * @param port : message broker port configuration.
	 * @param vhost : message broker VHost configuration.
	 * @param queue : message broker queue the connector publish data to.
	 * @param publisher : when set to 'off', it prints messages in your console (stdout) instead of publishing them to the message broker. 
	 * 
	 * @throws IOException
	 */
	@RetryOnFailure(attempts = RETRY_ON_FAILURE_ATTEMPTS, delay = RETRY_ON_FAILURE_DELAY, unit = TimeUnit.SECONDS)
	public void beginConnection(String host, String username, String password, Integer port, String vhost, String queue, String publisher) throws IOException {
		ConfigHelper.loadProperties(null);
		
		if(host == null && this.host == null){
			this.host = ConfigHelper.HOST;
		}else if(host != null){
			this.host = host;
		}
		
		if(username == null && this.username == null){
			this.username = ConfigHelper.USER;
		}else if(username != null){
			this.username = username;
		}
		
		if(password == null && this.password == null){
			this.password = ConfigHelper.PASSWORD;
		}else if(password != null){
			this.password = password;
		}
		
		if(port == null && this.port == null){
			this.port = ConfigHelper.PORT;
		}else if(port != null){
			this.port = port;
		}
		
		if(vhost == null && this.vhost == null){
			this.vhost = ConfigHelper.VHOST;
		}else if(vhost != null){
			this.vhost = vhost;
		}
		
		if(queue == null && this.queue == null){
			this.queue = ConfigHelper.QUEUE;
		}else if(queue != null){
			this.queue = queue;
		}
		
		if(publisher == null){
			publisher = ConfigHelper.PUBLISHER;
		}
		if("on".equals(publisher) || "true".equals(publisher)){
			this.publish = true;
		}else{
			this.publish = false;
			return;
		}
		
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(this.host);
		factory.setPort(this.port);
		factory.setUsername(this.username);
		factory.setPassword(this.password);
		factory.setVirtualHost(this.vhost);
		this.connection = factory.newConnection();
		this.channel = this.connection.createChannel();
		this.channel.queueDeclare(this.queue, false, false, false, null);
	}

	/**
	 * End Connector connection.
	 * @throws IOException
	 */
	@RetryOnFailure(attempts = RETRY_ON_FAILURE_ATTEMPTS, delay = RETRY_ON_FAILURE_DELAY, unit = TimeUnit.SECONDS)
	public void endConnection() throws IOException {
		if(channel != null){
			channel.close();
			if(connection != null){
				connection.close();
			}
		}
	}

	/**
	 * Check connection has been initialized.
	 * @throws IOException 
	 */
	private void checkConnection() throws IOException{
		if(channel == null || connection == null){
			if(publish == null || publish){
				throw new NoConnectionFoundException("Establish a connection before to publish any information.");
			}
		}
		//If the channel or connection is closed then we attempt to reopen it.
		//This logic should be called after a retry on a ChannelAlreadyClosedException.
		if(channel == null || channel.isOpen() || !connection.isOpen()){
			//First we close channel before to recreate one.
			if(channel != null && channel.isOpen()){
				channel.close();
			}
			//First we close connection before to recreate one.
			if(connection != null && connection.isOpen()){
				connection.close();
			}
			beginConnection(this.host, this.username, this.password, this.port, this.vhost, this.queue, this.publish == true ? "on" : "off");
		}
	}
	
	/**
	 * Create Build basic API. 
	 * 
	 * This is a generic API that take a {@link com.modeln.batam.connector.wrapper.BuildEntry BuildEntry Object} as parameter.
	 * It publishes the BuildEntry object with CREATE_BUILD action. 
	 * Calling this API will create a new Build in the BATAM system.
	 * 
	 * @param build : {@link com.modeln.batam.connector.wrapper.BuildEntry BuildEntry} Object.
	 * @return published message.
	 * @throws IOException
	 */
	@RetryOnFailure(attempts = RETRY_ON_FAILURE_ATTEMPTS, delay = RETRY_ON_FAILURE_DELAY, unit = TimeUnit.SECONDS)
	public String createBuild(BuildEntry build) throws IOException {
		
		checkConnection();
		
		String message = "{\""+ACTION_FIELD+"\": \""+CREATE_BUILD_ACTION+"\", \""+DATA_FIELD+"\": "+build.toJSONString()+"}";
		if(publish){
			channel.basicPublish("", queue, null, message.getBytes());
		}else{
			System.out.println(message);
		}

		return message;
	}
	
	/**
	 * Update Build basic API. 
	 * 
	 * This is a generic API that take a {@link com.modeln.batam.connector.wrapper.BuildEntry BuildEntry Object} as parameter.
	 * It publishes the BuildEntry object with UPDATE_BUILD action. 
	 * Calling this API will update a previously created Build in the BATAM system.
	 * 
	 * @param build : {@link com.modeln.batam.connector.wrapper.BuildEntry BuildEntry} Object.
	 * @return published message.
	 * @throws IOException
	 */
	@RetryOnFailure(attempts = RETRY_ON_FAILURE_ATTEMPTS, delay = RETRY_ON_FAILURE_DELAY, unit = TimeUnit.SECONDS)
	public String updateBuild(BuildEntry build) throws IOException {
		
		checkConnection();
		
		String message = "{\""+ACTION_FIELD+"\": \""+UPDATE_BUILD_ACTION+"\", \""+DATA_FIELD+"\": "+build.toJSONString()+"}";
		if(publish){
			channel.basicPublish("", queue, null, message.getBytes());
		}else{
			System.out.println(message);
		}
		
		return message;
	}
	
	/**
	 * Run Analysis basic API. 
	 * 
	 * This is a generic API that take a {@link com.modeln.batam.connector.wrapper.BuildEntry BuildEntry Object} as parameter.
	 * It publishes the BuildEntry object with RUN_ANALYSIS action. 
	 * Calling this API will kick off build analysis in the BATAM system.
	 * 
	 * @param build : {@link com.modeln.batam.connector.wrapper.BuildEntry BuildEntry} Object.
	 * @return published message.
	 * @throws IOException
	 */
	@RetryOnFailure(attempts = RETRY_ON_FAILURE_ATTEMPTS, delay = RETRY_ON_FAILURE_DELAY, unit = TimeUnit.SECONDS)
	public String runAnalysis(BuildEntry build) throws IOException {
		
		checkConnection();
		
		String message = "{\""+ACTION_FIELD+"\": \""+RUN_ANALYSIS_ACTION+"\", \""+DATA_FIELD+"\": "+build.toJSONString()+"}";
		if(publish){
			channel.basicPublish("", queue, null, message.getBytes());
		}else{
			System.out.println(message);
		}
		
		return message;
	}
	
	/**
	 * Create Report basic API. 
	 * 
	 * This is a generic API that take a {@link com.modeln.batam.connector.wrapper.ReportEntry ReportEntry Object} as parameter.
	 * It publishes the ReportEntry object with CREATE_REPORT action. 
	 * Calling this API will create a new report within an existing build in the BATAM system.
	 * 
	 * @param report : {@link com.modeln.batam.connector.wrapper.ReportEntry ReportEntry} Object.
	 * @return published message.
	 * @throws IOException
	 */
	@RetryOnFailure(attempts = RETRY_ON_FAILURE_ATTEMPTS, delay = RETRY_ON_FAILURE_DELAY, unit = TimeUnit.SECONDS)
	public String createReport(ReportEntry report) throws IOException {
		
		checkConnection();
		
		String message = "{\""+ACTION_FIELD+"\": \""+CREATE_REPORT_ACTION+"\", \""+DATA_FIELD+"\": "+report.toJSONString()+"}";
		if(publish){
			channel.basicPublish("", queue, null, message.getBytes());
		}else{
			System.out.println(message);
		}
		
		return message;
	}
	
	/**
	 * Update Report basic API. 
	 * 
	 * This is a generic API that take a {@link com.modeln.batam.connector.wrapper.ReportEntry ReportEntry Object} as parameter.
	 * It publishes the ReportEntry object with UPDATE_REPORT action. 
	 * Calling this API will update a previously created report in the BATAM system.
	 * 
	 * @param report : {@link com.modeln.batam.connector.wrapper.ReportEntry ReportEntry} Object.
	 * @return published message.
	 * @throws IOException
	 */
	@RetryOnFailure(attempts = RETRY_ON_FAILURE_ATTEMPTS, delay = RETRY_ON_FAILURE_DELAY, unit = TimeUnit.SECONDS)
	public String updateReport(ReportEntry report) throws IOException {
		
		checkConnection();
		
		String message = "{\""+ACTION_FIELD+"\": \""+UPDATE_REPORT_ACTION+"\", \""+DATA_FIELD+"\": "+report.toJSONString()+"}";
		if(publish){
			channel.basicPublish("", queue, null, message.getBytes());
		}else{
			System.out.println(message);
		}
		
		return message;
	}
	
	/**
	 * Create Test basic API. 
	 * 
	 * This is a generic API that take a {@link com.modeln.batam.connector.wrapper.TestEntry TestEntry Object} as parameter.
	 * It publishes the TestEntry object with CREATE_TEST action. 
	 * Calling this API will create a new test within an existing report in the BATAM system.
	 * 
	 * Do not specify the Test id since the id will be generated by the system automatically. The Test id is only useful when updating a test.
	 * 
	 * @param test : {@link com.modeln.batam.connector.wrapper.TestEntry TestEntry} Object.
	 * @return published message.
	 * @throws IOException
	 */
	@RetryOnFailure(attempts = RETRY_ON_FAILURE_ATTEMPTS, delay = RETRY_ON_FAILURE_DELAY, unit = TimeUnit.SECONDS)
	public String createTest(TestEntry test) throws IOException {
		
		checkConnection();
		
		String message = "{\""+ACTION_FIELD+"\": \""+CREATE_TEST_ACTION+"\", \""+DATA_FIELD+"\": "+test.toJSONString()+"}";
		if(publish){
			channel.basicPublish("", queue, null, message.getBytes());
		}else{
			System.out.println(message);
		}
		
		return message;
	}
	
	/**
	 * Update Test basic API. 
	 * 
	 * This is a generic API that take a {@link com.modeln.batam.connector.wrapper.TestEntry TestEntry Object} as parameter.
	 * It publishes the TestEntry object with UPDATE_TEST action. 
	 * Calling this API will update a previously created test in the BATAM system.
	 * 
	 * @param test : {@link com.modeln.batam.connector.wrapper.TestEntry TestEntry} Object.
	 * @return published message.
	 * @throws IOException
	 */
	@RetryOnFailure(attempts = RETRY_ON_FAILURE_ATTEMPTS, delay = RETRY_ON_FAILURE_DELAY, unit = TimeUnit.SECONDS)
	public String updateTest(TestEntry test) throws IOException {
		
		checkConnection();
		
		String message = "{\""+ACTION_FIELD+"\": \""+UPDATE_TEST_ACTION+"\", \""+DATA_FIELD+"\": "+test.toJSONString()+"}";
		if(publish){
			channel.basicPublish("", queue, null, message.getBytes());
		}else{
			System.out.println(message);
		}

		return message;
	}

	/**
	 * Create Build simple API with individual {@link com.modeln.batam.connector.wrapper.BuildEntry BuildEntry} parameters.
	 * 
	 * @param id : Build Unique Identifier.
	 * @param name : Build Name (required).
	 * @param startDate : Build Start Date.
	 * @param endDate : Build End Date.
	 * @param status : Build Status.
	 * @param description : Build Description. 
	 * @param criterias : Build Criterias, List of {@see com.modeln.batam.connector.wrapper.Pair pairs}.
	 * @param infos : Build Infos, List of {@see com.modeln.batam.connector.wrapper.Pair pairs}.
	 * @param reports : Build Reports, List of {@see com.modeln.batam.connector.wrapper.Pair pairs}.
	 * @param steps : Build Steps, List of {@see com.modeln.batam.connector.wrapper.Step steps}.
	 * @param commits : Build Commits, List of {@see com.modeln.batam.connector.wrapper.Commit commits}.
	 * @return published message.
	 * @throws IOException
	 */
	public String createBuild(String id, 
			String name, 
			Date startDate, 
			Date endDate, 
			String status, 
			String description, 
			List<Pair> criterias, 
			List<Pair> infos, 
			List<Pair> reports, 
			List<Step> steps, 
			List<Commit> commits) throws IOException {

		return createBuild(id, name, startDate, endDate, status,  description, criterias, infos, reports, steps, commits, false, null, null);
	}

	/**
	 * Create Build simple API with individual {@link com.modeln.batam.connector.wrapper.BuildEntry BuildEntry} parameters.
	 *
	 * @param id : Build Unique Identifier.
	 * @param name : Build Name (required).
	 * @param startDate : Build Start Date.
	 * @param endDate : Build End Date.
	 * @param status : Build Status.
	 * @param description : Build Description.
	 * @param criterias : Build Criterias, List of {@see com.modeln.batam.connector.wrapper.Pair pairs}.
	 * @param infos : Build Infos, List of {@see com.modeln.batam.connector.wrapper.Pair pairs}.
	 * @param reports : Build Reports, List of {@see com.modeln.batam.connector.wrapper.Pair pairs}.
	 * @param steps : Build Steps, List of {@see com.modeln.batam.connector.wrapper.Step steps}.
	 * @param commits : Build Commits, List of {@see com.modeln.batam.connector.wrapper.Commit commits}.
	 * @return published message.
	 * @throws IOException
	 */
	public String createBuild(String id,
							  String name,
							  Date startDate,
							  Date endDate,
							  String status,
							  String description,
							  List<Pair> criterias,
							  List<Pair> infos,
							  List<Pair> reports,
							  List<Step> steps,
							  List<Commit> commits,
							  boolean isCustomFormatEnabled,
							  String format,
							  String customEntry) throws IOException {

        return createBuild(id, name, startDate, endDate, status,  description, criterias, infos, reports, steps, commits, isCustomFormatEnabled, format, customEntry, null, null);
    }

	/**
	 * Create Build simple API with individual {@link com.modeln.batam.connector.wrapper.BuildEntry BuildEntry} parameters.
	 *
	 * @param id : Build Unique Identifier.
	 * @param name : Build Name (required).
	 * @param startDate : Build Start Date.
	 * @param endDate : Build End Date.
	 * @param status : Build Status.
	 * @param description : Build Description.
	 * @param criterias : Build Criterias, List of {@see com.modeln.batam.connector.wrapper.Pair pairs}.
	 * @param infos : Build Infos, List of {@see com.modeln.batam.connector.wrapper.Pair pairs}.
	 * @param reports : Build Reports, List of {@see com.modeln.batam.connector.wrapper.Pair pairs}.
	 * @param steps : Build Steps, List of {@see com.modeln.batam.connector.wrapper.Step steps}.
	 * @param commits : Build Commits, List of {@see com.modeln.batam.connector.wrapper.Commit commits}.
	 * @return published message.
	 * @throws IOException
	 */
	public String createBuild(String id,
							  String name,
							  Date startDate,
							  Date endDate,
							  String status,
							  String description,
							  List<Pair> criterias,
							  List<Pair> infos,
							  List<Pair> reports,
							  List<Step> steps,
							  List<Commit> commits,
							  boolean isCustomFormatEnabled,
							  String format,
							  String customEntry, String screenshotURL, Map<String, String> customAttributes) throws IOException {

		if(name == null){
			throw new InvalidArgumentException("name field should not be null.");
		}
		if(!(startDate != null)){
			throw new InvalidArgumentException("startDate field should not be null.");
		}

		if(isCustomFormatEnabled && (format == null ||  !validateCustomFormat(format)))  {
			throw new InvalidArgumentException("CustomFormat provided is not a valid one");
		}

		BuildEntry build = new BuildEntry(id, name, startDate, endDate, status, description, criterias, infos, reports, steps, commits, false, isCustomFormatEnabled, format, customEntry, screenshotURL, customAttributes);

		return createBuild(build);
	}

	/**
	 * Update Build simple API.
	 * This API only updates build {@see com.modeln.batam.connector.wrapper.Commit commits}.
	 * 
	 * @param id : Build id (required if name not provided).
	 * @param name : Build Name (required if id not provided and if build has a unique name among all builds ).
	 * @param commits : Build Commits, List of {@see com.modeln.batam.connector.wrapper.Commit commits}.
	 * @return published message.
	 * @throws IOException
	 */
	public String addBuildCommits(String id, String name, List<Commit> commits) throws IOException {
		
		if(id == null && name == null){
			throw new InvalidArgumentException("At least one of the fields id and name should be provided.");
		}
		
		BuildEntry build = new BuildEntry(id, name, null, null, null, null, null, null, null, null, commits, false);
		
		return updateBuild(build);
	}

	/**
	 * Update Build simple API.
	 * This API only updates build {@see com.modeln.batam.connector.wrapper.Pair infos}.
	 * 
	 * @param id : Build id (required if name not provided).
	 * @param name : Build Name (required if id not provided and if build has a unique name among all builds ).
	 * @param infos : Build Infos, List of {@see com.modeln.batam.connector.wrapper.Info infos}.
	 * @return published message.
	 * @throws IOException
	 */
	public String addBuildInfos(String id, String name, List<Pair> infos) throws IOException {
		
		if(id == null && name == null){
			throw new InvalidArgumentException("At least one of the fields id and name should be provided.");
		}
		
		BuildEntry build = new BuildEntry(id, name, null, null, null, null, null, infos, null, null, null, false);
		
		return updateBuild(build);
	}

	/**
	 * Update Build simple API.
	 * This API only updates build {@see com.modeln.batam.connector.wrapper.Pair reports}.
	 * 
	 * @param id : Build id (required if name not provided).
	 * @param name : Build Name (required if id not provided and if build has a unique name among all builds ).
	 * @param reports : Build Reports, List of {@see com.modeln.batam.connector.wrapper.Pair reports}.
	 * @return published message.
	 * @throws IOException
	 */
	public String addBuildReports(String id, String name, List<Pair> reports) throws IOException {
		
		if(id == null && name == null){
			throw new InvalidArgumentException("At least one of the fields id and name should be provided.");
		}
		
		BuildEntry build = new BuildEntry(id, name, null, null, null, null, null, null, reports, null, null, false);
		
		return updateBuild(build);
	}

	/**
	 * Update Build simple API.
	 * This API only updates build {@see com.modeln.batam.connector.wrapper.Pair reports}.
	 * 
	 * @param id : Build id (required if name not provided).
	 * @param name : Build Name (required if id not provided and if build has a unique name among all builds ).
	 * @param steps : Build Steps, List of {@see com.modeln.batam.connector.wrapper.Step steps}.
	 * @return published message.
	 * @throws IOException
	 */
	public String addBuildSteps(String id, String name, List<Step> steps) throws IOException {
		
		if(id == null && name == null){
			throw new InvalidArgumentException("At least one of the fields id and name should be provided.");
		}
		
		BuildEntry build = new BuildEntry(id, name, null, null, null, null, null, null, null, steps, null, false);
		
		return updateBuild(build);
	}

	/**
	 * Update Build simple API.
	 * This API only updates build end date.
	 * 
	 * @param id : Build id (required if name not provided).
	 * @param name : Build Name (required if id not provided and if build has a unique name among all builds ).
	 * @param endDate : Build End Date.
	 * @return published message.
	 * @throws IOException
	 */
	public String updateBuildEndDate(String id, String name, Date endDate) throws IOException {
	
		if(id == null && name == null){
			throw new InvalidArgumentException("At least one of the fields id and name should be provided.");
		}
		
		BuildEntry build = new BuildEntry(id, name, null, endDate, null, null, null, null, null, null, null, false);
		
		return updateBuild(build);
	}

	/**
	 * Update Build simple API.
	 * This API only updates build status.
	 * 
	 * @param id : Build id (required if name not provided).
	 * @param name : Build Name (required if id not provided and if build has a unique name among all builds ).
	 * @param status : Build status.
	 * @return published message.
	 * @throws IOException
	 */
	public String updateBuildStatus(String id, String name, String status) throws IOException {
		
		if(id == null && name == null){
			throw new InvalidArgumentException("At least one of the fields id and name should be provided.");
		}
		
		BuildEntry build = new BuildEntry(id, name, null, null, status, null, null, null, null, null, null, false);
		
		return updateBuild(build);
	}

	/**
	 * Run Analysis simple API.
	 * 
	 * @param id : Build id (required if name not provided).
	 * @param name : Build Name (required if id not provided and if build has a unique name among all builds ).
	 * @param override : false if the build is new, Otherwise true if test have been overriding previous test results from already analyzed build.
	 * @return published message.
	 * @throws IOException
	 */
	public String runAnalysis(String id, String name, boolean override) throws IOException {
		
		if(id == null && name == null){
			throw new InvalidArgumentException("At least one of the fields id and name should be provided.");
		}
		
		BuildEntry build = new BuildEntry(id, name, null, null, null, null, null, null, null, null, null, override);
		
		return runAnalysis(build);
	}

	/**
	 * Create Report simple API with individual {@link com.modeln.batam.connector.wrapper.ReportEntry ReportEntry} parameters.
	 * 
	 * @param id : Report id (required if name not provided).
	 * @param name : Report Name (required if id not provided and if report has a unique name in your build).
	 * @param buildId : buildId (required if id and buildName not provided).
	 * @param buildName : buildName (required if id and buildId not provided and if build has a unique name among all builds).
	 * @param description : Report Description.
	 * @param startDate : Report Start Date.
	 * @param endDate : Report End Date.
	 * @param status : Report Status.
	 * @param logs : Report logs, List of {@see java.lang.String String}.
	 * @return published message.
	 * @throws IOException
	 */
	public String createReport(String id, 
			String name,
			String buildId, 
			String buildName, 
			String description, 
			Date startDate,
			Date endDate,
			String status, 
			List<String> logs) throws IOException {

		return createReport(id, name, buildId, buildName, description, startDate, endDate, status, logs, false, null, null);
	};

	public String createReport(String id,
							   String name,
							   String buildId,
							   String buildName,
							   String description,
							   Date startDate,
							   Date endDate,
							   String status,
							   List<String> logs,
							   boolean isCustomFormatEnabled,
							   String format,
							   String customEntry) throws IOException {

		return createReport(id, name, buildId, buildName, description, startDate, endDate, status, logs, isCustomFormatEnabled, format, customEntry, null, null);
	}

	public String createReport(String id,
							   String name,
							   String buildId,
							   String buildName,
							   String description,
							   Date startDate,
							   Date endDate,
							   String status,
							   List<String> logs,
							   boolean isCustomFormatEnabled,
							   String format,
							   String customEntry, String screenshotURL, Map<String, String> customAttributes) throws IOException {

		if(name == null){
			throw new InvalidArgumentException("name field should not be null.");
		}
		if(buildId == null && buildName == null){
			throw new InvalidArgumentException("At least one of the fields buildId and buildName should be provided.");
		}
		if(isCustomFormatEnabled && (format == null ||  !validateCustomFormat(format)))  {
			throw new InvalidArgumentException("CustomFormat provided is not a valid one");
		}

		ReportEntry report = new ReportEntry(id, name, buildId, buildName, description, startDate, endDate, status, logs, isCustomFormatEnabled, format, customEntry, screenshotURL, customAttributes);

		return createReport(report);
	}
	
	/**
	 * Update Report simple API.
	 * This API only updates report logs link list.
	 * 
	 * @param id : Report id (required if name not provided).
	 * @param name : Report Name (required if id not provided and if report has a unique name in your build).
	 * @param buildId : buildId (required if id and buildName not provided).
	 * @param buildName : buildName (required if id and buildId not provided and if build has a unique name among all builds).
	 * @param logs : Build Logs links, List of {@see java.lang.String String}.
	 * @return published message.
	 * @throws IOException
	 */
	public String addReportLogs(String id, String name, String buildId, String buildName, List<String> logs) throws IOException {
		
		if(id == null && name == null && buildId == null && buildName == null){
			throw new InvalidArgumentException("At least id field should be provided.");
		}
		if(id == null && name == null){
			throw new InvalidArgumentException("At least one of the fields id and name should be provided.");
		}
		if(id == null && buildId == null && buildName == null){
			throw new InvalidArgumentException("At least one of the fields buildId and buildName should be provided.");
		}
		
		ReportEntry report = new ReportEntry(id, name, buildId, buildName, null, null, null, null, logs);
		
		return updateReport(report);
	}

	/**
	 * Update Report simple API.
	 * This API only updates report status.
	 * 
	 * @param id : Report id (required if name not provided).
	 * @param name : Report Name (required if id not provided and if report has a unique name in your build).
	 * @param buildId : buildId (required if id and buildName not provided).
	 * @param buildName : buildName (required if id and buildId not provided and if build has a unique name among all builds).
	 * @param status : Build Status.
	 * @return published message.
	 * @throws IOException
	 */
	public String updateReportStatus(String id, String name, String buildId, String buildName, String status) throws IOException {
		
		if(id == null && name == null && buildId == null && buildName == null){
			throw new InvalidArgumentException("At least id field should be provided.");
		}
		if(id == null && name == null){
			throw new InvalidArgumentException("At least one of the fields id and name should be provided.");
		}
		if(id == null && buildId == null && buildName == null){
			throw new InvalidArgumentException("At least one of the fields buildId and buildName should be provided.");
		}
		
		ReportEntry report = new ReportEntry(id, name, buildId, buildName, null, null, null, status, null);
		
		return updateReport(report);
	}
	
	/**
	 * Update Report simple API.
	 * This API only updates report end date.
	 * 
	 * @param id : Report id (required if name not provided).
	 * @param name : Report Name (required if id not provided and if report has a unique name in your build).
	 * @param buildId : buildId (required if id and buildName not provided).
	 * @param buildName : buildName (required if id and buildId not provided and if build has a unique name among all builds).
	 * @param endDate : Build End date.
	 * @return published message.
	 * @throws IOException
	 */
	public String updateReportEndDate(String id, String name, String buildId, String buildName, Date endDate) throws IOException {

		if(id == null && name == null && buildId == null && buildName == null){
			throw new InvalidArgumentException("At least id field should be provided.");
		}
		if(id == null && name == null){
			throw new InvalidArgumentException("At least one of the fields id and name should be provided.");
		}
		if(id == null && buildId == null && buildName == null){
			throw new InvalidArgumentException("At least one of the fields buildId and buildName should be provided.");
		}
		
		ReportEntry report = new ReportEntry(id, name, buildId, buildName, null, null, endDate, null, null);
		
		return updateReport(report);
	}

	/**
	 * Create Test simple API with individual {@link com.modeln.batam.connector.wrapper.TestEntry TestEntry} parameters.
	 * 
	 * Identification: There are various ways to specify where the test should be created.
	 * Option 1: Provide the reportId only (buildId, buildName and reportName become optional).
	 * Option 2: If reportName is unique in your build, provide the reportName and the buildId (buildName and reportId become optional)
	 * Option 3: If reportName is unique in your build and buildName is unique among your builds, provide the reportName and the buildName (buildId and reportId become optional)
	 * 
	 * @param buildId : Test Build Id.
	 * @param buildName : Test Build Name.
	 * @param reportId : Test Report Id.
	 * @param reportName : Test Report Name.
	 * @param name : Test Name (required).
	 * @param description : Test Description.
	 * @param startDate : Test Start Date.
	 * @param endDate : Test End Date.
	 * @param status : Test Status.
	 * @param criterias : Test Criterias, List of {@see com.modeln.batam.connector.wrapper.Pair criterias}.
	 * @param tags : Test Tags, List of {@see java.lang.String tags}.
	 * @param steps: Test Steps, List of {@see com.modeln.batam.connector.wrapper.Step steps}.
	 * @param log : Test log (raw logs).
	 * @return published message.
	 * @throws IOException
	 */
	public String createTest(String buildId,
			String buildName,
			String reportId,
			String reportName,
			String name, 
			String description, 
			Date startDate, 
			Date endDate, 
			String status, 
			List<Pair> criterias,
			List<String> tags,
			List<Step> steps,
			String log) throws IOException {
		
		if(name == null){
			throw new InvalidArgumentException("Name field is required.");
		}
		if(reportId == null && reportName == null){
			throw new InvalidArgumentException("At least one of the fields reportId and reportName should be provided.");
		}
		
		TestEntry test = new TestEntry(null, buildId, buildName, reportId, reportName, name, description, startDate, endDate, status, criterias, tags, steps, log, false);
		
		return createTest(test);
	}
	
	/**
	 * Update Test simple API.
	 * 
	 * Identification: There are various ways to find which test to update.
	 * Option 1: Provide the test Id only (buildId, buildName, reportId and reportName and name become optional).
	 * Option 2: If test name is unique in your report, provide the name and the reportId (buildName, buildId, reportName and id become optional)
	 * Option 3: If test name is unique in your report and reportName is unique in your build, provide the name and the reportName (buildName, buildId, reportId and id become optional)
	 * Option 4: If test name is unique in your report and reportName is unique in your build and buildName is unique among your builds, provide the name, reportName and the buildName (buildId and reportId and id become optional)
	 * 
	 * @param id : Test Id.
	 * @param buildId : Test Build Id.
	 * @param buildName : Test Build Name.
	 * @param reportId : Test Report Id.
	 * @param reportName : Test Report Name.
	 * @param name : Test Name.
	 * @param description : Test Description.
	 * @param startDate : Test Start Date.
	 * @param endDate : Test End Date.
	 * @param status : Test Status.
	 * @param criterias : Test Criterias, List of {@see com.modeln.batam.connector.wrapper.Pair criterias}.
	 * @param tags  : Test Tags, List of {@see java.lang.String tags}.
	 * @param steps : Test Steps, List of {@see com.modeln.batam.connector.wrapper.Step steps}.
	 * @param log : Test log (raw logs)
	 * @param override : true if update must be applied to previous analyzed build test. Otherwise false.
	 * @return published message.
	 * @throws IOException
	 */
	public String updateTest(String id,
			String buildId,
			String buildName,
			String reportId,
			String reportName,
			String name, 
			String description, 
			Date startDate, 
			Date endDate, 
			String status, 
			List<Pair> criterias,
			List<String> tags,
			List<Step> steps,
			String log,
			boolean override) throws IOException {

		return updateTest(id, buildId, buildName, reportId, reportName, name, description, startDate, endDate, status,
				criterias, tags, steps, log, override, false, null, null);
	}


	public String updateTest(String id,
							 String buildId,
							 String buildName,
							 String reportId,
							 String reportName,
							 String name,
							 String description,
							 Date startDate,
							 Date endDate,
							 String status,
							 List<Pair> criterias,
							 List<String> tags,
							 List<Step> steps,
							 String log,
							 boolean override,
							 boolean isCustomFormatEnabled,
							 String customFormat,
							 String customEntry) throws IOException {
		return updateTest(id, buildId, buildName, reportId, reportName, name, description, startDate, endDate, status,
                criterias, tags, steps, log, override, isCustomFormatEnabled, customFormat, customEntry, null, null, null, null, null, null, null, null, null, null);
	}

    public String updateTest(String id,
                             String buildId,
                             String buildName,
                             String reportId,
                             String reportName,
                             String name,
                             String description,
                             Date startDate,
                             Date endDate,
                             String status,
                             List<Pair> criterias,
                             List<String> tags,
                             List<Step> steps,
                             String log,
                             boolean override,
                             boolean isCustomFormatEnabled,
                             String customFormat,
                             String customEntry, String jiraTestID, String jiraReqID, String executionType, Map<String, String> customAttributes,
                             String authoredBy, String dateCreated, String approvalStatus, String approvedBy, String approvedDate, String comments) throws IOException {

		if(name == null){
			throw new InvalidArgumentException("Name field is required.");
		}
		if(reportId == null && reportName == null){
			throw new InvalidArgumentException("At least one of the fields reportId and reportName should be provided.");
		}

		if(isCustomFormatEnabled && (customFormat == null ||  !validateCustomFormat(customFormat)))  {
			throw new InvalidArgumentException("CustomFormat provided is not a valid one");
		}

        TestEntry test = new TestEntry
                        (id, buildId, buildName, reportId, reportName, name, description, startDate, endDate, status,
                        criterias, tags, steps, log, override, isCustomFormatEnabled, customFormat, customEntry,
                                jiraTestID, jiraReqID, executionType, customAttributes, authoredBy, dateCreated, approvalStatus, approvedBy, approvedDate, comments);

		return updateTest(test);
	}

	/**
	 * Returns true if the customFormat is any of these {STANDARD_FORMAT, UPGRADE_FORMAT, PERFORMANCE_FORMAT} values else it returns false
	 * @param customFormat
	 * @return
	 */
	private  boolean validateCustomFormat(String customFormat)  {
		if(customFormat.equals("STANDARD_FORMAT") || customFormat.equals("UPGRADE_FORMAT") || customFormat.equals("PERFORMANCE_FORMAT")) {
			return true;
		}
		return false;
	}
}
