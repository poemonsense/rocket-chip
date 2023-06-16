package freechips.rocketchip.system


import chisel3._
import chisel3.stage.{ChiselCli, ChiselGeneratorAnnotation, ChiselStage}
import difftest.{DifftestModule, LogCtrlIO, PerfInfoIO, UARTIO}
import firrtl.options.Shell
import firrtl.stage.FirrtlCli
import freechips.rocketchip.devices.debug.DebugModuleKey
import freechips.rocketchip.devices.tilelink.{BootROMLocated, BootROMParams, CLINTKey}
import freechips.rocketchip.subsystem.{InSubsystem, WithCoherentBusTopology, WithNBigCores}
import org.chipsalliance.cde.config.{Config, Parameters}
import xfuzz.CoverPoint

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
  new BaseConfig().alter((_, _, _) => {
    case DebugModuleKey => None
    case CLINTKey => None
    case BootROMLocated(InSubsystem) => Some(BootROMParams(
      contentFileName = "./bootrom/bootrom.img",
      address = 0x10000000,
      hang = 0x10000000
    ))
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
