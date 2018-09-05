package org.apache.qpid.dispatch.jmstest;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class IDEPathHelper {

	public static Path gatlingConfigurationUrl(){
		try {
			return Paths.get(Thread.currentThread().getContextClassLoader().getResource("gatling.conf").toURI());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public static Path projectRootDirectory(){
		return gatlingConfigurationUrl().getParent().getParent().getParent();
	}

	public static Path projectTestResourcesDirectory(){
		return projectRootDirectory().resolve("src").resolve("test").resolve("resources");
	}

	public static Path projectBuildDirectory(){
		return projectRootDirectory().resolve("target");
	}


	public static Path projectTestOutputDirectory(){
		return projectBuildDirectory().resolve("test-classes");
	}

	public static Path gatlingDataDirectory(){
		return projectTestResourcesDirectory().resolve("data");
	}

	public static Path gatlingBodiesDirectory(){
		return projectTestResourcesDirectory().resolve("bodies");
	}

	public static Path gatlingReportDirectory(){
		return projectBuildDirectory().resolve("gatling-reports");
	}

}
