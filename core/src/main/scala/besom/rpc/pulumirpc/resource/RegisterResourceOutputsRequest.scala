// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package pulumirpc.resource

/** RegisterResourceOutputsRequest adds extra resource outputs created by the program after registration has occurred.
  *
  * @param urn
  *   the URN for the resource to attach output properties to.
  * @param outputs
  *   additional output properties to add to the existing resource.
  */
@SerialVersionUID(0L)
final case class RegisterResourceOutputsRequest(
    urn: _root_.scala.Predef.String = "",
    outputs: _root_.scala.Option[com.google.protobuf.struct.Struct] = _root_.scala.None,
    unknownFields: _root_.scalapb.UnknownFieldSet = _root_.scalapb.UnknownFieldSet.empty
    ) extends scalapb.GeneratedMessage with scalapb.lenses.Updatable[RegisterResourceOutputsRequest] {
    @transient
    private[this] var __serializedSizeMemoized: _root_.scala.Int = 0
    private[this] def __computeSerializedSize(): _root_.scala.Int = {
      var __size = 0
      
      {
        val __value = urn
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, __value)
        }
      };
      if (outputs.isDefined) {
        val __value = outputs.get
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      };
      __size += unknownFields.serializedSize
      __size
    }
    override def serializedSize: _root_.scala.Int = {
      var __size = __serializedSizeMemoized
      if (__size == 0) {
        __size = __computeSerializedSize() + 1
        __serializedSizeMemoized = __size
      }
      __size - 1
      
    }
    def writeTo(`_output__`: _root_.com.google.protobuf.CodedOutputStream): _root_.scala.Unit = {
      {
        val __v = urn
        if (!__v.isEmpty) {
          _output__.writeString(1, __v)
        }
      };
      outputs.foreach { __v =>
        val __m = __v
        _output__.writeTag(2, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
      unknownFields.writeTo(_output__)
    }
    def withUrn(__v: _root_.scala.Predef.String): RegisterResourceOutputsRequest = copy(urn = __v)
    def getOutputs: com.google.protobuf.struct.Struct = outputs.getOrElse(com.google.protobuf.struct.Struct.defaultInstance)
    def clearOutputs: RegisterResourceOutputsRequest = copy(outputs = _root_.scala.None)
    def withOutputs(__v: com.google.protobuf.struct.Struct): RegisterResourceOutputsRequest = copy(outputs = Option(__v))
    def withUnknownFields(__v: _root_.scalapb.UnknownFieldSet) = copy(unknownFields = __v)
    def discardUnknownFields = copy(unknownFields = _root_.scalapb.UnknownFieldSet.empty)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = urn
          if (__t != "") __t else null
        }
        case 2 => outputs.orNull
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(urn)
        case 2 => outputs.map(_.toPMessage).getOrElse(_root_.scalapb.descriptors.PEmpty)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion: pulumirpc.resource.RegisterResourceOutputsRequest.type = pulumirpc.resource.RegisterResourceOutputsRequest
    // @@protoc_insertion_point(GeneratedMessage[pulumirpc.RegisterResourceOutputsRequest])
}

object RegisterResourceOutputsRequest extends scalapb.GeneratedMessageCompanion[pulumirpc.resource.RegisterResourceOutputsRequest] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[pulumirpc.resource.RegisterResourceOutputsRequest] = this
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): pulumirpc.resource.RegisterResourceOutputsRequest = {
    var __urn: _root_.scala.Predef.String = ""
    var __outputs: _root_.scala.Option[com.google.protobuf.struct.Struct] = _root_.scala.None
    var `_unknownFields__`: _root_.scalapb.UnknownFieldSet.Builder = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 => _done__ = true
        case 10 =>
          __urn = _input__.readStringRequireUtf8()
        case 18 =>
          __outputs = _root_.scala.Option(__outputs.fold(_root_.scalapb.LiteParser.readMessage[com.google.protobuf.struct.Struct](_input__))(_root_.scalapb.LiteParser.readMessage(_input__, _)))
        case tag =>
          if (_unknownFields__ == null) {
            _unknownFields__ = new _root_.scalapb.UnknownFieldSet.Builder()
          }
          _unknownFields__.parseField(tag, _input__)
      }
    }
    pulumirpc.resource.RegisterResourceOutputsRequest(
        urn = __urn,
        outputs = __outputs,
        unknownFields = if (_unknownFields__ == null) _root_.scalapb.UnknownFieldSet.empty else _unknownFields__.result()
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[pulumirpc.resource.RegisterResourceOutputsRequest] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      pulumirpc.resource.RegisterResourceOutputsRequest(
        urn = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        outputs = __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).flatMap(_.as[_root_.scala.Option[com.google.protobuf.struct.Struct]])
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = ResourceProto.javaDescriptor.getMessageTypes().get(6)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = ResourceProto.scalaDescriptor.messages(6)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 2 => __out = com.google.protobuf.struct.Struct
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = pulumirpc.resource.RegisterResourceOutputsRequest(
    urn = "",
    outputs = _root_.scala.None
  )
  implicit class RegisterResourceOutputsRequestLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, pulumirpc.resource.RegisterResourceOutputsRequest]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, pulumirpc.resource.RegisterResourceOutputsRequest](_l) {
    def urn: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.urn)((c_, f_) => c_.copy(urn = f_))
    def outputs: _root_.scalapb.lenses.Lens[UpperPB, com.google.protobuf.struct.Struct] = field(_.getOutputs)((c_, f_) => c_.copy(outputs = _root_.scala.Option(f_)))
    def optionalOutputs: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Option[com.google.protobuf.struct.Struct]] = field(_.outputs)((c_, f_) => c_.copy(outputs = f_))
  }
  final val URN_FIELD_NUMBER = 1
  final val OUTPUTS_FIELD_NUMBER = 2
  def of(
    urn: _root_.scala.Predef.String,
    outputs: _root_.scala.Option[com.google.protobuf.struct.Struct]
  ): _root_.pulumirpc.resource.RegisterResourceOutputsRequest = _root_.pulumirpc.resource.RegisterResourceOutputsRequest(
    urn,
    outputs
  )
  // @@protoc_insertion_point(GeneratedMessageCompanion[pulumirpc.RegisterResourceOutputsRequest])
}
