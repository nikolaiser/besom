//> using scala "3.3.1"
//> using options "-java-output-version:11", "-Ysafe-init", "-Xmax-inlines:64"
//> using options "-Werror", "-Wunused:all", "-deprecation", "-feature", "-Wconf:cat=deprecation:i"
// -language:noAutoTupling // after https://github.com/VirtusLab/scala-cli/issues/2708

//> using dep "org.virtuslab::besom-json:0.4.0-SNAPSHOT"
//> using dep "com.google.protobuf:protobuf-java-util:3.25.1"
//> using dep "io.grpc:grpc-netty:1.64.0"
//> using dep "io.netty:netty-transport-native-kqueue:4.1.100.Final"
//> using dep "io.netty:netty-transport-native-epoll:4.1.100.Final"
//> using dep "com.thesamet.scalapb::scalapb-runtime:0.11.15"
//> using dep "com.thesamet.scalapb::scalapb-runtime-grpc:0.11.15"
//> using dep "com.google.guava:guava:32.1.2-jre"
//> using dep "com.outr::scribe:3.13.5"
//> using dep "com.outr::scribe-file:3.13.5"
//> using dep "com.lihaoyi::sourcecode:0.4.1"
//> using dep "com.lihaoyi::pprint:0.9.0"
//> using test.dep "org.scalameta::munit:1.0.0"

//> using publish.name "besom-core"
//> using publish.organization "org.virtuslab"
//> using publish.url "https://github.com/VirtusLab/besom"
//> using publish.vcs "github:VirtusLab/besom"
//> using publish.license "Apache-2.0"
//> using publish.repository "central"
//> using publish.developer "lbialy|Łukasz Biały|https://github.com/lbialy"
//> using publish.developer "prolativ|Michał Pałka|https://github.com/prolativ"
//> using publish.developer "KacperFKorban|Kacper Korban|https://github.com/KacperFKorban"
//> using publish.developer "pawelprazak|Paweł Prażak|https://github.com/pawelprazak"
//> using repository sonatype:snapshots
