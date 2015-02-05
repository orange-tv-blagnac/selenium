/*
Copyright 2012 Selenium committers
Copyright 2012 Software Freedom Conservancy

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.openqa.selenium.server.htmlrunner;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openqa.jetty.http.HttpContext;
import org.openqa.jetty.http.HttpException;
import org.openqa.jetty.http.HttpHandler;
import org.openqa.jetty.http.HttpRequest;
import org.openqa.jetty.http.HttpResponse;
import org.openqa.jetty.util.StringUtil;

/**
 * Handles results of HTMLRunner (aka TestRunner, FITRunner) in automatic mode.
 * 
 * @author Dan Fabulich
 * @author Darren Cotterill
 * @author Ajit George
 * @author Olivier ETIENNE
 */
@SuppressWarnings("serial")
public class SeleniumHTMLRunnerResultsHandler implements HttpHandler {
	static Logger log = Logger.getLogger(SeleniumHTMLRunnerResultsHandler.class.getName());

	HttpContext context;
	List<HTMLResultsListener> listeners;
	boolean started = false;

	// private String serverExternalIp = "";

	public SeleniumHTMLRunnerResultsHandler() {
		listeners = new Vector<HTMLResultsListener>();
		// serverExternalIp = getServerIpAddress();
	}

	// private String getServerIpAddress() {
	// String result = "127.0.0.1";
	// try {
	// Enumeration<NetworkInterface> nets =
	// NetworkInterface.getNetworkInterfaces();
	// for (NetworkInterface netint : Collections.list(nets)) {
	// if (netint.getDisplayName().indexOf("eth0") != -1) {
	//
	// Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
	// for (InetAddress inetAddress : Collections.list(inetAddresses)) {
	// if (inetAddress instanceof Inet4Address) {
	// result = inetAddress.getHostAddress();
	// log.info("Local server address = *" + result + "*");
	// break;
	// }
	// }
	//
	// break;
	// }
	// }
	// } catch (Exception e) {
	// e.printStackTrace();
	// }
	// return result;
	// }

	public void addListener(HTMLResultsListener listener) {
		listeners.add(listener);
	}

	public void handle(String pathInContext, String pathParams, HttpRequest request, HttpResponse res)
			throws HttpException, IOException {
		if (!"/postResults".equals(pathInContext))
			return;

		request.setHandled(true);
		String postedLog = request.getParameter("log");
		String logToConsole = request.getParameter("logToConsole");
		String storeCrashReports = request.getParameter("storeCrashReports");

		if (logToConsole != null && !"".equals(logToConsole)) {

			String[] lines = postedLog.split("\n");
			for (String line : lines) {
				// line = line.replaceAll("__SERVERIP__", serverExternalIp);

				Level level = Level.INFO;

				if (line.indexOf("info: ") != -1) {
					line = line.replaceAll("info: ", "");
					level = Level.INFO;
				} else if (line.indexOf("warn: ") != -1) {
					line = line.replaceAll("warn: ", "");
					level = Level.WARNING;
				} else if (line.indexOf("error: ") != -1) {
					level = Level.SEVERE;
				}
				log.log(level, line);

			}

			// Log to console
			OutputStream out = res.getOutputStream();
			Writer writer = new OutputStreamWriter(out, StringUtil.__ISO_8859_1);
			writer.write("OK");
		} else if (storeCrashReports != null && !"".equals(storeCrashReports)) {
			String testSuiteName = request.getParameter("crashReportsTestSuite");
			String testCaseName = request.getParameter("crashReportsTestCase");
			String timeStamp = request.getParameter("crashReportsTimeStamp");

			for (Iterator<HTMLResultsListener> i = listeners.iterator(); i.hasNext();) {
				HTMLResultsListener listener = i.next();
				listener.processStoreReport(testSuiteName, testCaseName, timeStamp);
			}

		} else {
			log.info("Received posted results");
			String seleniumVersion = request.getParameter("selenium.version");
			String seleniumRevision = request.getParameter("selenium.revision");
			String totalTime = request.getParameter("totalTime");
			String numTestTotal = request.getParameter("numTestTotal");
			String numTestPasses = request.getParameter("numTestPasses");
			String numTestFailures = request.getParameter("numTestFailures");
			String numCommandPasses = request.getParameter("numCommandPasses");
			String numCommandFailures = request.getParameter("numCommandFailures");
			String numCommandErrors = request.getParameter("numCommandErrors");
			String suite = request.getParameter("suite");
			String result = request.getParameter("result");
			if (result == null) {
				res.getOutputStream().write("No result was specified!".getBytes());
			}
			int numTotalTests = Integer.parseInt(numTestTotal);

			List<String> testTables = createTestTables(request, numTotalTests);

			// Replace local server address
			// if (result != null) {
			// result = result.replaceAll("__SERVERIP__", serverExternalIp);
			// }

			// postedLog = postedLog.replaceAll("__SERVERIP__",
			// serverExternalIp);
			// suite = suite.replaceAll("__SERVERIP__", serverExternalIp);

			HTMLTestResults results = new HTMLTestResults(seleniumVersion, seleniumRevision, result, totalTime,
					numTestTotal, numTestPasses, numTestFailures, numCommandPasses, numCommandFailures,
					numCommandErrors, suite, testTables, postedLog);

			for (Iterator<HTMLResultsListener> i = listeners.iterator(); i.hasNext();) {
				HTMLResultsListener listener = i.next();
				listener.processResults(results);
				i.remove();
			}
			processResults(results, res);
		}
	}

	/** Print the test results out to the HTML response */
	private void processResults(HTMLTestResults results, HttpResponse res) throws IOException {
		res.setContentType("text/html");
		OutputStream out = res.getOutputStream();
		Writer writer = new OutputStreamWriter(out, StringUtil.__ISO_8859_1);
		results.write(writer);
		writer.flush();
	}

	private List<String> createTestTables(HttpRequest request, int numTotalTests) {
		List<String> testTables = new LinkedList<String>();
		for (int i = 1; i <= numTotalTests; i++) {
			String testTable = request.getParameter("testTable." + i);
			// testTable = testTable.replaceAll("__SERVERIP__",
			// serverExternalIp);
			// System.out.println("table " + i);
			// System.out.println(testTable);
			testTables.add(testTable);
		}
		return testTables;
	}

	public String getName() {
		return SeleniumHTMLRunnerResultsHandler.class.getName();
	}

	public HttpContext getHttpContext() {
		return context;
	}

	public void initialize(HttpContext c) {
		this.context = c;

	}

	public void start() throws Exception {
		started = true;
	}

	public void stop() throws InterruptedException {
		started = false;
	}

	public boolean isStarted() {
		return started;
	}
}
