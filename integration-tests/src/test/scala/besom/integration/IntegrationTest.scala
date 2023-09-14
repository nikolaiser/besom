package besom.integration

import java.io.*
import scala.sys.process.*

class IntegrationTest extends munit.FunSuite {

  test("compilation should fail with pulumi compiler plugin when using output parameter in an s interpolator") {
    var output = ""
    val logger = ProcessLogger(line => output += line + "\n")
    "just publish-local-sdk".!(logger)
    "just publish-local-compiler-plugin".!(logger)
    val compilePluginTestStr =
      """|scala-cli compile
         |integration-tests/src/test/resources/compilerplugintest.scala
         |""".stripMargin
    val result = compilePluginTestStr.!(logger)
    assert(output.contains("is used in a default string interpolator."))
  }

  test("should successfully run besom examples") {
    var output = ""
    val logger = ProcessLogger(line => output += line + "\n")
    "just publish-local-sdk".!(logger)

    "just generate-provider-sdk random 4.13.2".!(logger)
    "just publish-local-provider-sdk random 4.13.2".!(logger)
    Process("pulumi stack select test --create", File("integration-tests/src/test/resources/random-example")).!(logger)
    Process("pulumi up --yes", File("integration-tests/src/test/resources/random-example")).!(logger)
    Process("pulumi down --yes", File("integration-tests/src/test/resources/random-example")).!(logger)
    assert(!output.contains("error"), output.split("\n").filter(_.contains("error")).mkString("\n"))
    assert(output.contains("randomString:"))
    assert(output.contains("Duration:"))

    output = ""
    "just generate-provider-sdk time 0.0.15".!(logger)
    "just publish-local-provider-sdk time 0.0.15".!(logger)
    Process("pulumi stack select test --create", File("integration-tests/src/test/resources/time-example")).!(logger)
    Process("pulumi up --yes", File("integration-tests/src/test/resources/time-example")).!(logger)
    Process("pulumi down --yes", File("integration-tests/src/test/resources/time-example")).!(logger)
    assert(!output.contains("error"), output.split("\n").filter(_.contains("error")).mkString("\n"))
    assert(output.contains("rotatingTime:"))
    assert(output.contains("Duration:"))
  }

}
