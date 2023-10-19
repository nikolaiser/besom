package besom.internal

import scala.deriving.Mirror
import com.google.protobuf.struct.*, Value.Kind
import besom.internal.ProtobufUtil.*
import scala.util.*
import besom.util.*
import besom.types.*
import Asset.*
import Archive.*
import org.checkerframework.checker.units.qual.s
import org.checkerframework.checker.units.qual.m

object Constants:
  final val UnknownValue           = "04da6b54-80e4-46f7-96ec-b56ff0331ba9"
  final val SpecialSigKey          = "4dabf18193072939515e22adb298388d"
  final val SpecialAssetSig        = "c44067f5952c0a294b673a41bacd8c17"
  final val SpecialArchiveSig      = "0def7320c3a5731c473e5ecbe6d01bc7"
  final val SpecialSecretSig       = "1b47061264138c4ac30d75fd1eb44270"
  final val SpecialResourceSig     = "5cf8f73096256a8f31e491e813e4eb8e"
  final val SecretValueName        = "value"
  final val AssetTextName          = "text"
  final val ArchiveAssetsName      = "assets"
  final val AssetOrArchivePathName = "path"
  final val AssetOrArchiveUriName  = "uri"
  final val ResourceUrnName        = "urn"
  final val ResourceIdName         = "id"
  final val ResourceVersionName    = "packageVersion"
  final val IdPropertyName         = "id"
  final val UrnPropertyName        = "urn"
  final val StatePropertyName      = "state"

case class DecodingError(message: String, cause: Throwable = null, label: Label) extends Exception(message, cause)
case class AggregatedDecodingError(errors: NonEmptyVector[DecodingError])
    extends Exception(errors.map(_.message).toVector.mkString("\n"))
/*
 * Would be awesome to make error reporting better, ie:
 *  - make DecodingError work with any arity of errors and return aggregates
 *  - render yamled or jsoned version of top Value on error (so at the bottom of the stack!)
 *    this, along with the jq-like path to errors, would allow for very easy debugging
 *
 * [ ] TODO IMPORTANT: Decoder[A] derivation for case classes and ADTs MUST respect secretness of constituent fields
 *                     Whole record/adt must be considered secret if one of the fields held in plain value is secret!
 *                     Write a test, OutputData.combine should have taken care of it in sequence/traverse chains.
 * [ ] TODO: a better way than current Try/throw combo to traverse OutputData's lack of error channel
 */
trait Decoder[A]:
  self =>

  def decode(value: Value, label: Label): Validated[DecodingError, OutputData[A]] =
    Decoder.decodeAsPossibleSecret(value, label).flatMap { odv =>
      Try(odv.map(mapping(_, label))) match
        case Failure(exception) =>
          Decoder.errorInvalid(s"$label: Encountered an error - secret - ${odv}", label, exception)
        case Success(oda) => Validated.valid(oda)
    }

  // TODO this might not be the best idea for simplification in the end, just look at the impls for nested datatypes
  def mapping(value: Value, label: Label): A

  def emap[B](f: (A, Label) => Validated[DecodingError, B]): Decoder[B] = new Decoder[B]:
    override def decode(value: Value, label: Label): Validated[DecodingError, OutputData[B]] =
      self.decode(value, label).flatMap { odA =>
        odA
          .traverseValidated { a =>
            f(a, label).map(OutputData(_))
          }
          .map(_.flatten)
          .lmap(exception => DecodingError(s"$label: Encountered an error", label = label, cause = exception))
      }
    override def mapping(value: Value, label: Label): B = ???

object NameUnmangler:
  private val mangledAnyRefMethodNames = Set(
    "eq",
    "ne",
    "notify",
    "notifyAll",
    "synchronized",
    "wait",
    "asInstanceOf",
    "clone",
    "equals",
    "getClass",
    "hashCode",
    "isInstanceOf",
    "toString"
  ).map(_ + "_")

  /** Keep in sync with `manglePropertyName` in CodeGen.scala */
  def unmanglePropertyName(name: String): String =
    if (mangledAnyRefMethodNames.contains(name)) {
      name.dropRight(1)
    } else name

object Decoder extends DecoderInstancesLowPrio1:
  import spray.json.*

  // for recursive stuff like Map[String, Value]
  given Decoder[Value] with
    def mapping(value: Value, label: Label): Value = value

  given Decoder[Double] with
    def mapping(value: Value, label: Label): Double =
      if value.kind.isNumberValue then value.getNumberValue else error(s"$label: Expected a number!", label)

  given intDecoder(using dblDecoder: Decoder[Double]): Decoder[Int] =
    dblDecoder.emap { (dbl, label) =>
      if (dbl % 1 == 0) Validated.valid(dbl.toInt)
      else errorInvalid(s"$label: Numeric value was expected to be integer but had a decimal value!", label)
    }

  given stringDecoder: Decoder[String] with
    def mapping(value: Value, label: Label): String =
      if value.kind.isStringValue then value.getStringValue else error(s"$label: Expected a string!", label)

  given urnDecoder: Decoder[URN] with
    def mapping(value: Value, label: Label): URN =
      if value.kind.isStringValue then
        URN.from(value.getStringValue) match
          case Success(value)     => value
          case Failure(exception) => error(s"$label: Expected a valid URN string!", exception, label)
      else error(s"$label: Expected a string!", label)

  given Decoder[Boolean] with
    def mapping(value: Value, label: Label): Boolean =
      if value.kind.isBoolValue then value.getBoolValue else error(s"$label: Expected a boolean!", label)

  given jsonDecoder: Decoder[JsValue] with
    def convertToJsValue(value: Value): JsValue =
      value.kind match
        case Kind.Empty                => JsNull
        case Kind.NullValue(_)         => JsNull
        case Kind.NumberValue(num)     => JsNumber(num)
        case Kind.StringValue(str)     => JsString(str)
        case Kind.BoolValue(bool)      => JsBoolean(bool)
        case Kind.StructValue(struct)  => convertStructToJsObject(struct)
        case Kind.ListValue(listValue) => convertListValueToJsArray(listValue)

    def convertStructToJsObject(struct: Struct): JsObject =
      val fields = struct.fields.view.mapValues(convertToJsValue).toMap
      JsObject(fields)

    def convertListValueToJsArray(listValue: ListValue): JsArray =
      val values = listValue.values.map(convertToJsValue)
      JsArray(values: _*)

    def mapping(value: Value, label: Label): JsValue = convertToJsValue(value)

  given optDecoder[A](using innerDecoder: Decoder[A]): Decoder[Option[A]] = new Decoder[Option[A]]:
    override def decode(value: Value, label: Label): Validated[DecodingError, OutputData[Option[A]]] =
      decodeAsPossibleSecret(value, label).flatMap { odv =>
        odv
          .traverseValidated {
            case Value(Kind.NullValue(_), _) => Validated.valid(OutputData(None))
            case v                           => innerDecoder.decode(v, label).map(_.map(Some(_)))
          }
          .map(_.flatten)
          .lmap(exception =>
            DecodingError(
              s"$label: Encountered an error when deserializing an option",
              label = label,
              cause = exception
            )
          )
      }

    override def mapping(value: Value, label: Label): Option[A] = ???

  given unionIntStringDecoder: Decoder[Int | String] = unionDecoder[Int, String]

  // this is kinda different from what other pulumi sdks are doing because we disallow nulls in the list
  given listDecoder[A](using innerDecoder: Decoder[A]): Decoder[List[A]] = new Decoder[List[A]]:
    override def decode(value: Value, label: Label): Validated[DecodingError, OutputData[List[A]]] =
      decodeAsPossibleSecret(value, label).flatMap { odv =>
        odv
          .traverseValidated { v =>
            val listValue = if v.kind.isListValue then v.getListValue else error(s"$label: Expected a list!", label)

            listValue.values.zipWithIndex
              .map { case (v, i) =>
                innerDecoder.decode(v, label.atIndex(i))
              }
              .foldLeft[Validated[DecodingError, Vector[OutputData[A]]]](Validated.valid(Vector.empty))(
                accumulatedOutputDatasOrErrors(_, _, "list", label)
              )
              .map(_.toList)
              .map(OutputData.sequence)
          }
          .map(_.flatten)
          .lmap(exception =>
            DecodingError(s"$label: Encountered an error when deserializing a list", label = label, cause = exception)
          )
      }

    def mapping(value: Value, label: Label): List[A] = List.empty

  given mapDecoder[A](using innerDecoder: Decoder[A]): Decoder[Map[String, A]] = new Decoder[Map[String, A]]:
    override def decode(value: Value, label: Label): Validated[DecodingError, OutputData[Map[String, A]]] =
      decodeAsPossibleSecret(value, label).flatMap { odv =>
        odv
          .traverseValidated { innerValue =>
            val structValue =
              if innerValue.kind.isStructValue then innerValue.getStructValue
              else error(s"$label: Expected a struct!", label)

            structValue.fields.iterator
              .filterNot { (key, _) => key.startsWith("__") }
              .map { (k, v) =>
                innerDecoder.decode(v, label.withKey(k)).map(_.map(nv => (k, nv)))
              }
              .foldLeft[Validated[DecodingError, Vector[OutputData[(String, A)]]]](Validated.valid(Vector.empty))(
                accumulatedOutputDatasOrErrors(_, _, "struct", label)
              )
              .map(OutputData.sequence)
              .map(_.map(_.toMap))
          }
          .map(_.flatten)
          .lmap(exception =>
            DecodingError(s"$label: Encountered an error when deserializing a map", label = label, cause = exception)
          )
      }
    def mapping(value: Value, label: Label): Map[String, A] = Map.empty

  // wondering if this works, it's a bit of a hack
  given dependencyResourceDecoder(using Context): Decoder[DependencyResource] = new Decoder[DependencyResource]:
    override def decode(value: Value, label: Label): Validated[DecodingError, OutputData[DependencyResource]] =
      decodeAsPossibleSecret(value, label).flatMap { odv =>
        odv
          .traverseValidated { innerValue =>
            extractSpecialStructSignature(innerValue) match
              case None => error(s"$label: Expected a special struct signature!", label)
              case Some(specialSig) =>
                if specialSig != Constants.SpecialSecretSig then
                  errorInvalid(s"$label: Expected a special resource signature!", label)
                else
                  val structValue = innerValue.getStructValue
                  structValue.fields
                    .get(Constants.ResourceUrnName)
                    .map(_.getStringValue)
                    .toValidatedOrError(error(s"$label: Expected a resource urn in resource struct!", label))
                    .flatMap(urnString => URN.from(urnString).toEither.toValidated)
                    .map(urn => OutputData(DependencyResource(Output(urn))))
          }
          .map(_.flatten)
          .lmap(exception =>
            DecodingError(
              s"$label: Encountered an error when deserializing a resource",
              label = label,
              cause = exception
            )
          )
      }

    override def mapping(value: Value, label: Label): DependencyResource = ???

  def assetArchiveDecoder[A](
    specialSig: String,
    handle: (Label, Struct) => Validated[DecodingError, OutputData[A]]
  ): Decoder[A] = new Decoder[A]:
    override def decode(value: Value, label: Label): Validated[DecodingError, OutputData[A]] =
      decodeAsPossibleSecret(value, label).flatMap { odv =>
        odv
          .traverseValidated { innerValue =>
            extractSpecialStructSignature(innerValue) match
              case None => errorInvalid(s"$label: Expected a special struct signature!", label)
              case Some(extractedSpecialSig) =>
                if extractedSpecialSig != specialSig then
                  errorInvalid(s"$label: Expected a special asset signature!", label)
                else
                  val structValue = innerValue.getStructValue
                  handle(label, structValue)
          }
          .map(_.flatten)
          .lmap(exception =>
            DecodingError(s"$label: Encountered an error when deserializing an asset", label = label, cause = exception)
          )
      }

    override def mapping(value: Value, label: Label): A = ???

  given fileAssetDecoder: Decoder[FileAsset] = assetArchiveDecoder[FileAsset](
    Constants.SpecialAssetSig,
    (label, structValue) =>
      structValue.fields
        .get(Constants.AssetOrArchivePathName)
        .map(_.getStringValue)
        .toValidatedOrError(DecodingError(s"$label: Expected a path in asset struct!", label = label))
        .map(path => OutputData(FileAsset(path)))
  )

  given remoteAssetDecoder: Decoder[RemoteAsset] = assetArchiveDecoder[RemoteAsset](
    Constants.SpecialAssetSig,
    (label, structValue) =>
      structValue.fields
        .get(Constants.AssetOrArchiveUriName)
        .map(_.getStringValue)
        .toValidatedOrError(DecodingError(s"$label: Expected a uri in asset struct!", label = label))
        .map(uri => OutputData(RemoteAsset(uri)))
  )

  given stringAssetDecoder: Decoder[StringAsset] = assetArchiveDecoder(
    Constants.SpecialAssetSig,
    (label, structValue) =>
      structValue.fields
        .get(Constants.AssetTextName)
        .map(_.getStringValue)
        .toValidatedOrError(DecodingError(s"$label: Expected a text in asset struct!", label = label))
        .map(text => OutputData(StringAsset(text)))
  )

  given fileArchiveDecoder: Decoder[FileArchive] = assetArchiveDecoder[FileArchive](
    Constants.SpecialArchiveSig,
    (label, structValue) =>
      structValue.fields
        .get(Constants.AssetOrArchivePathName)
        .map(_.getStringValue)
        .toValidatedOrError(DecodingError(s"$label: Expected a path in archive struct!", label = label))
        .map(path => OutputData(FileArchive(path)))
  )

  given remoteArchiveDecoder: Decoder[RemoteArchive] = assetArchiveDecoder[RemoteArchive](
    Constants.SpecialArchiveSig,
    (label, structValue) =>
      structValue.fields
        .get(Constants.AssetOrArchiveUriName)
        .map(_.getStringValue)
        .toValidatedOrError(DecodingError(s"$label: Expected a uri in archive struct!", label = label))
        .map(uri => OutputData(RemoteArchive(uri)))
  )

  given assetArchiveDecoder: Decoder[AssetArchive] = assetArchiveDecoder[AssetArchive](
    Constants.SpecialArchiveSig,
    (label, structValue) =>
      val nested = structValue.fields
        .get(Constants.ArchiveAssetsName)
        .map(_.getStructValue)
        .toValidatedOrError(DecodingError(s"$label: Expected an assets field in archive struct!", label = label))

      val outputDataVecV = nested.flatMap { nestedVal =>
        nestedVal.fields.toVector.traverseV { (name, value) =>
          assetOrArchiveDecoder
            .decode(value, label.withKey(name))
            .lmap((exception: DecodingError) =>
              DecodingError(
                s"$label: Encountered an error when deserializing an archive",
                label = label,
                cause = exception
              )
            )
            .map(_.map((name, _)))
        }
      }

      outputDataVecV.map { outputDataVec =>
        OutputData.sequence(outputDataVec).map(vec => AssetArchive(vec.toMap))
      }
  )

  given assetDecoder: Decoder[Asset] = new Decoder[Asset]:
    override def decode(value: Value, label: Label): Validated[DecodingError, OutputData[Asset]] =
      fileAssetDecoder
        .decode(value, label)
        .orElse(stringAssetDecoder.decode(value, label))
        .orElse(remoteAssetDecoder.decode(value, label))
        .orElse(errorInvalid(s"$label: Found value is neither a FileAsset, StringAsset nor RemoteAsset", label))

    override def mapping(value: Value, label: Label): Asset = ???

  given archiveDecoder: Decoder[Archive] = new Decoder[Archive]:
    override def decode(value: Value, label: Label): Validated[DecodingError, OutputData[Archive]] =
      fileArchiveDecoder
        .decode(value, label)
        .orElse(remoteArchiveDecoder.decode(value, label))
        .orElse(assetArchiveDecoder.decode(value, label))
        .orElse(errorInvalid(s"$label: Found value is neither a FileArchive, AssetArchive nor RemoteArchive", label))
    override def mapping(value: Value, label: Label): Archive = ???

  given assetOrArchiveDecoder: Decoder[AssetOrArchive] = new Decoder[AssetOrArchive]:
    override def decode(value: Value, label: Label): Validated[DecodingError, OutputData[AssetOrArchive]] =
      assetDecoder
        .decode(value, label)
        .orElse(archiveDecoder.decode(value, label))
        .orElse(errorInvalid(s"$label: Found value is neither an Asset nor an Archive", label))
    override def mapping(value: Value, label: Label): AssetOrArchive = ???

trait DecoderInstancesLowPrio1 extends DecoderInstancesLowPrio2:
  import spray.json.JsValue

  given unionBooleanDecoder[A: Decoder](using NotGiven[A <:< Boolean]): Decoder[Boolean | A] = unionDecoder[A, Boolean]
  given unionStringDecoder[A: Decoder](using NotGiven[A <:< String]): Decoder[String | A]    = unionDecoder[A, String]
  given unionJsonDecoder[A: Decoder](using NotGiven[A <:< JsValue]): Decoder[JsValue | A]    = unionDecoder[A, JsValue]

trait DecoderInstancesLowPrio2 extends DecoderHelpers:
  given singleOrListDecoder[A: Decoder, L <: List[?]: Decoder]: Decoder[A | L] = unionDecoder[A, L]

trait DecoderHelpers:
  import Constants.*

  def unionDecoder[A, B](using aDecoder: Decoder[A], bDecoder: Decoder[B]): Decoder[A | B] = new Decoder[A | B]:
    override def decode(value: Value, label: Label): Validated[DecodingError, OutputData[A | B]] =
      decodeAsPossibleSecret(value, label).flatMap { odv =>
        odv
          .traverseValidated { v =>
            aDecoder
              .decode(v, label)
              .orElse(bDecoder.decode(v, label))
              .asInstanceOf[Validated[DecodingError, OutputData[A | B]]]
          }
          .map(_.flatten)
      }

    override def mapping(value: Value, label: Label): A | B = ???

  inline def derived[A](using m: Mirror.Of[A]): Decoder[A] =
    lazy val labels           = CodecMacros.summonLabels[m.MirroredElemLabels]
    lazy val instances        = CodecMacros.summonDecoders[m.MirroredElemTypes]
    lazy val nameDecoderPairs = labels.map(NameUnmangler.unmanglePropertyName).zip(instances)

    inline m match
      case s: Mirror.SumOf[A] => decoderSum(s, nameDecoderPairs)
      case p: Mirror.ProductOf[A] =>
        decoderProduct(p, nameDecoderPairs)

  inline def error(msg: String, label: Label): Nothing                   = throw DecodingError(msg, label = label)
  inline def error(msg: String, cause: Throwable, label: Label): Nothing = throw DecodingError(msg, cause, label)
  inline def errorInvalid(msg: String, label: Label, cause: Throwable = null): Validated[DecodingError, Nothing] =
    Validated.invalid(DecodingError(msg, cause, label))

  // this is, effectively, Decoder[Enum]
  def decoderSum[A](s: Mirror.SumOf[A], elems: => List[(String, Decoder[?])]): Decoder[A] =
    new Decoder[A]:
      private val enumNameToDecoder                   = elems.toMap
      private def getDecoder(key: String): Decoder[A] = enumNameToDecoder(key).asInstanceOf[Decoder[A]]
      override def decode(value: Value, label: Label): Validated[DecodingError, OutputData[A]] =
        if value.kind.isStringValue then
          val key = value.getStringValue
          getDecoder(key).decode(Map.empty.asValue, label.withKey(key))
        else
          errorInvalid(
            s"$label: Value was not a string, Enums should be serialized as strings", // TODO: This is not necessarily true
            label
          )

      override def mapping(value: Value, label: Label): A = ???

  def decoderProduct[A](p: Mirror.ProductOf[A], elems: => List[(String, Decoder[?])]): Decoder[A] =
    new Decoder[A]:
      override def decode(value: Value, label: Label): Validated[DecodingError, OutputData[A]] =
        decodeAsPossibleSecret(value, label).flatMap { odv =>
          odv
            .traverseValidated { innerValue =>
              if (innerValue.kind.isStructValue) then
                val fields = innerValue.getStructValue.fields
                elems
                  .foldLeft[Validated[DecodingError, OutputData[Tuple]]](Validated.valid(OutputData(EmptyTuple))) {
                    case (validatedAcc, (name -> decoder)) =>
                      val fieldValue: Value = fields.getOrElse(name, Null)
                      validatedAcc.zipWith(decoder.decode(fieldValue, label.withKey(name))) { (acc, odField) =>
                        acc.zip(odField)
                      }
                  }
                  .map(_.map(p.fromProduct(_)))
              else errorInvalid(s"$label: Expected a struct to deserialize Product!", label = label)
            }
            .map(_.flatten)
        }

      override def mapping(value: Value, label: Label): A = ???

  def decodeAsPossibleSecret(value: Value, label: Label): Validated[DecodingError, OutputData[Value]] =
    extractSpecialStructSignature(value) match
      case Some(sig) if sig == SpecialSecretSig =>
        val innerValue = value.getStructValue.fields
          .get(SecretValueName)
          .map(Validated.valid)
          .getOrElse(errorInvalid(s"$label: Secrets must have a field called $SecretValueName!", label))

        innerValue.map(OutputData(_, isSecret = true))
      case _ =>
        if value.kind.isStringValue && Constants.UnknownValue == value.getStringValue then
          Validated.valid(OutputData.unknown(isSecret = false))
        else Validated.valid(OutputData(value))

  def extractSpecialStructSignature(value: Value): Option[String] =
    Iterator(value)
      .filter(_.kind.isStructValue)
      .flatMap(_.getStructValue.fields)
      .filter((k, v) => k == SpecialSigKey)
      .flatMap((_, v) => v.kind.stringValue)
      .nextOption

  def accumulatedOutputDatasOrErrors[A](
    acc: Validated[DecodingError, Vector[OutputData[A]]],
    elementValidated: Validated[DecodingError, OutputData[A]],
    typ: String,
    label: Label
  ): Validated[DecodingError, Vector[OutputData[A]]] =
    acc.zipWith(
      elementValidated
        // TODO this should have an issue number from GH and should suggest reporting this to us
        .filterOrError(_.nonEmpty)(
          DecodingError(s"Encountered a null in $typ, this is illegal in besom!", label = label)
        )
    ) { (acc, elementOutputData) =>
      acc :+ elementOutputData
    }

/** ArgsEncoder - this is a separate typeclass required for serialization of top-level *Args classes
  *
  * ProviderArgsEncoder - this is a separate typeclass required for serialization of top-level ProviderArgs classes that
  * have all fields serialized as JSON strings
  *
  * JsonEncoder - this is a separate typeclass required for json-serialized fields of ProviderArgs
  */
trait Encoder[A]:
  self =>
  def encode(a: A): Result[(Set[Resource], Value)]
  def contramap[B](f: B => A): Encoder[B] = new Encoder[B]:
    def encode(b: B): Result[(Set[Resource], Value)] = self.encode(f(b))

object Encoder:
  import Constants.*
  import spray.json.*

  def encoderSum[A](mirror: Mirror.SumOf[A], nameEncoderPairs: List[(String, Encoder[?])]): Encoder[A] =
    new Encoder[A]:
      private val encoderMap                                    = nameEncoderPairs.toMap
      override def encode(a: A): Result[(Set[Resource], Value)] = Result.pure(Set.empty -> a.toString.asValue)

  def encoderProduct[A](nameEncoderPairs: List[(String, Encoder[?])]): Encoder[A] =
    new Encoder[A]:
      override def encode(a: A): Result[(Set[Resource], Value)] =
        Result
          .sequence {
            a.asInstanceOf[Product]
              .productIterator
              .zip(nameEncoderPairs)
              .map { case (v, (label, encoder)) =>
                encoder.asInstanceOf[Encoder[Any]].encode(v).map(res => label -> res)
              }
              .toList
          }
          .map { lst =>
            val (resources, labelsToValuesMap) =
              lst.foldLeft[(Set[Resource], Map[String, Value])](Set.empty -> Map.empty) {
                case ((allResources, props), (label, (fieldResources, value))) =>
                  (allResources ++ fieldResources) -> (props + (label -> value))
              }

            resources -> labelsToValuesMap.asValue
          }

  inline def derived[A](using m: Mirror.Of[A]): Encoder[A] =
    lazy val labels           = CodecMacros.summonLabels[m.MirroredElemLabels]
    lazy val instances        = CodecMacros.summonEncoders[m.MirroredElemTypes]
    lazy val nameEncoderPairs = labels.map(NameUnmangler.unmanglePropertyName).zip(instances)
    inline m match
      case s: Mirror.SumOf[A]     => encoderSum(s, nameEncoderPairs)
      case _: Mirror.ProductOf[A] => encoderProduct(nameEncoderPairs)

  given customResourceEncoder[A <: CustomResource](using
    ctx: Context,
    outputIdEnc: Encoder[Output[ResourceId]],
    outputURNEnc: Encoder[Output[URN]]
  ): Encoder[A] =
    new Encoder[A]:
      def encode(a: A): Result[(Set[Resource], Value)] =
        outputIdEnc.encode(a.id).flatMap { (idResources, idValue) =>
          if ctx.featureSupport.keepResources then
            outputURNEnc.encode(a.urn).flatMap { (urnResources, urnValue) =>
              val fixedIdValue =
                if idValue.kind.isStringValue && idValue.getStringValue == UnknownValue then Value(Kind.StringValue(""))
                else idValue

              val result = Map(
                SpecialSigKey -> SpecialResourceSig.asValue,
                ResourceUrnName -> urnValue,
                ResourceIdName -> fixedIdValue
              )

              Result.pure((idResources ++ urnResources) -> result.asValue)
            }
          else Result.pure(idResources -> idValue)
        }

  given componentResourceEncoder[A <: ComponentResource](using
    ctx: Context,
    outputURNEnc: Encoder[Output[URN]]
  ): Encoder[A] =
    new Encoder[A]:
      def encode(a: A): Result[(Set[Resource], Value)] =
        outputURNEnc.encode(a.urn).flatMap { (urnResources, urnValue) =>
          if ctx.featureSupport.keepResources then
            val result = Map(
              SpecialSigKey -> SpecialResourceSig.asValue,
              ResourceUrnName -> urnValue
            )

            Result.pure(urnResources -> result.asValue)
          else Result.pure(urnResources -> urnValue)
        }

  given stringEncoder: Encoder[String] with
    def encode(str: String): Result[(Set[Resource], Value)] = Result.pure(Set.empty -> str.asValue)

  given urnEncoder: Encoder[URN] with
    def encode(urn: URN): Result[(Set[Resource], Value)] = Result.pure(Set.empty -> urn.asString.asValue)

  given idEncoder: Encoder[ResourceId] with
    def encode(id: ResourceId): Result[(Set[Resource], Value)] = Result.pure(Set.empty -> id.asString.asValue)

  given Encoder[NonEmptyString] with
    def encode(nestr: NonEmptyString): Result[(Set[Resource], Value)] =
      Result.pure(Set.empty -> Value(Kind.StringValue(nestr.asString)))

  given Encoder[Int] with
    def encode(int: Int): Result[(Set[Resource], Value)] = Result.pure(Set.empty -> int.asValue)

  given Encoder[Double] with
    def encode(dbl: Double): Result[(Set[Resource], Value)] = Result.pure(Set.empty -> dbl.asValue)

  given Encoder[Value] with
    def encode(vl: Value): Result[(Set[Resource], Value)] = Result.pure(Set.empty -> vl)

  given Encoder[Boolean] with
    def encode(bool: Boolean): Result[(Set[Resource], Value)] = Result.pure(Set.empty -> bool.asValue)

  given jsonEncoder: Encoder[JsValue] with
    // TODO not stack-safe
    def encodeInternal(json: JsValue): Value =
      json match
        case JsObject(fields)  => fields.view.mapValues(encodeInternal(_)).toMap.asValue
        case JsArray(elements) => elements.map(encodeInternal(_)).asValue
        case JsString(str)     => str.asValue
        case JsNumber(num)     => num.toDouble.asValue // TODO unsafe but oh well
        case JsTrue            => true.asValue
        case JsFalse           => false.asValue
        case JsNull            => Null

    def encode(json: JsValue): Result[(Set[Resource], Value)] =
      Result.pure(Set.empty -> encodeInternal(json))

  given optEncoder[A](using inner: Encoder[A]): Encoder[Option[A]] with
    def encode(optA: Option[A]): Result[(Set[Resource], Value)] =
      optA match
        case Some(value) => inner.encode(value)
        case None        => Result.pure(Set.empty -> Null)

  given outputEncoder[A](using inner: Encoder[Option[A]]): Encoder[Output[A]] with
    def encode(outA: Output[A]): Result[(Set[Resource], Value)] = outA.getData.flatMap { oda =>
      oda match
        case OutputData.Unknown(resources, isSecret) =>
          Result.pure(resources -> UnknownValue.asValue)

        case OutputData.Known(resources, isSecret, maybeValue) =>
          inner.encode(maybeValue).map { (innerResources, serializedValue) =>
            val aggregatedResources = resources ++ innerResources
            if isSecret then
              val secretStruct = Map(
                SpecialSigKey -> SpecialSecretSig.asValue,
                SecretValueName -> serializedValue
              )

              aggregatedResources -> secretStruct.asValue
            else aggregatedResources -> serializedValue
          }
    }

  private def assetWrapper(key: String, value: Value): Value = Map(
    Constants.SpecialSigKey -> Constants.SpecialAssetSig.asValue,
    key -> value
  ).asValue

  private def archiveWrapper(key: String, value: Value): Value = Map(
    Constants.SpecialSigKey -> Constants.SpecialArchiveSig.asValue,
    key -> value
  ).asValue

  given fileAssetEncoder: Encoder[FileAsset] = new Encoder[FileAsset]:
    def encode(fileAsset: FileAsset): Result[(Set[Resource], Value)] = Result {
      Set.empty -> assetWrapper(Constants.AssetOrArchivePathName, fileAsset.path.asValue)
    }

  given remoteAssetEncoder: Encoder[RemoteAsset] = new Encoder[RemoteAsset]:
    def encode(remoteAsset: RemoteAsset): Result[(Set[Resource], Value)] = Result {
      Set.empty -> assetWrapper(Constants.AssetOrArchiveUriName, remoteAsset.uri.asValue)
    }

  given stringAssetEncoder: Encoder[StringAsset] = new Encoder[StringAsset]:
    def encode(stringAsset: StringAsset): Result[(Set[Resource], Value)] = Result {
      Set.empty -> assetWrapper(Constants.AssetTextName, stringAsset.text.asValue)
    }

  given fileArchiveEncoder: Encoder[FileArchive] = new Encoder[FileArchive]:
    def encode(fileArchive: FileArchive): Result[(Set[Resource], Value)] = Result {
      Set.empty -> archiveWrapper(Constants.AssetOrArchivePathName, fileArchive.path.asValue)
    }

  given remoteArchiveEncoder: Encoder[RemoteArchive] = new Encoder[RemoteArchive]:
    def encode(remoteArchive: RemoteArchive): Result[(Set[Resource], Value)] = Result {
      Set.empty -> archiveWrapper(Constants.AssetOrArchiveUriName, remoteArchive.uri.asValue)
    }

  given assetArchiveEncoder: Encoder[AssetArchive] = new Encoder[AssetArchive]:
    def encode(assetArchive: AssetArchive): Result[(Set[Resource], Value)] =
      val serializedAssets =
        assetArchive.assets.toVector.map { case (key, assetOrArchive) =>
          assetOrArchiveEncoder.encode(assetOrArchive).map((key, _))
        }

      Result.sequence(serializedAssets).map { vec =>
        val (keys, depsAndValues) = vec.unzip
        val (deps, values)        = depsAndValues.unzip
        val allDeps = deps.foldLeft(Set.empty[Resource]) { case (acc, resources) =>
          acc ++ resources
        }

        allDeps -> archiveWrapper(Constants.ArchiveAssetsName, keys.zip(values).toMap.asValue)
      }

  given assetEncoder: Encoder[Asset] = new Encoder[Asset]:
    def encode(asset: Asset): Result[(Set[Resource], Value)] =
      asset match
        case fa: FileAsset   => fileAssetEncoder.encode(fa)
        case ra: RemoteAsset => remoteAssetEncoder.encode(ra)
        case sa: StringAsset => stringAssetEncoder.encode(sa)
        // case ia: InvalidAsset.type =>
        //   Result.fail(Exception("Cannot serialize invalid asset")) // TODO is this necessary?

  given archiveEncoder: Encoder[Archive] = new Encoder[Archive]:
    def encode(archive: Archive): Result[(Set[Resource], Value)] =
      archive match
        case fa: FileArchive   => fileArchiveEncoder.encode(fa)
        case ra: RemoteArchive => remoteArchiveEncoder.encode(ra)
        case aa: AssetArchive  => assetArchiveEncoder.encode(aa)
        // case ia: InvalidArchive.type =>
        //   Result.fail(Exception("Cannot serialize invalid archive")) // TODO is this necessary?

  given assetOrArchiveEncoder: Encoder[AssetOrArchive] = new Encoder[AssetOrArchive]:
    def encode(assetOrArchive: AssetOrArchive): Result[(Set[Resource], Value)] =
      assetOrArchive match
        case a: Asset   => assetEncoder.encode(a)
        case a: Archive => archiveEncoder.encode(a)

  given listEncoder[A](using innerEncoder: Encoder[A]): Encoder[List[A]] = new Encoder[List[A]]:
    def encode(lstA: List[A]): Result[(Set[Resource], Value)] =
      Result
        .sequence(lstA.map(innerEncoder.encode(_)))
        .map { lst =>
          val (resources, values) = lst.unzip
          val joinedResources     = resources.foldLeft(Set.empty[Resource])(_ ++ _)

          joinedResources -> values.asValue
        }

  // TODO is this ever necessary?
  given vectorEncoder[A](using lstEncoder: Encoder[List[A]]): Encoder[Vector[A]] =
    new Encoder[Vector[A]]:
      def encode(vecA: Vector[A]): Result[(Set[Resource], Value)] = lstEncoder.encode(vecA.toList)

  given mapEncoder[A](using inner: Encoder[A]): Encoder[Map[String, A]] = new Encoder[Map[String, A]]:
    def encode(map: Map[String, A]): Result[(Set[Resource], Value)] =
      Result
        .sequence {
          map.toList.map { case (key, value) =>
            inner.encode(value).map { encodingResultTuple =>
              key -> encodingResultTuple
            }
          }
        }
        .map { lst =>
          val (resources, map) = lst.foldLeft(Set.empty[Resource] -> Map.empty[String, Value]) {
            case ((allResources, map), (key, (resources, value))) =>
              (allResources ++ resources) -> (map + (key -> value))
          }

          resources -> map.asValue
        }

  // Support for Scala 3 Union Types or Tagged Unions vel Either below:

  transparent inline given unionEncoder[U]: Encoder[U] = ${ unionEncoderImpl[U] }

  import scala.quoted.*

  private def unionEncoderImpl[U: Type](using Quotes) =
    import quotes.reflect.*
    TypeRepr.of[U] match
      case OrType(t1, t2) =>
        (t1.asType, t2.asType) match
          case ('[t1], '[t2]) =>
            (Expr.summon[Encoder[t1]], Expr.summon[Encoder[t2]]) match
              case (Some(enc1), Some(enc2)) =>
                '{
                  new Encoder[U]:
                    def encode(aOrB: U): Result[(Set[Resource], Value)] = aOrB match
                      case a: t1 => ${ enc1 }.encode(a)
                      case b: t2 => ${ enc2 }.encode(b)
                }
              case (None, None) =>
                report.errorAndAbort(s"Encoders for ${Type.show[t1]} and ${Type.show[t2]} are missing")
              case (None, _) =>
                report.errorAndAbort(s"Encoder for ${Type.show[t1]} is missing")
              case (_, None) =>
                report.errorAndAbort(s"Encoder for ${Type.show[t2]} is missing")

  given eitherEncoder[A, B](using innerA: Encoder[A], innerB: Encoder[B]): Encoder[Either[A, B]] =
    new Encoder[Either[A, B]]:
      def encode(optA: Either[A, B]): Result[(Set[Resource], Value)] =
        optA match
          case Left(a)  => innerA.encode(a)
          case Right(b) => innerB.encode(b)

// ArgsEncoder and ProviderArgsEncoder are nearly the same with the small difference of
// ProviderArgsEncoder summoning JsonEncoder instances instead of Encoder (because all
// of the fields in provider arguments are serialized to JSON strings)
trait ArgsEncoder[A]:
  def encode(a: A, filterOut: String => Boolean): Result[(Map[String, Set[Resource]], Struct)]

object ArgsEncoder:
  def argsEncoderProduct[A](
    elems: List[(String, Encoder[?])]
  ): ArgsEncoder[A] =
    new ArgsEncoder[A]:
      override def encode(a: A, filterOut: String => Boolean): Result[(Map[String, Set[Resource]], Struct)] =
        Result
          .sequence {
            a.asInstanceOf[Product]
              .productIterator
              .zip(elems)
              .map { case (v, (label, encoder)) =>
                encoder.asInstanceOf[Encoder[Any]].encode(v).map(res => label -> res)
              }
              .toList
          }
          .map(_.toMap)
          .map { serializedMap =>
            val (mapOfResources, mapOfValues) =
              serializedMap.foldLeft[(Map[String, Set[Resource]], Map[String, Value])](Map.empty -> Map.empty) {
                case ((mapOfResources, mapOfValues), (label, (resources, value))) =>
                  if filterOut(label) then (mapOfResources, mapOfValues) // skip filtered
                  else (mapOfResources + (label -> resources), mapOfValues + (label -> value))
              }

            val struct = mapOfValues.asStruct

            mapOfResources -> struct
          }

  inline def derived[A <: Product](using m: Mirror.ProductOf[A]): ArgsEncoder[A] =
    lazy val labels: List[String]        = CodecMacros.summonLabels[m.MirroredElemLabels]
    lazy val instances: List[Encoder[?]] = CodecMacros.summonEncoders[m.MirroredElemTypes]
    lazy val nameEncoderPairs            = labels.zip(instances)

    argsEncoderProduct(nameEncoderPairs)

trait ProviderArgsEncoder[A] extends ArgsEncoder[A]:
  def encode(a: A, filterOut: String => Boolean): Result[(Map[String, Set[Resource]], Struct)]

object ProviderArgsEncoder:
  def providerArgsEncoderProduct[A](
    elems: List[(String, JsonEncoder[?])]
  ): ProviderArgsEncoder[A] =
    new ProviderArgsEncoder[A]:
      override def encode(a: A, filterOut: String => Boolean): Result[(Map[String, Set[Resource]], Struct)] =
        Result
          .sequence {
            a.asInstanceOf[Product]
              .productIterator
              .zip(elems)
              .map { case (v, (label, encoder)) =>
                encoder.asInstanceOf[JsonEncoder[Any]].encode(v).map(res => label -> res)
              }
              .toList
          }
          .map(_.toMap)
          .map { serializedMap =>
            val (mapOfResources, mapOfValues) =
              serializedMap.foldLeft[(Map[String, Set[Resource]], Map[String, Value])](Map.empty -> Map.empty) {
                case ((mapOfResources, mapOfValues), (label, (resources, value))) =>
                  if filterOut(label) then (mapOfResources, mapOfValues)
                  else (mapOfResources + (label -> resources), mapOfValues + (label -> value))
              }

            val struct = mapOfValues.asStruct

            mapOfResources -> struct
          }

  inline def derived[A <: Product](using m: Mirror.ProductOf[A]): ProviderArgsEncoder[A] =
    lazy val labels: List[String]            = CodecMacros.summonLabels[m.MirroredElemLabels]
    lazy val instances: List[JsonEncoder[?]] = CodecMacros.summonJsonEncoders[m.MirroredElemTypes]
    lazy val nameEncoderPairs                = labels.zip(instances)

    providerArgsEncoderProduct(nameEncoderPairs)

trait JsonEncoder[A]:
  def encode(a: A): Result[(Set[Resource], Value)]

object JsonEncoder:
  import ProtobufUtil.*

  given jsonEncoder[A](using enc: Encoder[A]): JsonEncoder[A] =
    new JsonEncoder[A]:
      def encode(a: A): Result[(Set[Resource], Value)] = enc.encode(a).flatMap { case (resources, value) =>
        Result.evalEither(value.asJsonString).transform {
          case Left(ex) =>
            Left(Exception("Encountered a malformed protobuf Value that could not be serialized to JSON", ex))
          case Right(jsonString) => Right(resources -> jsonString.asValue)
        }
      }
