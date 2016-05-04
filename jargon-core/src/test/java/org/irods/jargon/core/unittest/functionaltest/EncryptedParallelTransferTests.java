/**
 * 
 */
package org.irods.jargon.core.unittest.functionaltest;

import java.io.File;
import java.util.Properties;

import org.irods.jargon.core.connection.AuthScheme;
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.connection.JargonProperties;
import org.irods.jargon.core.connection.SettableJargonProperties;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.DataTransferOperations;
import org.irods.jargon.core.pub.IRODSAccessObjectFactory;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.testutils.IRODSTestAssertionException;
import org.irods.jargon.testutils.TestingPropertiesHelper;
import org.irods.jargon.testutils.TestingUtilsException;
import org.irods.jargon.testutils.filemanip.FileGenerator;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Functional tests of various permutations of ssl negotiation and auth methods.
 * These tests are contingent on the iRODS configuration and various testing
 * properties settings
 * 
 * @author Mike Conway - DICE
 *
 */
public class EncryptedParallelTransferTests {

	private static Properties testingProperties = new Properties();
	private static JargonProperties jargonOriginalProperties = null;
	private static org.irods.jargon.testutils.TestingPropertiesHelper testingPropertiesHelper = new TestingPropertiesHelper();
	private static org.irods.jargon.testutils.filemanip.ScratchFileUtils scratchFileUtils = null;
	public static final String IRODS_TEST_SUBDIR_PATH = "EncryptedParallelTransferTests";
	private static org.irods.jargon.testutils.IRODSTestSetupUtilities irodsTestSetupUtilities = null;
	private static org.irods.jargon.testutils.AssertionHelper assertionHelper = null;
	private static IRODSFileSystem irodsFileSystem = null;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		irodsFileSystem = IRODSFileSystem.instance();
		SettableJargonProperties settableJargonProperties = new SettableJargonProperties(
				irodsFileSystem.getJargonProperties());
		settableJargonProperties.setInternalCacheBufferSize(-1);
		settableJargonProperties.setInternalOutputStreamBufferSize(65535);
		jargonOriginalProperties = settableJargonProperties;
		irodsFileSystem.getIrodsSession().setJargonProperties(
				settableJargonProperties);
		org.irods.jargon.testutils.TestingPropertiesHelper testingPropertiesLoader = new TestingPropertiesHelper();
		testingProperties = testingPropertiesLoader.getTestProperties();
		scratchFileUtils = new org.irods.jargon.testutils.filemanip.ScratchFileUtils(
				testingProperties);
		scratchFileUtils
				.clearAndReinitializeScratchDirectory(IRODS_TEST_SUBDIR_PATH);
		irodsTestSetupUtilities = new org.irods.jargon.testutils.IRODSTestSetupUtilities();
		irodsTestSetupUtilities.clearIrodsScratchDirectory();
		irodsTestSetupUtilities.initializeIrodsScratchDirectory();
		irodsTestSetupUtilities
				.initializeDirectoryForTest(IRODS_TEST_SUBDIR_PATH);
		assertionHelper = new org.irods.jargon.testutils.AssertionHelper();
	}

	@AfterClass
	public static void afterClass() throws Exception {
		irodsFileSystem.closeAndEatExceptions();
	}

	@Before
	public void before() throws Exception {
		// be sure that normal parallel stuff is set up
		irodsFileSystem.getIrodsSession().setJargonProperties(
				jargonOriginalProperties);
	}

	@Test
	public void testParallelTransferWithAesEncryptionSet()
			throws JargonException, TestingUtilsException,
			IRODSTestAssertionException {

		/*
		 * Only run if ssl enabled
		 */
		if (!testingPropertiesHelper.isTestSsl(testingProperties)) {
			return;
		}

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		irodsAccount.setAuthenticationScheme(AuthScheme.STANDARD);

		SettableJargonProperties settableJargonProperties = new SettableJargonProperties(
				irodsFileSystem.getJargonProperties());
		settableJargonProperties
				.setNegotiationPolicy(SslNegotiationPolicy.CS_NEG_REQ);
		irodsFileSystem.getIrodsSession().setJargonProperties(
				settableJargonProperties);

		IRODSAccessObjectFactory accessObjectFactory = irodsFileSystem
				.getIRODSAccessObjectFactory();
		String testFileName = "testPutOneFile.txt";
		String absPath = scratchFileUtils
				.createAndReturnAbsoluteScratchPath(IRODS_TEST_SUBDIR_PATH);
		String localFileName = FileGenerator
				.generateFileOfFixedLengthGivenName(absPath, testFileName,
						34 * 1024 * 1024);

		String targetIrodsPath = testingPropertiesHelper
				.buildIRODSCollectionAbsolutePathFromTestProperties(
						testingProperties, IRODS_TEST_SUBDIR_PATH + '/'
								+ testFileName);
		File localFile = new File(localFileName);

		DataTransferOperations dataTransferOperationsAO = irodsFileSystem
				.getIRODSAccessObjectFactory().getDataTransferOperations(
						irodsAccount);

		dataTransferOperationsAO.putOperation(localFileName, targetIrodsPath,
				"", null, null);

	}

}
