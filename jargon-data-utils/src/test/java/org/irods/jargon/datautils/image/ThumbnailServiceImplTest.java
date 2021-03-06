package org.irods.jargon.datautils.image;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64InputStream;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.pub.CollectionAndDataObjectListAndSearchAO;
import org.irods.jargon.core.pub.EnvironmentalInfoAO;
import org.irods.jargon.core.pub.IRODSAccessObjectFactory;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.RuleProcessingAO;
import org.irods.jargon.core.pub.domain.ObjStat;
import org.irods.jargon.core.pub.domain.RemoteCommandInformation;
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry.ObjectType;
import org.irods.jargon.core.rule.IRODSRule;
import org.irods.jargon.core.rule.IRODSRuleExecResult;
import org.irods.jargon.core.rule.IRODSRuleExecResultOutputParameter;
import org.irods.jargon.core.rule.IRODSRuleExecResultOutputParameter.OutputParamType;
import org.irods.jargon.core.rule.IRODSRuleParameter;
import org.irods.jargon.core.rule.RuleInvocationConfiguration;
import org.irods.jargon.testutils.TestingPropertiesHelper;
import org.irods.jargon.testutils.filemanip.ScratchFileUtils;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

import junit.framework.Assert;

public class ThumbnailServiceImplTest {

	private static Properties testingProperties = new Properties();
	private static TestingPropertiesHelper testingPropertiesHelper = new TestingPropertiesHelper();
	private static IRODSFileSystem irodsFileSystem;
	public static final String IRODS_TEST_SUBDIR_PATH = "ThumbnailServiceImplTest";
	private static org.irods.jargon.testutils.IRODSTestSetupUtilities irodsTestSetupUtilities = null;
	private static final String IMAGE_FILE_NAME = "img/irodsimg.png";
	private static ScratchFileUtils scratchFileUtils = null;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		TestingPropertiesHelper testingPropertiesLoader = new TestingPropertiesHelper();
		testingProperties = testingPropertiesLoader.getTestProperties();
		scratchFileUtils = new ScratchFileUtils(testingProperties);
		scratchFileUtils.clearAndReinitializeScratchDirectory(IRODS_TEST_SUBDIR_PATH);
		irodsFileSystem = IRODSFileSystem.instance();
		irodsTestSetupUtilities = new org.irods.jargon.testutils.IRODSTestSetupUtilities();
		irodsTestSetupUtilities.clearIrodsScratchDirectory();
		irodsTestSetupUtilities.initializeIrodsScratchDirectory();
		irodsTestSetupUtilities.initializeDirectoryForTest(IRODS_TEST_SUBDIR_PATH);
	}

	@After
	public void tearDown() throws Exception {
		irodsFileSystem.closeAndEatExceptions();
	}

	@Test
	public void testGenerateThumbnailForIRODSPath() throws Exception {

		/*
		 * Grab the test image, base64 encode as a string and emulate return from iRODS.
		 */
		String testDir = "testGenerateThumbnailForIRODSPathWorkingDir";
		String sourceIRODSPath = "/source/irods/path/image.png";

		String absPath = scratchFileUtils.createAndReturnAbsoluteScratchPath(IRODS_TEST_SUBDIR_PATH + "/" + testDir);
		File workingDirAsFile = new File(absPath);

		ClassLoader loader = this.getClass().getClassLoader();
		InputStream sourceStream = loader.getResourceAsStream(IMAGE_FILE_NAME);

		InputStreamReader testInput = new InputStreamReader(new Base64InputStream(sourceStream, true));

		// Base64 encoded stream dumped into string

		String dataAsString = null;

		StringWriter writer = new StringWriter();
		char[] buffer = new char[1024];
		int len = testInput.read(buffer);
		while (len != -1) {
			writer.write(buffer, 0, len);
			len = testInput.read(buffer);
		}

		dataAsString = writer.toString();

		testInput.close();
		writer.close();

		/*
		 * build mock framework to emulate rule running
		 */

		String testRule = "testRule";
		String fileParam = ThumbnailService.THUMBNAIL_RULE_DATA_PARAMETER;
		List<IRODSRuleParameter> inputParms = new ArrayList<IRODSRuleParameter>();
		List<IRODSRuleParameter> outputParms = new ArrayList<IRODSRuleParameter>();
		IRODSRuleParameter outputParm = new IRODSRuleParameter(fileParam);
		outputParms.add(outputParm);
		RuleInvocationConfiguration irodsRuleInvocationConfiguration = new RuleInvocationConfiguration();

		IRODSRule irodsRule = IRODSRule.instance(testRule, inputParms, inputParms, "body",
				irodsRuleInvocationConfiguration);

		IRODSRuleExecResultOutputParameter outputResultParm = IRODSRuleExecResultOutputParameter.instance(fileParam,
				OutputParamType.STRING, dataAsString);
		Map<String, IRODSRuleExecResultOutputParameter> outputResultParms = new HashMap<String, IRODSRuleExecResultOutputParameter>();
		outputResultParms.put(fileParam, outputResultParm);

		IRODSRuleExecResult result = IRODSRuleExecResult.instance(irodsRule, outputResultParms);

		IRODSAccount irodsAccount = testingPropertiesHelper.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory irodsAccessObjectFactory = Mockito.mock(IRODSAccessObjectFactory.class);
		RuleProcessingAO ruleProcessingAO = Mockito.mock(RuleProcessingAO.class);
		Mockito.when(ruleProcessingAO.executeRule(Matchers.any(String.class))).thenReturn(result);
		Mockito.when(irodsAccessObjectFactory.getRuleProcessingAO(irodsAccount)).thenReturn(ruleProcessingAO);

		CollectionAndDataObjectListAndSearchAO collectionAndDataObjectListAndSearchAO = Mockito
				.mock(CollectionAndDataObjectListAndSearchAO.class);
		Mockito.when(irodsAccessObjectFactory.getCollectionAndDataObjectListAndSearchAO(irodsAccount))
				.thenReturn(collectionAndDataObjectListAndSearchAO);

		ObjStat objStat = new ObjStat();
		objStat.setAbsolutePath(sourceIRODSPath);
		objStat.setObjectType(ObjectType.DATA_OBJECT);
		objStat.setCollectionPath(sourceIRODSPath);
		Mockito.when(collectionAndDataObjectListAndSearchAO.retrieveObjectStatForPath(sourceIRODSPath))
				.thenReturn(objStat);

		ThumbnailService thumbnailService = new ThumbnailServiceImpl(irodsAccessObjectFactory, irodsAccount);
		File actual = thumbnailService.generateThumbnailForIRODSPathViaRule(workingDirAsFile, sourceIRODSPath);
		Assert.assertNotNull("null file returned", actual);
		Assert.assertTrue("file does not exist as file", actual.exists() && actual.isFile());

	}

	@Test
	public void testIsIRODSThumbnailGeneratorAvailable() throws Exception {
		IRODSAccount irodsAccount = testingPropertiesHelper.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory irodsAccessObjectFactory = Mockito.mock(IRODSAccessObjectFactory.class);
		EnvironmentalInfoAO environmentalInfoAO = Mockito.mock(EnvironmentalInfoAO.class);
		List<RemoteCommandInformation> scripts = new ArrayList<RemoteCommandInformation>();
		RemoteCommandInformation info = new RemoteCommandInformation();
		info.setCommand("makeThumbnail.py");
		scripts.add(info);
		Mockito.when(environmentalInfoAO.listAvailableRemoteCommands()).thenReturn(scripts);
		Mockito.when(irodsAccessObjectFactory.getEnvironmentalInfoAO(irodsAccount)).thenReturn(environmentalInfoAO);
		ThumbnailService thumbnailService = new ThumbnailServiceImpl(irodsAccessObjectFactory, irodsAccount);
		boolean isThumbnailGenerator = thumbnailService.isIRODSThumbnailGeneratorAvailable();
		Assert.assertTrue("should have said yes to thumbnail service", isThumbnailGenerator);
	}

	@Test
	public void testIsIRODSThumbnailGeneratorNotAvailable() throws Exception {
		IRODSAccount irodsAccount = testingPropertiesHelper.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory irodsAccessObjectFactory = Mockito.mock(IRODSAccessObjectFactory.class);
		EnvironmentalInfoAO environmentalInfoAO = Mockito.mock(EnvironmentalInfoAO.class);
		List<RemoteCommandInformation> scripts = new ArrayList<RemoteCommandInformation>();
		RemoteCommandInformation info = new RemoteCommandInformation();
		info.setCommand("dontMakeThumbnail.py");
		scripts.add(info);
		Mockito.when(environmentalInfoAO.listAvailableRemoteCommands()).thenReturn(scripts);
		Mockito.when(irodsAccessObjectFactory.getEnvironmentalInfoAO(irodsAccount)).thenReturn(environmentalInfoAO);
		ThumbnailService thumbnailService = new ThumbnailServiceImpl(irodsAccessObjectFactory, irodsAccount);
		boolean isThumbnailGenerator = thumbnailService.isIRODSThumbnailGeneratorAvailable();
		Assert.assertFalse("should have said no to thumbnail service", isThumbnailGenerator);
	}

	@Test
	public void testGenerateThumbnailForIRODSPathTwice() throws Exception {

		/*
		 * Grab the test image, base64 encode as a string and emulate return from iRODS.
		 */
		String testDir = "testGenerateThumbnailForIRODSPathTwice";
		String sourceIRODSPath = "/source/irods/path/image.png";

		String absPath = scratchFileUtils.createAndReturnAbsoluteScratchPath(IRODS_TEST_SUBDIR_PATH + "/" + testDir);
		File workingDirAsFile = new File(absPath);

		ClassLoader loader = this.getClass().getClassLoader();
		InputStream sourceStream = loader.getResourceAsStream(IMAGE_FILE_NAME);

		InputStreamReader testInput = new InputStreamReader(new Base64InputStream(sourceStream, true));

		// Base64 encoded stream dumped into string

		String dataAsString = null;

		StringWriter writer = new StringWriter();
		char[] buffer = new char[1024];
		int len = testInput.read(buffer);
		while (len != -1) {
			writer.write(buffer, 0, len);
			len = testInput.read(buffer);
		}

		dataAsString = writer.toString();

		testInput.close();
		writer.close();

		/*
		 * build mock framework to emulate rule running
		 */

		String testRule = "testRule";
		String fileParam = ThumbnailService.THUMBNAIL_RULE_DATA_PARAMETER;
		List<IRODSRuleParameter> inputParms = new ArrayList<IRODSRuleParameter>();
		List<IRODSRuleParameter> outputParms = new ArrayList<IRODSRuleParameter>();
		IRODSRuleParameter outputParm = new IRODSRuleParameter(fileParam);
		outputParms.add(outputParm);
		RuleInvocationConfiguration irodsRuleInvocationConfiguration = new RuleInvocationConfiguration();

		IRODSRule irodsRule = IRODSRule.instance(testRule, inputParms, inputParms, "body",
				irodsRuleInvocationConfiguration);

		IRODSRuleExecResultOutputParameter outputResultParm = IRODSRuleExecResultOutputParameter.instance(fileParam,
				OutputParamType.STRING, dataAsString);
		Map<String, IRODSRuleExecResultOutputParameter> outputResultParms = new HashMap<String, IRODSRuleExecResultOutputParameter>();
		outputResultParms.put(fileParam, outputResultParm);

		IRODSRuleExecResult result = IRODSRuleExecResult.instance(irodsRule, outputResultParms);

		IRODSAccount irodsAccount = testingPropertiesHelper.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory irodsAccessObjectFactory = Mockito.mock(IRODSAccessObjectFactory.class);
		RuleProcessingAO ruleProcessingAO = Mockito.mock(RuleProcessingAO.class);
		Mockito.when(ruleProcessingAO.executeRule(Matchers.any(String.class))).thenReturn(result);
		Mockito.when(irodsAccessObjectFactory.getRuleProcessingAO(irodsAccount)).thenReturn(ruleProcessingAO);
		CollectionAndDataObjectListAndSearchAO collectionAndDataObjectListAndSearchAO = Mockito
				.mock(CollectionAndDataObjectListAndSearchAO.class);
		Mockito.when(irodsAccessObjectFactory.getCollectionAndDataObjectListAndSearchAO(irodsAccount))
				.thenReturn(collectionAndDataObjectListAndSearchAO);

		ObjStat objStat = new ObjStat();
		objStat.setAbsolutePath(sourceIRODSPath);
		objStat.setObjectType(ObjectType.DATA_OBJECT);
		objStat.setCollectionPath(sourceIRODSPath);
		Mockito.when(collectionAndDataObjectListAndSearchAO.retrieveObjectStatForPath(sourceIRODSPath))
				.thenReturn(objStat);

		ThumbnailService thumbnailService = new ThumbnailServiceImpl(irodsAccessObjectFactory, irodsAccount);
		File actual = thumbnailService.generateThumbnailForIRODSPathViaRule(workingDirAsFile, sourceIRODSPath);
		actual = thumbnailService.generateThumbnailForIRODSPathViaRule(workingDirAsFile, sourceIRODSPath);
		Assert.assertNotNull("null file returned", actual);
		Assert.assertTrue("file does not exist as file", actual.exists() && actual.isFile());

	}

	@Test(expected = IRODSThumbnailProcessUnavailableException.class)
	public void testGenerateThumbnailForIRODSPathNoService() throws Exception {

		/*
		 * Grab the test image, base64 encode as a string and emulate return from iRODS.
		 */
		String testDir = "testGenerateThumbnailForIRODSPathNoService";
		String sourceIRODSPath = "/source/irods/path/image.png";

		String absPath = scratchFileUtils.createAndReturnAbsoluteScratchPath(IRODS_TEST_SUBDIR_PATH + "/" + testDir);
		File workingDirAsFile = new File(absPath);

		ClassLoader loader = this.getClass().getClassLoader();
		InputStream sourceStream = loader.getResourceAsStream(IMAGE_FILE_NAME);

		InputStreamReader testInput = new InputStreamReader(new Base64InputStream(sourceStream, true));

		// Base64 encoded stream dumped into string

		StringWriter writer = new StringWriter();
		char[] buffer = new char[1024];
		int len = testInput.read(buffer);
		while (len != -1) {
			writer.write(buffer, 0, len);
			len = testInput.read(buffer);
		}

		writer.toString();

		testInput.close();
		writer.close();

		/*
		 * build mock framework to emulate rule running
		 */

		String testRule = "testRule";
		String fileParam = ThumbnailService.THUMBNAIL_RULE_DATA_PARAMETER;
		List<IRODSRuleParameter> inputParms = new ArrayList<IRODSRuleParameter>();
		List<IRODSRuleParameter> outputParms = new ArrayList<IRODSRuleParameter>();
		IRODSRuleParameter outputParm = new IRODSRuleParameter(fileParam);
		outputParms.add(outputParm);
		RuleInvocationConfiguration irodsRuleInvocationConfiguration = new RuleInvocationConfiguration();

		IRODSRule irodsRule = IRODSRule.instance(testRule, inputParms, inputParms, "body",
				irodsRuleInvocationConfiguration);

		Map<String, IRODSRuleExecResultOutputParameter> outputResultParms = new HashMap<String, IRODSRuleExecResultOutputParameter>();

		IRODSRuleExecResult result = IRODSRuleExecResult.instance(irodsRule, outputResultParms);

		IRODSAccount irodsAccount = testingPropertiesHelper.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory irodsAccessObjectFactory = Mockito.mock(IRODSAccessObjectFactory.class);
		RuleProcessingAO ruleProcessingAO = Mockito.mock(RuleProcessingAO.class);
		Mockito.when(ruleProcessingAO.executeRule(Matchers.any(String.class))).thenReturn(result);
		Mockito.when(irodsAccessObjectFactory.getRuleProcessingAO(irodsAccount)).thenReturn(ruleProcessingAO);

		CollectionAndDataObjectListAndSearchAO collectionAndDataObjectListAndSearchAO = Mockito
				.mock(CollectionAndDataObjectListAndSearchAO.class);
		Mockito.when(irodsAccessObjectFactory.getCollectionAndDataObjectListAndSearchAO(irodsAccount))
				.thenReturn(collectionAndDataObjectListAndSearchAO);

		ObjStat objStat = new ObjStat();
		objStat.setAbsolutePath(sourceIRODSPath);
		objStat.setObjectType(ObjectType.DATA_OBJECT);
		objStat.setCollectionPath(sourceIRODSPath);
		Mockito.when(collectionAndDataObjectListAndSearchAO.retrieveObjectStatForPath(sourceIRODSPath))
				.thenReturn(objStat);

		ThumbnailService thumbnailService = new ThumbnailServiceImpl(irodsAccessObjectFactory, irodsAccount);
		File actual = thumbnailService.generateThumbnailForIRODSPathViaRule(workingDirAsFile, sourceIRODSPath);
		Assert.assertNotNull("null file returned", actual);
		Assert.assertTrue("file does not exist as file", actual.exists() && actual.isFile());

	}
}
