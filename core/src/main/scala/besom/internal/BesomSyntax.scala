package besom.internal

import scala.reflect.Typeable
import besom.util.NonEmptyString
import besom.types.ResourceType
import besom.types.URN
import besom.types.ResourceId

/** This trait is the main export point that exposes Besom specific functions and types to the user. The only exception is the [[Output]]
  * object which is exposed in [[BesomModule]] which extends this trait.
  * @see
  *   [[besom.Pulumi]]
  * @see
  *   [[besom.internal.BesomModule]]
  * @see
  *   [[besom.internal.EffectBesomModule]]
  */
trait BesomSyntax:

  /** A dry run is a program evaluation for purposes of planning, instead of performing a true deployment.
    * @param ctx
    *   the Besom context
    * @return
    *   true if the current run is a dry run
    */
  def isDryRun(using ctx: Context): Boolean = ctx.isDryRun

  /** Returns the current project configuration.
    * @param ctx
    *   the Besom context
    * @return
    *   the current project [[Config]] instance
    */
  def config(using ctx: Context): Config = ctx.config

  /** Returns the current project logger.
    * @param ctx
    *   the Besom context
    * @return
    *   the current project [[besom.aliases.Logger]] instance
    */
  def log(using ctx: Context): besom.aliases.Logger =
    besom.internal.logging.UserLoggerFactory(using ctx)

  /** The current project [[besom.types.URN]]
    * @param ctx
    *   the Besom context
    * @return
    *   the current project [[besom.types.URN]] instance
    */
  def urn(using ctx: Context): Output[URN] =
    Output.ofData(ctx.getParentURN.map(OutputData(_)))

  /** @param ctx
    *   the Besom context
    * @return
    *   the organization of the current Pulumi stack.
    */
  def pulumiOrganization(using ctx: Context): Option[NonEmptyString] = ctx.pulumiOrganization

  /** @param ctx
    *   the Besom context
    * @return
    *   the project name of the current Pulumi stack.
    */
  def pulumiProject(using ctx: Context): NonEmptyString = ctx.pulumiProject

  /** @param ctx
    *   the Besom context
    * @return
    *   the stack name of the current Pulumi stack.
    */
  def pulumiStack(using ctx: Context): NonEmptyString = ctx.pulumiStack

  /** Creates a new component resource.
    * @param name
    *   a unique resource name for this component
    * @param typ
    *   the Pulumi [[ResourceType]] of the component resource
    * @param f
    *   the block of code that defines all the resources that should belong to the component
    * @param ctx
    *   the Besom context
    * @tparam A
    *   the type of the component resource
    * @return
    *   the component resource instance
    */
  def component[A <: ComponentResource & Product: RegistersOutputs: Typeable](using ctx: Context)(
    name: NonEmptyString,
    typ: ResourceType,
    opts: ComponentResourceOptions = ComponentResourceOptions()
  )(
    f: Context ?=> ComponentBase ?=> A
  ): Output[A] =
    Output.ofData {
      ctx
        .registerComponentResource(name, typ, opts)
        .flatMap { componentBase =>
          val urnRes: Result[URN] = componentBase.urn.getValueOrFail {
            s"Urn for component resource $name is not available. This should not happen."
          }

          val componentContext = ComponentContext(ctx, urnRes, componentBase)
          val componentOutput =
            try Output(Result.pure(f(using componentContext)(using componentBase)))
            catch case e: Exception => Output(Result.fail(e))

          val componentResult = componentOutput.getValueOrFail {
            s"Component resource $name of type $typ did not return a value. This should not happen."
          }

          componentResult.flatMap { a =>
            val serializedOutputs = RegistersOutputs[A].serializeOutputs(a)
            ctx.registerResourceOutputs(name, typ, urnRes, serializedOutputs) *> Result.pure(a)
          }
        }
        .map(OutputData(_))
    }
  end component

  extension [A <: ProviderResource](pr: A)
    def provider(using Context): Output[Option[ProviderResource]] = Output {
      Context().resources.getStateFor(pr).map(_.custom.provider)
    }

  extension [A <: CustomResource](cr: A)
    def provider(using Context): Output[Option[ProviderResource]] = Output {
      Context().resources.getStateFor(cr).map(_.provider)
    }

  extension [A <: ComponentResource](cmpr: A)
    def providers(using Context): Output[Map[String, ProviderResource]] = Output {
      Context().resources.getStateFor(cmpr).map(_.providers)
    }

  extension [A <: RemoteComponentResource](cb: A)
    def providers(using Context): Output[Map[String, ProviderResource]] = Output {
      Context().resources.getStateFor(cb).map(_.providers)
    }

  extension [A <: Resource: ResourceDecoder](companion: ResourceCompanion[A])
    def get(name: Input[NonEmptyString], id: Input[ResourceId])(using ctx: Context): Output[A] =
      for
        name <- name.asOutput()
        id   <- id.asOutput()
        res  <- ctx.readOrRegisterResource[A, EmptyArgs](companion.typeToken, name, EmptyArgs(), CustomResourceOptions(importId = id))
      yield res

  extension (s: String)
    /** Converts a [[String]] to a [[NonEmptyString]] if it is not empty or blank.
      */
    def toNonEmpty: Option[NonEmptyString] = NonEmptyString(s)

    /** Converts a [[String]] to a [[NonEmptyString]] if it is not empty or blank, otherwise throws an [[IllegalArgumentException]].
      */
    def toNonEmptyOrThrow: NonEmptyString = NonEmptyString(s).getOrElse(throw IllegalArgumentException(s"String $s was empty!"))

    /** Converts a [[String]] to an [[Output]] of [[NonEmptyString]] if it is not empty or blank, otherwise returns a failed [[Output]] with
      * an [[IllegalArgumentException]].
      */
    def toNonEmptyOutput(using Context): Output[NonEmptyString] =
      NonEmptyString(s).fold(Output.fail(IllegalArgumentException(s"String $s was empty!")))(Output.apply(_))

  extension (os: Output[String])
    /** Converts an [[Output]] of [[String]] to an [[Output]] of [[NonEmptyString]] which will be failed if the string is empty.
      */
    def toNonEmptyOutput(using Context): Output[NonEmptyString] =
      os.flatMap(_.toNonEmptyOutput)

  /** Shortcut function allowing for uniform resource options syntax everywhere.
    *
    * @param variant
    *
    * @return
    */
  def opts(using variant: ResourceOptsVariant): variant.Constructor = variant.constructor

end BesomSyntax
