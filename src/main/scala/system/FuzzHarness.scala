package freechips.rocketchip.system


import chisel3._
import chisel3.stage.{ChiselCli, ChiselGeneratorAnnotation, ChiselStage}
import difftest.{DifftestModule, LogCtrlIO, PerfInfoIO, UARTIO}
import firrtl.options.Shell
import firrtl.stage.FirrtlCli
import freechips.rocketchip.devices.debug.DebugModuleKey
import freechips.rocketchip.devices.tilelink.{BootROMLocated, BootROMParams, CLINTKey}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
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
  new WithNBigCores(1).alter((site, _, up) => {
    case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site).map {
      case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
        core = tp.tileParams.core.copy(
          nPMPs = 0
        )
      ))
    }
  }) ++
  new WithCoherentBusTopology ++
  new BaseConfig().alter((site, _, _) => {
    case DebugModuleKey => None
    case CLINTKey => None
    case BootROMLocated(InSubsystem) => Some(BootROMParams(
      contentFileName = "./bootrom/bootrom.img",
      address = 0x10000000,
      hang = 0x10000000
    ))
    case ExtMem => Some(MemoryPortParams(MasterPortParams(
      base = x"8000_0000",
      size = x"8000_0000",
      beatBytes = site(MemoryBusKey).beatBytes,
      idBits = 4), 1))
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
