package org.juke.remix.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.ConfigurableEnvironment;


// KI-2 fix: scan org.juke.framework too so framework-side @Component
// beans (e.g. NoOpUiHarness in org.juke.framework.harness) are picked
// up. JukeHarnessConfiguration's activeUiHarness factory needs a
// non-empty List<UiHarness>; without the framework scan it resolved to
// [] and threw "no UiHarness with id 'none'".
@SpringBootApplication(scanBasePackages = {"org.juke.remix", "org.juke.framework"})
@EnableCaching
@EnableAspectJAutoProxy
@Profile("dev")
public class RemixBootstrapTestStarter implements CommandLineRunner {

	@Autowired
	private ConfigurableEnvironment env;
	
	private String appname="remix";
	public static void main(String[] args) {
		SpringApplication.run(RemixBootstrapTestStarter.class, args);
	}
	
	@Override
	public void run(String... args) throws Exception {
		String[] profiles = env.getActiveProfiles();
		
	}

}
