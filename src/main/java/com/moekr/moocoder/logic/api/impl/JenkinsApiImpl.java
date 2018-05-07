package com.moekr.moocoder.logic.api.impl;

import com.moekr.moocoder.logic.api.JenkinsApi;
import com.moekr.moocoder.logic.api.vo.BuildDetails;
import com.moekr.moocoder.logic.api.vo.CoberturaResult;
import com.moekr.moocoder.logic.api.vo.ExecutableDetails;
import com.moekr.moocoder.util.ApplicationProperties;
import com.moekr.moocoder.util.ApplicationProperties.Jenkins;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.Executable;
import com.offbytwo.jenkins.model.QueueItem;
import com.offbytwo.jenkins.model.QueueReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

@Component
public class JenkinsApiImpl implements JenkinsApi {
	private final ApplicationProperties properties;

	private JenkinsServer server;
	private String configTemplate;

	public JenkinsApiImpl(ApplicationProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	private void initialize() throws URISyntaxException, IOException {
		Jenkins jenkins = properties.getJenkins();
		server = new JenkinsServer(new URI(jenkins.getHost()), jenkins.getUsername(), jenkins.getToken());
		configTemplate = readTemplate();
	}

	@Override
	public void createJob(int id) throws IOException {
		server.createJob(String.valueOf(id), configTemplate);
	}

	@Override
	public QueueItem invokeBuild(int id, Map<String, String> paramMap) throws IOException {
		QueueReference reference;
		if (paramMap == null) {
			reference = server.getJob(String.valueOf(id)).build();
		} else {
			// 0.3.7版本的Jenkins客户端库中build(Map<String, String> params)方法存在BUG，会触发两次构建
			reference = server.getJob(String.valueOf(id)).build(paramMap, false);
		}
		QueueItem item =  server.getQueueItem(reference);
		try {
			// 等待一段时间，避免对executable的请求返回404
			Thread.sleep(500);
		} catch (InterruptedException ignore) { }
		ExecutableDetails details = item.getClient().get(item.getUrl() + "executable", ExecutableDetails.class);
		Executable executable = new Executable();
		BeanUtils.copyProperties(details, executable);
		item.setExecutable(executable);
		return item;
	}

	@Override
	public void deleteJob(int id) throws IOException {
		server.deleteJob(String.valueOf(id));
	}

	@Override
	public BuildDetails fetchBuildDetails(int id, int buildNumber) throws IOException {
		BuildDetails buildDetails = new BuildDetails();
		BuildWithDetails build;
		build = server.getJob(String.valueOf(id)).getBuildByNumber(buildNumber).details();
		buildDetails.setConsoleOutput(build.getConsoleOutputText());
		buildDetails.setNumber(build.getNumber());
		buildDetails.setDuration(build.getDuration());
		buildDetails.setBuildResult(build.getResult());
		try {
			buildDetails.setTestResult(build.getTestResult());
		} catch (IOException e) {
			buildDetails.setTestResult(null);
		}
		try {
			buildDetails.setCoberturaResult(build.getClient().get(build.getUrl() + "/cobertura/?depth=2", CoberturaResult.class));
		} catch (IOException e) {
			buildDetails.setCoberturaResult(null);
		}
		return buildDetails;
	}

	private String readTemplate() throws IOException {
		StringBuilder stringBuilder = new StringBuilder();
		InputStream inputStream = JenkinsApiImpl.class.getClassLoader().getResourceAsStream("jenkins/config.xml");
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
		String buffer;
		while ((buffer = bufferedReader.readLine()) != null) {
			stringBuilder.append(buffer).append("\n");
		}
		return stringBuilder.toString();
	}
}