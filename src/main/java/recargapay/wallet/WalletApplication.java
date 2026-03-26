package recargapay.wallet;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WalletApplication {
	private static final Logger log = LoggerFactory.getLogger(WalletApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(WalletApplication.class, args);
	}

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		log.info("Default JVM timezone configured to UTC");
	}

}
