/** okay-teach: a small model taught a domain — a space of meanings (okay-frames), questions a teacher writes,
 * kept only when the laws reach their meaning, a LoRA on a small model, a calibrated bar; and a teacher that writes
 * Datalog rules by choices, judged (specs/teach.md) */
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / organization := "io.github.okay-effects"
ThisBuild / scalaVersion := "3.3.8"
ThisBuild / version := "0.1.0"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all", "-Werror")
ThisBuild / homepage := Some(url("https://github.com/okay-effects/okay-teach"))
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / resolvers ++= Seq("okay-scala" at "https://okay-effects.github.io/okay-scala/maven",
  "okay-simple" at "https://okay-effects.github.io/okay-simple/maven",
  "okay-frames" at "https://okay-effects.github.io/okay-frames/maven",
  "okay-datalog" at "https://okay-effects.github.io/okay-datalog/maven",
  "okay-llm" at "https://okay-effects.github.io/okay-llm/maven",
  "mlx-scala" at "https://sergey-scherbina.github.io/mlx-scala/maven")
/** RELEASES, ON GITHUB PAGES: `sbt publish` writes into `target/pages-maven/` */
val pagesRepository = settingKey[File]("the Maven repository a release is written to, for GitHub Pages")
ThisBuild / pagesRepository := (ThisBuild / baseDirectory).value / "target" / "pages-maven"
ThisBuild / publishTo := Some(MavenCache("pages", (ThisBuild / pagesRepository).value))
ThisBuild / Compile / packageDoc / publishArtifact := false

val munit = Def.setting("org.scalameta" %%% "munit" % "1.2.3" % Test)

/** THE TOOLS, no device: domains, the reading (calibrated), the teacher's questions and their filter, the bar,
 * the rule space and its teacher — programs over okay-llm's effects (`Read`, `Llm`); the JVM and Scala.js */
lazy val core = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("core")).dependsOn(equations)
  .settings(name := "okay-teach",
    libraryDependencies ++= Seq("io.github.okay-effects" %%% "okay-frames" % "0.1.0",
      "io.github.okay-effects" %%% "okay-datalog" % "0.1.0",
      "io.github.okay-effects" %%% "okay-llm-read" % "0.1.0", munit.value))

/** THE EQUATIONS ENGINE (specs/equations.md): quantities, laws as data, the solver — no dependency; JVM and Scala.js */
lazy val equations = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("equations"))
  .settings(name := "okay-teach-equations", libraryDependencies += munit.value)

/** ON APPLE'S GPU: the reading answered by okay-llm's Qwen3 on MLX (the page's numbers), a LoRA trained on the
 * examples (mlx-scala), the teacher over HTTP (okay-llm's `HttpBackend`) */
val mlxNativeLib =
  sys.env.get("MLX_SCALA_LIB").map(file).getOrElse(file("../mlx-scala/native-lib/out/lib")).getCanonicalFile
lazy val mlx = (project in file("mlx")).dependsOn(core.jvm)
  .settings(name := "okay-teach-mlx",
    libraryDependencies ++= Seq("io.github.okay-effects" %% "okay-llm-qwen-mlx" % "0.1.0",
      "io.github.sergey-scherbina" %% "mlx-scala-llm" % "0.1.0", munit.value),
    fork := true, run / baseDirectory := (ThisBuild / baseDirectory).value,
    javaOptions ++= Seq("--enable-native-access=ALL-UNNAMED", s"-Dokm.lib=$mlxNativeLib/libokm.dylib", "-Xss16m"))

lazy val root = (project in file(".")).aggregate(equations.jvm, equations.js, core.jvm, core.js, mlx, domains)
  .settings(publish / skip := true)

/** THE DOMAINS TAUGHT, and the command `teach` (write, train, read): okay-tax's rules first */
lazy val domains = (project in file("domains")).dependsOn(mlx)
  .settings(name := "okay-teach-domains", publish / skip := true,
    resolvers ++= Seq("okay-tax" at "https://okay-effects.github.io/okay-tax/maven",
      "okay-study" at "https://okay-effects.github.io/okay-study/maven"),
    libraryDependencies ++= Seq("io.github.okay-effects" %% "okay-tax-core" % "0.1.0",
      "io.github.okay-effects" %% "okay-study" % "0.1.0", munit.value),
    Test / fork := true, Test / baseDirectory := (ThisBuild / baseDirectory).value,
    fork := true, run / baseDirectory := (ThisBuild / baseDirectory).value,
    javaOptions ++= Seq("--enable-native-access=ALL-UNNAMED", s"-Dokm.lib=$mlxNativeLib/libokm.dylib", "-Xss16m"))
