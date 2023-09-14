import mill._
import mill.scalalib._
import mill.scalalib.publish._
import coursier.maven.MavenRepository
import $file.hardfloat.common
import $file.cde.common
import $file.common
import $file.difftest.build

object v {
  val scala = "2.13.10"
  // the first version in this Map is the mainly supported version which will be used to run tests
  val chiselCrossVersions = Map(
    "3.6.0" -> (ivy"edu.berkeley.cs::chisel3:3.6.0", ivy"edu.berkeley.cs:::chisel3-plugin:3.6.0"),
    "5.0.0" -> (ivy"org.chipsalliance::chisel:6.0.0-M3", ivy"org.chipsalliance:::chisel-plugin:6.0.0-M3"),
  )
  val mainargs = ivy"com.lihaoyi::mainargs:0.5.0"
  val json4sJackson = ivy"org.json4s::json4s-jackson:4.0.5"
  val scalaReflect = ivy"org.scala-lang:scala-reflect:${scala}"
}

object macros extends Macros

trait Macros
  extends millbuild.common.MacrosModule
    with RocketChipPublishModule
    with SbtModule {

  def scalaVersion: T[String] = T(v.scala)

  def scalaReflectIvy = v.scalaReflect
}

object hardfloat extends mill.define.Cross[Hardfloat](v.chiselCrossVersions.keys.toSeq)

trait Hardfloat
  extends millbuild.hardfloat.common.HardfloatModule
    with RocketChipPublishModule
    with Cross.Module[String] {

  def scalaVersion: T[String] = T(v.scala)

  override def millSourcePath = os.pwd / "hardfloat" / "hardfloat"

  def chiselModule = None

  def chiselPluginJar = None

  def chiselIvy = Some(v.chiselCrossVersions(crossValue)._1)

  def chiselPluginIvy = Some(v.chiselCrossVersions(crossValue)._2)
}

object cde extends CDE

trait CDE
  extends millbuild.cde.common.CDEModule
    with RocketChipPublishModule
    with ScalaModule {

  def scalaVersion: T[String] = T(v.scala)

  override def millSourcePath = os.pwd / "cde" / "cde"
}

trait DiffTest extends difftest.build.CommonDiffTest with RocketChipPublishModule {
  override def millSourcePath = os.pwd / "difftest"
}

object difftestDep extends DiffTest

object rocketchip extends Cross[RocketChip](v.chiselCrossVersions.keys.toSeq)

trait RocketChip
  extends millbuild.common.RocketChipModule
    with RocketChipPublishModule
    with SbtModule
    with Cross.Module[String] {
  def scalaVersion: T[String] = T(v.scala)

  override def millSourcePath = super.millSourcePath / os.up

  def chiselModule = None

  def chiselPluginJar = None

  def chiselIvy = Some(v.chiselCrossVersions(crossValue)._1)

  def chiselPluginIvy = Some(v.chiselCrossVersions(crossValue)._2)

  def macrosModule = macros

  def hardfloatModule = hardfloat(crossValue)

  def cdeModule = cde

  def difftestModule = difftestDep

  def mainargsIvy = v.mainargs

  def json4sJacksonIvy = v.json4sJackson
}

trait RocketChipPublishModule
  extends PublishModule {
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "org.chipsalliance",
    url = "http://github.com/chipsalliance/rocket-chip",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("chipsalliance", "rocket-chip"),
    developers = Seq(
      Developer("aswaterman", "Andrew Waterman", "https://aspire.eecs.berkeley.edu/author/waterman/")
    )
  )

  override def publishVersion: T[String] = T("1.6-SNAPSHOT")
}
