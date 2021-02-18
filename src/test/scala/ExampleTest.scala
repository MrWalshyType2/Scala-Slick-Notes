import java.util.concurrent.TimeUnit

import org.scalatest._
import org.scalatest.matchers._
import org.scalatestplus.selenium._
import org.openqa.selenium.WebDriver
import org.openqa.selenium.htmlunit.HtmlUnitDriver

class ExampleTest extends flatspec.AnyFlatSpec with should.Matchers with WebBrowser {

  implicit val webDriver: WebDriver = new HtmlUnitDriver()

  webDriver.manage().window().maximize()
  webDriver.manage().timeouts().implicitlyWait(2, TimeUnit.SECONDS)

  val host = "https://www.google.com/"

//  webDriver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS)
  "The Google homepage title" should "have the page title Google" in {
    go to (host + "index.html")
    pageTitle should be ("Google")
  }


}
