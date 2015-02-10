/**
 *
 */
package org.irods.jargon.datautils.connectiontester;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.DataTransferOperations;
import org.irods.jargon.core.pub.IRODSAccessObjectFactory;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.service.AbstractJargonService;
import org.irods.jargon.datautils.connectiontester.TestResultEntry.OperationType;
import org.irods.jargon.testutils.TestingUtilsException;
import org.irods.jargon.testutils.filemanip.FileGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mike Conway - DICE
 *
 */
public class ConnectionTesterImpl extends AbstractJargonService implements
		ConnectionTester {

	public static final Logger log = LoggerFactory
			.getLogger(ConnectionTesterImpl.class);

	private final ConnectionTesterConfiguration connectionTesterConfiguration;

	/**
	 * @param irodsAccessObjectFactory
	 * @param irodsAccount
	 */
	public ConnectionTesterImpl(
			IRODSAccessObjectFactory irodsAccessObjectFactory,
			IRODSAccount irodsAccount,
			ConnectionTesterConfiguration connectionTesterConfiguration) {
		super(irodsAccessObjectFactory, irodsAccount);

		if (connectionTesterConfiguration == null) {
			throw new IllegalArgumentException(
					"null connectionTesterConfiguration");
		}

		this.connectionTesterConfiguration = connectionTesterConfiguration;
	}

	/**
	 * Run the given tests in the list, returning a result
	 *
	 * @param testTypes
	 *            <code>List</code> of type {
	 * @TestType
	 * @return {@link ConnectionTestResult}
	 * @throws JargonException
	 */
	@Override
	public ConnectionTestResult runTests(final List<TestType> testTypes)
			throws JargonException {

		log.info("runTests{}");

		if (testTypes == null || testTypes.isEmpty()) {
			throw new IllegalArgumentException("null or empty testTypes");
		}

		ConnectionTestResult testResult = new ConnectionTestResult();
		testResult.setConfiguration(connectionTesterConfiguration);
		testResult.setIrodsAccount(getIrodsAccount());

		for (TestType testType : testTypes) {
			List<TestResultEntry> individualTestResults = processTest(testType);

			testResult.getTestResults().addAll(individualTestResults);

		}

		return testResult;

	}

	/**
	 * Do a put and get and return the results
	 *
	 * @param testType
	 * @return
	 * @throws JargonException
	 */
	private List<TestResultEntry> processTest(TestType testType)
			throws JargonException {

		log.info("processTest:{}", testType);
		List<TestResultEntry> entries = new ArrayList<TestResultEntry>();

		log.info("processTest() for type:{}", testType);
		TestResultEntry result = new TestResultEntry();

		result.setTestType(testType);

		long dataSize = 0L;
		String prefix = "testFile";
		String suffix = ".dat";

		switch (testType) {
		case SMALL:
			dataSize = 100 * 1024;
			break;
		case MEDIUM:
			dataSize = 2 * 1024 * 1024;
			break;
		case LARGE:
			dataSize = 40 * 1024 * 1024;
			break;
		case EXTRA_LARGE:
			dataSize = 1 * 1024 * 1024 * 1024;
		}

		log.info("dataSize:{}", dataSize);

		result.setTotalBytes(dataSize);

		StringBuilder sb = new StringBuilder();
		sb.append(prefix);
		sb.append(testType.name());
		sb.append(suffix);
		String testFileSourceName = sb.toString();
		boolean putSucceeded = true;
		File localFile = null;
		IRODSFile irodsFile = null;
		File localGetFile = null;

		try {
			localFile = new File(
					connectionTesterConfiguration
							.getLocalSourceParentDirectory(),
					testFileSourceName);
			log.info("delete previous files and generate a local file for:{}",
					localFile);
			localFile.delete();

			log.info("using configuration:{}", connectionTesterConfiguration);
			DataTransferOperations dataTransferOperations = this
					.getIrodsAccessObjectFactory().getDataTransferOperations(
							getIrodsAccount());

			FileGenerator.generateFileOfFixedLengthGivenName(
					connectionTesterConfiguration
							.getLocalSourceParentDirectory(),
					testFileSourceName, dataSize);
			log.info("test file generated at:{}", testFileSourceName);
			sb = new StringBuilder();
			sb.append("get");
			sb.append(testFileSourceName);
			String testFileGetName = sb.toString();
			localGetFile = new File(localFile.getParent(), testFileGetName);
			localGetFile.delete();
			long startTime = System.currentTimeMillis();

			irodsFile = this
					.getIrodsAccessObjectFactory()
					.getIRODSFileFactory(getIrodsAccount())
					.instanceIRODSFile(
							connectionTesterConfiguration
									.getIrodsParentDirectory(),
							testFileSourceName);
			log.info("delete old irods file:{}", irodsFile);
			irodsFile.deleteWithForceOption();

			log.info("initiating put operation to:{}",
					connectionTesterConfiguration.getIrodsParentDirectory());

			result.setOperationType(OperationType.PUT);

			dataTransferOperations.putOperation(localFile, irodsFile, null,
					null);

			long endTime = System.currentTimeMillis();
			result.setTotalMilliseconds(endTime - startTime);

			float totalSeconds = result.getTotalMilliseconds() / 1000;

			if (totalSeconds == 0) {
				totalSeconds = 1;
			}

			result.setTransferRateBytesPerSecond((int) (dataSize / totalSeconds));
			result.setSuccess(true);
			entries.add(result);
			if (!putSucceeded) {
				log.warn("put failed, do not try get");
				return entries;
			}

			// put succeeded, continue
			log.info("result for put done, do a get");

			result = new TestResultEntry();

			result.setTestType(testType);

			result.setTotalBytes(dataSize);
			result.setOperationType(OperationType.GET);

			startTime = System.currentTimeMillis();

			try {
				dataTransferOperations.getOperation(irodsFile, localGetFile,
						null, null);
			} catch (Exception e) {
				log.error("exception in transfer reported back in status", e);
				result.setException(e);
				result.setSuccess(false);
				entries.add(result);
				return entries;
			}

			endTime = System.currentTimeMillis();
			result.setTotalMilliseconds(endTime - startTime);
			totalSeconds = result.getTotalMilliseconds() / 1000;

			if (totalSeconds == 0) {
				totalSeconds = 1;
			}

			result.setTransferRateBytesPerSecond((int) (dataSize / totalSeconds));
			result.setSuccess(true);
			entries.add(result);
			return entries;

		} catch (TestingUtilsException e) {
			log.error("error generating local file", e);
			putSucceeded = false;
			result.setException(e);
			result.setSuccess(false);
			entries.add(result);
			return entries;

		} catch (Exception e) {
			log.error("error generating local file", e);
			putSucceeded = false;
			result.setException(e);
			result.setSuccess(false);
			entries.add(result);
			return entries;
		} finally {

			if (this.connectionTesterConfiguration.isCleanupOnCompletion()) {
				log.info("cleanup");
				try {
					localFile.delete();
				} catch (Exception e) {
					// ignore
				}
				try {
					localGetFile.delete();
				} catch (Exception e) {
					// ignore
				}
				try {
					irodsFile.deleteWithForceOption();
				} catch (Exception e) {
					// ignore
				}
			}
		}

	}
}