package freechips.rocketchip.system


import chisel3._
import chisel3.stage.{ChiselCli, ChiselGeneratorAnnotation, ChiselStage}
import difftest.{DifftestModule, LogCtrlIO, PerfInfoIO, UARTIO}
import firrtl.options.Shell
import firrtl.stage.FirrtlCli
import org.chipsalliance.cde.config.{Config, Parameters}
import xfuzz.CoverPoint
import freechips.rocketchip.system.BaseConfig
import freechips.rocketchip.subsystem.{WithCoherentBusTopology, WithNBigCores}
import freechips.rocketchip.devices.debug.DebugModuleKey
import freechips.rocketchip.devices.tilelink.{CLINTKey, PLICKey}

class SimTop()(implicit p: Parameters) extends TestHarness {
  val io = IO(new Bundle {
    val logCtrl = new LogCtrlIO
    val perfInfo = new PerfInfoIO
    val uart = new UARTIO
  })
  io := DontCare
  success := DontCare
}

class FuzzStage extends ChiselStage {
  override val shell: Shell = new Shell("rocket-chip")
    with ChiselCli
    with FirrtlCli
}

class FuzzConfig extends Config(
  new WithNBigCores(1) ++
  new WithCoherentBusTopology ++
  new BaseConfig().alter((site,here,up) => {
    case DebugModuleKey => None
    case CLINTKey => None
  })
)

object FuzzMain {
  def main(args: Array[String]): Unit = {
    (new FuzzStage).execute(args, Seq(
      ChiselGeneratorAnnotation(() => {
        freechips.rocketchip.diplomacy.DisableMonitors(p => new SimTop()(p))(new FuzzConfig)
      })
    ) ++ CoverPoint.getTransforms(args))
    DifftestModule.finish("rocket-chip")
  }
}
