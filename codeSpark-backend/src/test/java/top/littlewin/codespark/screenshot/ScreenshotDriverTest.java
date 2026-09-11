package top.littlewin.codespark.screenshot;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class ScreenshotDriverTest {

    @Test
    void saveWebPageScreenshot() {
        String testUrl = "https://blog.littlewin.top/";
        try (ScreenshotDriver driver = new ScreenshotDriver(1600, 1000, 30)) {
            String path = driver.saveWebPageScreenshot(testUrl);
            Assertions.assertNotNull(path);
        }
    }
}
