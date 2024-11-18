
package com.macbackpackers.config;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A factory for creating WebDriver instances logged into LittleHotelier.
 *
 */
@Component
public class LittleHotelierWebDriverFactory extends BasePooledObjectFactory<WebDriver> {

    @Value( "${chromescraper.maxwait.seconds:60}" )
    private int maxWaitSeconds;

    @Value( "${chromescraper.driver.options:user-data-dir=chromeprofile --headless --disable-gpu --start-maximized --ignore-certificate-errors --remote-allow-origins=*}" )
    private String chromeOptions;

    @Override
    public WebDriver create() throws Exception {
        System.setProperty( "webdriver.chrome.driver", getClass().getClassLoader().getResource(
                SystemUtils.IS_OS_WINDOWS ? "chromedriver.exe" : "chromedriver" ).getPath() );

        ChromeOptions options = new ChromeOptions();
        List<String> optionValues = new ArrayList<>(Arrays.asList(chromeOptions.split( " " )));
        options.addArguments( optionValues.toArray(new String[optionValues.size()]) );
//        options.setExperimentalOption("debuggerAddress", "127.0.0.1:9222");

        ChromeDriver driver = new ChromeDriver( options );

        // https://bot.sannysoft.com/
        // https://stackoverflow.com/a/69533548
        // https://stackoverflow.com/questions/33225947/can-a-website-detect-when-you-are-using-selenium-with-chromedriver/52108199#52108199
        // https://piprogramming.org/articles/How-to-make-Selenium-undetectable-and-stealth--7-Ways-to-hide-your-Bot-Automation-from-Detection-0000000017.html
        //  - see undetected-chromedriver but only python support
        // https://www.npmjs.com/package/puppeteer-extra-plugin-stealth
        // https://scrapeops.io/blog/the-state-of-web-scraping-2022/
        /*
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("source", "Object.defineProperty(Navigator.prototype, 'webdriver', {\n" +
                "        set: undefined,\n" +
                "        enumerable: true,\n" +
                "        configurable: true,\n" +
                "        get: new Proxy(\n" +
                "            Object.getOwnPropertyDescriptor(Navigator.prototype, 'webdriver').get,\n" +
                "            { apply: (target, thisArg, args) => {\n" +
                "                Reflect.apply(target, thisArg, args);\n" +
                "                return false;\n" +
                "            }}\n" +
                "        )\n" +
                "    });");
        driver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params);
         */

        // configure wait-time when finding elements on the page
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(maxWaitSeconds));

        return driver;
    }

    /**
     * Use the default PooledObject implementation.
     */
    @Override
    public PooledObject<WebDriver> wrap( WebDriver driver ) {
        return new DefaultPooledObject<WebDriver>( driver );
    }

    @Override
    public void destroyObject( PooledObject<WebDriver> pooledObj ) throws Exception {
        pooledObj.getObject().quit();
    }

}
