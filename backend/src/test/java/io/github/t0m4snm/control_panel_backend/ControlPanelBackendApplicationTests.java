package io.github.t0m4snm.control_panel_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ControlPanelBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
