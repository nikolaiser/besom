package besom.codegen

import besom.codegen.metaschema.PulumiPackage
import besom.codegen.Utils.PulumiPackageOps

import scala.meta.*
import scala.meta.dialects.Scala33

//noinspection ScalaFileName,TypeAnnotation
class DefinitionCoordinatesTest extends munit.FunSuite {

  case class Data(
    token: PulumiToken,
    tags: munit.Tag*
  )(val expected: Expectations*)
  sealed trait Expectations {
    def fullPackageName: String
    def fullyQualifiedTypeRef: String
    def filePath: String
  }
  case class ResourceClassExpectations(
    fullPackageName: String,
    fullyQualifiedTypeRef: String,
    filePath: String,
    asArgsType: Boolean = false
  ) extends Expectations
  case class ObjectClassExpectations(
    fullPackageName: String,
    fullyQualifiedTypeRef: String,
    filePath: String,
    asArgsType: Boolean = false
  ) extends Expectations
  case class EnumClassExpectations(
    fullPackageName: String,
    fullyQualifiedTypeRef: String,
    filePath: String
  ) extends Expectations

  val tests = List(
    Data(
      PulumiToken("example", "", "Provider")
    )(expected =
      ResourceClassExpectations(
        fullPackageName = "besom.api.example",
        fullyQualifiedTypeRef = "besom.api.example.Provider",
        filePath = "src/index/Provider.scala"
      )
    ),
    Data(
      PulumiToken("example", "index", "Provider")
    )(expected =
      ResourceClassExpectations(
        fullPackageName = "besom.api.example",
        fullyQualifiedTypeRef = "besom.api.example.Provider",
        filePath = "src/index/Provider.scala"
      )
    )
  )

  tests.foreach(data => {
    test(s"Type: ${data.token.asString}".withTags(data.tags.toSet)) {
      given config: Config                 = Config()
      given logger: Logger                 = Logger()
      given schemaProvider: SchemaProvider = DownloadingSchemaProvider()

      val pulumiPackage = PulumiPackage("test")
      val packageInfo   = schemaProvider.packageInfo(pulumiPackage.toPackageMetadata(), pulumiPackage)

      val coords = data.token.toCoordinates(packageInfo)

      given Config.Provider = packageInfo.providerConfig

      data.expected.foreach {
        case ResourceClassExpectations(fullPackageName, fullyQualifiedTypeRef, filePath, asArgsType) =>
          val rc = coords.asResourceClass(asArgsType = asArgsType)
          assertEquals(rc.packageRef.syntax, fullPackageName)
          assertEquals(rc.typeRef.syntax, fullyQualifiedTypeRef)
          assertEquals(rc.filePath.osSubPath.toString(), filePath)
        case ObjectClassExpectations(fullPackageName, fullyQualifiedTypeRef, filePath, asArgsType) =>
          val oc = coords.asObjectClass(asArgsType = asArgsType)
          assertEquals(oc.packageRef.syntax, fullPackageName)
          assertEquals(oc.typeRef.syntax, fullyQualifiedTypeRef)
          assertEquals(oc.filePath.osSubPath.toString(), filePath)
        case EnumClassExpectations(fullPackageName, fullyQualifiedTypeRef, filePath) =>
          val ec = coords.asEnumClass
          assertEquals(ec.packageRef.syntax, fullPackageName)
          assertEquals(ec.typeRef.syntax, fullyQualifiedTypeRef)
          assertEquals(ec.filePath.osSubPath.toString(), filePath)
      }
    }
  })
}
