package com.salas.musicwave.qa.ui;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicWaveUiTest {

    private static WebDriver driver;
    private static WebDriverWait wait;
    private static String baseUrl;

    @BeforeAll
    static void startBrowser() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=1440,1000");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        baseUrl = System.getProperty("uiBaseUrl", "http://localhost:4321");
    }

    @AfterAll
    static void stopBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    void homeExposesThePrimaryBlackBoxWorkflowAndAccessibleNavigation() {
        driver.get(baseUrl + "/");

        wait.until(ExpectedConditions.titleContains("MusicWave"));
        WebElement urlInput = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.id("single-url-input")));

        assertTrue(driver.getTitle().contains("Bandeja de Descargas"));
        assertEquals("url", urlInput.getAttribute("type"));
        assertTrue(driver.findElement(By.id("single-submit-btn")).isDisplayed());
        assertEquals("Navegación principal",
                driver.findElement(By.cssSelector("nav")).getAttribute("aria-label"));
        assertEquals("#main-content",
                driver.findElement(By.cssSelector("a[href='#main-content']")).getAttribute("href")
                        .substring(baseUrl.length() + 1));
    }

    @Test
    void settingsExposeTheAcceptedConcurrencyAndQualityControls() {
        driver.get(baseUrl + "/settings");

        WebElement concurrency = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.id("max-concurrency-input")));
        WebElement quality = driver.findElement(By.id("audio-quality-select"));

        assertEquals("1", concurrency.getAttribute("min"));
        assertEquals("10", concurrency.getAttribute("max"));
        assertEquals(7, quality.findElements(By.tagName("option")).size());
        assertTrue(driver.findElement(By.id("settings-heading")).getText()
                .contains("Preferencias de Descarga"));
    }
}
