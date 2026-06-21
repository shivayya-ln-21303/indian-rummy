package card_game_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 * Scans com.cardgame where the Indian Rummy implementation lives.
 */
@SpringBootApplication(scanBasePackages = "com.cardgame")
public class CardGameBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(CardGameBackendApplication.class, args);
    }
}
