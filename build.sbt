import scala.sys.process._

name := "auction-system"
version := "1.0"

cancelable in Global := true
Compile / run / fork := true
Compile / run / connectInput := true

scalaVersion := "2.13.8"

libraryDependencies ++= Seq(
  "com.daml" % "bindings-rxjava" % "2.1.1",
)

 // autogenerate java bindings during compile time
Compile / sourceGenerators += Def.task {
  val path = (Compile / sourceManaged).value / "java-bindings"
  val projectRoot = (root / baseDirectory).value.getAbsolutePath
  println(projectRoot)
  val build = "daml build"
  val codeGen = s"daml codegen java --output-directory=$path .daml/dist/auction-system-0.0.1.dar"
  // generate files and return them in a Sequence
  (build #&& codeGen)!

  val pathDir = new File(path.toString())
    (pathDir ** (-DirectoryFilter)).get.toSeq
}.taskValue

// Reference: https://docs.scala-lang.org/overviews/compiler-options/index.html
// Recommendations: https://nathankleyn.com/2019/05/13/recommended-scalac-flags-for-2-13/
// Wconf: https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
scalacOptions ++= Seq(
  // Keep the target version in sync with the JVM version in the Dockerfile.
  "-target:jvm-11",
  // Read Scala files as UTF-8.
  "-encoding",
  "utf-8",
  // Silence warnings for generated code.
  "-Wconf:src=src_managed/.*:silent",
  "-Wconf:src=gcp_gen/.*:silent",
  // Fail the compilation if there are any warnings.
  "-Werror",
  // Emit warning and location for usages of deprecated APIs.
  "-deprecation",
  // Explain type errors in more detail.
  "-explaintypes",
  // Emit warning and location for usages of features that should be imported explicitly.
  "-feature",
  // Enable additional warnings where generated code depends on assumptions.
  "-unchecked",
  // Warn if an argument list is modified to match the receiver.
  "-Xlint:adapted-args",
  // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:constant",
  // Selecting member of DelayedInit.
  "-Xlint:delayedinit-select",
  // A Scaladoc comment appears to be detached from its element.
  "-Xlint:doc-detached",
  // Warn about inaccessible types in method signatures.
  "-Xlint:inaccessible",
  // Warn when a type argument is inferred to be `Any`.
  "-Xlint:infer-any",
  // A string literal appears to be missing an interpolator id.
  "-Xlint:missing-interpolator",
  // Warn when nullary methods return Unit.
  "-Xlint:nullary-unit",
  // Option.apply used implicit view.
  "-Xlint:option-implicit",
  // Class or object defined in package object.
  "-Xlint:package-object-classes",
  // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:poly-implicit-overload",
  // A private field (or class parameter) shadows a superclass field.
  "-Xlint:private-shadow",
  // Pattern sequence wildcard must align with sequence component.
  "-Xlint:stars-align",
  // A local type parameter shadows a type already in scope.
  "-Xlint:type-parameter-shadow",
  // Warn when dead code is identified.
  "-Ywarn-dead-code",
  // Warn when more than one implicit parameter section is defined.
  "-Ywarn-extra-implicit",
  // Warn when numerics are widened.
  "-Ywarn-numeric-widen",
  // Warn if an implicit parameter is unused.
  "-Ywarn-unused:implicits",
  // Warn if an import selector is not referenced.
  "-Ywarn-unused:imports",
  // Warn if a local definition is unused.
  "-Ywarn-unused:locals",
  // Warn if a @nowarn annotation does not suppress any warnings.
  "-Ywarn-unused:nowarn",
  // Warn if a value parameter is unused.
  "-Ywarn-unused:params",
  // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:patvars",
  // Warn if a private member is unused.
  "-Ywarn-unused:privates",
  // Warn when non-Unit expression results are unused.
  "-Ywarn-value-discard",
  // Avoid "Exhaustivity analysis reached max recursion depth".
  "-Ypatmat-exhaust-depth",
  "80",
)

compileOrder := CompileOrder.JavaThenScala

lazy val root = (project in file("."))

