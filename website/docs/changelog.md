---
title: Changelog
---

0.3.2 (11-06-2024)
---

* Added missing `provider` / `providers` field on resources to fix #397 (@lbialy in [505](https://github.com/VirtusLab/besom/pull/505))
* Exposed `CustomTimeouts` resource option configuration value (@lbialy in [502](https://github.com/VirtusLab/besom/pull/502))
* Fixed issue 489 where ANSI control chars crashed internal logging (@lbialy in [492](https://github.com/VirtusLab/besom/pull/492))
* First working draft of Automation API (sync, Either-monad based) (@pawelprazak in [336](https://github.com/VirtusLab/besom/pull/336))
* Fixed issues with eviction of dependencies of core besom library (@lbialy in [500](https://github.com/VirtusLab/besom/pull/500))
* Fixed bug that crashed besom when resource options were provided for component resource (@lbialy in [502](https://github.com/VirtusLab/besom/pull/502))
* First working draft of Besom Configure (besom-cfg) (Kubernetes only) (@lbialy in [494](https://github.com/VirtusLab/besom/pull/494))
* Allow passing options to scala-cli via language plugin executor (@lbialy in [503](https://github.com/VirtusLab/besom/pull/503))

New examples:

* Add AWS API Gateway V2 with EventBridge example by @polkx in https://github.com/VirtusLab/besom/pull/479

**Full Changelog**: https://github.com/VirtusLab/besom/compare/v0.3.1...v0.3.2

0.3.1 (19-04-2024)
---

* Support for `ConfigGroup`, `ConfigFile` resources in [Kubernetes provider 4.11.0+](https://www.pulumi.com/blog/kubernetes-yaml-v2/) 
  is now available in Besom!

* Added new combinators on `Output`:
  * `recover` allows to map an error inside of a failed `Output` to a new value
  * `recoverWith` allows the same but using an effectful function returning either an `Output` or any 
  other supported effect, e.g.: `Future`, `IO` or `Task`
  * `tap` allows to access the value of an `Output` and apply an effectful function to it while 
  discarding said function's results
  * `tapError` allows the same but for an error of a failed `Output`
  * `tapBoth` takes two effectful function and allows to access either error or value of an `Output` by 
  applying one of them to the contents of the `Output`
  * `void` discards the value of an `Output`, comes with a static constructor function - `Output.unit`
  * `unzip` can be called on an `Output` of a tuple to receive a tuple of `Output`s, all of which are 
  descendents of the original `Output`

**Full Changelog**: [GitHub (v0.3.0...v0.3.1)](https://github.com/VirtusLab/besom/compare/v0.3.0...v0.3.1)

0.3.0 (16-04-2024)
---

### API Changes and New Features

* Added new `besom.json` interpolation API. Now this snippet from our tutorial:
```scala
s3.BucketPolicyArgs(
  bucket = feedBucket.id,
  policy = JsObject(
    "Version" -> JsString("2012-10-17"),
    "Statement" -> JsArray(
      JsObject(
        "Sid" -> JsString("PublicReadGetObject"),
        "Effect" -> JsString("Allow"),
        "Principal" -> JsObject(
          "AWS" -> JsString("*")
        ),
        "Action" -> JsArray(JsString("s3:GetObject")),
        "Resource" -> JsArray(JsString(s"arn:aws:s3:::${name}/*"))
      )
    )
  ).prettyPrint
)
```
can be rewritten as:
```scala
s3.BucketPolicyArgs(
  bucket = feedBucket.id,
  policy = json"""{
    "Version": "2012-10-17",
    "Statement": [{
      "Sid": "PublicReadGetObject",
      "Effect": "Allow",
      "Principal": {
        "AWS": "*"
      },
      "Action": ["s3:GetObject"],
      "Resource": ["arn:aws:s3::${name}/*"]
    }]
  }""".map(_.prettyPrint)
)
```
The json interpolator returns an `Output[JsValue]` and is fully **compile-time type safe** and verifies JSON string for correctness. 
The only types that can be interpolated are `String`, `Int`, `Short`, `Long`, `Float`, `Double`, `JsValue` and `Option` and `Output` 
of the former (in whatever amount of nesting). If you need to interpolate a more complex type it's advised to derive a `JsonFormat` 
for it and convert it to `JsValue`.

* Package `besom.json` was modified to ease the use of `JsonFormat` derivation. This change is breaking compatibility by exporting 
default instances from `DefaultJsonProtocol` and providing a given `JsonProtocol` instance for use with `derives JsonFormat`.
If you need to define a custom `JsonProcol` change the import to `import besom.json.custom.*` which preserves the older semantics
from spray-json and requires manual extension of `DefaultJsonProtocol`.

* Derived `JsonFormat` from package `besom.json` now respects arguments with default values. In conjunction with the previous change 
it means one can now use it like this:
```scala
import besom.json.*

case class Color(name: String, red: Int, green: Int, blue: Int = 160) derives JsonFormat
val color = Color("CadetBlue", 95, 158)

val json = """{"name":"CadetBlue","red":95,"green":158}"""

assert(color.toJson.convertTo[Color] == color)
assert(json.parseJson.convertTo[Color] == color)
```

### Bug Fixes

* fixed infinite loop in encoders [#407](https://github.com/VirtusLab/besom/issues/407) when a recursive type is encountered 
* fixed cause passing in `AggregateException` to improve debugging of decoders [#426](https://github.com/VirtusLab/besom/issues/426)
* fixed Pulumi side effects memoization issues in Component API [#429](https://github.com/VirtusLab/besom/pull/429)
* fixed traverse problem caused by export bug in compiler with a temporary workaround [#430](https://github.com/VirtusLab/besom/issues/430)

### Other Changes

* custom timeouts have scaladocs now [#419](https://github.com/VirtusLab/besom/pull/419)
* overhauled serde layer with refined outputs implemented to improve parity with upstream Pulumi engine [#414](https://github.com/VirtusLab/besom/pull/414)
* StackReferences are now documented [#428](https://github.com/VirtusLab/besom/pull/428)
* updated AWS EKS hello world example [#399](https://github.com/VirtusLab/besom/pull/399/files)
* Component API now disallows returning component instances wrapped in Outputs to prevent users from dry run issues [#441](https://github.com/VirtusLab/besom/pull/441)
* added `parSequence` and `parTraverse` combinators on `Output` [#440](https://github.com/VirtusLab/besom/pull/440)
* added `Output.when` combinator [#439](https://github.com/VirtusLab/besom/pull/439)
* improved compilation errors around `Output.eval` and `Output#flatMap` [#443](https://github.com/VirtusLab/besom/pull/443)
* all `Output` combinators have scaladocs now [#445](https://github.com/VirtusLab/besom/pull/445)
* added extension-based combinators for `Output[Option[A]]`, `Output[List[A]]` etc [#445](https://github.com/VirtusLab/besom/pull/445)
* added support for overlays (package-specific extensions) in besom `codegen`, this opens a way for support of Helm, magic lambdas and other advanced features [#402](https://github.com/VirtusLab/besom/pull/402)

**Full Changelog**: [GitHub (v0.2.2...v0.3.0)](https://github.com/VirtusLab/besom/compare/v0.2.2...v0.3.0)

0.2.2 (22-02-2024)
---

### Bug Fixes

* fixed component argument serialization issue [398](https://github.com/VirtusLab/besom/pull/398)

### Other Changes

* added Kubernetes guestbook example [395](https://github.com/VirtusLab/besom/pull/395)

**Full Changelog**: [GitHub (v0.2.1...v0.2.2)](https://github.com/VirtusLab/besom/compare/v0.2.1...v0.2.2)

0.2.1 (15-02-2024)
---

### Bug Fixes

* Fix URL validation to allow for kubernetes types [#385](https://github.com/VirtusLab/besom/pull/385)
* Loosen up and fix URN parsing [#389](https://github.com/VirtusLab/besom/pull/389)
* Fix serializer now skips fields with null value secrets [#386](https://github.com/VirtusLab/besom/pull/386)

**Full Changelog**: [GitHub (v0.2.0...v0.2.1)](https://github.com/VirtusLab/besom/compare/v0.2.0...v0.2.1)

0.2.0 (08-02-2024)
---

### API Changes

* Changed the type of main `Pulumi.run` function from `Context ?=> Output[Exports]` (a [context function](https://docs.scala-lang.org/scala3/reference/contextual/context-functions.html) providing `besom.Context` 
  instance implicitly in its scope and expecting `Output[Exports]` as returned value) to `Context ?=> Stack`. This change has one core 
  reason: it helps us solve a problem related to dry run functionality that was hindered when a external Output was interwoven in the final
  for-comprehension. External Outputs (Outputs that depend on real return values from Pulumi engine) no-op their flatMap/map chains in dry
  run similarly to Option's None (because there is no value to feed to the passed function) and therefore led to exports code not being
  executed in dry run at all, causing a diff showing that all exports are going to be removed in preview and then recreated in apply phase.
  New type of `Pulumi.run` function disallows returning of async values - Stack has to be returned unwrapped, synchronously. Stack is just a
  case class that takes only two arguments: `exports: Exports` and `dependsOn: Vector[Output[?]]`. `exports` serve the same purpose as
  before and `dependsOn` is used to _use_ all the `Outputs` that have to be evaluated for this stack to be constructed but are not to be
  exported. You can return a `Stack` that only consists of exports (for instance when everything you depend on is composed into a thing that
  you export in the final step) using `Stack.export(x = a, y = b)` or a `Stack` that has only dependencies when you don't want to export
  anything using `Stack(x, y)`. You can also use some resources and export others using `Stack(a, b).export(x = i, y = j)` syntax.
  Here's an example use of Stack:
  ```scala
  @main def main = Pulumi.run {
    val awsPolicy = aws.iam.Policy("my-policy",...)
    val s3 = aws.s3.Bucket("my-bucket")
    val logMessage = log.info("Creating your bucket!") // logs are values too!
  
    Stack(logMessage, awsPolicy).exports(
      url = s3.publicEndpoint
    )
  }
  ```

* Improved `Config` ergonomy, by automatic secrets handling and using `Output[A]` as the return value, 
and also adding a helpful error message when a key is missing [#204](https://github.com/VirtusLab/besom/issues/204)

* Overhauled `ResourceOptions` [#355](https://github.com/VirtusLab/besom/pull/355)

### New Features

* added support for Gradle and Maven to the Besom Scala language host [#303](https://github.com/VirtusLab/besom/issues/303)

* [Provider functions](https://www.pulumi.com/docs/concepts/resources/functions/) like `aws.ec2.getAmi` were added to Besom. 
They allow you to use the Pulumi SDK to fetch data from the cloud provider and use it in your program. Here's an example of how to use them:

  ```scala 
  @main def main = Pulumi.run {
    val ami = ec2.getAmi(
      ec2.GetAmiArgs(
        filters = List(
          GetAmiFilterArgs(
            name = "name",
            values = List("amzn2-ami-hvm-*-x86_64-ebs")
          )
        ),
        owners = List("137112412989"), // Amazon
        mostRecent = true
      )
    )
    val server = ec2.Instance("web-server-www", 
      ec2.InstanceArgs(ami = ami.id,...)
    )
  
    Stack(server).exports(
      ami = ami.id
    )
  }
  ```

* [Stack References](https://www.pulumi.com/docs/concepts/stack/#stackreferences) were added to Besom. They allow you to use outputs from
  another stack as inputs to the current stack. Here's an example of how to use them [#348](https://github.com/VirtusLab/besom/pull/348):

  ```scala
  @main def main = Pulumi.run {
    ...
    Stack.exports(
      someOutput = "Hello world!",
    )
  }
  ```
  ```scala
  @main def main = Pulumi.run {
    val otherStack = besom.StackReference("stackRef", StackReferenceArgs("organization/source-stack-test/my-stack-name"))
    val otherStackOutput = otherStack.output[String]("someOutput")
    ...
  }
  ```

* Added support for [Get functions](https://www.pulumi.com/docs/concepts/resources/get/) to Besom. You can use the static get function, 
which is available on all resource types, to look up an existing resource that is not managed by Pulumi. Here's an example of how to use it:

  ```scala
  @main def main = Pulumi.run {
    val group = aws.ec2.SecurityGroup.get("group", "sg-0dfd33cdac25b1ec9")
    ...
  }
  ```

* Added support for [Structured Configuration](https://www.pulumi.com/docs/concepts/config/#structured-configuration), this allows user to
read structured configuration from Pulumi configuration into JSON AST (`config.getJson` or `config.requireJson`) 
or deserialize to an object (`config.getObject` or `config.requireObject`) [#207](https://github.com/VirtusLab/besom/issues/207)

* Added new methods to the Besom `Context` and `Resource`, that allow for introspection into basic Pulumi metadata:
`pulumiResourceName` and `pulumiProject`, `pulumiOrganization`, `pulumiStack` [#295](https://github.com/VirtusLab/besom/issues/295)

* Added support for [Remote Components](https://www.pulumi.com/docs/intro/concepts/programming-model/#remote-components) to Besom
  [#355](https://github.com/VirtusLab/besom/pull/355).

* Provider SDKs Code Generator was improved on multiple fronts:
  - normalized the generated code to be more idiomatic and consistent with the rest of the SDK
  - added support for provider functions and methods
  - added support for component providers 
  - added support for convenient provider configuration access [#259](https://github.com/VirtusLab/besom/issues/259)
  - allow to refer to other resources by reference instead of ID [#144](https://github.com/VirtusLab/besom/issues/144)

* Improved [NonEmptyString] inference via metaprogramming to find string literals by recursively traversing the AST tree. It now coveres cases where strings are defined directly as `: String`, concatenated with `+`, multiplied with `*`, all kinds of interpolators (including `p`/`pulumi` interpolator) are covered. Additionally, some new extension methods are defined to ease work with NES:
  - `"string".toNonEmpty: Option[NonEmptyString]` - safe, same as `NonEmptyString("string")`
  - `"string".toNonEmptyOrThrow: `NonEmptyString` - an unsafe extension for situations where you just can't be bothered
  - `"string".toNonEmptyOutput: Output[NonEmptyString]` - safe, if the string is empty the returned Output will be failed
  - `Output("string").toNonEmptyOutput: Output[NonEmptyString]` - safe, if the string inside of the Output is empty the returned Output will be failed

### Bug Fixes

* fixed logging via Pulumi RPC and added user-level MDC, now logs are properly displayed in the Pulumi console [#199](https://github.com/VirtusLab/besom/issues/199)
* fixed failing gRPC shutdown in our core SDK by correcting the lifecycle handling, now the SDK properly shuts down [#228](https://github.com/VirtusLab/besom/issues/228)
* fixed failing gRPC serialisation in multiple cases, now the SDK properly serialises and deserializes messages [#148](https://github.com/VirtusLab/besom/issues/148) [#150](https://github.com/VirtusLab/besom/issues/150)
* fixed failing shorthand config key names, now the SDK properly handles shorthand config key names [#205](https://github.com/VirtusLab/besom/issues/205)
* fixed `Output.sequence`/`Result.sequence` multiple evaluations issue, now the SDK properly handles multiple evaluations of `Output.sequence`/`Result.sequence` [#313](https://github.com/VirtusLab/besom/pull/313)
* fixed failing codecs for provider inputs, now the SDK properly handles provider inputs [#312](https://github.com/VirtusLab/besom/pull/312)
* fixed `Output.traverse` runtime exception, now the SDK properly handles `Output.traverse` [#360](https://github.com/VirtusLab/besom/pull/360)
* fixed fatal errors hanging the SDK, now the SDK properly handles fatal errors [#361](https://github.com/VirtusLab/besom/pull/361)
* fixed resource decoders failing at runtime when handling special resources [#364](https://github.com/VirtusLab/besom/pull/364)
* fixed transitive dependency resolution algorithm, now the SDK properly resolves transitive dependencies [#371](https://github.com/VirtusLab/besom/pull/371)
* fixed failing code generation for unsupported or malformed schema files, now the generator properly handles schemas [#265](https://github.com/VirtusLab/besom/pull/265) [#270](https://github.com/VirtusLab/besom/pull/270) [#274](https://github.com/VirtusLab/besom/pull/274) [#329](https://github.com/VirtusLab/besom/pull/329) [#332](https://github.com/VirtusLab/besom/pull/332) 
* fixed failing code generation when clashing names are present in the schema, now the generator properly handles clashing names [#275](https://github.com/VirtusLab/besom/pull/275)
* fixed failing code generation schema deserialization for complex types used as underlying types of named types [#282](https://github.com/VirtusLab/besom/pull/282)

### Other Changes

* added more [examples](https://github.com/VirtusLab/besom/blob/release/v0.2.0/examples/README.md) to the Besom repository
* introduce schema-driven integration tests for `codegen` fed from upstream Pulumi to improve reliability of the code generator
* many internal improvements and refactorings to the codebase to improve the overall quality of the SDK and its maintainability

**Full Changelog**: [GitHub (v0.1.0...v0.2.0)](https://github.com/VirtusLab/besom/compare/v0.1.0...v0.2.0)

