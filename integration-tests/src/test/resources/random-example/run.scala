//> using scala "3.3.0"
//> using lib "org.virtuslab::besom-core:0.0.1-SNAPSHOT"
//> using lib "org.virtuslab::besom-random:4.13.2-SNAPSHOT.0.0.1"

import besom.*
import besom.api.random.*

@main
def main(): Unit = Pulumi.run {

  val strOutput = RandomString(
    name = "random-string",
    args = RandomStringArgs(
      length = 10
    )
  )

  for
    str <- strOutput
  yield Pulumi.exports(
    randomString = str.result
  )
}
