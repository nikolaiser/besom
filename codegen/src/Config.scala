package besom.codegen

import besom.model.SemanticVersion

case class Config(
  logLevel: Logger.Level = Logger.Level.Info,
  besomVersion: String = Config.DefaultBesomVersion,
  javaVersion: String = Config.DefaultJavaVersion,
  scalaVersion: String = Config.DefaultScalaVersion,
  schemasDir: os.Path = Config.DefaultSchemasDir,
  codegenDir: os.Path = Config.DefaultCodegenDir,
  overlaysDir: os.Path = Config.DefaultOverlaysDir,
  outputDir: Option[os.RelPath] = None,
  providers: String => Config.Provider = Config.DefaultProvidersConfigs
):
  val coreShortVersion: String = SemanticVersion
    .parseTolerant(besomVersion)
    .fold(
      e => throw GeneralCodegenException(s"Invalid besom version: ${besomVersion}", e),
      _.copy(patch = 0).toShortString
    )
end Config

// noinspection ScalaWeakerAccess
object Config {

  val DefaultJavaVersion  = "11"
  val DefaultScalaVersion = "3.3.1"

  val DefaultBesomVersion: String = {
    try {
      os.read(os.pwd / "version.txt").trim
    } catch {
      case ex: java.nio.file.NoSuchFileException =>
        throw GeneralCodegenException(
          "Expected './version.txt' file or explicit 'besom.codegen.Config(besomVersion = \"1.2.3\")",
          ex
        )
    }
  }
  val DefaultSchemasDir: os.Path  = os.pwd / ".out" / "schemas"
  val DefaultCodegenDir: os.Path  = os.pwd / ".out" / "codegen"
  val DefaultOverlaysDir: os.Path = os.pwd / "codegen" / "resources" / "overlays"

  case class Provider(
    nonCompiledModules: Seq[String] = Seq.empty,
    moduleToPackages: Map[String, String] = Map.empty
  )

  val DefaultProvidersConfigs: Map[String, Provider] = Map().withDefault(_ => Config.Provider())
}
